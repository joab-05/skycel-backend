# Skycel Backend - Spring Boot 3 & Java 21

Este es el repositorio del Backend del Sistema Skycel, rediseñado bajo una arquitectura empresarial sólida, escalable y transaccional utiliznado Spring Boot 3.3 y Java 21.

## Fase Actual: FASE 2 Completada (Seguridad y Setup)
Actualmente el proyecto cuenta con:
1. Conexión local a MySQL (`skyceldb2`) usando **HikariCP**.
2. Entidades Base para Autenticación: `Usuario`, `Tienda`, `UsuarioSesion` y Enum `Rol`.
3. Historial Automático de Auditoría para tablas críticas activo vía **Hibernate Envers** (`@Audited`).
4. Seguridad **JWT** Standalone configurada sin estado (Stateless).
5. Filtro Activo de Límite de Peticiones (**Bucket4j**) para mitigar ataques DoS.
6. Controlador Auth `/api/auth/login` listo para probar.

## Pasos para probar en IntelliJ IDEA
1. Abre **IntelliJ IDEA**.
2. Selecciona `File` -> `Open...` y elige la carpeta `skycel-backend`. (Asegúrate de abrir la carpeta que contiene el archivo pom.xml).
3. IntelliJ detectará automáticamente que es un proyecto Maven e indexará las dependencias. Esto puede tardar 1-2 minutos.
4. Asegúrate que en `File -> Project Structure`, el `Project SDK` esté seteado a **Java 21**.
5. Ve a `src/main/resources/application.yml` y valida que la contraseña local del root de MySQL sea correcta.
6. Ejecuta la clase principal: `src/main/java/com/skycel/backend/SkycelBackendApplication.java` dándole clic derecho -> `Run 'SkycelBackendApplication.main()'`.
7. Si el log de la consola dice `Started SkycelBackendApplication in X seconds`, ¡Felicidades! Tienes tu servicio conectado a tu DDL original.

## Documentación de la API (Swagger UI)
La documentación del código está **automatizada y viva**. No escribimos documentos de Word que se desactualizan.
Inicia tu aplicación en IntelliJ IDEA y abre tu navegador web en la siguiente ruta:

👉 **[http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)**

Ahí encontrarás la interfaz interactiva generada por **Springdoc OpenAPI**.
1. Despliega el bloque `Autenticación` -> `POST /api/auth/login`.
2. Dale a `Try it out`.
3. Ingresa credenciales válidas en el JSON de prueba.
4. Dale a `Execute`. Verás como tu API te responde con un token JWT válido y audita de fondo usando la BD.
