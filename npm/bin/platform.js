const GITHUB_REPO = "proton6980/db-connector";
const FALLBACK_TAG = "v0.3.0";
const API_LATEST = `https://api.github.com/repos/${GITHUB_REPO}/releases/latest`;

const PLATFORM_MAP = {
  "darwin-arm64": "db-connector-darwin-arm64",
  "darwin-x64": "db-connector-darwin-x64",
  "linux-x64": "db-connector-linux-x64",
  "win32-x64": "db-connector-windows-x64.exe",
};

const LEGACY_ALIASES = {
  "darwin-arm64": "db-connector",
};

function buildBaseUrl(tag) {
  return `https://github.com/${GITHUB_REPO}/releases/download/${tag}`;
}

function buildJarName(tag) {
  const version = tag.replace(/^v/, "");
  return `db-connector-${version}.jar`;
}

function fetchLatestTag() {
  return new Promise((resolve, reject) => {
    const https = require("https");
    const req = https.get(API_LATEST, {
      headers: { "User-Agent": "node", "Accept": "application/vnd.github+json" },
    }, (res) => {
      let data = "";
      res.on("data", (chunk) => { data += chunk; });
      res.on("end", () => {
        try {
          if (res.statusCode !== 200) {
            console.error(`Warning: GitHub API returned ${res.statusCode}, using fallback ${FALLBACK_TAG}`);
            resolve(FALLBACK_TAG);
            return;
          }
          const release = JSON.parse(data);
          resolve(release.tag_name || FALLBACK_TAG);
        } catch (e) {
          console.error(`Warning: Failed to parse release info, using fallback ${FALLBACK_TAG}`);
          resolve(FALLBACK_TAG);
        }
      });
    });
    req.on("error", () => {
      console.error(`Warning: GitHub API unreachable, using fallback ${FALLBACK_TAG}`);
      resolve(FALLBACK_TAG);
    });
    req.setTimeout(10000, () => {
      req.destroy();
      console.error(`Warning: GitHub API timeout, using fallback ${FALLBACK_TAG}`);
      resolve(FALLBACK_TAG);
    });
  });
}

function getPlatformKey() {
  return `${process.platform}-${process.arch}`;
}

function getBinaryName() {
  const key = getPlatformKey();
  return PLATFORM_MAP[key] || null;
}

function getNativeUrl(tag) {
  const name = getBinaryName();
  if (!name) return null;
  return `${buildBaseUrl(tag)}/${name}`;
}

function getLegacyUrl(tag) {
  const key = getPlatformKey();
  const alias = LEGACY_ALIASES[key];
  if (!alias) return null;
  return `${buildBaseUrl(tag)}/${alias}`;
}

function getJarFallbackUrl(tag) {
  return `${buildBaseUrl(tag)}/${buildJarName(tag)}`;
}

function getJarFallbackName(tag) {
  return buildJarName(tag);
}

function getBinaryPath() {
  const path = require("path");
  return path.join(__dirname, "native", getBinaryName() || "");
}

function getLegacyPath() {
  const name = getLegacyName();
  if (!name) return null;
  const path = require("path");
  return path.join(__dirname, "native", name);
}

function getLegacyName() {
  const key = getPlatformKey();
  return LEGACY_ALIASES[key] || null;
}

function getJarPath(tag) {
  const path = require("path");
  return path.join(__dirname, "native", buildJarName(tag));
}

module.exports = {
  fetchLatestTag,
  getPlatformKey, getBinaryName, getNativeUrl,
  getLegacyUrl, getLegacyName, getLegacyPath,
  getJarFallbackUrl, getJarFallbackName,
  getBinaryPath, getJarPath,
  PLATFORM_MAP, FALLBACK_TAG,
};