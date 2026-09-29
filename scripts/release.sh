#!/usr/bin/env bash
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
VERSION_FILE="${PROJECT_DIR}/VERSION"
POM_XML="${PROJECT_DIR}/pom.xml"
PACKAGE_JSON="${PROJECT_DIR}/npm/package.json"
PLATFORM_JS="${PROJECT_DIR}/npm/bin/platform.js"
INSTALL_SH="${PROJECT_DIR}/npm/install.sh"
DIST_DIR="${PROJECT_DIR}/dist"

usage() {
  cat <<EOF
Usage: $(basename "$0") <command> [options]

Commands:
  sync         Sync VERSION to pom.xml, package.json, platform.js, install.sh
  build        Build native image for current platform
  release      Sync version + build + show upload instructions
  bump <ver>   Bump version (e.g., bump 0.2.0)

Options:
  --no-upx     Skip UPX compression
  --skip-test  Skip smoke test

Examples:
  $(basename "$0") bump 0.2.0
  $(basename "$0") sync
  $(basename "$0") build
  $(basename "$0") release
  $(basename "$0") release --no-upx
EOF
  exit 1
}

read_version() {
  if [ ! -f "$VERSION_FILE" ]; then
    echo "ERROR: VERSION file not found at ${VERSION_FILE}"
    exit 1
  fi
  tr -d '[:space:]' < "$VERSION_FILE"
}

detect_platform() {
  local OS ARCH
  OS="$(uname -s | tr '[:upper:]' '[:lower:]')"
  ARCH="$(uname -m)"

  case "$OS" in
    darwin) OS="darwin" ;;
    linux)  OS="linux" ;;
    mingw*|msys*|cygwin*|windows_nt) OS="windows" ;;
    *) echo "Unsupported OS: $OS"; exit 1 ;;
  esac

  case "$ARCH" in
    x86_64|amd64)  ARCH="x64" ;;
    arm64|aarch64) ARCH="arm64" ;;
    *) echo "Unsupported architecture: $ARCH"; exit 1 ;;
  esac

  echo "${OS}-${ARCH}"
}

get_artifact_name() {
  local platform="$1"
  case "$platform" in
    darwin-arm64) echo "db-connector-darwin-arm64" ;;
    darwin-x64)   echo "db-connector-darwin-x64" ;;
    linux-x64)    echo "db-connector-linux-x64" ;;
    windows-x64)  echo "db-connector-windows-x64.exe" ;;
    *) echo "Unsupported platform: $platform"; exit 1 ;;
  esac
}

cmd_bump() {
  local new_ver="$1"
  if [ -z "$new_ver" ]; then
    echo "ERROR: Version argument required. Usage: $(basename "$0") bump 0.2.0"
    exit 1
  fi
  echo "$new_ver" > "$VERSION_FILE"
  echo "VERSION file updated to: ${new_ver}"
  cmd_sync
}

cmd_sync() {
  local ver
  ver="$(read_version)"
  local tag="v${ver}"

  echo "Syncing version ${ver} (tag ${tag}) to all files..."

  # pom.xml — only replace the project version (after <artifactId>db-connector</artifactId>)
  if [ -f "$POM_XML" ]; then
    local line
    line=$(grep -n '<artifactId>db-connector</artifactId>' "$POM_XML" | head -1 | cut -d: -f1)
    if [ -n "$line" ]; then
      local next_line=$((line + 1))
      sed -E -i.bak "${next_line}s|<version>[0-9]+\.[0-9]+\.[0-9]+</version>|<version>${ver}</version>|" "$POM_XML"
      rm -f "${POM_XML}.bak"
      echo "  ✓ pom.xml (line ${next_line})"
    else
      echo "  ⚠ pom.xml: could not find <artifactId>db-connector</artifactId>"
    fi
  fi

  # package.json
  if [ -f "$PACKAGE_JSON" ]; then
    sed -E -i.bak "s|\"version\": \"[0-9]+\.[0-9]+\.[0-9]+\"|\"version\": \"${ver}\"|" "$PACKAGE_JSON"
    rm -f "${PACKAGE_JSON}.bak"
    echo "  ✓ npm/package.json"
  fi

  # platform.js
  if [ -f "$PLATFORM_JS" ]; then
    sed -E -i.bak "s|const RELEASE_TAG = \"v[0-9]+\.[0-9]+\.[0-9]+\";|const RELEASE_TAG = \"${tag}\";|" "$PLATFORM_JS"
    rm -f "${PLATFORM_JS}.bak"
    echo "  ✓ npm/bin/platform.js"
  fi

  # install.sh
  if [ -f "$INSTALL_SH" ]; then
    sed -E -i.bak "s|RELEASE_TAG=\"v[0-9]+\.[0-9]+\.[0-9]+\"|RELEASE_TAG=\"${tag}\"|" "$INSTALL_SH"
    rm -f "${INSTALL_SH}.bak"
    echo "  ✓ npm/install.sh"
  fi

  echo "Done. All files synced to version ${ver}."
}

