# Respaldo automático de la base de datos

La base `skyceldb2` vive en una sola PC (Corpo), así que hay una tarea programada de Windows que
la respalda sola, sin depender de que alguien se acuerde.

- **Tarea de Windows:** `Skycel - Respaldo BD`, corre a las 8am y 8pm. Si la PC estaba apagada a
  esa hora, corre en cuanto se prenda y alguien inicie sesión (no se pierde el respaldo del día).
- **Script:** `C:\SkycelBackups\backup-db.ps1` en esa PC (no vive en este repo: usa una ruta de
  credenciales local, `C:\SkycelBackups\.my-backup.cnf`, con permisos restringidos solo al usuario
  de esa PC — nunca se sube a Git).
- **Qué hace:** `mysqldump` completo (estructura, datos, rutinas y triggers) → lo comprime a `.zip`
  → guarda una copia en `C:\SkycelBackups` y otra en la carpeta de OneDrive de esa PC (para no
  perderlo si la PC falla, se roba o se incendia la oficina).
- **Cuánto se guarda:** 14 días en local, 60 días en OneDrive (después se borran solos los viejos).
- **Restaurar:** ver `C:\SkycelBackups\RESTAURAR.md` en esa PC (pasos con `mysql < archivo.sql`
  tras descomprimir el `.zip` que se quiera usar).

## Si hay que rearmar esto en otra PC

1. Instalar MySQL Server (o tener acceso a `mysqldump.exe`) y tener OneDrive (opcional, para la
   segunda copia).
2. Copiar `backup-db.ps1` (ver plantilla en esta carpeta) a `C:\SkycelBackups\` en la PC nueva y
   ajustar las rutas si cambian.
3. Crear `C:\SkycelBackups\.my-backup.cnf` con el usuario/contraseña de MySQL:
   ```
   [client]
   user=adminpj
   password=<la contraseña real>
   ```
   y restringir el archivo al usuario de esa PC:
   ```powershell
   icacls "C:\SkycelBackups\.my-backup.cnf" /inheritance:r
   icacls "C:\SkycelBackups\.my-backup.cnf" /grant:r "$env:USERDOMAIN\$env:USERNAME:(R)"
   ```
4. Registrar la tarea programada (dos veces al día, corre solo si la sesión está iniciada):
   ```powershell
   $accion   = New-ScheduledTaskAction -Execute "powershell.exe" -Argument '-NoProfile -ExecutionPolicy Bypass -File "C:\SkycelBackups\backup-db.ps1"'
   $trigger1 = New-ScheduledTaskTrigger -Daily -At 8:00AM
   $trigger2 = New-ScheduledTaskTrigger -Daily -At 8:00PM
   $settings = New-ScheduledTaskSettingsSet -StartWhenAvailable -DontStopOnIdleEnd -ExecutionTimeLimit (New-TimeSpan -Minutes 15) -MultipleInstances IgnoreNew
   Register-ScheduledTask -TaskName "Skycel - Respaldo BD" -Action $accion -Trigger @($trigger1,$trigger2) -Settings $settings -Description "Respaldo diario (8am y 8pm) de skyceldb2."
   ```
5. Correrla una vez a mano para probar antes de confiar en el horario:
   ```powershell
   powershell -ExecutionPolicy Bypass -File "C:\SkycelBackups\backup-db.ps1"
   ```
