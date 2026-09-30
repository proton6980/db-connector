#!/usr/bin/env node
const { spawn } = require("child_process");
const path = require("path");
const fs = require("fs");
const http = require("http");
const net = require("net");

const { PLATFORM_PACKAGES, PLATFORM_LAUNCHERS, getPlatformKey } = require("./platform");

function findRuntime() {
  const platformKey = getPlatformKey();
  const javaExe = process.platform === "win32" ? "java.exe" : "java";

  // 1. Adjacent JRE (GitHub Release layout: jre/ and db-connector.jar next to bin/)
  const pkgRoot = path.join(__dirname, "..");
  const adjacentJava = path.join(pkgRoot, "jre", "bin", javaExe);
  const adjacentJar = path.join(pkgRoot, "db-connector.jar");
  if (fs.existsSync(adjacentJava) && fs.existsSync(adjacentJar)) {
    return { java: adjacentJava, jar: adjacentJar, source: "adjacent" };
  }

  // 2. Platform package via require.resolve (npx optionalDependencies)
  const pkgName = PLATFORM_PACKAGES[platformKey];
  if (pkgName) {
    try {
      const launcherRel = PLATFORM_LAUNCHERS[platformKey];
      const launcherPath = require.resolve(`${pkgName}/${launcherRel}`);
      const platformPkgRoot = path.join(path.dirname(launcherPath), "..");
      const platformJava = path.join(platformPkgRoot, "jre", "bin", javaExe);
      const platformJar = path.join(platformPkgRoot, "db-connector.jar");
      if (fs.existsSync(platformJava) && fs.existsSync(platformJar)) {
        return { java: platformJava, jar: platformJar, source: `platform-package:${pkgName}` };
      }
    } catch (_) {}
  }

  // 3. Local platforms directory (dev/debug after release.sh build)
  if (pkgName) {
    const localRoot = path.join(__dirname, "..", "platforms", platformKey);
    const localJava = path.join(localRoot, "jre", "bin", javaExe);
    const localJar = path.join(localRoot, "db-connector.jar");
    if (fs.existsSync(localJava) && fs.existsSync(localJar)) {
      return { java: localJava, jar: localJar, source: `local:${platformKey}` };
    }
  }

  return null;
}

const preferredJavaPort = parseInt(process.env.DBCONNECTOR_PORT || "63306", 10);
const preferredWebPort = parseInt(process.env.DBCONNECTOR_WEB_PORT || "63380", 10);

const runtime = findRuntime();
if (!runtime) {
  const platformKey = getPlatformKey();
  const pkgName = PLATFORM_PACKAGES[platformKey];
  console.error("No Java runtime found for this platform.");
  console.error(`Platform: ${platformKey}`);
  if (pkgName) {
    console.error(`Expected platform package: ${pkgName}`);
  }
  console.error("Please reinstall db-connector-mcp or report this issue.");
  process.exit(1);
}

function tryListen(port, host, extra) {
  return new Promise((resolve) => {
    const server = net.createServer();
    server.on("error", (err) => resolve(err.code || "ERROR"));
    server.listen({ port, host, ...extra }, () => {
      server.close(() => resolve(null));
    });
  });
}

async function isPortAvailable(port) {
  // Probe both bind styles: the JVM binds an IPv6 "::" dual-stack wildcard,
  // while Node/Express binds 127.0.0.1, and neither probe alone catches the
  // other — a 127.0.0.1 probe passes next to Java's socket (JVM then dies
  // with BindException), and a wildcard probe passes next to a Node socket
  // (Express then crashes with EADDRINUSE). Port is free only if every probe
  // succeeds. Hosts without IPv6 fall back to the IPv4 wildcard.
  const checks = [tryListen(port, "127.0.0.1")];
  const wildcard = await tryListen(port, "::", { ipv6Only: false });
  if (wildcard === "EAFNOSUPPORT" || wildcard === "EADDRNOTAVAIL" || wildcard === "EPROTONOSUPPORT") {
    checks.push(tryListen(port, "0.0.0.0"));
  } else {
    checks.push(wildcard);
  }
  return (await Promise.all(checks)).every((err) => err === null);
}

async function findAvailablePort(preferred, maxTries = 100) {
  for (let port = preferred; port < preferred + maxTries; port++) {
    if (await isPortAvailable(port)) {
      return port;
    }
  }
  throw new Error(
    `No available port found in range ${preferred}–${preferred + maxTries - 1}`
  );
}

let javaPort, webPort, java;

