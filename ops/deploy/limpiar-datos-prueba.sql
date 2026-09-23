-- Limpia los datos de PRUEBA de inventario para poder cargar el inventario real (ops/migracion).
--
-- Borra: traspasos, productos, unidades (IMEI), movimientos de inventario, artículos maestros, categorías,
--        colores y proveedores, y reinicia el contador de códigos de equipos (CEL-).
-- NO toca: tiendas, usuarios, empleados, cajas, configuración, motivos de caja, clientes, ventas ni órdenes
--          de servicio, ni el historial de auditoría (*_aud).
--
-- Seguridad: si ya existen ventas, órdenes o garantías que dependan de esos productos/proveedores, el script
-- se detiene ANTES de borrar nada (esos datos no son de prueba y hay que revisarlos a mano).
--
-- Correr en MySQL Workbench conectado como root, con la base skyceldb2 seleccionada. Por defecto termina con
-- ROLLBACK (solo muestra qué borraría); para aplicar de verdad cambiar el ROLLBACK final por COMMIT.

USE skyceldb2;

-- 1) Guardia: nada de esto debe estar en uso por datos reales.
SELECT
  (SELECT COUNT(*) FROM venta_detalle)          AS ventas_detalle,
  (SELECT COUNT(*) FROM orden_servicio_detalle) AS ordenes_detalle,
  (SELECT COUNT(*) FROM garantia)               AS garantias;
-- Los tres deben ser 0. Si alguno no lo es, NO continuar.

START TRANSACTION;

-- 2) Qué hay hoy (para comparar antes/después).
SELECT 'traspaso' t, COUNT(*) n FROM traspaso UNION ALL SELECT 'producto', COUNT(*) FROM producto
UNION ALL SELECT 'producto_imei', COUNT(*) FROM producto_imei UNION ALL SELECT 'movimiento_inventario', COUNT(*) FROM movimiento_inventario
UNION ALL SELECT 'producto_master', COUNT(*) FROM producto_master UNION ALL SELECT 'categoria', COUNT(*) FROM categoria
UNION ALL SELECT 'color', COUNT(*) FROM color UNION ALL SELECT 'proveedor', COUNT(*) FROM proveedor;

-- 3) Borrado en orden de dependencias.
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
UNION ALL SELECT 'color', COUNT(*) FROM color UNION ALL SELECT 'proveedor', COUNT(*) FROM proveedor;

-- ROLLBACK = solo probar. Cambiar por COMMIT para aplicar de verdad.
ROLLBACK;
