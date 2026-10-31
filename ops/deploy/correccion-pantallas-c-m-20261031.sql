-- En Pantallas, C/M significa "con Marco" (no "con Mica"): la limpieza del 2026-10-30 lo habia cambiado a c/Mica.
SET NAMES utf8mb4;
START TRANSACTION;
SET @pantalla = (SELECT idcat FROM categoria WHERE nombre = 'Pantalla' AND idcatsup IS NOT NULL OR (nombre = 'Pantalla' AND idcatsup IS NULL) LIMIT 1);
UPDATE descripcion_producto d JOIN categoria c ON c.idcat = d.idcat
SET d.nombre = REPLACE(REPLACE(d.nombre, 'c/Mica', 'con Marco'), 's/Mica', 'sin Marco')
WHERE (c.idcat = @pantalla OR c.idcatsup = @pantalla) AND (d.nombre LIKE '%c/Mica%' OR d.nombre LIKE '%s/Mica%');
SELECT ROW_COUNT() AS descripciones_corregidas;
UPDATE producto_master m JOIN categoria c ON c.idcat = m.idcat
SET m.nombre_base = REPLACE(REPLACE(m.nombre_base, 'c/Mica', 'con Marco'), 's/Mica', 'sin Marco'),
    m.nombre_producto = REPLACE(REPLACE(m.nombre_producto, 'c/Mica', 'con Marco'), 's/Mica', 'sin Marco')
WHERE (c.idcat = @pantalla OR c.idcatsup = @pantalla) AND (m.nombre_base LIKE '%c/Mica%' OR m.nombre_base LIKE '%s/Mica%');
SELECT ROW_COUNT() AS articulos_corregidos;
SELECT nombre_base FROM producto_master m JOIN categoria c ON c.idcat = m.idcat WHERE (c.idcat = @pantalla OR c.idcatsup = @pantalla) AND m.nombre_base LIKE '%Marco%' ORDER BY nombre_base;
COMMIT;
