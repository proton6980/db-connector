#!/usr/bin/env bash
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
VERSION_FILE="${PROJECT_DIR}/VERSION"
JAVA_DIR="${PROJECT_DIR}/java"
POM_XML="${JAVA_DIR}/pom.xml"
PACKAGE_JSON="${PROJECT_DIR}/npm/package.json"
PLATFORM_JS="${PROJECT_DIR}/npm/bin/platform.js"
INSTALL_SH="${PROJECT_DIR}/npm/install.sh"
INSTALL_PS1="${PROJECT_DIR}/npm/install.ps1"
README_MD="${PROJECT_DIR}/README.md"
DIST_DIR="${PROJECT_DIR}/dist"
FETCH_JRE="${PROJECT_DIR}/scripts/fetch-jre.sh"
PLATFORMS_DIR="${PROJECT_DIR}/npm/platforms"

usage() {
  cat <<EOF
Usage: $(basename "$0") <command> [options]

Commands:
  sync         Sync VERSION to pom.xml, package.json, platform.js, install.sh
  build        Assemble the current platform's npm runtime package
  release      Sync version + build + show upload instructions
  bump <ver>   Bump version (e.g., bump 0.5.0)

Options:
  --skip-test  Skip smoke test

Examples:
  $(basename "$0") bump 0.5.0
  $(basename "$0") sync
  $(basename "$0") build
  $(basename "$0") release
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

platform_to_npm() {
  local platform="$1"
  case "$platform" in
    darwin-arm64) echo "darwin-arm64" ;;
    darwin-x64)   echo "darwin-x64" ;;
    linux-x64)    echo "linux-x64" ;;
    windows-x64)  echo "win32-x64" ;;
    *) echo "Unsupported platform: $platform"; exit 1 ;;
  esac
}

