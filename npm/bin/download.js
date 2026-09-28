const https = require("https");
const fs = require("fs");
const path = require("path");
const { getBinaryName, getDownloadUrl, getPlatformKey, PLATFORM_MAP } = require("./platform");

const binaryName = getBinaryName();
const downloadUrl = getDownloadUrl();
const nativeDir = path.join(__dirname, "native");
const binaryPath = path.join(nativeDir, binaryName || "");

function download(url, dest) {
  return new Promise((resolve, reject) => {
    const file = fs.createWriteStream(dest);
    let redirectCount = 0;

    function follow(currentUrl) {
      if (redirectCount++ > 10) {
        reject(new Error("Too many redirects"));
        return;
      }

      const req = https.get(currentUrl, { headers: { "User-Agent": "node" } }, (res) => {
        if (res.statusCode >= 300 && res.statusCode < 400 && res.headers.location) {
          follow(res.headers.location);
          return;
        }
        if (res.statusCode !== 200) {
          reject(new Error(`Download failed: HTTP ${res.statusCode}`));
          return;
        }

        const total = parseInt(res.headers["content-length"], 10);
        let downloaded = 0;
        let lastPercent = -1;

        res.on("data", (chunk) => {
          downloaded += chunk.length;
          if (total) {
            const percent = Math.floor((downloaded / total) * 100);
            if (percent !== lastPercent && percent % 10 === 0) {
              lastPercent = percent;
              process.stderr.write(`\rDownloading ${binaryName}: ${percent}%`);
            }
          }
        });

        res.pipe(file);
        file.on("finish", () => {
          file.close();
          process.stderr.write("\r");
          resolve();
        });
      });

      req.on("error", (err) => {
        fs.unlink(dest, () => {});
        reject(err);
      });

      req.setTimeout(120000, () => {
        req.destroy();
        reject(new Error("Download timeout"));
      });
    }

    follow(url);
  });
}

async function main() {
  if (!binaryName) {
    const key = getPlatformKey();
    const supported = Object.keys(PLATFORM_MAP).join(", ");
    console.error(`Unsupported platform: ${key}`);
    console.error(`Supported platforms: ${supported}`);
    console.error("You can download binaries manually from:");
    console.error("  https://github.com/proton6980/db-connector/releases/tag/v0.1.0-native");
    process.exit(1);
  }

  if (fs.existsSync(binaryPath)) {
    return;
  }

  if (!fs.existsSync(nativeDir)) {
    fs.mkdirSync(nativeDir, { recursive: true });
  }

  console.log(`Installing db-connector-mcp for ${getPlatformKey()}...`);

  try {
    await download(downloadUrl, binaryPath);
    fs.chmodSync(binaryPath, 0o755);
    console.log(`Installed: ${binaryName}`);
  } catch (err) {
    console.error(`Download failed: ${err.message}`);
    console.error("Please download manually from:");
    console.error(`  ${downloadUrl}`);
    console.error(`And place it at: ${binaryPath}`);
    process.exit(1);
  }
}

main();