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

-- MySQL no permite usar la misma tabla temporal dos veces en una consulta: se hacen copias
DROP TEMPORARY TABLE IF EXISTS viejo_piezas_b; CREATE TEMPORARY TABLE viejo_piezas_b AS SELECT * FROM viejo_piezas;
DROP TEMPORARY TABLE IF EXISTS nuevo_piezas_b; CREATE TEMPORARY TABLE nuevo_piezas_b AS SELECT * FROM nuevo_piezas;
DROP TEMPORARY TABLE IF EXISTS viejo_equipos_b; CREATE TEMPORARY TABLE viejo_equipos_b AS SELECT * FROM viejo_equipos;
DROP TEMPORARY TABLE IF EXISTS nuevo_equipos_b; CREATE TEMPORARY TABLE nuevo_equipos_b AS SELECT * FROM nuevo_equipos;

-- ── Resumen por tienda ───────────────────────────────────────────────────────────────────────────────────────────────────
SELECT 'RESUMEN POR TIENDA (piezas = unidades; equipos = IMEI)' AS aviso;
SELECT t.codti, t.nombre,
  (SELECT COALESCE(SUM(cant),0) FROM viejo_piezas v WHERE v.codti = t.codti) AS piezas_viejo,
  (SELECT COALESCE(SUM(cant),0) FROM nuevo_piezas n WHERE n.codti = t.codti) AS piezas_nuevo,
  (SELECT COUNT(*) FROM viejo_equipos v WHERE v.codti = t.codti) AS equipos_viejo,
  (SELECT COUNT(*) FROM nuevo_equipos n WHERE n.codti = t.codti) AS equipos_nuevo
FROM tienda t WHERE t.codti IN (SELECT nuevo FROM mapa_tiendas) ORDER BY t.codti;

-- ── Piezas con diferencia ────────────────────────────────────────────────────────────────────────────────────────────────
SELECT 'PIEZAS: cantidad distinta, falta en el sistema nuevo o sobra en él' AS aviso;
SELECT x.codti, x.codigo, x.viejo, x.nuevo, x.nuevo - x.viejo AS diferencia,
       CASE WHEN x.nuevo = 0 THEN 'FALTA EN NUEVO' WHEN x.viejo = 0 THEN 'SOBRA EN NUEVO' ELSE 'CANTIDAD DISTINTA' END AS caso
FROM (
  SELECT v.codti, v.codigo, v.cant AS viejo, COALESCE(n.cant, 0) AS nuevo FROM viejo_piezas v LEFT JOIN nuevo_piezas n ON n.codigo = v.codigo AND n.codti = v.codti
  UNION
  SELECT n.codti, n.codigo, COALESCE(v.cant, 0), n.cant FROM nuevo_piezas_b n LEFT JOIN viejo_piezas_b v ON v.codigo = n.codigo AND v.codti = n.codti
) x WHERE x.viejo <> x.nuevo ORDER BY x.codti, caso, x.codigo;

-- ── Equipos con diferencia ───────────────────────────────────────────────────────────────────────────────────────────────
SELECT 'EQUIPOS: IMEI que falta, que sobra o que está en otra tienda' AS aviso;
SELECT v.codti AS tienda_vieja_mapeada, v.imei, n.codti AS tienda_nueva, 'FALTA EN NUEVO (o está en otra tienda)' AS caso
FROM viejo_equipos v LEFT JOIN nuevo_equipos n ON n.imei = v.imei WHERE n.imei IS NULL OR n.codti <> v.codti
UNION ALL
SELECT NULL, n.imei, n.codti, 'SOBRA EN NUEVO (disponible allá, no en el sistema anterior)'
FROM nuevo_equipos_b n LEFT JOIN viejo_equipos_b v ON v.imei = n.imei WHERE v.imei IS NULL;

-- Para los IMEI que faltan: ¿existen en el sistema nuevo en otro estado (vendido, dado de baja...)?
SELECT i.imei, i.estado, i.codti
FROM viejo_equipos v JOIN producto_imei i ON i.imei = v.imei WHERE i.estado <> 'DISPONIBLE';

-- ── Totales de diferencias ───────────────────────────────────────────────────────────────────────────────────────────────
SELECT 'TOTALES' AS aviso;
SELECT
 (SELECT COUNT(*) FROM viejo_piezas v LEFT JOIN nuevo_piezas n ON n.codigo = v.codigo AND n.codti = v.codti WHERE COALESCE(n.cant, 0) <> v.cant) AS piezas_distintas_o_faltantes,
 (SELECT COUNT(*) FROM nuevo_piezas_b n LEFT JOIN viejo_piezas_b v ON v.codigo = n.codigo AND v.codti = n.codti WHERE v.codigo IS NULL) AS piezas_que_sobran,
 (SELECT COUNT(*) FROM viejo_equipos v LEFT JOIN nuevo_equipos n ON n.imei = v.imei AND n.codti = v.codti WHERE n.imei IS NULL) AS equipos_faltantes,
 (SELECT COUNT(*) FROM nuevo_equipos_b n LEFT JOIN viejo_equipos_b v ON v.imei = n.imei WHERE v.imei IS NULL) AS equipos_que_sobran;
