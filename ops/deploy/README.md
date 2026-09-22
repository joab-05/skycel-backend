# Desplegar skycel-backend en Corpo (en paralelo, en pruebas)

Pone a correr el backend nuevo en el servidor real de Corpo (`192.168.1.105`), en el puerto 8080
(donde ya apunta Caddy/HTTPS — ver `ops/https/`), **sin tocar el sistema actual** ("softhards",
puerto 8081, bases `skyceldb`/`softhards_db`). Arranca con una base de datos nueva y vacía —
es para probar el sistema nuevo en paralelo, no una migración de datos.

## Pasos, en orden

1. **Base de datos** — en MySQL Workbench, conectado como root, en el servidor de Corpo: correr
   `crear-base-datos.sql` (edita antes la contraseña que trae de ejemplo). Crea `skyceldb2` vacía
   y un usuario `skycel_app` que solo puede tocar esa base.

2. **Llevar el jar compilado a esa PC.** Se compiló en la PC de desarrollo
   (`target\backend-0.0.1-SNAPSHOT.jar`, ~59 MB) y se dejó también en
   `OneDrive\SkycelDeploy\api.jar` de esta cuenta, para bajarlo fácil desde la sesión remota.
   Ponlo en el servidor como `C:\skycel\api.jar` (crea la carpeta si no existe).

3. **Correr el script**, como Administrador, en el servidor:
   ```powershell
   .\desplegar-backend.ps1
   ```
   Descarga un JDK 21 aparte (el sistema actual usa Java 11, no se toca), te pide ahí mismo el
   usuario/contraseña de MySQL del paso 1 y un `JWT_SECRET` (Enter para que lo genere solo),
   registra el servicio de Windows `SkycelBackend` (arranque automático) y lo arranca. Al final
   prueba `http://localhost:8080/api/publico/salud` y te dice si quedó listo.

4. **Entrar por primera vez:** usuario `root`, contraseña `admin123` (se crea sola en una base
   vacía) — cámbiala en cuanto entres, porque esta instancia va a ser accesible por internet.
   Desde ahí: `https://skycelsys.ddns.net/app` (web) o JSystem apuntando a esa URL.

## Qué NO hace esto

- No mueve datos de `skyceldb` / `softhards_db`. Si más adelante se decide migrar de verdad,
  es un proyecto aparte (mapear tablas, limpiar datos, cutover con las tiendas).
- No apaga ni toca el servicio del sistema actual.

## Revisar / reiniciar

```powershell
Get-Service SkycelBackend
Get-Content C:\skycel\logs\service.log -Tail 50
C:\caddy\nssm.exe restart SkycelBackend    # o el nssm.exe que haya quedado en C:\skycel
```