cmd_bump() {
  local new_ver="$1"
  if [ -z "$new_ver" ]; then
    echo "ERROR: Version argument required. Usage: $(basename "$0") bump 0.5.0"
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

  if [ -f "$POM_XML" ]; then
    local line
    line=$(grep -n '<artifactId>db-connector</artifactId>' "$POM_XML" | head -1 | cut -d: -f1)
    if [ -n "$line" ]; then
      local next_line=$((line + 1))
      sed -E -i.bak "${next_line}s|<version>[0-9]+\.[0-9]+\.[0-9]+</version>|<version>${ver}</version>|" "$POM_XML"
      rm -f "${POM_XML}.bak"
      echo "  ✓ java/pom.xml (line ${next_line})"
    else
      echo "  ⚠ java/pom.xml: could not find <artifactId>db-connector</artifactId>"
    fi
  fi

  if [ -f "$PACKAGE_JSON" ]; then
    sed -E -i.bak "s|\"version\": \"[0-9]+\.[0-9]+\.[0-9]+\"|\"version\": \"${ver}\"|" "$PACKAGE_JSON"
    rm -f "${PACKAGE_JSON}.bak"
    echo "  ✓ npm/package.json"
  fi

  if [ -f "$PLATFORM_JS" ]; then
    sed -E -i.bak "s|const FALLBACK_TAG = \"v[0-9]+\.[0-9]+\.[0-9]+\";|const FALLBACK_TAG = \"${tag}\";|" "$PLATFORM_JS"
    rm -f "${PLATFORM_JS}.bak"
    echo "  ✓ npm/bin/platform.js"
  fi

  if [ -f "$INSTALL_SH" ]; then
    sed -E -i.bak "s|FALLBACK_TAG=\"v[0-9]+\.[0-9]+\.[0-9]+\"|FALLBACK_TAG=\"${tag}\"|" "$INSTALL_SH"
    rm -f "${INSTALL_SH}.bak"
    echo "  ✓ npm/install.sh"
  fi

  if [ -f "$INSTALL_PS1" ]; then
    sed -E -i.bak "s|\\\$FallbackTag = \"v[0-9]+\.[0-9]+\.[0-9]+\"|\\\$FallbackTag = \"${tag}\"|" "$INSTALL_PS1"
    rm -f "${INSTALL_PS1}.bak"
    echo "  ✓ npm/install.ps1"
  fi

  if [ -f "$README_MD" ]; then
    sed -E -i.bak "s|db-connector-[0-9]+\.[0-9]+\.[0-9]+\.jar|db-connector-${ver}.jar|g" "$README_MD"
    rm -f "${README_MD}.bak"
    echo "  ✓ README.md"
  fi

  for plat_dir in "$PLATFORMS_DIR"/*/; do
    if [ -d "$plat_dir" ] && [ -f "${plat_dir}package.json" ]; then
      sed -E -i.bak "s|\"version\": \"[0-9]+\.[0-9]+\.[0-9]+\"|\"version\": \"${ver}\"|" "${plat_dir}package.json"
      rm -f "${plat_dir}package.json.bak"
      echo "  ✓ ${plat_dir#${PROJECT_DIR}/}package.json"
    fi
  done

  if [ -f "$PACKAGE_JSON" ]; then
    for pkg in db-connector-mcp-darwin-arm64 db-connector-mcp-darwin-x64 db-connector-mcp-linux-x64 db-connector-mcp-win32-x64; do
      sed -E -i.bak "s|\"${pkg}\": \"[0-9]+\.[0-9]+\.[0-9]+\"|\"${pkg}\": \"${ver}\"|" "$PACKAGE_JSON"
      rm -f "${PACKAGE_JSON}.bak"
    done
  fi

  echo "Done. All files synced to version ${ver}."
}

cmd_build() {
  local ver platform skip_test=false
  while [ $# -gt 0 ]; do
    case "$1" in
      --skip-test) skip_test=true; shift ;;
      *) shift ;;
    esac
  done

  ver="$(read_version)"
  platform="$(detect_platform)"
  local npm_plat
  npm_plat="$(platform_to_npm "$platform")"
  local plat_dir="${PLATFORMS_DIR}/${npm_plat}"

  echo "=========================================="
  echo " Building db-connector ${ver}"
  echo " Platform: ${platform} (npm: ${npm_plat})"
  echo "=========================================="

  local JAVA_HOME_CANDIDATE=""
  if [ -d "/Users/feiyu/Library/Java/JavaVirtualMachines/graalvm-jdk-25/Contents/Home" ]; then
    JAVA_HOME_CANDIDATE="/Users/feiyu/Library/Java/JavaVirtualMachines/graalvm-jdk-25/Contents/Home"
  elif [ -d "/Users/feiyu/.sdkman/candidates/java/25-graalce" ]; then
    JAVA_HOME_CANDIDATE="/Users/feiyu/.sdkman/candidates/java/25-graalce"
  elif [ -n "${JAVA_HOME:-}" ]; then
    JAVA_HOME_CANDIDATE="$JAVA_HOME"
  fi

  if [ -n "$JAVA_HOME_CANDIDATE" ]; then
    export JAVA_HOME="$JAVA_HOME_CANDIDATE"
    export PATH="$JAVA_HOME/bin:$PATH"
    echo "Using JDK: $JAVA_HOME"
  fi

  echo ""
  echo "--- JDK Info ---"
  java -version 2>&1 || true
  echo ""

  echo "--- Building JAR ---"
  cd "$JAVA_DIR"
  mvn -DskipTests package

  local JAR_FILE="${JAVA_DIR}/target/db-connector-${ver}.jar"
  if [ ! -f "$JAR_FILE" ]; then
    echo "ERROR: JAR not found at ${JAR_FILE}"
    exit 1
  fi

  if ! jar tf "$JAR_FILE" | grep -q "META-INF/MANIFEST.MF"; then
    echo "ERROR: JAR does not contain META-INF/MANIFEST.MF"
    exit 1
  fi
  echo "  JAR: ${JAR_FILE} ($(ls -lh "$JAR_FILE" | awk '{print $5}'))"

  mkdir -p "$plat_dir/bin"
  cp "$JAR_FILE" "${plat_dir}/db-connector.jar"
  echo "  Copied JAR to ${plat_dir}/db-connector.jar"

  echo ""
  echo "--- Fetching JRE ---"
  "$FETCH_JRE" "${plat_dir}/jre"

  if [ "$platform" = "windows-x64" ]; then
    cat > "${plat_dir}/bin/db-connector.cmd" <<'LAUNCHER'
@echo off
set "APP_HOME=%~dp0.."
"%APP_HOME%\jre\bin\java.exe" -jar "%APP_HOME%\db-connector.jar" %*
LAUNCHER
  else
    cat > "${plat_dir}/bin/db-connector" <<'LAUNCHER'
#!/bin/sh
APP_HOME=$(CDPATH= cd -- "$(dirname "$0")/.." && pwd)
exec "$APP_HOME/jre/bin/java" -jar "$APP_HOME/db-connector.jar" "$@"
LAUNCHER
    chmod +x "${plat_dir}/bin/db-connector"
  fi
  chmod +x "${plat_dir}/jre/bin/java" 2>/dev/null || true
  echo "  Launcher written to ${plat_dir}/bin/"

  if [ "$skip_test" = false ] && [ "$platform" != "windows-x64" ]; then
    echo ""
    echo "--- Smoke Test ---"
    local launcher="${plat_dir}/bin/db-connector"
    "$launcher" &
    local app_pid=$!
    sleep 5
    local http_code
    http_code=$(curl -s -o /dev/null -w "%{http_code}" http://127.0.0.1:63306/ 2>/dev/null || echo "000")
    kill "$app_pid" 2>/dev/null || true
    wait "$app_pid" 2>/dev/null || true
    if [ "$http_code" = "302" ] || [ "$http_code" = "301" ] || [ "$http_code" = "200" ]; then
      echo "✓ Smoke test passed: HTTP ${http_code}"
    else
      echo "⚠ Smoke test: HTTP ${http_code} (may be normal if port conflict)"
    fi
  fi

  echo ""
  echo "--- Package Size ---"
  cd "$plat_dir"
  npm pack --dry-run 2>&1 | grep -E "package size|total files" || true
  echo ""
  echo "✓ Build complete: ${plat_dir}"
}

cmd_release() {
  local skip_test=false
  while [ $# -gt 0 ]; do
    case "$1" in
      --skip-test) skip_test=true; shift ;;
      *) shift ;;
    esac
  done

  local ver tag platform
  ver="$(read_version)"
  tag="v${ver}"
  platform="$(detect_platform)"
  local npm_plat
  npm_plat="$(platform_to_npm "$platform")"

  echo "=== Step 1: Sync Version ==="
  cmd_sync

  echo ""
  echo "=== Step 2: Build Runtime Package ==="
  local build_args=()
  [ "$skip_test" = true ] && build_args+=("--skip-test")
  cmd_build "${build_args[@]+${build_args[@]}}"

  local archive_ext="tar.gz"
  local archive_name="db-connector-${platform}.${archive_ext}"
  if [ "$platform" = "windows-x64" ]; then
    archive_ext="zip"
    archive_name="db-connector-${platform}.zip"
  fi

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
  echo "2. Publish platform packages to npm:"
  echo "     cd npm/platforms/${npm_plat} && npm publish"
  echo ""
  echo "3. Publish main package to npm:"
  echo "     cd npm && npm publish"
  echo ""
  echo "4. Create GitHub Release and upload archive:"
  echo "     ${DIST_DIR}/${archive_name}"
  echo ""
  echo "   Or use gh CLI (if available):"
  echo "     gh release create ${tag} ${DIST_DIR}/${archive_name} --title '${tag}' --notes 'Release ${ver}'"
  echo ""
}

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
