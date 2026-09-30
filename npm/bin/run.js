#!/usr/bin/env node
const { spawn } = require("child_process");
const path = require("path");
const fs = require("fs");
const http = require("http");
const net = require("net");
const { getBinaryPath, getLegacyPath } = require("./platform");

function findJar() {
  const nativeDir = path.join(__dirname, "native");
  if (!fs.existsSync(nativeDir)) return null;
  const files = fs.readdirSync(nativeDir);
  const jar = files.find((f) => f.startsWith("db-connector-") && f.endsWith(".jar"));
  return jar ? path.join(nativeDir, jar) : null;
}

const preferredJavaPort = parseInt(process.env.DBCONNECTOR_PORT || "63306", 10);
const preferredWebPort = parseInt(process.env.DBCONNECTOR_WEB_PORT || "63380", 10);

let binaryPath = getBinaryPath();
let isJar = false;

if (!fs.existsSync(binaryPath)) {
  const legacyPath = getLegacyPath();
  if (legacyPath && fs.existsSync(legacyPath)) {
    binaryPath = legacyPath;
  } else {
    const jarPath = findJar();
    if (jarPath) {
      binaryPath = jarPath;
      isJar = true;
    } else {
      console.error("Binary not found. Please run: npm install");
      console.error(`Expected at: ${getBinaryPath()}, or db-connector-*.jar`);
      process.exit(1);
    }
  }
}

if (isJar) {
  const javaHome = process.env.JAVA_HOME;
  const javaCmd = javaHome ? path.join(javaHome, "bin", "java") : "java";
  try {
    const { execSync } = require("child_process");
    execSync(`"${javaCmd}" -version`, { stdio: "pipe" });
  } catch (e) {
    console.error("Java is required to run db-connector (JAR fallback mode).");
    console.error(`Native binary not available for this platform, and Java was not found.`);
    console.error(`Please install JDK 17+ from https://adoptium.net/`);
    process.exit(1);
  }
}

function isPortAvailable(port) {
  return new Promise((resolve) => {
    const server = net.createServer();
    server.on("error", () => resolve(false));
    server.listen(port, "127.0.0.1", () => {
      server.close(() => resolve(true));
    });
  });
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

let javaPort, webPort, java; // resolved at runtime

function startJava(port) {
  if (isJar) {
    const javaHome = process.env.JAVA_HOME;
    const javaCmd = javaHome ? path.join(javaHome, "bin", "java") : "java";
    java = spawn(javaCmd, ["-jar", binaryPath], {
      stdio: "pipe",
      env: {
        ...process.env,
        DBCONNECTOR_PORT: String(port),
        DBCONNECTOR_WEB_PORT: "",
      },
    });
  } else {
    java = spawn(binaryPath, [], {
      stdio: "pipe",
      env: {
        ...process.env,
        DBCONNECTOR_PORT: String(port),
        DBCONNECTOR_WEB_PORT: "",
      },
    });
  }

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

  // 2. Start Java first to occupy the port
  startJava(javaPort);

  // 3. Wait for Java to be ready
  try {
    await waitForJava(30, 1000);
  } catch (err) {
    console.error(`Warning: ${err.message}. Starting frontend anyway...`);
  }

  // 4. Find available Web port (Java already occupies javaPort, no conflict)
  webPort = await findAvailablePort(preferredWebPort);
  if (webPort !== preferredWebPort) {
    console.warn(`Port ${preferredWebPort} is in use, using ${webPort} instead`);
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