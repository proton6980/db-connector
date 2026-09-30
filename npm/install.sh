#!/usr/bin/env sh
set -e

REPO="proton6980/db-connector"
FALLBACK_TAG="v0.5.0"
INSTALL_DIR="${HOME}/.db-connector-mcp/db-connector"
API_LATEST="https://api.github.com/repos/${REPO}/releases/latest"

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

get_archive_name() {
  case "$PLATFORM" in
    darwin-arm64) echo "db-connector-darwin-arm64.tar.gz" ;;
    darwin-x64)   echo "db-connector-darwin-x64.tar.gz" ;;
    linux-x64)    echo "db-connector-linux-x64.tar.gz" ;;
    windows-x64)  echo "db-connector-windows-x64.zip" ;;
    *)
      echo "No archive available for platform: $PLATFORM"
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
    else
      rm -f "$DEST"
      echo "Error: HTTP ${HTTP_CODE} from ${URL}"
      return 1
    fi
  elif command -v wget > /dev/null 2>&1; then
    wget -q --show-progress -O "$DEST" "$URL" 2>&1
    WGET_EXIT=$?
    if [ $WGET_EXIT -eq 0 ]; then
      return 0
    else
      rm -f "$DEST"
      echo "Error: wget failed with exit code ${WGET_EXIT}"
      return 1
    fi
  else
    echo "Error: curl or wget is required"
    exit 1
  fi
}

main() {
  detect_platform

  if ! command -v node > /dev/null 2>&1; then
    echo "Error: Node.js is required but not found."
    echo "Please install Node 16+ from https://nodejs.org/ and rerun this script."
    exit 1
  fi

  RELEASE_TAG=$(fetch_latest_tag)
  ARCHIVE_NAME="$(get_archive_name)"
  ARCHIVE_URL="https://github.com/${REPO}/releases/download/${RELEASE_TAG}/${ARCHIVE_NAME}"

  PARENT_DIR="${HOME}/.db-connector-mcp"
  mkdir -p "$PARENT_DIR"

  echo "Installing db-connector-mcp for ${PLATFORM} (${RELEASE_TAG})..."
  echo ""

  TMP_ARCHIVE=$(mktemp)
  trap 'rm -f "$TMP_ARCHIVE"' EXIT

  echo "  Downloading ${ARCHIVE_NAME}..."
  download "$ARCHIVE_URL" "$TMP_ARCHIVE"
  if [ $? -ne 0 ]; then
    echo "Download failed. Please check the release page:"
    echo "  https://github.com/${REPO}/releases/tag/${RELEASE_TAG}"
    exit 1
  fi

  rm -rf "$INSTALL_DIR"
  mkdir -p "$INSTALL_DIR"

  case "$ARCHIVE_NAME" in
    *.tar.gz)
      tar xzf "$TMP_ARCHIVE" -C "$PARENT_DIR"
      ;;
    *.zip)
      if command -v unzip > /dev/null 2>&1; then
        unzip -q -o "$TMP_ARCHIVE" -d "$PARENT_DIR"
      else
        python3 -c "
import zipfile, sys
with zipfile.ZipFile('$TMP_ARCHIVE') as z:
    z.extractall('$PARENT_DIR')
"
      fi
      ;;
  esac

  if [ "$OS" = "windows" ]; then
    CMD_NAME="db-connector-mcp.cmd"
  else
    CMD_NAME="db-connector-mcp"
  fi

  LINK="${PARENT_DIR}/${CMD_NAME}"

  if [ "$OS" = "windows" ]; then
    cat > "$LINK" << WRAPPER
@echo off
node "${INSTALL_DIR}\\bin\\run.js" %*
WRAPPER
  else
    cat > "$LINK" << WRAPPER
#!/usr/bin/env sh
exec node "${INSTALL_DIR}/bin/run.js" "\$@"
WRAPPER
    chmod +x "$LINK"
  fi

  SHELL_RC=""
  case "$SHELL" in
    */zsh)  SHELL_RC="${HOME}/.zshrc" ;;
    */bash) SHELL_RC="${HOME}/.bashrc" ;;
    */fish) SHELL_RC="${HOME}/.config/fish/config.fish" ;;
  esac

  if [ -n "$SHELL_RC" ]; then
    PATH_LINE="export PATH=\"${PARENT_DIR}:\$PATH\""
    if ! grep -qF "$PARENT_DIR" "$SHELL_RC" 2>/dev/null; then
      echo "" >> "$SHELL_RC"
      echo "$PATH_LINE" >> "$SHELL_RC"
      echo "Added ${PARENT_DIR} to PATH in ${SHELL_RC}"
    fi
  fi

  echo ""
  echo "Installed successfully!"
  echo "  Location: ${INSTALL_DIR}"
  echo "  Command:  ${CMD_NAME}"
  echo ""
  echo "Usage:"
  echo "  db-connector-mcp                                    # Start MCP SSE server + Web console"
  echo ""
  echo "After starting, two services are available:"
  echo "  MCP SSE Endpoint:  http://127.0.0.1:63306/mcp"
  echo "  Web Console:       http://127.0.0.1:63380"
  echo ""
  echo "(若默认端口被占用，启动时将自动选择空闲端口)"
  echo ""
  echo "MCP client configuration (add to your MCP settings):"
  echo '  { "url": "http://127.0.0.1:63306/mcp" }'
  echo ""
  if [ -n "$SHELL_RC" ]; then
    echo "Please restart your shell or run: source ${SHELL_RC}"
  else
    echo "Please add ${PARENT_DIR} to your PATH"
  fi
}

main