function startJava(port, webPort) {
  java = spawn(runtime.java, ["-jar", runtime.jar], {
    stdio: "pipe",
    env: {
      ...process.env,
      DBCONNECTOR_PORT: String(port),
      // Real web port so Java's CORS allowed-origin matches the console even
      // when 63380 was taken and a fallback port was chosen.
      DBCONNECTOR_WEB_PORT: String(webPort),
    },
  });

  java.stdout.on("data", (data) => {
    process.stdout.write(`[java] ${data}`);
  });

  java.stderr.on("data", (data) => {
    process.stderr.write(`[java] ${data}`);
  });

  java.on("close", (code) => {
    console.error(`Java process exited with code ${code}`);
    process.exit(code || 1);
  });
}

function waitForJava(maxRetries, interval) {
  return new Promise((resolve, reject) => {
    let attempts = 0;
    function check() {
      const req = http.get(`http://127.0.0.1:${javaPort}/`, (res) => {
        res.resume();
        resolve();
      });
      req.on("error", () => {
        attempts++;
        if (attempts >= maxRetries) {
          reject(new Error(`Java did not start after ${maxRetries * interval}ms`));
        } else {
          setTimeout(check, interval);
        }
      });
      req.setTimeout(2000, () => {
        req.destroy();
        attempts++;
        if (attempts >= maxRetries) {
          reject(new Error(`Java health check timed out after ${maxRetries} attempts`));
        } else {
          setTimeout(check, interval);
        }
      });
    }
    check();
  });
}

async function startFrontend() {
  // 1. Find available Java port
  javaPort = await findAvailablePort(preferredJavaPort);
  if (javaPort !== preferredJavaPort) {
    console.warn(`Port ${preferredJavaPort} is in use, using ${javaPort} instead`);
  }

  // 2. Pick the web port BEFORE spawning Java: it is passed to the JVM as its
  // CORS allowed-origin, so choosing it later leaves the console blocked.
  webPort = await findAvailablePort(preferredWebPort);
  if (webPort !== preferredWebPort) {
    console.warn(`Port ${preferredWebPort} is in use, using ${webPort} instead`);
  }

  // 3. Start Java with both ports and wait for it to be ready
  startJava(javaPort, webPort);

  try {
    await waitForJava(30, 1000);
  } catch (err) {
    console.error(`Warning: ${err.message}. Starting frontend anyway...`);
  }

  const frontendDir = path.join(__dirname, "..", "frontend");
  if (!fs.existsSync(frontendDir)) {
    console.error(`Frontend directory not found: ${frontendDir}`);
    console.error("Falling back to Java-only mode (SSE endpoint only)");
    printInfo();
    return;
  }

  const express = require("express");
  const app = express();

  app.use((req, res, next) => {
    res.header("Access-Control-Allow-Origin", "*");
    res.header("Access-Control-Allow-Methods", "GET, OPTIONS");
    res.header("Access-Control-Allow-Headers", "Content-Type");
    if (req.method === "OPTIONS") {
      return res.sendStatus(204);
    }
    next();
  });

  const apiUrl = `http://127.0.0.1:${javaPort}`;

  app.get("/*.html", (req, res, next) => {
    const filePath = path.join(frontendDir, req.path);
    if (!fs.existsSync(filePath)) {
      return next();
    }
    let html = fs.readFileSync(filePath, "utf-8");
    html = html.replace(
      /window\.__DBCONNECTOR_API_URL__\s*=\s*window\.__DBCONNECTOR_API_URL__\s*\|\|[^;]+;/,
      `window.__DBCONNECTOR_API_URL__ = "${apiUrl}";`
    );
    res.header("Content-Type", "text/html;charset=UTF-8");
    res.send(html);
  });

  app.use(express.static(frontendDir));

  app.listen(webPort, "127.0.0.1", () => {
    printInfo();
  });
}

function printInfo() {
  const pkg = require("../package.json");
  console.log(``);
  console.log(`  db-connector-mcp v${pkg.version}`);
  console.log(`  ─────────────────────────────────────────────`);
  console.log(`  MCP SSE Endpoint:  http://127.0.0.1:${javaPort}/mcp`);
  console.log(`  Web Console:       http://127.0.0.1:${webPort}`);
  console.log(`  ─────────────────────────────────────────────`);
  console.log(``);
}

startFrontend().catch((err) => {
  console.error(`Failed to start frontend: ${err.message}`);
  if (javaPort) {
    printInfo();
  }
});

process.on("SIGINT", () => {
  if (java) java.kill("SIGTERM");
  process.exit(0);
});

process.on("SIGTERM", () => {
  if (java) java.kill("SIGTERM");
  process.exit(0);
});