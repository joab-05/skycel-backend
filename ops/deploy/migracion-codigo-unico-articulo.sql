-- Unifica el código de un mismo artículo entre sucursales.
--
-- Antes, un traspaso (o dar de alta el artículo en otra tienda) le ponía al producto un código NUEVO
-- (p. ej. SIM-000001 en la bodega y SIM-000003 en Zócalo). Desde este cambio el artículo conserva su código
-- en todas las sucursales. Este script arregla los productos que ya se habían creado con código distinto.
--
-- Regla: productos con el mismo artículo maestro y el mismo color son "el mismo artículo". El código que se
-- conserva es el del producto más antiguo (menor idproducto). En cada otra sucursal se le pone ese código a su
-- producto más antiguo de ese artículo, salvo que esa sucursal ya use ese código para otra fila.
--
-- Correr en MySQL Workbench, conectado como root, ANTES de desplegar el jar nuevo y SIN traspasos en tránsito
-- (los renglones de traspaso_detalle guardan el código; uno pendiente que apunte a un código cambiado no se
-- podría recibir). Por defecto termina con ROLLBACK: revisa la vista previa y, si es lo esperado, cambia el
-- ROLLBACK del final por COMMIT y vuelve a correrlo.
--
-- No toca el historial de auditoría (producto_aud): ahí queda el código con que se registró cada cambio.

USE skyceldb2;

-- Traspasos sin resolver (deben ser 0; si no, esperar a que se reciban, resuelvan o anulen).
-- Estados: 1 = enviado / solicitud pendiente, 3 = solicitud leída (2 recibido, 4 aceptada, 5 rechazada ya cerraron).
SELECT COUNT(*) AS traspasos_sin_resolver
FROM traspaso
WHERE activo = 1 AND estado IN (1, 3);

START TRANSACTION;

DROP TEMPORARY TABLE IF EXISTS canon;
DROP TEMPORARY TABLE IF EXISTS cambios;

CREATE TEMPORARY TABLE canon AS
SELECT idprodmaster, idcolor, MIN(idproducto) AS idcanon
FROM producto
WHERE activo = 1
GROUP BY idprodmaster, idcolor;

CREATE TEMPORARY TABLE cambios AS
SELECT p.idproducto, p.codti, p.codpro AS codigo_actual, c.codpro AS codigo_nuevo
FROM producto p
JOIN canon k ON k.idprodmaster = p.idprodmaster AND k.idcolor <=> p.idcolor
JOIN producto c ON c.idproducto = k.idcanon
WHERE p.activo = 1
  AND p.codti <> c.codti
  AND p.codpro <> c.codpro
  -- solo la fila más antigua de ese artículo en esa sucursal
  AND p.idproducto = (SELECT MIN(x.idproducto) FROM producto x
                      WHERE x.idprodmaster = p.idprodmaster AND x.idcolor <=> p.idcolor
                        AND x.codti = p.codti AND x.activo = 1)
  -- y que la sucursal no use ya el código nuevo en otra fila
  AND NOT EXISTS (SELECT 1 FROM producto y WHERE y.codti = p.codti AND y.codpro = c.codpro);

-- Vista previa: qué código cambiaría en qué sucursal.
SELECT * FROM cambios ORDER BY codti, idproducto;

UPDATE producto p
JOIN cambios ch ON ch.idproducto = p.idproducto
SET p.codpro = ch.codigo_nuevo;

-- Comprobación: no debe quedar el mismo código repetido dentro de una misma sucursal.
SELECT codti, codpro, COUNT(*) AS veces FROM producto GROUP BY codti, codpro HAVING COUNT(*) > 1;

-- ROLLBACK = solo probar. Cambiar por COMMIT para aplicar de verdad.
ROLLBACK;
