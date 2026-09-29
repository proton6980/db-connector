#!/usr/bin/env sh
set -e

REPO="proton6980/db-connector"
RELEASE_TAG="v0.2.0"
INSTALL_DIR="${HOME}/.db-connector-mcp"

DARWIN_ARM64="db-connector-darwin-arm64"
DARWIN_X64="db-connector-darwin-x64"
LINUX_X64="db-connector-linux-x64"
WINDOWS_X64="db-connector-windows-x64.exe"

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
    curl -fSL --progress-bar -o "$DEST" "$URL"
  elif command -v wget > /dev/null 2>&1; then
    wget -q --show-progress -O "$DEST" "$URL"
  else
    echo "Error: curl or wget is required"
    exit 1
  fi
}

main() {
  detect_platform
  BINARY_NAME="$(get_binary_name)"
  DOWNLOAD_URL="https://github.com/${REPO}/releases/download/${RELEASE_TAG}/${BINARY_NAME}"

  if [ "$OS" = "windows" ]; then
    CMD_NAME="db-connector-mcp.exe"
  else
    CMD_NAME="db-connector-mcp"
  fi

  mkdir -p "$INSTALL_DIR"

  DEST="${INSTALL_DIR}/${BINARY_NAME}"
  LINK="${INSTALL_DIR}/${CMD_NAME}"

  echo "Installing db-connector-mcp for ${PLATFORM}..."
  echo "Downloading from ${DOWNLOAD_URL}"

  download "$DOWNLOAD_URL" "$DEST"

  chmod +x "$DEST"

  if [ "$OS" = "windows" ]; then
    cp "$DEST" "$LINK"
  else
    ln -sf "$BINARY_NAME" "$LINK"
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
  echo "  db-connector-mcp                                    # Start MCP server (STDIO mode)"
  echo "  db-connector-mcp --spring.profiles.active=stdio     # Explicit STDIO mode"
  echo "  db-connector-mcp --server.port=9090                 # Custom HTTP port"
  echo ""
  echo "MCP client configuration (add to your MCP settings):"
  echo '  { "command": "db-connector-mcp" }'
  echo ""
  if [ -n "$SHELL_RC" ]; then
    echo "Please restart your shell or run: source ${SHELL_RC}"
  else
    echo "Please add ${INSTALL_DIR} to your PATH"
  fi
}

main