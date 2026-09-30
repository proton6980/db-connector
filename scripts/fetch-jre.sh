#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<EOF
Usage: $(basename "$0") <dest-jre-home>

Download Eclipse Temurin 25 JRE for the current platform,
verify SHA256, and normalize the layout so that
  <dest-jre-home>/bin/java  (or java.exe on Windows)
is directly executable.

A jre-version.txt file is written next to <dest-jre-home>.

Supported platforms:
  darwin-arm64  darwin-x64  linux-x64  win32-x64
EOF
  exit 1
}

if [ $# -ne 1 ]; then
  usage
fi

DEST_JRE_HOME="$1"

detect_platform() {
  local OS ARCH
  OS="$(uname -s | tr '[:upper:]' '[:lower:]')"
  ARCH="$(uname -m)"

  case "$OS" in
    darwin) OS="darwin" ;;
    linux)  OS="linux" ;;
    mingw*|msys*|cygwin*|windows_nt) OS="win32" ;;
    *) echo "ERROR: Unsupported OS: $OS"; exit 1 ;;
  esac

  case "$ARCH" in
    x86_64|amd64)  ARCH="x64" ;;
    arm64|aarch64) ARCH="arm64" ;;
    *) echo "ERROR: Unsupported architecture: $ARCH"; exit 1 ;;
  esac

  echo "${OS}-${ARCH}"
}

PLATFORM="$(detect_platform)"

case "$PLATFORM" in
  darwin-arm64) ADOPTIUM_OS="mac";   ADOPTIUM_ARCH="aarch64" ;;
  darwin-x64)   ADOPTIUM_OS="mac";   ADOPTIUM_ARCH="x64" ;;
  linux-x64)    ADOPTIUM_OS="linux"; ADOPTIUM_ARCH="x64" ;;
  win32-x64)    ADOPTIUM_OS="windows"; ADOPTIUM_ARCH="x64" ;;
  *)
    echo "ERROR: Unsupported platform: ${PLATFORM}"
    echo "Supported: darwin-arm64, darwin-x64, linux-x64, win32-x64"
    exit 1
    ;;
esac

echo "=== Fetching Temurin 25 JRE for ${PLATFORM} ==="

API_URL="https://api.adoptium.net/v3/assets/feature_releases/25/ga?architecture=${ADOPTIUM_ARCH}&image_type=jre&os=${ADOPTIUM_OS}&heap_size=normal&jvm_impl=hotspot&vendor=eclipse&page_size=1"

echo "Querying Adoptium API..."
API_RESPONSE=$(curl -fsSL "$API_URL")

if [ -z "$API_RESPONSE" ]; then
  echo "ERROR: Adoptium API returned empty response"
  exit 1
fi

DOWNLOAD_URL=$(python3 -c "
import json, sys
data = json.load(sys.stdin)
if not data:
    sys.exit('No releases found')
release = data[0]
binary = release['binaries'][0]
print(binary['package']['link'])
" <<< "$API_RESPONSE")

CHECKSUM=$(python3 -c "
import json, sys
data = json.load(sys.stdin)
release = data[0]
binary = release['binaries'][0]
print(binary['package']['checksum'])
" <<< "$API_RESPONSE")

RELEASE_NAME=$(python3 -c "
import json, sys
data = json.load(sys.stdin)
release = data[0]
print(release['release_name'])
" <<< "$API_RESPONSE")

if [ -z "$DOWNLOAD_URL" ] || [ -z "$CHECKSUM" ]; then
  echo "ERROR: Could not parse download URL or checksum from API response"
  exit 1
fi

echo "  Release:  ${RELEASE_NAME}"
echo "  Download: ${DOWNLOAD_URL}"
echo "  Checksum: ${CHECKSUM}"

TMPDIR_WORK=$(mktemp -d)
trap 'rm -rf "$TMPDIR_WORK"' EXIT

FILENAME=$(basename "$DOWNLOAD_URL")
TMP_ARCHIVE="${TMPDIR_WORK}/${FILENAME}"

echo ""
echo "Downloading..."
curl -fsSL -o "$TMP_ARCHIVE" "$DOWNLOAD_URL"

echo "Verifying SHA256..."
if command -v shasum &>/dev/null; then
  ACTUAL=$(shasum -a 256 "$TMP_ARCHIVE" | cut -d' ' -f1)
elif command -v sha256sum &>/dev/null; then
  ACTUAL=$(sha256sum "$TMP_ARCHIVE" | cut -d' ' -f1)
else
  ACTUAL=$(python3 -c "
import hashlib
h = hashlib.sha256()
with open('$TMP_ARCHIVE', 'rb') as f:
    for chunk in iter(lambda: f.read(8192), b''):
        h.update(chunk)
print(h.hexdigest())
")
fi

if [ "$ACTUAL" != "$CHECKSUM" ]; then
  echo "ERROR: SHA256 mismatch!"
  echo "  Expected: ${CHECKSUM}"
  echo "  Actual:   ${ACTUAL}"
  exit 1
fi
echo "  SHA256 OK"

echo ""
echo "Extracting..."
EXTRACT_DIR="${TMPDIR_WORK}/extracted"
mkdir -p "$EXTRACT_DIR"

case "$FILENAME" in
  *.tar.gz)
    tar xzf "$TMP_ARCHIVE" -C "$EXTRACT_DIR"
    ;;
  *.zip)
    python3 -c "
import zipfile, sys
with zipfile.ZipFile('$TMP_ARCHIVE') as z:
    z.extractall('$EXTRACT_DIR')
"
    ;;
  *)
    echo "ERROR: Unknown archive format: ${FILENAME}"
    exit 1
    ;;
esac

JAVA_RELATIVE="bin/java"
if [ "$PLATFORM" = "win32-x64" ]; then
  JAVA_RELATIVE="bin/java.exe"
fi

FOUND_JAVA=""
for candidate in "$EXTRACT_DIR"/*/Contents/Home/"$JAVA_RELATIVE" \
                 "$EXTRACT_DIR"/*/"$JAVA_RELATIVE" \
                 "$EXTRACT_DIR"/Contents/Home/"$JAVA_RELATIVE"; do
  if [ -f "$candidate" ]; then
    FOUND_JAVA="$candidate"
    break
  fi
done

if [ -z "$FOUND_JAVA" ]; then
  echo "ERROR: Could not find ${JAVA_RELATIVE} in extracted archive"
  echo "Archive contents:"
  find "$EXTRACT_DIR" -maxdepth 4 -type f | head -30
  exit 1
fi

JRE_SRC=$(cd "$(dirname "$FOUND_JAVA")/.." && pwd)

echo "  Found JRE at: ${JRE_SRC}"

rm -rf "$DEST_JRE_HOME"
mkdir -p "$(dirname "$DEST_JRE_HOME")"
mv "$JRE_SRC" "$DEST_JRE_HOME"

if [ "$PLATFORM" != "win32-x64" ]; then
  chmod +x "$DEST_JRE_HOME/bin/java" 2>/dev/null || true
fi

JAVA_VERSION=$("$DEST_JRE_HOME/bin/java" -version 2>&1 | head -3)

JRE_VERSION_FILE="$(dirname "$DEST_JRE_HOME")/jre-version.txt"
cat > "$JRE_VERSION_FILE" <<EOF
release_name=${RELEASE_NAME}
platform=${PLATFORM}
${JAVA_VERSION}
EOF

echo ""
echo "=== JRE installed ==="
echo "  Location: ${DEST_JRE_HOME}"
echo "  Version info:"
echo "${JAVA_VERSION}" | sed 's/^/    /'
echo "  Written:  ${JRE_VERSION_FILE}"