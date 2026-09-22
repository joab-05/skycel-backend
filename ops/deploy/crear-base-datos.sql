-- Corre esto en MySQL Workbench, conectado como root, en el MySQL del servidor de Corpo.
-- Crea una base NUEVA y VACÍA para el sistema Skycel (en pruebas, en paralelo al sistema actual):
-- no toca `skyceldb` ni `softhards_db` para nada. Las tablas se crean solas cuando arranca el
-- backend por primera vez (Hibernate con ddl-auto: update).

CREATE DATABASE IF NOT EXISTS skyceldb2 CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- Cambia la contraseña de abajo por una tuya antes de correr esto (no la dejes tal cual).
-- Este usuario solo puede tocar skyceldb2 (no ve skyceldb ni softhards_db).
CREATE USER IF NOT EXISTS 'skycel_app'@'localhost' IDENTIFIED BY 'CAMBIA-ESTA-CONTRASENA';
GRANT ALL PRIVILEGES ON skyceldb2.* TO 'skycel_app'@'localhost';
FLUSH PRIVILEGES;
