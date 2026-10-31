-- Limpieza del catalogo de articulos (2026-10-30). SOLO ASCII a proposito: se copia por el portapapeles del escritorio remoto.
--
--  1. Borra los articulos inactivos que nunca se usaron (sin compras, ventas, movimientos, existencias, etc.).
--  2. Agrupa las 70 lineas de "Protector" en 12 familias; el nombre de cada linea pasa a la descripcion para no perderlo.
--  3. Corrige errores de escritura, uniforma c/Mica y s/Mica, quita el "Case" repetido y las palabras duplicadas.
--  4. Une las descripciones que quedaron iguales, borra las que ningun articulo usa y recalcula los nombres.
--
-- Probado primero contra una copia de produccion. Todo corre en una transaccion: si algo falla, no queda nada a medias.
-- Para ensayar sin guardar, cambiar el COMMIT del final por ROLLBACK.

SET NAMES utf8mb4;
SET SESSION group_concat_max_len = 1000000;
START TRANSACTION;

-- ---------------------------------------------------------------------------------------------------------------------
-- 1. Articulos inactivos que nunca se usaron
-- ---------------------------------------------------------------------------------------------------------------------
DELETE m FROM producto_master m
WHERE m.activo = 0
  AND NOT EXISTS (SELECT 1 FROM compra_detalle x WHERE x.idprodmaster = m.idprodmaster)
  AND NOT EXISTS (SELECT 1 FROM garantia x WHERE x.idprodmaster = m.idprodmaster)
  AND NOT EXISTS (SELECT 1 FROM inventario_auditoria_detalle x WHERE x.idprodmaster = m.idprodmaster)
  AND NOT EXISTS (SELECT 1 FROM inventario_auditoria_unidad x WHERE x.idprodmaster = m.idprodmaster)
  AND NOT EXISTS (SELECT 1 FROM movimiento_inventario x WHERE x.idprodmaster = m.idprodmaster)
  AND NOT EXISTS (SELECT 1 FROM orden_servicio_detalle x WHERE x.idprodmaster = m.idprodmaster)
  AND NOT EXISTS (SELECT 1 FROM producto x WHERE x.idprodmaster = m.idprodmaster)
  AND NOT EXISTS (SELECT 1 FROM producto_imei x WHERE x.idprodmaster = m.idprodmaster)
  AND NOT EXISTS (SELECT 1 FROM solicitud_descuento x WHERE x.idprodmaster = m.idprodmaster)
  AND NOT EXISTS (SELECT 1 FROM traspaso_detalle x WHERE x.idprodmaster = m.idprodmaster)
  AND NOT EXISTS (SELECT 1 FROM venta_detalle x WHERE x.idprodmaster = m.idprodmaster);
SELECT ROW_COUNT() AS articulos_inactivos_borrados;

-- ---------------------------------------------------------------------------------------------------------------------
-- 2. Categorias: nombre mal escrito y familias de Protector
-- ---------------------------------------------------------------------------------------------------------------------
UPDATE categoria SET nombre = 'Huawei' WHERE nombre = 'Wuawei';

SET @protector = (SELECT idcat FROM categoria WHERE nombre = 'Protector' AND idcatsup = (SELECT idcat FROM categoria WHERE nombre = 'Accesorios' AND idcatsup IS NULL));
SET @tipo = (SELECT tipo FROM categoria WHERE idcat = @protector);

-- "Disenos" y "Otras lineas": agrupan lineas de proveedor; no entran al nombre del articulo (la linea va en la descripcion)
INSERT INTO categoria (activo, codigo, tipo, nombre, idcatsup, incluir_en_nombre)
VALUES (1, NULL, @tipo, CONVERT(UNHEX('44697365C3B16F73') USING utf8mb4), @protector, 0);
SET @disenos = LAST_INSERT_ID();
INSERT INTO categoria (activo, codigo, tipo, nombre, idcatsup, incluir_en_nombre)
VALUES (1, NULL, @tipo, CONVERT(UNHEX('4F74726173206CC3AD6E656173') USING utf8mb4), @protector, 0);
SET @otras = LAST_INSERT_ID();

