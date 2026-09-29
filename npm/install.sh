#!/usr/bin/env sh
set -e

REPO="proton6980/db-connector"
FALLBACK_TAG="v0.4.0"
INSTALL_DIR="${HOME}/.db-connector-mcp"
API_LATEST="https://api.github.com/repos/${REPO}/releases/latest"

DARWIN_ARM64="db-connector-darwin-arm64"
DARWIN_X64="db-connector-darwin-x64"
LINUX_X64="db-connector-linux-x64"
WINDOWS_X64="db-connector-windows-x64.exe"

DARWIN_ARM64_LEGACY="db-connector"

fetch_latest_tag() {
  if command -v curl > /dev/null 2>&1; then
    TAG=$(curl -s --max-time 10 "${API_LATEST}" | grep -o '"tag_name": *"[^"]*"' | head -1 | sed 's/.*"tag_name": *"\([^"]*\)"/\1/')
    if [ -n "$TAG" ]; then
      echo "$TAG"
      return
    fi
  fi
  echo "$FALLBACK_TAG"
}

build_jar_name() {
  TAG="$1"
  VERSION=$(echo "$TAG" | sed 's/^v//')
  echo "db-connector-${VERSION}.jar"
}

detect_platform() {
  OS="$(uname -s | tr '[:upper:]' '[:lower:]')"
  ARCH="$(uname -m)"

  case "$OS" in
    darwin) OS="darwin" ;;
    linux)  OS="linux" ;;
    mingw*|msys*|cygwin*|windows_nt) OS="windows" ;;
    *)
      echo "Unsupported OS: $OS"
      exit 1
      ;;
  esac

  case "$ARCH" in
    x86_64|amd64)  ARCH="x64" ;;
    arm64|aarch64) ARCH="arm64" ;;
    *)
      echo "Unsupported architecture: $ARCH"
      exit 1
      ;;
  esac

  PLATFORM="${OS}-${ARCH}"
}

get_binary_name() {
  case "$PLATFORM" in
    darwin-arm64) echo "$DARWIN_ARM64" ;;
    darwin-x64)   echo "$DARWIN_X64" ;;
    linux-x64)    echo "$LINUX_X64" ;;
    windows-x64)  echo "$WINDOWS_X64" ;;
    *)
      echo "No binary available for platform: $PLATFORM"
      echo "Supported: darwin-arm64, darwin-x64, linux-x64, windows-x64"
      exit 1
      ;;
  esac
}

download() {
  URL="$1"
  DEST="$2"

  if command -v curl > /dev/null 2>&1; then
    HTTP_CODE=$(curl -sL --progress-bar -o "$DEST" -w "%{http_code}" "$URL")
    if [ "$HTTP_CODE" = "200" ]; then
      return 0
    elif [ "$HTTP_CODE" = "404" ]; then
      rm -f "$DEST"
      return 1
    else
      rm -f "$DEST"
      echo "Error: HTTP ${HTTP_CODE} from ${URL}"
      return 2
    fi
  elif command -v wget > /dev/null 2>&1; then
    wget -q --show-progress -O "$DEST" "$URL" 2>&1
    WGET_EXIT=$?
    if [ $WGET_EXIT -eq 0 ]; then
      return 0
    elif [ $WGET_EXIT -eq 8 ]; then
      rm -f "$DEST"
      return 1
    else
      rm -f "$DEST"
      echo "Error: wget failed with exit code ${WGET_EXIT}"
      return 2
    fi
  else
    echo "Error: curl or wget is required"
    exit 1
  fi
}

try_download() {
  URL="$1"
  DEST="$2"
  LABEL="$3"

  echo "  Trying ${LABEL}..."
  download "$URL" "$DEST"
  return $?
}

