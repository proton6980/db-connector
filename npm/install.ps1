$ErrorActionPreference = "Stop"

$Repo = "proton6980/db-connector"
$FallbackTag = "v0.5.3"
$ParentDir = "$env:USERPROFILE\.db-connector-mcp"
$InstallDir = "$ParentDir\db-connector"
$ApiLatest = "https://api.github.com/repos/$Repo/releases/latest"

$ReleaseTag = $FallbackTag
try {
    $response = Invoke-RestMethod -Uri $ApiLatest -TimeoutSec 10 -ErrorAction Stop
    if ($response.tag_name) {
        $ReleaseTag = $response.tag_name
    }
} catch {
    Write-Host "Warning: GitHub API unreachable, using fallback $FallbackTag"
}

$Os = "windows"
if ([System.Environment]::Is64BitOperatingSystem) {
    $Arch = "x64"
} else {
    Write-Error "Unsupported architecture: 32-bit"
    exit 1
}
$Platform = "$Os-$Arch"

$ArchiveName = "db-connector-$Platform.zip"
$ArchiveUrl = "https://github.com/$Repo/releases/download/$ReleaseTag/$ArchiveName"

if (-not (Get-Command node -ErrorAction SilentlyContinue)) {
    Write-Error "Node.js is required but not found."
    Write-Host "Please install Node 16+ from https://nodejs.org/ and rerun this script."
    exit 1
}

New-Item -ItemType Directory -Force -Path $ParentDir | Out-Null

Write-Host "Installing db-connector-mcp for $Platform ($ReleaseTag)..."
Write-Host ""

$TmpArchive = [System.IO.Path]::GetTempFileName()
try {
    Write-Host "  Downloading $ArchiveName..."
    Invoke-WebRequest -Uri $ArchiveUrl -OutFile $TmpArchive -UseBasicParsing -ErrorAction Stop
} catch {
    Write-Error "Download failed: ${_}"
    Write-Host "Please check the release page: https://github.com/$Repo/releases/tag/$ReleaseTag"
    exit 1
}

if (Test-Path $InstallDir) {
    Remove-Item -Recurse -Force $InstallDir
}

Write-Host "  Extracting..."
Expand-Archive -Path $TmpArchive -DestinationPath $ParentDir -Force
Remove-Item -Force $TmpArchive

if (-not (Test-Path $InstallDir)) {
    Write-Error "Extraction failed: $InstallDir not found after unzip"
    exit 1
}

$CmdName = "db-connector-mcp.cmd"
$Link = Join-Path $ParentDir $CmdName
$Wrapper = "@echo off`r`nnode `"$InstallDir\bin\run.js`" %*"
Set-Content -Path $Link -Value $Wrapper

$PathValue = [System.Environment]::GetEnvironmentVariable("Path", "User")
if ($PathValue -notlike "*$ParentDir*") {
    [System.Environment]::SetEnvironmentVariable("Path", "$ParentDir;$PathValue", "User")
    Write-Host "Added $ParentDir to user PATH"
}

Write-Host ""
Write-Host "Installed successfully!"
Write-Host "  Location: $InstallDir"
Write-Host "  Command:  $CmdName"
Write-Host ""
Write-Host "Usage:"
Write-Host "  db-connector-mcp                                    # Start MCP SSE server + Web console"
Write-Host ""
Write-Host "After starting, two services are available:"
Write-Host "  MCP SSE Endpoint:  http://127.0.0.1:63306/mcp"
Write-Host "  Web Console:       http://127.0.0.1:63380"
Write-Host ""
Write-Host "MCP client configuration (add to your MCP settings):"
Write-Host '  { "url": "http://127.0.0.1:63306/mcp" }'
Write-Host ""
Write-Host "Please restart your terminal to update PATH."