-- Cada linea actual -> familia nueva y el texto que se antepone a la descripcion (vacio si la linea ya ES la familia)
DROP TEMPORARY TABLE IF EXISTS mapa_sub;
CREATE TEMPORARY TABLE mapa_sub (old SMALLINT PRIMARY KEY, nuevo SMALLINT NOT NULL, prefijo VARCHAR(60) NOT NULL);
INSERT INTO mapa_sub (old, nuevo, prefijo)
SELECT c.idcat,
  CASE
    WHEN c.nombre IN ('Silicon', 'Uso Rudo', 'Antigolpe', 'Acrigel', 'Acrilico', '360', 'Case', 'Magsafe', 'Cartera', 'Tablet') THEN c.idcat
    WHEN c.nombre = 'Rudo con Anillo' THEN (SELECT idcat FROM (SELECT idcat FROM categoria WHERE idcatsup = @protector AND nombre = 'Uso Rudo') u)
    WHEN c.nombre = '3 en 1' THEN (SELECT idcat FROM (SELECT idcat FROM categoria WHERE idcatsup = @protector AND nombre = 'Antigolpe') u)
    WHEN c.nombre = 'Bommper 360' THEN (SELECT idcat FROM (SELECT idcat FROM categoria WHERE idcatsup = @protector AND nombre = '360') u)
    WHEN c.nombre = 'Botarga' THEN (SELECT idcat FROM (SELECT idcat FROM categoria WHERE idcatsup = @protector AND nombre = 'Tablet') u)
    WHEN c.nombre IN ('Sparkle Cover', 'Fashion', 'Brillo', 'Disney', 'Shimmer', 'Glitter', 'Fancy Cover', 'Girly Cover', 'Pecera Brillo',
                      'Neon', 'Grafitti', 'Flower', 'Classy', 'Naturaleza Muerta', 'Marmo Brillo', 'Starlight', 'Love Muse', 'Diamante',
                      'Fnko', 'The North Face', 'Guess', 'Edge Color', 'Mobo', 'Rubik Case', 'Frame', 'Chochitos', 'Cadena', 'Magic Case',
                      'Funny Case', 'Creative Case', 'Kimo Case', 'Sharp Case', 'Hover Case', 'Covery Case', 'New Case', 'Spin',
                      'Holograma Samsung') THEN @disenos
    ELSE @otras
  END,
  CASE
    WHEN c.nombre IN ('Silicon', 'Uso Rudo', 'Antigolpe', 'Acrigel', 'Acrilico', '360', 'Case', 'Magsafe', 'Cartera', 'Tablet') THEN ''
    WHEN c.nombre = 'Rudo con Anillo' THEN 'con Anillo'
    WHEN c.nombre = 'Bommper 360' THEN 'Bommper'
    ELSE c.nombre
  END
FROM categoria c
WHERE c.idcatsup = @protector AND c.idcat NOT IN (@disenos, @otras);

-- Los articulos que estaban sueltos en Protector (sin linea) van a Disenos u Otras lineas segun lo que son
INSERT INTO mapa_sub (old, nuevo, prefijo) VALUES (@protector, @disenos, '');

-- Arbol de nombres (solo categorias que entran al nombre) para saber que palabras ya trae la categoria
DROP TEMPORARY TABLE IF EXISTS cat_ruta;
CREATE TEMPORARY TABLE cat_ruta (idcat SMALLINT PRIMARY KEY, ruta VARCHAR(500) NOT NULL);
INSERT INTO cat_ruta
WITH RECURSIVE t AS (
  SELECT idcat, CAST(IF(incluir_en_nombre = 1, nombre, '') AS CHAR(500)) AS ruta FROM categoria WHERE idcatsup IS NULL
  UNION ALL
  SELECT c.idcat, CAST(CONCAT_WS(' ', NULLIF(t.ruta, ''), IF(c.incluir_en_nombre = 1, c.nombre, NULL)) AS CHAR(500))
  FROM categoria c JOIN t ON c.idcatsup = t.idcat)
