# Instala Caddy como servicio de Windows (con NSSM) para servir https://skycelsys.ddns.net con
# HTTPS automático, reenviando todo al backend en localhost:8080.
#
# Correr en LA PC DEL SERVIDOR (Corpo, 192.168.1.105), como Administrador:
#   1. Clic derecho sobre PowerShell -> "Ejecutar como administrador"
#   2. cd a esta carpeta (o usa la ruta completa) y corre:  .\instalar-caddy.ps1
#
# Antes de correrlo, confirma que ya reenviaste los puertos 80 y 443 (TCP) del router hacia esta PC
# (192.168.1.105) — ver README.md en esta misma carpeta.

$ErrorActionPreference = "Stop"

$esAdmin = ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
if (-not $esAdmin) {
    Write-Host "Este script necesita permisos de administrador. Ciérralo y ábrelo de nuevo con 'Ejecutar como administrador'." -ForegroundColor Red
    exit 1
}

$raiz = "C:\caddy"
New-Item -ItemType Directory -Force -Path $raiz | Out-Null
New-Item -ItemType Directory -Force -Path "$raiz\logs" | Out-Null

# ── 1) Descargar Caddy ───────────────────────────────────────────────────────
Write-Host "Descargando Caddy..."
$release = Invoke-RestMethod -Uri "https://api.github.com/repos/caddyserver/caddy/releases/latest"
$asset = $release.assets | Where-Object { $_.name -match "windows_amd64\.zip$" } | Select-Object -First 1
if (-not $asset) { throw "No se encontró el paquete de Windows en la última versión de Caddy." }
$zipCaddy = "$env:TEMP\caddy.zip"
Invoke-WebRequest -Uri $asset.browser_download_url -OutFile $zipCaddy
Expand-Archive -Path $zipCaddy -DestinationPath "$env:TEMP\caddy_extraido" -Force
Copy-Item "$env:TEMP\caddy_extraido\caddy.exe" "$raiz\caddy.exe" -Force
Remove-Item $zipCaddy, "$env:TEMP\caddy_extraido" -Recurse -Force
Write-Host "Caddy $($release.tag_name) listo en $raiz\caddy.exe"

# ── 2) Descargar NSSM (para correr Caddy como servicio de Windows) ───────────
if (-not (Test-Path "$raiz\nssm.exe")) {
    Write-Host "Descargando NSSM..."
    $zipNssm = "$env:TEMP\nssm.zip"
    Invoke-WebRequest -Uri "https://nssm.cc/release/nssm-2.24.zip" -OutFile $zipNssm
    Expand-Archive -Path $zipNssm -DestinationPath "$env:TEMP\nssm_extraido" -Force
    Copy-Item "$env:TEMP\nssm_extraido\nssm-2.24\win64\nssm.exe" "$raiz\nssm.exe" -Force
    Remove-Item $zipNssm, "$env:TEMP\nssm_extraido" -Recurse -Force
}

# ── 3) Copiar el Caddyfile de este repo ──────────────────────────────────────
Copy-Item "$PSScriptRoot\Caddyfile" "$raiz\Caddyfile" -Force
Write-Host "Caddyfile copiado a $raiz\Caddyfile"

# ── 4) Registrar el servicio ──────────────────────────────────────────────────
$servicio = Get-Service -Name Caddy -ErrorAction SilentlyContinue
if ($servicio) {
    Write-Host "El servicio Caddy ya existe: se detiene para actualizarlo."
    & "$raiz\nssm.exe" stop Caddy | Out-Null
    & "$raiz\nssm.exe" remove Caddy confirm | Out-Null
}
& "$raiz\nssm.exe" install Caddy "$raiz\caddy.exe" "run --config `"$raiz\Caddyfile`" --adapter caddyfile"
& "$raiz\nssm.exe" set Caddy AppDirectory $raiz
& "$raiz\nssm.exe" set Caddy Start SERVICE_AUTO_START
& "$raiz\nssm.exe" set Caddy AppStdout "$raiz\logs\service.log"
& "$raiz\nssm.exe" set Caddy AppStderr "$raiz\logs\service.log"

# ── 5) Abrir el firewall de Windows (además del reenvío de puertos del router) ─
New-NetFirewallRule -DisplayName "Caddy HTTP"  -Direction Inbound -Protocol TCP -LocalPort 80  -Action Allow -ErrorAction SilentlyContinue | Out-Null
New-NetFirewallRule -DisplayName "Caddy HTTPS" -Direction Inbound -Protocol TCP -LocalPort 443 -Action Allow -ErrorAction SilentlyContinue | Out-Null

# ── 6) Arrancar ────────────────────────────────────────────────────────────────
& "$raiz\nssm.exe" start Caddy
Start-Sleep -Seconds 3
Get-Service Caddy | Select-Object Name, Status, StartType

Write-Host ""
Write-Host "Listo. En unos segundos deberías poder abrir https://skycelsys.ddns.net" -ForegroundColor Green
Write-Host "Si algo falla, revisa C:\caddy\logs\service.log" -ForegroundColor Yellow
