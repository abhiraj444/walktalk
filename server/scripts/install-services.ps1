<#
.SYNOPSIS
  Installs WalkTalk Signaling Server and Cloudflare Tunnel as resilient Windows Services.
.DESCRIPTION
  Uses NSSM (Non-Sucking Service Manager) to guarantee the signaling server starts on PC boot,
  restarts upon crash, and outputs logs to server\logs.
.NOTES
  Run this script in PowerShell as Administrator.
#>

$ErrorActionPreference = "Stop"

Write-Host "====================================================" -ForegroundColor Cyan
Write-Host " WalkTalk Windows 24/7 Service Installer" -ForegroundColor Cyan
Write-Host "====================================================" -ForegroundColor Cyan

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$serverDir = Split-Path -Parent $scriptDir
$logsDir = Join-Path $serverDir "logs"

if (-not (Test-Path $logsDir)) {
    New-Item -ItemType Directory -Path $logsDir -Force | Out-Null
    Write-Host "[+] Created logs directory: $logsDir" -ForegroundColor Green
}

# 1. Check Node.js
$nodePath = (Get-Command node -ErrorAction SilentlyContinue)?.Source
if (-not $nodePath) {
    Write-Error "Node.js is not found in PATH. Please install Node.js before running this script."
}
Write-Host "[+] Found Node.js at: $nodePath" -ForegroundColor Green

# 2. Check or install Cloudflare Tunnel (cloudflared)
$cloudflaredCmd = (Get-Command cloudflared -ErrorAction SilentlyContinue)?.Source
if (-not $cloudflaredCmd) {
    Write-Host "[*] cloudflared not found in PATH. Attempting install via winget..." -ForegroundColor Yellow
    try {
        winget install --id Cloudflare.cloudflared --silent --accept-package-agreements --accept-source-agreements
        $env:Path = [System.Environment]::GetEnvironmentVariable("Path","Machine") + ";" + [System.Environment]::GetEnvironmentVariable("Path","User")
        Write-Host "[+] cloudflared installed successfully!" -ForegroundColor Green
    } catch {
        Write-Warning "Failed to auto-install cloudflared. You can download it manually from https://github.com/cloudflare/cloudflared/releases"
    }
} else {
    Write-Host "[+] Found cloudflared at: $cloudflaredCmd" -ForegroundColor Green
}

# 3. Check or download NSSM
$nssmToolDir = "C:\tools\nssm"
$nssmExe = Join-Path $nssmToolDir "nssm.exe"

if (-not (Test-Path $nssmExe)) {
    $existingNssm = (Get-Command nssm -ErrorAction SilentlyContinue)?.Source
    if ($existingNssm) {
        $nssmExe = $existingNssm
    } else {
        Write-Host "[*] Downloading NSSM..." -ForegroundColor Yellow
        New-Item -ItemType Directory -Path $nssmToolDir -Force | Out-Null
        $zipPath = "$env:TEMP\nssm.zip"
        Invoke-WebRequest -Uri "https://nssm.cc/release/nssm-2.24.zip" -OutFile $zipPath
        Expand-Archive -Path $zipPath -DestinationPath "$env:TEMP\nssm_extracted" -Force
        Copy-Item -Path "$env:TEMP\nssm_extracted\nssm-2.24\win64\nssm.exe" -Destination $nssmExe -Force
        Remove-Item -Path $zipPath, "$env:TEMP\nssm_extracted" -Recurse -Force
        Write-Host "[+] NSSM installed at $nssmExe" -ForegroundColor Green
    }
}

# 4. Register WalkTalkSignaling Service via NSSM
$serviceName = "WalkTalkSignaling"
$serverIndex = Join-Path $serverDir "src\index.js"

Write-Host "[*] Configuring Windows Service: $serviceName..." -ForegroundColor Cyan

# Stop existing service if already installed
& $nssmExe stop $serviceName 2>$null
& $nssmExe remove $serviceName confirm 2>$null

# Install service
& $nssmExe install $serviceName $nodePath $serverIndex
& $nssmExe set $serviceName AppDirectory $serverDir
& $nssmExe set $serviceName AppStdout (Join-Path $logsDir "service-stdout.log")
& $nssmExe set $serviceName AppStderr (Join-Path $logsDir "service-stderr.log")
& $nssmExe set $serviceName Start SERVICE_AUTO_START
& $nssmExe set $serviceName AppRestartDelay 3000

Write-Host "[+] Starting $serviceName service..." -ForegroundColor Green
& $nssmExe start $serviceName

Write-Host "====================================================" -ForegroundColor Green
Write-Host " WalkTalk Signaling Service is installed & running!" -ForegroundColor Green
Write-Host " It will start automatically whenever Windows boots." -ForegroundColor Green
Write-Host " Logs can be inspected at: $logsDir" -ForegroundColor Green
Write-Host "====================================================" -ForegroundColor Green
