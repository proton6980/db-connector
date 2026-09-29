#!/usr/bin/env node
const { execFileSync } = require("child_process");
const path = require("path");
const fs = require("fs");
const { getBinaryName, getBinaryPath } = require("./platform");

const binaryPath = getBinaryPath();

if (!fs.existsSync(binaryPath)) {
  console.error("Native binary not found. Please run: npm install");
  console.error(`Expected at: ${binaryPath}`);
  process.exit(1);
}

const args = process.argv.slice(2);

try {
  execFileSync(binaryPath, args, {
    stdio: "inherit",
    env: { ...process.env },
  });
} catch (err) {
  if (err.status) {
    process.exit(err.status);
  }
  console.error(`Failed to start db-connector-mcp: ${err.message}`);
  process.exit(1);
}