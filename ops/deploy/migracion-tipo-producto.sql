-- Migración: TipoProducto pasa de VARCHAR a TINYINT (ordinal), y Categoria gana su propio tipo
-- (para que un Accesorio no vea categorías de Servicio o Celular, y viceversa).
--
-- Orden de los valores del enum TipoProducto — NO reordenar, solo agregar nuevos al final:
--   0 = CELULAR, 1 = ACCESORIO, 2 = SERVICIO, 3 = TABLET
--
-- Correr ANTES de actualizar el jar a la versión que ya espera estas columnas como TINYINT
-- (con ddl-auto: update, Hibernate no convierte de forma confiable un VARCHAR existente a TINYINT).

-- Workbench bloquea por defecto un UPDATE que no filtre por una columna llave (Modo seguro).
-- Se desactiva solo para esta sesión (no toca ninguna preferencia permanente) y se reactiva al final.
SET SQL_SAFE_UPDATES = 0;

-- 1) producto_master.tipo: VARCHAR -> TINYINT
UPDATE producto_master SET tipo = '0' WHERE tipo = 'CELULAR';
UPDATE producto_master SET tipo = '1' WHERE tipo = 'ACCESORIO';
UPDATE producto_master SET tipo = '2' WHERE tipo = 'SERVICIO';
ALTER TABLE producto_master MODIFY COLUMN tipo TINYINT NOT NULL DEFAULT 1;

-- 2) categoria.tipo: columna nueva. Se llena heredando el tipo de la categoría raíz de cada árbol
--    (buscando por NOMBRE de la raíz — "Celular"/"Accesorio"/"Servicio" —, no por ID, porque los IDs
--    pueden ser distintos en cada base de datos).
ALTER TABLE categoria ADD COLUMN tipo TINYINT NULL AFTER codigo;

WITH RECURSIVE arbol AS (
    SELECT idcat, idcatsup,
        CASE nombre WHEN 'Celular' THEN 0 WHEN 'Accesorio' THEN 1 WHEN 'Servicio' THEN 2 END AS tipo_raiz
    FROM categoria WHERE idcatsup IS NULL
    UNION ALL
    SELECT c.idcat, c.idcatsup, a.tipo_raiz
    FROM categoria c JOIN arbol a ON c.idcatsup = a.idcat
)
UPDATE categoria c
JOIN arbol a ON c.idcat = a.idcat
SET c.tipo = a.tipo_raiz
WHERE a.tipo_raiz IS NOT NULL;

-- Antes de la siguiente línea, revisar si quedó alguna categoría sin tipo (una raíz con nombre distinto
-- a Celular/Accesorio/Servicio, por ejemplo una creada a mano desde la web):
--   SELECT idcat, nombre, idcatsup FROM categoria WHERE tipo IS NULL;
-- Si aparece alguna y no es Accesorio, asígnale el tipo correcto a mano antes de continuar, ej.:
--   UPDATE categoria SET tipo = 0 WHERE idcat = <id>;   -- 0 Celular, 1 Accesorio, 2 Servicio, 3 Tablet

-- Cualquiera que siga sin tipo después de tu revisión, se le asigna Accesorio por default:
UPDATE categoria SET tipo = 1 WHERE tipo IS NULL;

ALTER TABLE categoria MODIFY COLUMN tipo TINYINT NOT NULL;

SET SQL_SAFE_UPDATES = 1;
