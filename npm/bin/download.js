const https = require("https");
const fs = require("fs");
const path = require("path");
const {
  fetchLatestTag,
  getBinaryName, getNativeUrl, getPlatformKey,
  getLegacyUrl, getLegacyName, getLegacyPath,
  getJarFallbackUrl, getJarFallbackName,
  getBinaryPath, getJarPath,
  PLATFORM_MAP, FALLBACK_TAG,
} = require("./platform");

const nativeDir = path.join(__dirname, "native");

function download(url, dest, displayName) {
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
        if (res.statusCode === 404) {
          const err = new Error("NOT_FOUND");
          err.code = 404;
          reject(err);
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
              process.stderr.write(`\rDownloading ${displayName}: ${percent}%`);
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
  const platformKey = getPlatformKey();

  if (!getBinaryName()) {
    const supported = Object.keys(PLATFORM_MAP).join(", ");
    console.error(`Unsupported platform: ${platformKey}`);
    console.error(`Supported platforms: ${supported}`);
    console.error("You can download binaries manually from:");
    console.error("  https://github.com/proton6980/db-connector/releases/latest");
    process.exit(1);
  }

  const tag = await fetchLatestTag();
  let binaryName = getBinaryName();
  let downloadUrl = getNativeUrl(tag);
  let binaryPath = getBinaryPath();
  let isJar = false;

  if (fs.existsSync(binaryPath)) {
    return;
  }

  if (!fs.existsSync(nativeDir)) {
    fs.mkdirSync(nativeDir, { recursive: true });
  }

  console.log(`Installing db-connector-mcp for ${platformKey} (${tag})...`);

  try {
    await download(downloadUrl, binaryPath, binaryName);
    fs.chmodSync(binaryPath, 0o755);
    console.log(`Installed: ${binaryName} (native)`);
  } catch (err) {
    if (err.code === 404) {
      const legacyUrl = getLegacyUrl(tag);

      if (legacyUrl) {
        console.log(`Standard native not found, trying legacy alias...`);
        try {
          binaryName = getLegacyName();
          downloadUrl = legacyUrl;
          binaryPath = getLegacyPath();
          await download(downloadUrl, binaryPath, binaryName);
          fs.chmodSync(binaryPath, 0o755);
          console.log(`Installed: ${binaryName} (native, legacy name)`);
        } catch (err2) {
          if (err2.code === 404) {
            console.log(`Legacy alias not found either, falling back to JAR...`);
            binaryName = getJarFallbackName(tag);
            downloadUrl = getJarFallbackUrl(tag);
            binaryPath = getJarPath(tag);
            isJar = true;

            try {
              await download(downloadUrl, binaryPath, binaryName);
              console.log(`Installed: ${binaryName} (JAR fallback)`);
            } catch (err3) {
              console.error(`Download failed: ${err3.message}`);
              console.error("Please download manually from:");
              console.error(`  ${downloadUrl}`);
              console.error(`And place it at: ${binaryPath}`);
              process.exit(1);
            }
          } else {
            console.error(`Download failed: ${err2.message}`);
            process.exit(1);
          }
        }
      } else {
        console.log(`Native binary not found, falling back to JAR...`);
        binaryName = getJarFallbackName(tag);
        downloadUrl = getJarFallbackUrl(tag);
        binaryPath = getJarPath(tag);
        isJar = true;

        try {
          await download(downloadUrl, binaryPath, binaryName);
          console.log(`Installed: ${binaryName} (JAR fallback)`);
        } catch (err2) {
          console.error(`Download failed: ${err2.message}`);
          console.error("Please download manually from:");
          console.error(`  ${downloadUrl}`);
          console.error(`And place it at: ${binaryPath}`);
          process.exit(1);
        }
      }
    } else {
      console.error(`Download failed: ${err.message}`);
      console.error("Please download manually from:");
      console.error(`  ${downloadUrl}`);
      console.error(`And place it at: ${binaryPath}`);
      process.exit(1);
    }
  }
}

main();