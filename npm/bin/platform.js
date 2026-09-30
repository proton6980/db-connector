const PLATFORM_PACKAGES = {
  "darwin-arm64": "db-connector-mcp-darwin-arm64",
  "darwin-x64": "db-connector-mcp-darwin-x64",
  "linux-x64": "db-connector-mcp-linux-x64",
  "win32-x64": "db-connector-mcp-win32-x64",
};

const PLATFORM_LAUNCHERS = {
  "darwin-arm64": "bin/db-connector",
  "darwin-x64": "bin/db-connector",
  "linux-x64": "bin/db-connector",
  "win32-x64": "bin/db-connector.cmd",
};

function getPlatformKey() {
  return `${process.platform}-${process.arch}`;
}

module.exports = { PLATFORM_PACKAGES, PLATFORM_LAUNCHERS, getPlatformKey };