SELECT idcat, ruta FROM t;

-- ---------------------------------------------------------------------------------------------------------------------
-- 3. Descripciones en uso: nueva categoria y texto limpio
-- ---------------------------------------------------------------------------------------------------------------------
DROP TEMPORARY TABLE IF EXISTS desc_nueva;
CREATE TEMPORARY TABLE desc_nueva (
  iddescripcion INT PRIMARY KEY,
  idcat_nueva SMALLINT NOT NULL,
  prefijo VARCHAR(60) NOT NULL,
  limpio VARCHAR(250) NOT NULL,
  final_nombre VARCHAR(250) NULL,
  conserva INT NULL
);
INSERT INTO desc_nueva (iddescripcion, idcat_nueva, prefijo, limpio)
SELECT d.iddescripcion, COALESCE(ms.nuevo, d.idcat), COALESCE(ms.prefijo, ''), d.nombre
FROM descripcion_producto d LEFT JOIN mapa_sub ms ON ms.old = d.idcat
WHERE EXISTS (SELECT 1 FROM producto_master m WHERE m.iddescripcion = d.iddescripcion);

-- Los pocos articulos sueltos en Protector que no son "disenos": Brazalete, Promocion, Safety -> Otras lineas
UPDATE desc_nueva dn JOIN descripcion_producto d ON d.iddescripcion = dn.iddescripcion
SET dn.idcat_nueva = @otras
WHERE d.idcat = @protector AND (d.nombre LIKE 'Brazalete%' OR d.nombre LIKE 'Promocion%' OR d.nombre LIKE 'Safety%');

-- 3a. Errores de escritura
UPDATE desc_nueva SET limpio = REGEXP_REPLACE(limpio, '(\\d)Pro ?Max', '$1 Pro Max', 1, 0, 'i');
UPDATE desc_nueva SET limpio = REGEXP_REPLACE(limpio, '\\bPromax\\b', 'Pro Max', 1, 0, 'i');
UPDATE desc_nueva SET limpio = REGEXP_REPLACE(limpio, '(\\d)(Pro|Plus|Ultra|Lite|Mini)\\b', '$1 $2', 1, 0, 'i');
UPDATE desc_nueva SET limpio = REGEXP_REPLACE(limpio, '\\b(Hjonor|Hnor)\\b', 'Honor', 1, 0, 'i');
UPDATE desc_nueva SET limpio = REGEXP_REPLACE(limpio, '\\b(Wuawei|Huawey)\\b', 'Huawei', 1, 0, 'i');
UPDATE desc_nueva SET limpio = REGEXP_REPLACE(limpio, '\\b(Opoo|Opo)\\b', 'Oppo', 1, 0, 'i');
UPDATE desc_nueva SET limpio = REGEXP_REPLACE(limpio, '\\b(Mootorola|Motolora|Motora|Mototola)\\b', 'Motorola', 1, 0, 'i');
UPDATE desc_nueva SET limpio = REGEXP_REPLACE(limpio, '\\b(Samsumg|Ssmsung)\\b', 'Samsung', 1, 0, 'i');
UPDATE desc_nueva SET limpio = REGEXP_REPLACE(limpio, '\\bXiami\\b', 'Xiaomi', 1, 0, 'i');
UPDATE desc_nueva SET limpio = REGEXP_REPLACE(limpio, '\\bZtev\\b', 'ZTE', 1, 0, 'i');
UPDATE desc_nueva SET limpio = REGEXP_REPLACE(limpio, '\\bPsmart\\b', 'P Smart', 1, 0, 'i');
UPDATE desc_nueva SET limpio = REGEXP_REPLACE(limpio, '\\bMagentico\\b', 'Magnetico', 1, 0, 'i');
UPDATE desc_nueva SET limpio = REGEXP_REPLACE(limpio, '\\bOrginal\\b', 'Original', 1, 0, 'i');
UPDATE desc_nueva SET limpio = REGEXP_REPLACE(limpio, '\\bChargue\\b', 'Charge', 1, 0, 'i');
UPDATE desc_nueva SET limpio = REGEXP_REPLACE(limpio, '\\bSmartwach\\b', 'Smartwatch', 1, 0, 'i');
UPDATE desc_nueva SET limpio = REGEXP_REPLACE(limpio, '\\bParede\\b', 'Pared', 1, 0, 'i');

