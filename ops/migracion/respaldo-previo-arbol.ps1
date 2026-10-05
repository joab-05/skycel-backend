# Respaldo de skyceldb2 ANTES de migrar el catálogo al árbol de categorías.
# Correr en el servidor de Corpo, en PowerShell (como administrador):
#     powershell -ExecutionPolicy Bypass -File C:\skycel\respaldo-previo-arbol.ps1
# Solo LEE la base: no modifica nada. Deja un .zip en C:\SkycelBackups\pre-arbol y muestra los conteos de
# las tablas que toca la migración (para comparar después).

$ErrorActionPreference = 'Stop'
$destino = 'C:\SkycelBackups\pre-arbol'
$cnf = 'C:\SkycelBackups\.my-backup.cnf'     # el mismo archivo de credenciales del respaldo automático
New-Item -ItemType Directory -Force $destino | Out-Null

if (-not (Test-Path $cnf)) { throw "No existe $cnf (credenciales de MySQL). Ver ops/backup/README.md." }
$bin = Get-ChildItem 'C:\Program Files\MySQL' -Recurse -Filter mysqldump.exe -ErrorAction SilentlyContinue | Select-Object -First 1
if (-not $bin) { throw 'No se encontró mysqldump.exe en C:\Program Files\MySQL.' }
$mysql = Join-Path $bin.DirectoryName 'mysql.exe'

$sello = Get-Date -Format 'yyyyMMdd_HHmm'
$sql = Join-Path $destino "skyceldb2_antes_arbol_$sello.sql"
& $bin.FullName "--defaults-extra-file=$cnf" --single-transaction --routines --triggers --default-character-set=utf8mb4 --result-file=$sql skyceldb2
if ($LASTEXITCODE -ne 0) { throw "mysqldump falló (código $LASTEXITCODE)." }

$cola = Get-Content $sql -Tail 3 | Out-String
if ($cola -notmatch 'Dump completed') { throw 'El respaldo parece incompleto (no termina con "Dump completed").' }

Compress-Archive -Path $sql -DestinationPath "$sql.zip" -Force
Remove-Item $sql
$zip = Get-Item "$sql.zip"
"`nRespaldo listo: $($zip.FullName)  ($([math]::Round($zip.Length / 1MB, 1)) MB)"
"SHA256: $((Get-FileHash $zip.FullName -Algorithm SHA256).Hash)"

"`nConteos actuales (para comparar después de migrar):"
& $mysql "--defaults-extra-file=$cnf" skyceldb2 -e "SELECT 'categorias' t, COUNT(*) n FROM categoria UNION ALL SELECT 'categorias activas', COUNT(*) FROM categoria WHERE activo=1 UNION ALL SELECT 'masters', COUNT(*) FROM producto_master UNION ALL SELECT 'masters activos', COUNT(*) FROM producto_master WHERE activo=1 UNION ALL SELECT 'productos', COUNT(*) FROM producto UNION ALL SELECT 'unidades IMEI', COUNT(*) FROM producto_imei;"
