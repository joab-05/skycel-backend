-- Iguala la existencia del sistema nuevo (skyceldb2) a la que dejó el sistema anterior (skyceldb) al corte del 2026-11-01.
-- ESCRIBE en producción: hacer respaldo antes (mysqldump skyceldb2) y correr primero comparar_existencias.sql (ops/migracion).
--   mysql -u <usuario> -p --default-character-set=utf8mb4 --table < igualar-existencias.sql
-- Todo va en una transacción. Qué hace:
--   * Piezas (accesorios...): fija el stock por código+tienda al del sistema anterior y deja kardex tipo CORTE;
--     lo que sobra se pone en 0; lo que no existe en esa tienda se copia (precios del mismo código en otra tienda) con kardex ALTA.
--   * Equipos: lo DISPONIBLE que el sistema anterior no tiene pasa a VENDIDO (CORTE); los IMEI del anterior se dejan DISPONIBLES
--     en su tienda; el stock de celular/tablet se recalcula contando IMEI disponibles.
-- Los IMEI/códigos que no existen en ninguna tienda se dan de alta aparte con ops/migracion/completar_faltantes.py.
-- Equivalencia de tiendas viejo → nuevo: 1→1, 2→2, 6→3, 7→4, 8→5, 9→6, 10→7. No escribe historial Envers (*_aud).
-- Compara la existencia que quedó en el sistema anterior (skyceldb) con la del sistema nuevo (skyceldb2). SOLO LECTURA.
-- Correr en el MySQL del servidor (mysql.exe) como root: no cambia nada, solo muestra diferencias.
--   mysql -u root -p --default-character-set=utf8mb4 --table < comparar_existencias.sql
--
-- Equivalencia de tiendas (viejo → nuevo): 1 Bodega→1 Matriz Central, 2 Zócalo→2, 6 Mina→3, 7 Superche→4, 8 Corpo→5, 9 Morelos→6,
-- 10 Taller Superche→7. Las tiendas 3, 4 y 5 del sistema anterior (Deportivo, Hidalgo, Guerrero) están cerradas y no se comparan.
-- Piezas (accesorios, servicios...): se compara por código y tienda, sumando la existencia.
-- Equipos (clase EQUIPO del sistema anterior; celular, tablet y módem en el nuevo): se compara por IMEI/serie, una unidad cada uno.

USE skyceldb2;

DROP TEMPORARY TABLE IF EXISTS mapa_tiendas;
CREATE TEMPORARY TABLE mapa_tiendas (viejo INT PRIMARY KEY, nuevo INT);
INSERT INTO mapa_tiendas VALUES (1,1),(2,2),(6,3),(7,4),(8,5),(9,6),(10,7);

SELECT 'Equivalencia de tiendas (revisar que los nombres correspondan)' AS aviso;
SELECT m.viejo, tv.nombre_ti AS tienda_vieja, m.nuevo, tn.nombre AS tienda_nueva
FROM mapa_tiendas m JOIN skyceldb.tienda tv ON tv.codti = m.viejo JOIN tienda tn ON tn.codti = m.nuevo ORDER BY m.viejo;

DROP TEMPORARY TABLE IF EXISTS viejo_piezas;
CREATE TEMPORARY TABLE viejo_piezas (codigo VARCHAR(40) NOT NULL, codti INT NOT NULL, cant DECIMAL(12,2), PRIMARY KEY (codigo, codti));
INSERT INTO viejo_piezas
SELECT TRIM(a.codigoArt), m.nuevo, SUM(a.existencia)
FROM skyceldb.articulo a JOIN skyceldb.clase c ON c.idclase = a.idclase JOIN mapa_tiendas m ON m.viejo = a.codti
WHERE a.existencia > 0 AND UPPER(c.nombre) <> 'EQUIPO' GROUP BY TRIM(a.codigoArt), m.nuevo;

DROP TEMPORARY TABLE IF EXISTS viejo_equipos;
CREATE TEMPORARY TABLE viejo_equipos (imei VARCHAR(40) NOT NULL, codti INT NOT NULL, PRIMARY KEY (imei));
INSERT IGNORE INTO viejo_equipos
SELECT TRIM(a.codigoArt), m.nuevo
FROM skyceldb.articulo a JOIN skyceldb.clase c ON c.idclase = a.idclase JOIN mapa_tiendas m ON m.viejo = a.codti
WHERE a.existencia > 0 AND UPPER(c.nombre) = 'EQUIPO';

DROP TEMPORARY TABLE IF EXISTS nuevo_piezas;
CREATE TEMPORARY TABLE nuevo_piezas (codigo VARCHAR(40) NOT NULL, codti INT NOT NULL, cant DECIMAL(12,2), PRIMARY KEY (codigo, codti));
INSERT INTO nuevo_piezas
SELECT p.codpro, p.codti, SUM(p.stock)
FROM producto p JOIN producto_master pm ON pm.idprodmaster = p.idprodmaster
WHERE p.stock > 0 AND pm.tipo NOT IN (0, 3)      -- 0 celular y 3 tablet se cuentan por unidad (IMEI)
GROUP BY p.codpro, p.codti;

DROP TEMPORARY TABLE IF EXISTS nuevo_equipos;
CREATE TEMPORARY TABLE nuevo_equipos (imei VARCHAR(40) NOT NULL, codti INT, PRIMARY KEY (imei));
INSERT IGNORE INTO nuevo_equipos SELECT imei, codti FROM producto_imei WHERE estado = 'DISPONIBLE';

