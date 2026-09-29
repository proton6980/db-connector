const RELEASE_TAG = "v0.2.2";
const GITHUB_REPO = "proton6980/db-connector";
const BASE_URL = `https://github.com/${GITHUB_REPO}/releases/download/${RELEASE_TAG}`;

const PLATFORM_MAP = {
  "darwin-arm64": "db-connector-darwin-arm64",
  "darwin-x64": "db-connector-darwin-x64",
  "linux-x64": "db-connector-linux-x64",
  "win32-x64": "db-connector-windows-x64.exe",
};

function getPlatformKey() {
  return `${process.platform}-${process.arch}`;
}

function getBinaryName() {
  const key = getPlatformKey();
  return PLATFORM_MAP[key] || null;
}

function getDownloadUrl() {
  const name = getBinaryName();
  if (!name) return null;
  return `${BASE_URL}/${name}`;
}

function getBinaryPath() {
  const path = require("path");
  return path.join(__dirname, "native", getBinaryName() || "");
}

module.exports = { getPlatformKey, getBinaryName, getDownloadUrl, getBinaryPath, PLATFORM_MAP };