cmd_build() {
  local ver tag platform artifact use_upx=true skip_test=false

  while [ $# -gt 0 ]; do
    case "$1" in
      --no-upx)    use_upx=false; shift ;;
      --skip-test) skip_test=true; shift ;;
      *) shift ;;
    esac
  done

  ver="$(read_version)"
  tag="v${ver}"
  platform="$(detect_platform)"
  artifact="$(get_artifact_name "$platform")"

  echo "=========================================="
  echo " Building db-connector ${ver}"
  echo " Platform: ${platform}"
  echo " Artifact: ${artifact}"
  echo "=========================================="

  # Detect GraalVM (prefer JDK 25 for micronaut-parent 5.2.0+)
  local GRAALVM_HOME=""
  if [ -d "/Users/feiyu/Library/Java/JavaVirtualMachines/graalvm-jdk-25/Contents/Home" ]; then
    GRAALVM_HOME="/Users/feiyu/Library/Java/JavaVirtualMachines/graalvm-jdk-25/Contents/Home"
  elif [ -d "${HOME}/.sdkman/candidates/java/25-graalce" ]; then
    GRAALVM_HOME="${HOME}/.sdkman/candidates/java/25-graalce"
  fi

  if [ -n "$GRAALVM_HOME" ]; then
    export JAVA_HOME="$GRAALVM_HOME"
    export PATH="$JAVA_HOME/bin:$PATH"
    echo "Using GraalVM JDK 25: $GRAALVM_HOME"
  fi

  if ! command -v native-image &>/dev/null; then
    echo "ERROR: native-image not found. Install GraalVM JDK 25 first."
    exit 1
  fi

  echo ""
  echo "--- GraalVM Info ---"
  java -version 2>&1 || true
  native-image --version 2>&1 || true
  echo ""

  # Build native image
  echo "--- Building Native Image ---"
  cd "$PROJECT_DIR"
  mvn -Pnative native:compile -DskipTests

  # Rename artifact
  mkdir -p "$DIST_DIR"
  if [ "$platform" = "windows-x64" ]; then
    mv target/db-connector.exe "${DIST_DIR}/${artifact}"
  else
    mv target/db-connector "${DIST_DIR}/${artifact}"
  fi
  chmod +x "${DIST_DIR}/${artifact}"

  # UPX compression
  if [ "$use_upx" = true ] && [ "$platform" != "darwin-arm64" ] && [ "$platform" != "darwin-x64" ]; then
    if command -v upx &>/dev/null; then
      echo ""
      echo "--- Compressing with UPX ---"
      upx --best "${DIST_DIR}/${artifact}"
    else
      echo "UPX not found, skipping compression."
    fi
  else
    echo "Skipping UPX (macOS not supported or --no-upx)."
  fi

  # Report size
  echo ""
  echo "--- Build Result ---"
  ls -lh "${DIST_DIR}/${artifact}"

  # Smoke test
  if [ "$skip_test" = false ] && [ "$platform" != "windows-x64" ]; then
    echo ""
    echo "--- Smoke Test ---"
    local binary="${DIST_DIR}/${artifact}"
    "$binary" &
    local app_pid=$!
    sleep 5

    local http_code
    http_code=$(curl -s -o /dev/null -w "%{http_code}" http://127.0.0.1:63306/ 2>/dev/null || echo "000")
    kill "$app_pid" 2>/dev/null || true
    wait "$app_pid" 2>/dev/null || true

    if [ "$http_code" = "302" ] || [ "$http_code" = "200" ]; then
      echo "✓ Smoke test passed: HTTP ${http_code}"
    else
      echo "⚠ Smoke test: HTTP ${http_code} (may be normal if port conflict)"
    fi
  fi

  echo ""
  echo "✓ Build complete: ${DIST_DIR}/${artifact}"
}

cmd_release() {
  local use_upx=true skip_test=false

  while [ $# -gt 0 ]; do
    case "$1" in
      --no-upx)    use_upx=false; shift ;;
      --skip-test) skip_test=true; shift ;;
      *) shift ;;
    esac
  done

  local ver tag platform artifact
  ver="$(read_version)"
  tag="v${ver}"
  platform="$(detect_platform)"
  artifact="$(get_artifact_name "$platform")"

  # Step 1: Sync version
  echo "=== Step 1: Sync Version ==="
  cmd_sync

  # Step 2: Build
  echo ""
  echo "=== Step 2: Build Native Image ==="
  local build_args=()
  [ "$use_upx" = false ] && build_args+=("--no-upx")
  [ "$skip_test" = true ] && build_args+=("--skip-test")
  cmd_build "${build_args[@]+${build_args[@]}}"

  # Step 3: Instructions
  echo ""
  echo "=========================================="
  echo " Release Instructions for ${tag}"
  echo "=========================================="
  echo ""
  echo "1. Commit and tag:"
  echo "     git add -A && git commit -m 'release ${tag}'"
  echo "     git tag ${tag}"
  echo "     git push origin main --tags"
  echo ""
  echo "2. Create GitHub Release:"
  echo "     https://github.com/proton6980/db-connector/releases/new?tag=${tag}"
  echo ""
  echo "3. Upload artifact:"
  echo "     ${DIST_DIR}/${artifact}"
  echo ""
  echo "   Or use gh CLI (if available):"
  echo "     gh release create ${tag} ${DIST_DIR}/${artifact} --title '${tag}' --notes 'Release ${ver}'"
  echo ""
  echo "4. For cross-platform, build on each OS and upload all artifacts:"
  echo "     - db-connector-darwin-arm64  (macOS Apple Silicon)"
  echo "     - db-connector-darwin-x64    (macOS Intel)"
  echo "     - db-connector-linux-x64     (Linux x64)"
  echo "     - db-connector-windows-x64.exe (Windows x64)"
  echo ""
}

# Main
if [ $# -eq 0 ]; then
  usage
fi

CMD="$1"
shift

case "$CMD" in
  bump)   cmd_bump "$@" ;;
  sync)   cmd_sync ;;
  build)  cmd_build "$@" ;;
  release) cmd_release "$@" ;;
  *)      usage ;;
esac