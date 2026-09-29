$ErrorActionPreference = "Stop"

$Repo = "proton6980/db-connector"
$FallbackTag = "v0.3.0"
$InstallDir = "$env:USERPROFILE\.db-connector-mcp"
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

$Version = $ReleaseTag -replace "^v", ""
$JarName = "db-connector-$Version.jar"

$Os = "windows"
if ([System.Environment]::Is64BitOperatingSystem) {
    $Arch = "x64"
} else {
    Write-Error "Unsupported architecture: 32-bit"
    exit 1
}

$NativeName = "db-connector-windows-x64.exe"
$NativeUrl = "https://github.com/$Repo/releases/download/$ReleaseTag/$NativeName"
$JarUrl = "https://github.com/$Repo/releases/download/$ReleaseTag/$JarName"

New-Item -ItemType Directory -Force -Path $InstallDir | Out-Null

Write-Host "Installing db-connector-mcp for $Os-$Arch ($ReleaseTag)..."

$isJar = $false
$binaryPath = Join-Path $InstallDir $NativeName
$cmdName = "db-connector-mcp.exe"
$link = Join-Path $InstallDir $cmdName

Write-Host "  Trying native ($NativeName)..."
try {
    Invoke-WebRequest -Uri $NativeUrl -OutFile $binaryPath -UseBasicParsing -ErrorAction Stop
    Write-Host "  Downloaded native binary"
} catch {
    if ($_.Exception.Response.StatusCode -eq 404) {
        Write-Host "  Native binary not available, falling back to JAR..."
        $binaryPath = Join-Path $InstallDir $JarName
        $isJar = $true
        try {
            Invoke-WebRequest -Uri $JarUrl -OutFile $binaryPath -UseBasicParsing -ErrorAction Stop
            Write-Host "  Downloaded JAR fallback"
        } catch {
            Write-Error "Download failed: $_"
            Write-Host "Please check the release page: https://github.com/$Repo/releases/tag/$ReleaseTag"
            exit 1
        }
    } else {
        Write-Error "Download failed: $_"
        exit 1
    }
}

if ($isJar) {
    $javaCmd = "java"
    if ($env:JAVA_HOME) {
        $javaCmd = Join-Path $env:JAVA_HOME "bin\java.exe"
    }
    $wrapper = "@echo off`r`n`"$javaCmd`" -jar `"$binaryPath`" %*"
    Set-Content -Path $link -Value $wrapper
} else {
    Copy-Item $binaryPath $link -Force
}

$PathValue = [System.Environment]::GetEnvironmentVariable("Path", "User")
if ($PathValue -notlike "*$InstallDir*") {
    [System.Environment]::SetEnvironmentVariable("Path", "$InstallDir;$PathValue", "User")
    Write-Host "Added $InstallDir to user PATH"
}

Write-Host ""
Write-Host "Installed successfully!"
Write-Host "  Binary: $binaryPath"
Write-Host "  Command: $cmdName"
Write-Host ""
Write-Host "Usage:"
Write-Host "  db-connector-mcp                                    # Start MCP SSE server + Web console"
Write-Host ""
Write-Host "MCP client configuration:"
Write-Host '  { "url": "http://127.0.0.1:63306/mcp" }'
Write-Host ""
if ($isJar) {
    Write-Host "Note: Running via JAR (JDK 17+ required). Install JDK from https://adoptium.net/"
}
Write-Host "Please restart your terminal to update PATH."