START TRANSACTION;
SET @ref = 'CORTE-20261101';
SET @mot = 'Igualado a la existencia final del sistema anterior';

DROP TEMPORARY TABLE IF EXISTS cambios;
CREATE TEMPORARY TABLE cambios (idproducto INT PRIMARY KEY, codti INT, idprodmaster INT, antes DECIMAL(10,2), despues DECIMAL(10,2));
INSERT INTO cambios
SELECT p.idproducto, p.codti, p.idprodmaster, COALESCE(p.stock,0), v.cant
FROM producto p JOIN producto_master pm ON pm.idprodmaster = p.idprodmaster JOIN viejo_piezas v ON v.codigo = p.codpro AND v.codti = p.codti
WHERE pm.tipo NOT IN (0,3) AND COALESCE(p.stock,0) <> v.cant;
INSERT INTO cambios
SELECT p.idproducto, p.codti, p.idprodmaster, p.stock, 0
FROM producto p JOIN producto_master pm ON pm.idprodmaster = p.idprodmaster
WHERE pm.tipo NOT IN (0,3) AND p.stock > 0 AND NOT EXISTS (SELECT 1 FROM viejo_piezas v WHERE v.codigo = p.codpro AND v.codti = p.codti);
INSERT INTO movimiento_inventario (cantidad, fecha, motivo, referencia, stock_antes, stock_despues, tipo, idproducto, codti, idprodmaster)
SELECT despues - antes, NOW(6), @mot, @ref, antes, despues, 'CORTE', idproducto, codti, idprodmaster FROM cambios;
UPDATE producto p JOIN cambios c ON c.idproducto = p.idproducto SET p.stock = c.despues, p.activo = IF(c.despues > 0, 1, p.activo);

DROP TEMPORARY TABLE IF EXISTS copias;
CREATE TEMPORARY TABLE copias (codigo VARCHAR(40), codti INT, cant DECIMAL(12,2), idorigen INT);
INSERT INTO copias
SELECT v.codigo, v.codti, v.cant, (SELECT MIN(p2.idproducto) FROM producto p2 JOIN producto_master pm2 ON pm2.idprodmaster = p2.idprodmaster WHERE p2.codpro = v.codigo AND pm2.tipo NOT IN (0,3))
FROM viejo_piezas v WHERE NOT EXISTS (SELECT 1 FROM producto p WHERE p.codpro = v.codigo AND p.codti = v.codti);
DELETE FROM copias WHERE idorigen IS NULL;
INSERT INTO producto (codpro, codti, idprodmaster, idmagnitud, idcolor, idproveedor, stock, preciopro, preciopub, activo, publico, rezagado, version)
SELECT c.codigo, c.codti, o.idprodmaster, o.idmagnitud, o.idcolor, o.idproveedor, c.cant, o.preciopro, o.preciopub, 1, o.publico, 0, 0
FROM copias c JOIN producto o ON o.idproducto = c.idorigen;
INSERT INTO movimiento_inventario (cantidad, fecha, motivo, referencia, stock_antes, stock_despues, tipo, idproducto, codti, idprodmaster)
SELECT c.cant, NOW(6), @mot, @ref, 0, c.cant, 'ALTA', p.idproducto, p.codti, p.idprodmaster FROM copias c JOIN producto p ON p.codpro = c.codigo AND p.codti = c.codti;

DROP TEMPORARY TABLE IF EXISTS imei_baja;
CREATE TEMPORARY TABLE imei_baja AS SELECT i.id, i.imei, i.idproducto, i.codti, i.idprodmaster FROM producto_imei i WHERE i.estado = 'DISPONIBLE' AND NOT EXISTS (SELECT 1 FROM viejo_equipos v WHERE v.imei = i.imei);
INSERT INTO movimiento_inventario (cantidad, fecha, motivo, referencia, stock_antes, stock_despues, tipo, idproducto, codti, imei, idprodmaster)
SELECT -1, NOW(6), @mot, @ref, 1, 0, 'CORTE', idproducto, codti, imei, idprodmaster FROM imei_baja;
UPDATE producto_imei i JOIN imei_baja b ON b.id = i.id SET i.estado = 'VENDIDO';
UPDATE producto_imei i JOIN viejo_equipos v ON v.imei = i.imei JOIN producto p0 ON p0.idproducto = i.idproducto JOIN producto pd ON pd.codpro = p0.codpro AND pd.codti = v.codti
SET i.codti = v.codti, i.idproducto = pd.idproducto, i.estado = 'DISPONIBLE' WHERE NOT (i.estado = 'DISPONIBLE' AND i.codti = v.codti);
UPDATE producto p JOIN producto_master pm ON pm.idprodmaster = p.idprodmaster
SET p.stock = (SELECT COUNT(*) FROM producto_imei i WHERE i.idproducto = p.idproducto AND i.estado = 'DISPONIBLE') WHERE pm.tipo IN (0,3);

SELECT (SELECT COUNT(*) FROM cambios) AS piezas_ajustadas, (SELECT COUNT(*) FROM copias) AS piezas_copiadas, (SELECT COUNT(*) FROM imei_baja) AS equipos_dados_de_baja;
COMMIT;
