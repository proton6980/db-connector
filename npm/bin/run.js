#!/usr/bin/env node
const { spawn } = require("child_process");
const path = require("path");
const fs = require("fs");
const http = require("http");
const { getBinaryPath } = require("./platform");

const JAVA_PORT = parseInt(process.env.DBCONNECTOR_PORT || "8080", 10);
const WEB_PORT = parseInt(process.env.DBCONNECTOR_WEB_PORT || "8081", 10);
const binaryPath = getBinaryPath();

if (!fs.existsSync(binaryPath)) {
  console.error("Native binary not found. Please run: npm install");
  console.error(`Expected at: ${binaryPath}`);
  process.exit(1);
}

const java = spawn(binaryPath, [], {
  stdio: "pipe",
  env: { ...process.env, DBCONNECTOR_PORT: String(JAVA_PORT) },
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

function waitForJava(maxRetries, interval) {
  return new Promise((resolve, reject) => {
    let attempts = 0;
    function check() {
      const req = http.get(`http://127.0.0.1:${JAVA_PORT}/`, (res) => {
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

  const apiUrl = `http://127.0.0.1:${JAVA_PORT}`;

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

  app.listen(WEB_PORT, "127.0.0.1", () => {
    printInfo();
  });
}

function printInfo() {
  const pkg = require("../package.json");
  console.log(``);
  console.log(`  db-connector-mcp v${pkg.version}`);
  console.log(`  ─────────────────────────────────────────────`);
  console.log(`  MCP SSE Endpoint:  http://127.0.0.1:${JAVA_PORT}/mcp`);
  console.log(`  Web Console:       http://127.0.0.1:${WEB_PORT}`);
  console.log(`  ─────────────────────────────────────────────`);
  console.log(``);
}

startFrontend().catch((err) => {
  console.error(`Failed to start frontend: ${err.message}`);
  printInfo();
});

process.on("SIGINT", () => {
  java.kill("SIGTERM");
  process.exit(0);
});

process.on("SIGTERM", () => {
  java.kill("SIGTERM");
  process.exit(0);
});