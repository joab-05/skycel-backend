# HTTPS para skycelsys.ddns.net (Caddy)

Deja el backend (API y la web `/app`) accesible desde fuera de la tienda con HTTPS de verdad,
usando el dominio dinámico que ya se tenía (`skycelsys.ddns.net`, apunta a la IP pública de Corpo,
`187.171.172.10` con Telmex Empresarial, PPPoE).

**Estado (2026-09-22): la parte de HTTPS ya está lista y probada de punta a punta** (Caddy consigue
su certificado real de Let's Encrypt y responde en el dominio). Lo que falta es aparte y más
grande: **skycel-backend todavía no está desplegado en esa PC** — las tiendas siguen operando con
el sistema anterior ("softhards", una app Dropwizard/Java 11 distinta, corriendo en el puerto 8081
de esa misma PC). El Caddyfile ya apunta a `localhost:8080`, que es donde debe quedar escuchando
skycel-backend el día que se despliegue ahí — no hay que tocarlo de nuevo para eso.

## Qué se necesita (una sola vez, en el servidor de Corpo — 192.168.1.105)

1. **DNS dinámico**: ya está bien — `skycelsys.ddns.net` ya resuelve a la IP pública actual del
   router. Como es PPPoE, esa IP podría cambiar si el router se reinicia; si algún día deja de
   coincidir, hay que revisar el cliente de No-IP (cuenta del usuario) o configurar el DDNS del
   propio router de Telmex si lo soporta.

2. **Reenvío de puertos en el router** (`192.168.1.254` → Firewall → Reenvío de Puertos): ✅ ya
   hecho — se agregaron dos reglas (TCP, puertos 80 y 443) hacia `192.168.1.105`, junto a las que
   ya existían para Escritorio Remoto (3389) y MySQL (3306).

   **IP fija recomendada:** confirma que `192.168.1.105` esté reservada para esta PC en el DHCP del
   router (o configurada como IP fija en la propia PC). Si el router le llega a dar otra IP algún
   día, el reenvío de puertos apuntaría al lugar equivocado.

3. **Instalar y arrancar Caddy** como servicio de Windows: correr, como Administrador, en esta
   carpeta (`ops/https/` de este repo, ya clonado o copiado a la PC del servidor):
   ```powershell
   .\instalar-caddy.ps1
   ```
   Descarga Caddy y NSSM, copia el `Caddyfile` de este repo a `C:\caddy\`, registra el servicio
   `Caddy` (arranque automático con Windows) y abre el firewall local en 80/443. Caddy pide,
   instala y renueva el certificado de Let's Encrypt solo — no hay que tocar nada más.

4. **Verificar**: abre `https://skycelsys.ddns.net/api/publico/salud` desde un celular con datos
   móviles (no wifi de la tienda) — debe responder `200` con un candado válido. También pídeme
   verificarlo yo desde aquí (llego desde fuera de tu red, así que es una prueba real).

## Después de que quede andando

- **JSystem, en las computadoras que NO están en la red de Corpo** (si alguna vez se usa así):
  cambiar `servidor.url` en `jsystem.properties` a `https://skycelsys.ddns.net` en vez de una IP
  local. Las que sí están en la red de Corpo pueden seguir usando la IP local (más rápido, no
  depende de que el internet de Corpo esté bien).
- **La web `/app`**: ya sirve en el mismo dominio, así que un celular fuera de la tienda puede
  entrar directo a `https://skycelsys.ddns.net/app`.

## Nota de seguridad (no es parte de este cambio, pero se vio al configurar el router)

El puerto **3306 (MySQL) está reenviado directo a internet** hacia `192.168.1.105`. Eso expone el
motor de base de datos completo a cualquiera que lo intente, protegido solo por la contraseña de
MySQL. Si no es indispensable administrar la base desde fuera de la tienda por ese puerto, conviene
quitar esa regla del router y, si hace falta administrarla remoto, usar el mismo túnel del
Escritorio Remoto (3389) en su lugar, o una VPN. Esto lo decide el usuario — no se tocó.
