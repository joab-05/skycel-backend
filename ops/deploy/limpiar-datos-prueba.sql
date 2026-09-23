-- Limpia los datos de PRUEBA de inventario para poder cargar el inventario real (ops/migracion).
--
-- Borra: traspasos, productos, unidades (IMEI), movimientos de inventario, artículos maestros, categorías,
--        colores y proveedores, y reinicia el contador de códigos de equipos (CEL-).
-- Además borra la ÚNICA venta de prueba conocida (idventa 1, $79, SIM-000003, 2026-09-23) con su detalle y su
-- movimiento de caja "Venta #1" ($79), porque depende de los productos de prueba.
-- NO toca: tiendas, usuarios, empleados, cajas, configuración, motivos de caja, clientes, otras ventas ni órdenes
--          de servicio, ni el historial de auditoría (*_aud).
--
-- Seguridad: los borrados de la venta están acotados por id, monto y observaciones; cualquier otra venta, orden o
-- garantía que dependa de los productos hace fallar el borrado por llave foránea (y no se borra nada). Antes de
-- correrlo, revisar la guardia: debe dar 1 venta, 1 detalle y 0 órdenes/garantías.
--
-- OJO: si una sentencia falla a mitad, la transacción queda ABIERTA: correr ROLLBACK; antes de reintentar.
-- Correr en MySQL Workbench conectado como root, con la base skyceldb2 seleccionada. Por defecto termina con
-- ROLLBACK (solo muestra qué borraría); para aplicar de verdad cambiar el ROLLBACK final por COMMIT.

USE skyceldb2;
-- El modo seguro del Workbench rechaza DELETE sin WHERE (error 1175) y deja la transacción a medias.
SET SQL_SAFE_UPDATES = 0;

-- 1) Guardia: nada de esto debe estar en uso por datos reales.
SELECT
  (SELECT COUNT(*) FROM venta)                  AS ventas,            -- debe ser 1
  (SELECT COUNT(*) FROM venta_detalle)          AS ventas_detalle,    -- debe ser 1
  (SELECT COUNT(*) FROM venta_pago_detalle)     AS ventas_pagos,      -- debe ser 0
  (SELECT COUNT(*) FROM movimiento_caja)        AS mov_caja,          -- debe ser 1 ("Venta #1")
  (SELECT COUNT(*) FROM orden_servicio)         AS ordenes,           -- debe ser 0
  (SELECT COUNT(*) FROM orden_servicio_detalle) AS ordenes_detalle,   -- debe ser 0
  (SELECT COUNT(*) FROM garantia)               AS garantias,         -- debe ser 0
  (SELECT COUNT(*) FROM cuenta_por_cobrar)      AS cuentas,           -- debe ser 0
  (SELECT COUNT(*) FROM devolucion)             AS devoluciones;      -- debe ser 0
-- Si algo no coincide, NO continuar.

START TRANSACTION;

-- 2) Qué hay hoy (para comparar antes/después).
SELECT 'traspaso' t, COUNT(*) n FROM traspaso UNION ALL SELECT 'producto', COUNT(*) FROM producto
UNION ALL SELECT 'producto_imei', COUNT(*) FROM producto_imei UNION ALL SELECT 'movimiento_inventario', COUNT(*) FROM movimiento_inventario
UNION ALL SELECT 'producto_master', COUNT(*) FROM producto_master UNION ALL SELECT 'categoria', COUNT(*) FROM categoria
UNION ALL SELECT 'color', COUNT(*) FROM color UNION ALL SELECT 'proveedor', COUNT(*) FROM proveedor
UNION ALL SELECT 'venta', COUNT(*) FROM venta UNION ALL SELECT 'movimiento_caja', COUNT(*) FROM movimiento_caja;

-- 3) Borrado en orden de dependencias.
DELETE FROM movimiento_caja WHERE idmovimiento = 1 AND observaciones = 'Venta #1' AND monto = 79.00;
DELETE FROM venta_detalle WHERE idventa = 1;
DELETE FROM venta WHERE idventa = 1 AND total = 79.00;
DELETE FROM traspaso_faltante_mov;
DELETE FROM traspaso_detalle;
DELETE FROM traspaso;
DELETE FROM producto_imei;
DELETE FROM movimiento_inventario;
DELETE FROM producto;
DELETE FROM producto_master;
UPDATE categoria SET idcatsup = NULL;      -- las subcategorías apuntan a su padre
DELETE FROM categoria;
DELETE FROM color;
DELETE FROM proveedor;
DELETE FROM notificacion WHERE ruta LIKE '%traspaso%';

-- 4) Contador de códigos de equipos (CEL-) de nuevo en 0. Los reservados negativos (órdenes, garantías,
--    devoluciones) no se tocan.
UPDATE categoria_folio SET ultimo_folio = 0 WHERE idcat = 0;
DELETE FROM categoria_folio WHERE idcat > 0;

-- 5) Después: todo debe estar en 0.
SELECT 'traspaso' t, COUNT(*) n FROM traspaso UNION ALL SELECT 'producto', COUNT(*) FROM producto
UNION ALL SELECT 'producto_imei', COUNT(*) FROM producto_imei UNION ALL SELECT 'movimiento_inventario', COUNT(*) FROM movimiento_inventario
UNION ALL SELECT 'producto_master', COUNT(*) FROM producto_master UNION ALL SELECT 'categoria', COUNT(*) FROM categoria
UNION ALL SELECT 'color', COUNT(*) FROM color UNION ALL SELECT 'proveedor', COUNT(*) FROM proveedor
UNION ALL SELECT 'venta', COUNT(*) FROM venta UNION ALL SELECT 'movimiento_caja', COUNT(*) FROM movimiento_caja;

-- ROLLBACK = solo probar. Cambiar por COMMIT para aplicar de verdad.
ROLLBACK;
