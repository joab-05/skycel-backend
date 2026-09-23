-- Exporta el inventario con existencia del sistema anterior (skyceldb). Solo lectura.
-- Correr en MySQL Workbench (conectado a la PC del servidor), copiar la cuadrícula (Ctrl+A, Ctrl+C) y pegarla en un
-- archivo de texto UTF-8 llamado inventario_existente.tsv (sin encabezado). El orden de columnas debe respetarse.
SELECT TRIM(a.codigoArt) AS codigo, a.codti, c.nombre AS clase, t.nombre AS tipo, m.nombre AS marca,
       mo.nombre AS modelo, co.nombre AS color, d.nombre AS distribuidor, a.existencia,
       a.Preciopro AS costo, a.preciopub AS publico, DATE(a.fechaIngreso) AS ingreso, a.rezagado
FROM skyceldb.articulo a
JOIN skyceldb.clase c ON c.idclase = a.idclase
JOIN skyceldb.tipo t ON t.idtipo = a.idtipo
LEFT JOIN skyceldb.marca m ON m.idmarca = a.idmarca
LEFT JOIN skyceldb.modelo mo ON mo.idmodelo = a.idmodelo
LEFT JOIN skyceldb.color co ON co.idcolor = a.idcolor
LEFT JOIN skyceldb.distribuidor d ON d.iddistribuidor = a.iddistribuidor
WHERE a.existencia > 0
ORDER BY a.codti, c.nombre, t.nombre, m.nombre, mo.nombre;
