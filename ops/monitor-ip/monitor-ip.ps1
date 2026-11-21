# Vigilante de la IP del servidor Skycel (se instala con instalar-monitor-ip.ps1).
#
# El servidor tiene IP pública dinámica (Telmex). Cuando cambia, el nombre skycelsys.ddns.net sigue apuntando a la IP vieja hasta
# que el cliente de No-IP lo actualiza, y mientras tanto nadie entra (JSystem, web ni escritorio remoto). Este script anota, cada
# minuto, cuál es la IP real, a cuál apunta el nombre y cuánto dura cada desajuste, para saber cuántas veces pasa y qué tan grave es.
#
# SOLO OBSERVA Y ANOTA: no cambia nada del sistema. Bitácora: C:\skycel\logs\ip-monitor.log
#   «IP real cambió»     la IP pública del servidor es otra.
#   «DESAJUSTE»          el nombre apunta a una IP distinta de la real (aquí nadie puede entrar desde fuera).
#   «DESAJUSTE resuelto» el nombre ya coincide; dice cuánto duró.
#   «SIN INTERNET»       el servidor no pudo consultar su IP (se cayó su salida a internet); «INTERNET recuperado» dice cuánto duró.
#   «DUC»                el servicio de No-IP (NoIPDUCService4) no está corriendo.
#   «ok»                 una línea por hora de que todo sigue bien (así se sabe que el vigilante vive).

$nombre    = 'skycelsys.ddns.net'
$carpeta   = 'C:\skycel\logs'
$bitacora  = Join-Path $carpeta 'ip-monitor.log'
$intervalo = 60
$servidoresIp = @('https://api.ipify.org', 'https://checkip.amazonaws.com', 'https://icanhazip.com')

if (-not (Test-Path $carpeta)) { New-Item -ItemType Directory -Path $carpeta -Force | Out-Null }

function Anotar([string]$texto) {
    # si la bitácora pasa de 5 MB se guarda como .anterior y se empieza otra
    if ((Test-Path $bitacora) -and (Get-Item $bitacora).Length -gt 5MB) { Move-Item $bitacora "$bitacora.anterior" -Force }
    Add-Content -Path $bitacora -Value ('{0} {1}' -f (Get-Date -Format 'yyyy-MM-dd HH:mm:ss'), $texto) -Encoding UTF8
}

function IpReal {
    foreach ($url in $servidoresIp) {
        try {
            $ip = (Invoke-RestMethod -Uri $url -TimeoutSec 8).ToString().Trim()
            if ($ip -match '^\d{1,3}(\.\d{1,3}){3}$') { return $ip }
        } catch { }
    }
    return $null
}

function IpDelNombre {
    # se pregunta a un DNS público (no al caché de este equipo) para ver lo mismo que ve el resto de internet
    foreach ($dns in @('8.8.8.8', '1.1.1.1')) {
        try {
            $r = Resolve-DnsName -Name $nombre -Type A -Server $dns -DnsOnly -ErrorAction Stop | Where-Object { $_.Type -eq 'A' } | Select-Object -First 1
            if ($r) { return $r.IPAddress }
        } catch { }
    }
    return $null
}

function Duracion($desde) {
    $s = [int]((Get-Date) - $desde).TotalSeconds
    if ($s -lt 120) { return "$s s" }
    if ($s -lt 7200) { return ('{0} min' -f [math]::Round($s / 60)) }
    return ('{0:N1} h' -f ($s / 3600))
}

$ipAnterior = $null
$desajusteDesde = $null
$sinInternetDesde = $null
$ducAvisado = $false
$ultimoOk = [datetime]::MinValue

Anotar "Vigilante iniciado (cada $intervalo s) para $nombre"
while ($true) {
    $real = IpReal
    $dns  = IpDelNombre

    # internet del servidor
    if (-not $real) {
        if (-not $sinInternetDesde) { $sinInternetDesde = Get-Date; Anotar 'SIN INTERNET: el servidor no pudo consultar su IP pública' }
    } else {
        if ($sinInternetDesde) { Anotar ('INTERNET recuperado tras {0}' -f (Duracion $sinInternetDesde)); $sinInternetDesde = $null }

        # cambio de la IP real
        if ($real -ne $ipAnterior) {
            if ($ipAnterior) { Anotar "IP real cambió: $ipAnterior -> $real (el nombre apunta a: $(if ($dns) { $dns } else { 'no se pudo consultar' }))" }
            else { Anotar "IP real actual: $real (el nombre apunta a: $(if ($dns) { $dns } else { 'no se pudo consultar' }))" }
            $ipAnterior = $real
        }

        # el nombre contra la IP real
        if ($dns) {
            if ($dns -ne $real) {
                if (-not $desajusteDesde) { $desajusteDesde = Get-Date; Anotar "DESAJUSTE: el nombre apunta a $dns pero la IP real es $real" }
            } elseif ($desajusteDesde) {
                Anotar ('DESAJUSTE resuelto tras {0}: el nombre ya apunta a {1}' -f (Duracion $desajusteDesde), $real)
                $desajusteDesde = $null
            }
        }
    }

    # el cliente de No-IP
    $duc = Get-Service -Name 'NoIPDUCService4' -ErrorAction SilentlyContinue
    if ($duc -and $duc.Status -ne 'Running') {
        if (-not $ducAvisado) { Anotar "DUC: el servicio de No-IP está $($duc.Status)"; $ducAvisado = $true }
    } else { $ducAvisado = $false }

    if (((Get-Date) - $ultimoOk).TotalMinutes -ge 60) {
        Anotar ('ok: IP real {0}, el nombre apunta a {1}{2}' -f $(if ($real) { $real } else { '?' }), $(if ($dns) { $dns } else { '?' }), $(if ($desajusteDesde) { ' (DESAJUSTE abierto)' } else { '' }))
        $ultimoOk = Get-Date
    }
    Start-Sleep -Seconds $intervalo
}
