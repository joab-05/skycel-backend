-- Une los 4 pares de articulos que eran el mismo producto dado de alta dos veces (opcion B): el articulo se deja uno solo y
-- cada renglon de cada tienda conserva su codigo de barras, su precio y sus existencias. Ninguno de los repetidos tenia ventas,
-- compras, traspasos, garantias ni movimientos; si alguno los tuviera, el borrado falla y no se guarda nada.
START TRANSACTION;
CREATE TEMPORARY TABLE par (conserva INT PRIMARY KEY, quita INT NOT NULL);
INSERT INTO par VALUES (608, 2641), (947, 960), (1098, 4731), (5732, 5736);
SELECT COUNT(*) AS renglones_antes, SUM(stock) AS existencias_antes FROM producto WHERE idprodmaster IN (608, 947, 1098, 5732, 2641, 960, 4731, 5736);
UPDATE producto p JOIN par ON p.idprodmaster = par.quita SET p.idprodmaster = par.conserva;
SELECT ROW_COUNT() AS renglones_pasados;
DELETE m FROM producto_master m JOIN par ON m.idprodmaster = par.quita;
SELECT ROW_COUNT() AS articulos_repetidos_borrados;
DELETE d FROM descripcion_producto d WHERE NOT EXISTS (SELECT 1 FROM producto_master m WHERE m.iddescripcion = d.iddescripcion);
SELECT m.idprodmaster, m.nombre_base, COUNT(p.idproducto) AS renglones, SUM(p.stock) AS existencias FROM producto_master m LEFT JOIN producto p ON p.idprodmaster = m.idprodmaster WHERE m.idprodmaster IN (SELECT conserva FROM par) GROUP BY m.idprodmaster, m.nombre_base;
SELECT COUNT(*) AS nombres_repetidos_entre_activos FROM (SELECT 1 FROM producto_master WHERE activo = 1 GROUP BY nombre_base HAVING COUNT(*) > 1) x;
COMMIT;