-- 3b. "Case" al principio de la descripcion (el articulo ya es un protector/case)
UPDATE desc_nueva SET limpio = REGEXP_REPLACE(limpio, '^Case\\s+(?=\\S)', '', 1, 1, 'i');

-- 3c. Palabra repetida: la descripcion empieza con una palabra que la categoria ya aporta al nombre
UPDATE desc_nueva dn JOIN cat_ruta r ON r.idcat = dn.idcat_nueva
SET dn.limpio = TRIM(SUBSTRING(dn.limpio, LENGTH(SUBSTRING_INDEX(dn.limpio, ' ', 1)) + 1))
WHERE dn.limpio LIKE '% %' AND CONCAT(' ', LOWER(r.ruta), ' ') LIKE CONCAT('% ', LOWER(SUBSTRING_INDEX(dn.limpio, ' ', 1)), ' %');

-- (otra vez, por si la descripcion repetia dos palabras seguidas)
UPDATE desc_nueva dn JOIN cat_ruta r ON r.idcat = dn.idcat_nueva
SET dn.limpio = TRIM(SUBSTRING(dn.limpio, LENGTH(SUBSTRING_INDEX(dn.limpio, ' ', 1)) + 1))
WHERE dn.limpio LIKE '% %' AND CONCAT(' ', LOWER(r.ruta), ' ') LIKE CONCAT('% ', LOWER(SUBSTRING_INDEX(dn.limpio, ' ', 1)), ' %');

-- 3d. Se antepone la linea de proveedor y se uniforman c/Mica y s/Mica
UPDATE desc_nueva SET final_nombre = TRIM(CONCAT_WS(' ', NULLIF(prefijo, ''), NULLIF(limpio, '')));
UPDATE desc_nueva SET final_nombre = REGEXP_REPLACE(final_nombre, '\\b(S/M|S/Mica|sin Mica)\\b', 's/Mica', 1, 0, 'i');
UPDATE desc_nueva SET final_nombre = REGEXP_REPLACE(final_nombre, '\\b(C/M|C/Mica|con Mica)\\b', 'c/Mica', 1, 0, 'i');
UPDATE desc_nueva SET final_nombre = REGEXP_REPLACE(final_nombre, '\\bC/Soporte\\b', 'c/Soporte', 1, 0, 'i');
UPDATE desc_nueva SET final_nombre = TRIM(REGEXP_REPLACE(final_nombre, '\\s+', ' '));

-- 3e. Las que quedaron iguales dentro de la misma categoria se unen en una sola
DROP TEMPORARY TABLE IF EXISTS grupos_desc;
CREATE TEMPORARY TABLE grupos_desc AS
  SELECT idcat_nueva, final_nombre, MIN(iddescripcion) AS conserva FROM desc_nueva GROUP BY idcat_nueva, final_nombre;
UPDATE desc_nueva dn JOIN grupos_desc g ON g.idcat_nueva = dn.idcat_nueva AND g.final_nombre = dn.final_nombre
SET dn.conserva = g.conserva;

SELECT COUNT(*) AS descripciones_en_uso, SUM(iddescripcion <> conserva) AS descripciones_que_se_unen,
       SUM(final_nombre <> (SELECT nombre FROM descripcion_producto d WHERE d.iddescripcion = desc_nueva.iddescripcion)) AS textos_que_cambian
FROM desc_nueva;

-- ---------------------------------------------------------------------------------------------------------------------
-- 4. Aplicar
-- ---------------------------------------------------------------------------------------------------------------------
-- 4a. Los articulos pasan a su nueva categoria y a la descripcion que se conserva
UPDATE producto_master m JOIN desc_nueva dn ON dn.iddescripcion = m.iddescripcion
SET m.idcat = dn.idcat_nueva, m.iddescripcion = dn.conserva;

