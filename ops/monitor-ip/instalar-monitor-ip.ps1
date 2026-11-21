# Instala el vigilante de la IP como tarea programada del servidor (arranca con Windows, como SYSTEM, y se reinicia si falla).
# Ejecutar en PowerShell como administrador, en la misma carpeta donde está monitor-ip.ps1:
#     .\instalar-monitor-ip.ps1
# Para quitarlo:  Unregister-ScheduledTask -TaskName SkycelMonitorIP -Confirm:$false

$origen  = Join-Path $PSScriptRoot 'monitor-ip.ps1'
$destino = 'C:\skycel\monitor-ip.ps1'
$tarea   = 'SkycelMonitorIP'

if (-not (Test-Path $origen)) { throw "No encuentro $origen" }
Copy-Item $origen $destino -Force

# si ya existía una versión corriendo, se reemplaza
if (Get-ScheduledTask -TaskName $tarea -ErrorAction SilentlyContinue) {
    Stop-ScheduledTask -TaskName $tarea -ErrorAction SilentlyContinue
    Unregister-ScheduledTask -TaskName $tarea -Confirm:$false
}

$accion = New-ScheduledTaskAction -Execute 'powershell.exe' -Argument "-NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File `"$destino`""
$disparador = New-ScheduledTaskTrigger -AtStartup
$cuenta = New-ScheduledTaskPrincipal -UserId 'SYSTEM' -LogonType ServiceAccount -RunLevel Highest
$ajustes = New-ScheduledTaskSettingsSet -ExecutionTimeLimit ([TimeSpan]::Zero) -RestartCount 5 -RestartInterval (New-TimeSpan -Minutes 1) -StartWhenAvailable -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries
Register-ScheduledTask -TaskName $tarea -Action $accion -Trigger $disparador -Principal $cuenta -Settings $ajustes -Description 'Anota la IP publica del servidor y si skycelsys.ddns.net apunta a ella (solo observa).' | Out-Null
Start-ScheduledTask -TaskName $tarea
Start-Sleep -Seconds 8
Get-ScheduledTask -TaskName $tarea | Select-Object TaskName, State
Get-Content 'C:\skycel\logs\ip-monitor.log' -Tail 5