main() {
  detect_platform

  RELEASE_TAG=$(fetch_latest_tag)
  JAR_FALLBACK=$(build_jar_name "$RELEASE_TAG")

  BINARY_NAME="$(get_binary_name)"
  NATIVE_URL="https://github.com/${REPO}/releases/download/${RELEASE_TAG}/${BINARY_NAME}"
  JAR_URL="https://github.com/${REPO}/releases/download/${RELEASE_TAG}/${JAR_FALLBACK}"

  if [ "$PLATFORM" = "darwin-arm64" ]; then
    LEGACY_URL="https://github.com/${REPO}/releases/download/${RELEASE_TAG}/${DARWIN_ARM64_LEGACY}"
  fi

  if [ "$OS" = "windows" ]; then
    CMD_NAME="db-connector-mcp.exe"
  else
    CMD_NAME="db-connector-mcp"
  fi

  mkdir -p "$INSTALL_DIR"

  DEST="${INSTALL_DIR}/${BINARY_NAME}"
  LINK="${INSTALL_DIR}/${CMD_NAME}"

  echo "Installing db-connector-mcp for ${PLATFORM} (${RELEASE_TAG})..."
  echo ""

  try_download "$NATIVE_URL" "$DEST" "native (${BINARY_NAME})"
  DL_RESULT=$?

  IS_JAR=false
  if [ $DL_RESULT -eq 1 ]; then
    if [ -n "$LEGACY_URL" ]; then
      echo "  Standard native not found, trying legacy alias..."
      DEST="${INSTALL_DIR}/${DARWIN_ARM64_LEGACY}"
      try_download "$LEGACY_URL" "$DEST" "legacy (${DARWIN_ARM64_LEGACY})"
      DL_RESULT=$?
    fi

    if [ $DL_RESULT -eq 1 ]; then
      echo "  Native binary not available, falling back to JAR..."
      DEST="${INSTALL_DIR}/${JAR_FALLBACK}"
      try_download "$JAR_URL" "$DEST" "JAR (${JAR_FALLBACK})"
      DL_RESULT=$?
      IS_JAR=true
    fi
  fi

  if [ $DL_RESULT -ne 0 ]; then
    echo "Download failed. Please check the release page:"
    echo "  https://github.com/${REPO}/releases/tag/${RELEASE_TAG}"
    exit 1
  fi

  if [ "$OS" = "windows" ]; then
    if $IS_JAR; then
      JAVA_CMD="java"
      if [ -n "$JAVA_HOME" ]; then
        JAVA_CMD="${JAVA_HOME}\\bin\\java.exe"
      fi
      cat > "$LINK" << WRAPPER
@echo off
"${JAVA_CMD}" -jar "${DEST}" %*
WRAPPER
    else
      cp "$DEST" "$LINK"
    fi
  else
    if $IS_JAR; then
      if ! command -v java > /dev/null 2>&1; then
        echo "Warning: Java not found in PATH. Please install JDK 17+ from https://adoptium.net/"
        echo "Then run: java -jar ${DEST}"
      fi
      cat > "$LINK" << WRAPPER
#!/usr/bin/env sh
exec java -jar "${DEST}" "\$@"
WRAPPER
      chmod +x "$LINK"
    else
      chmod +x "$DEST"
      ln -sf "$(basename "$DEST")" "$LINK"
    fi
  fi

  SHELL_RC=""
  case "$SHELL" in
    */zsh)  SHELL_RC="${HOME}/.zshrc" ;;
    */bash) SHELL_RC="${HOME}/.bashrc" ;;
    */fish) SHELL_RC="${HOME}/.config/fish/config.fish" ;;
  esac

  if [ -n "$SHELL_RC" ]; then
    PATH_LINE="export PATH=\"${INSTALL_DIR}:\$PATH\""
    if ! grep -qF "$INSTALL_DIR" "$SHELL_RC" 2>/dev/null; then
      echo "" >> "$SHELL_RC"
      echo "$PATH_LINE" >> "$SHELL_RC"
      echo "Added ${INSTALL_DIR} to PATH in ${SHELL_RC}"
    fi
  fi

  echo ""
  echo "Installed successfully!"
  echo "  Binary: ${DEST}"
  echo "  Command: ${CMD_NAME}"
  echo ""
  echo "Usage:"
  echo "  db-connector-mcp                                    # Start MCP SSE server + Web console"
  echo ""
  echo "After starting, two services are available:"
  echo "  MCP SSE Endpoint:  http://127.0.0.1:63306/mcp"
  echo "  Web Console:       http://127.0.0.1:68080"
  echo ""
  echo "(若默认端口被占用，启动时将自动选择空闲端口)"
  echo ""
  echo "MCP client configuration (add to your MCP settings):"
  echo '  { "url": "http://127.0.0.1:63306/mcp" }'
  echo ""
  if $IS_JAR; then
    echo "Note: Running via JAR (JDK 17+ required). Install JDK from https://adoptium.net/"
  fi
  if [ -n "$SHELL_RC" ]; then
    echo "Please restart your shell or run: source ${SHELL_RC}"
  else
    echo "Please add ${INSTALL_DIR} to your PATH"
  fi
}

main