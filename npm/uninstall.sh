#!/usr/bin/env sh
set -e

INSTALL_DIR="${HOME}/.db-connector-mcp"

if [ ! -d "$INSTALL_DIR" ]; then
  echo "db-connector-mcp is not installed"
  exit 0
fi

rm -rf "$INSTALL_DIR"
echo "Removed ${INSTALL_DIR}"

for SHELL_RC in "${HOME}/.zshrc" "${HOME}/.bashrc" "${HOME}/.config/fish/config.fish"; do
  if [ -f "$SHELL_RC" ]; then
    if grep -qF "$INSTALL_DIR" "$SHELL_RC" 2>/dev/null; then
      TEMP_RC="$(mktemp)"
      grep -vF "$INSTALL_DIR" "$SHELL_RC" > "$TEMP_RC" || true
      mv "$TEMP_RC" "$SHELL_RC"
      echo "Cleaned PATH in ${SHELL_RC}"
    fi
  fi
done

echo "Uninstalled db-connector-mcp"
echo "Please restart your shell"