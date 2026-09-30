#!/usr/bin/env node
const path = require("path");
const fs = require("fs");
const express = require("express");

const javaPort = parseInt(process.env.DBCONNECTOR_PORT || "63306", 10);
const webPort = parseInt(process.env.DBCONNECTOR_WEB_PORT || "63380", 10);
const apiUrl = `http://127.0.0.1:${javaPort}`;
const frontendDir = path.join(__dirname, "..", "frontend");

if (!fs.existsSync(frontendDir)) {
  console.error(`Frontend directory not found: ${frontendDir}`);
  process.exit(1);
}

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

app.listen(webPort, () => {
  console.log(`Frontend dev server: http://127.0.0.1:${webPort}`);
  console.log(`Proxying API to:  ${apiUrl}`);
  console.log(`Start Java in IDEA first (DbConnectorApplication), then open the URL above.`);
});
