# Despliega skycel-backend como servicio de Windows en el servidor de Corpo, en el puerto 8080
# (donde ya apunta Caddy). NO toca el sistema actual (otro puerto, otra base de datos).
#
# Antes de correr esto:
#   1. Corre ops/deploy/crear-base-datos.sql en MySQL Workbench (conectado como root) — crea
#      skyceldb2 vacía y un usuario 'skycel_app' solo para ella.
#   2. Copia el jar compilado a esta misma PC, en C:\skycel\api.jar
#      (se compiló en la PC de desarrollo: target\backend-0.0.1-SNAPSHOT.jar).
#
# Correr en LA PC DEL SERVIDOR (Corpo, 192.168.1.105), como Administrador:
#   cd a esta carpeta (o usa la ruta completa) y corre:  .\desplegar-backend.ps1
# Te va a pedir el usuario/contraseña de MySQL que creaste en el paso 1 — se piden aquí mismo,
# en esta ventana; nunca se comparten por chat ni quedan escritos en ningún archivo de este repo.

$ErrorActionPreference = "Stop"

$esAdmin = ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
if (-not $esAdmin) {
    Write-Host "Este script necesita permisos de administrador. Cierralo y abrelo de nuevo con 'Ejecutar como administrador'." -ForegroundColor Red
    exit 1
}

$raiz = "C:\skycel"
$jar  = "$raiz\api.jar"
if (-not (Test-Path $jar)) {
    Write-Host "No encuentro $jar. Copia ahi el jar compilado (target\backend-0.0.1-SNAPSHOT.jar) y vuelve a correr este script." -ForegroundColor Red
    exit 1
}

# ── 1) JDK 21 (el sistema actual usa Java 11 aparte; este backend necesita 21) ──
$javaExe = "$raiz\jdk-21\bin\java.exe"
if (-not (Test-Path $javaExe)) {
    Write-Host "Descargando JDK 21 (Eclipse Temurin)..."
    $zipJdk = "$env:TEMP\jdk21.zip"
    Invoke-WebRequest -Uri "https://api.adoptium.net/v3/binary/latest/21/ga/windows/x64/jdk/hotspot/normal/eclipse" -OutFile $zipJdk
    Expand-Archive -Path $zipJdk -DestinationPath "$env:TEMP\jdk21_extraido" -Force
    $carpetaJdk = Get-ChildItem "$env:TEMP\jdk21_extraido" -Directory | Select-Object -First 1
    New-Item -ItemType Directory -Force -Path "$raiz\jdk-21" | Out-Null
    Copy-Item "$carpetaJdk\*" "$raiz\jdk-21" -Recurse -Force
    Remove-Item $zipJdk, "$env:TEMP\jdk21_extraido" -Recurse -Force
    Write-Host "JDK 21 listo en $raiz\jdk-21"
}
& $javaExe -version

# ── 2) Credenciales de la base (se piden aquí, no viajan por chat ni se guardan en el repo) ──
Write-Host ""
Write-Host "Credenciales de MySQL para skyceldb2 (las del usuario 'skycel_app' que creaste con el .sql):" -ForegroundColor Cyan
$dbUser = Read-Host "Usuario"
$dbPassSecura = Read-Host "Contrasena" -AsSecureString
$dbPass = [System.Runtime.InteropServices.Marshal]::PtrToStringAuto([System.Runtime.InteropServices.Marshal]::SecureStringToBSTR($dbPassSecura))

$jwtInput = Read-Host "JWT_SECRET (Enter para generar uno nuevo automaticamente)"
if ([string]::IsNullOrWhiteSpace($jwtInput)) {
    $bytes = New-Object byte[] 64
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    $rng.GetBytes($bytes)
    $rng.Dispose()
    $jwtSecret = [Convert]::ToBase64String($bytes)
    Write-Host "JWT_SECRET generado. Se queda guardado solo en la configuracion del servicio." -ForegroundColor Yellow
} else {
    $jwtSecret = $jwtInput
}

# ── 3) NSSM (reutiliza el que ya se descargó para Caddy, o lo descarga de nuevo) ──
$nssm = if (Test-Path "C:\caddy\nssm.exe") { "C:\caddy\nssm.exe" } else { "$raiz\nssm.exe" }
if (-not (Test-Path $nssm)) {
    Write-Host "Descargando NSSM..."
    $zipNssm = "$env:TEMP\nssm.zip"
    Invoke-WebRequest -Uri "https://nssm.cc/release/nssm-2.24.zip" -OutFile $zipNssm
    Expand-Archive -Path $zipNssm -DestinationPath "$env:TEMP\nssm_extraido" -Force
    Copy-Item "$env:TEMP\nssm_extraido\nssm-2.24\win64\nssm.exe" $nssm -Force
    Remove-Item $zipNssm, "$env:TEMP\nssm_extraido" -Recurse -Force
}

# ── 4) Registrar el servicio ──────────────────────────────────────────────────
New-Item -ItemType Directory -Force -Path "$raiz\logs" | Out-Null
$servicio = Get-Service -Name SkycelBackend -ErrorAction SilentlyContinue
if ($servicio) {
    Write-Host "El servicio SkycelBackend ya existe: se detiene para actualizarlo."
    & $nssm stop SkycelBackend | Out-Null
    & $nssm remove SkycelBackend confirm | Out-Null
}
& $nssm install SkycelBackend $javaExe ('-jar "' + $jar + '"')
& $nssm set SkycelBackend AppDirectory $raiz
& $nssm set SkycelBackend Start SERVICE_AUTO_START
& $nssm set SkycelBackend AppStdout "$raiz\logs\service.log"
& $nssm set SkycelBackend AppStderr "$raiz\logs\service.log"
$envVars = "DB_USERNAME=$dbUser" + "`n" + "DB_PASSWORD=$dbPass" + "`n" + "JWT_SECRET=$jwtSecret"
& $nssm set SkycelBackend AppEnvironmentExtra $envVars

# ── 5) Arrancar ────────────────────────────────────────────────────────────────
& $nssm start SkycelBackend
Write-Host "Esperando a que arranque (puede tardar 20-30s la primera vez)..."
$listo = $false
for ($i = 0; $i -lt 30; $i++) {
    Start-Sleep -Seconds 2
    try {
        $r = Invoke-WebRequest -Uri "http://localhost:8080/api/publico/salud" -UseBasicParsing -TimeoutSec 3
        if ($r.StatusCode -eq 200) { $listo = $true; break }
    } catch { }
}

Get-Service SkycelBackend | Select-Object Name, Status, StartType
if ($listo) {
    Write-Host ""
    Write-Host "Listo. El backend responde en localhost:8080 y ya deberia verse en https://skycelsys.ddns.net" -ForegroundColor Green
    Write-Host "Usuario para entrar por primera vez: root / admin123 (cambia la contrasena en cuanto entres)." -ForegroundColor Yellow
} else {
    Write-Host ""
    Write-Host "No respondio a tiempo. Revisa C:\skycel\logs\service.log para ver el error." -ForegroundColor Red
}