-- 4b. Se borran las descripciones que ningun articulo usa (incluidas las que se acaban de unir)
DELETE d FROM descripcion_producto d WHERE NOT EXISTS (SELECT 1 FROM producto_master m WHERE m.iddescripcion = d.iddescripcion);
SELECT ROW_COUNT() AS descripciones_borradas;

-- 4c. Las que quedan se renombran en dos pasos para no chocar con la llave unica (categoria, nombre)
UPDATE descripcion_producto d JOIN desc_nueva dn ON dn.conserva = d.iddescripcion AND dn.iddescripcion = dn.conserva
SET d.nombre = CONCAT('~', d.iddescripcion);
UPDATE descripcion_producto d JOIN desc_nueva dn ON dn.conserva = d.iddescripcion AND dn.iddescripcion = dn.conserva
SET d.idcat = dn.idcat_nueva, d.nombre = dn.final_nombre;

-- 4d. Las lineas que ya se agruparon desaparecen (si alguna conserva algo, se queda y se avisa abajo)
DELETE c FROM categoria c JOIN mapa_sub ms ON ms.old = c.idcat
WHERE ms.old <> ms.nuevo AND c.idcat <> @protector
  AND NOT EXISTS (SELECT 1 FROM producto_master m WHERE m.idcat = c.idcat)
  AND NOT EXISTS (SELECT 1 FROM descripcion_producto d WHERE d.idcat = c.idcat)
  AND c.idcat NOT IN (SELECT idcatsup FROM (SELECT DISTINCT idcatsup FROM categoria WHERE idcatsup IS NOT NULL) hijos);
SELECT ROW_COUNT() AS categorias_borradas;

-- ---------------------------------------------------------------------------------------------------------------------
-- 5. Recalcular los nombres (categorias que entran al nombre + descripcion + color)
-- ---------------------------------------------------------------------------------------------------------------------
DELETE FROM cat_ruta;
INSERT INTO cat_ruta
WITH RECURSIVE t AS (
  SELECT idcat, CAST(IF(incluir_en_nombre = 1, nombre, '') AS CHAR(500)) AS ruta FROM categoria WHERE idcatsup IS NULL
  UNION ALL
  SELECT c.idcat, CAST(CONCAT_WS(' ', NULLIF(t.ruta, ''), IF(c.incluir_en_nombre = 1, c.nombre, NULL)) AS CHAR(500))
  FROM categoria c JOIN t ON c.idcatsup = t.idcat)
SELECT idcat, ruta FROM t;

UPDATE producto_master m
JOIN cat_ruta r ON r.idcat = m.idcat
JOIN descripcion_producto d ON d.iddescripcion = m.iddescripcion
LEFT JOIN color co ON co.idcolor = m.idcolor
SET m.nombre_producto = d.nombre,
    m.nombre_base = TRIM(REGEXP_REPLACE(CONCAT_WS(' ', NULLIF(r.ruta, ''), d.nombre, co.nombre), '\\s+', ' '));

-- ---------------------------------------------------------------------------------------------------------------------
-- 6. Revision antes de guardar
-- ---------------------------------------------------------------------------------------------------------------------
SELECT 'articulos' AS que, COUNT(*) AS total, SUM(activo = 1) AS activos FROM producto_master
UNION ALL SELECT 'descripciones', COUNT(*), NULL FROM descripcion_producto
UNION ALL SELECT 'categorias', COUNT(*), NULL FROM categoria
UNION ALL SELECT 'subcategorias de Protector', COUNT(*), NULL FROM categoria WHERE idcatsup = @protector
UNION ALL SELECT 'articulos activos repetidos (categoria+descripcion+color)', COUNT(*), NULL FROM
  (SELECT 1 FROM producto_master WHERE activo = 1 GROUP BY idcat, iddescripcion, idcolor HAVING COUNT(*) > 1) x
UNION ALL SELECT 'nombres de articulo repetidos entre activos', COUNT(*), NULL FROM
  (SELECT 1 FROM producto_master WHERE activo = 1 GROUP BY nombre_base HAVING COUNT(*) > 1) y;

COMMIT;
