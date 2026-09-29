$ErrorActionPreference = "Stop"

$Repo = "proton6980/db-connector"
$ReleaseTag = "v0.1.0"
$InstallDir = "$env:USERPROFILE\.db-connector-mcp"

$Os = "windows"
if ([System.Environment]::Is64BitOperatingSystem) {
    $Arch = "x64"
} else {
    Write-Error "Unsupported architecture: 32-bit"
    exit 1
}

$BinaryName = "db-connector-windows-x64.exe"
$DownloadUrl = "https://github.com/$Repo/releases/download/$ReleaseTag/$BinaryName"
$Dest = Join-Path $InstallDir $BinaryName
$CmdName = "db-connector-mcp.exe"
$Link = Join-Path $InstallDir $CmdName

Write-Host "Installing db-connector-mcp for $Os-$Arch..."
Write-Host "Downloading from $DownloadUrl"

New-Item -ItemType Directory -Force -Path $InstallDir | Out-Null

try {
    Invoke-WebRequest -Uri $DownloadUrl -OutFile $Dest -UseBasicParsing
} catch {
    Write-Error "Download failed: $_"
    exit 1
}

Copy-Item $Dest $Link -Force

$PathValue = [System.Environment]::GetEnvironmentVariable("Path", "User")
if ($PathValue -notlike "*$InstallDir*") {
    [System.Environment]::SetEnvironmentVariable("Path", "$InstallDir;$PathValue", "User")
    Write-Host "Added $InstallDir to user PATH"
}

Write-Host ""
Write-Host "Installed successfully!"
Write-Host "  Binary: $Dest"
Write-Host "  Command: $CmdName"
Write-Host ""
Write-Host "Usage:"
Write-Host "  db-connector-mcp                                    # Start MCP SSE server + Web console"
Write-Host ""
Write-Host "MCP client configuration:"
Write-Host '  { "url": "http://127.0.0.1:63306/mcp" }'
Write-Host ""
Write-Host "Please restart your terminal to update PATH."