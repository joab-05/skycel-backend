-- Deja la base limpia de OPERACIÓN DE PRUEBA antes de que las tiendas empiecen a usar el sistema.
--
-- BORRA todo lo que son movimientos: ventas (con sus detalles, pagos, descuentos solicitados y devoluciones), cuentas por cobrar y sus
-- abonos, órdenes de servicio y sus anticipos, garantías, traspasos y faltantes, inventarios físicos, compras y sus pagos, turnos y
-- movimientos de caja, cortes (de caja y de servicios), movimientos de inventario, nómina (períodos, percepciones, deducciones),
-- préstamos a empleados, comisiones, avisos (notificaciones) y su historial de cambios (_aud).
--
-- NO TOCA: tiendas, usuarios y empleados (perfil, descansos), cajas, motivos de caja, categorías, colores, descripciones, artículos
-- (producto_master), productos y sus existencias, unidades (IMEI), secciones, proveedores, clientes, descuentos y promociones,
-- configuración del negocio, tipos de servicio ni permisos.
--
-- LAS EXISTENCIAS NO SE CORRIGEN AQUÍ: borrar las ventas, traspasos o inventarios de prueba no devuelve las piezas que movieron. Las existencias
-- iniciales se cargan aparte desde el sistema anterior (ops/deploy/cargar-existencias-iniciales.sql).
--
-- Cómo correrlo (MySQL Workbench conectado como root, ANTES un respaldo completo):
--   1) Correr el script tal cual: termina con ROLLBACK y muestra los conteos de lo que borraría (nada se borra).
--   2) Revisar los conteos. Si todo es de prueba, cambiar el ROLLBACK final por COMMIT y volver a correr.
--   3) Después correr la parte B (reinicia los contadores para que la primera venta real sea la número 1).
-- OJO: si una sentencia falla a mitad, la transacción queda ABIERTA: correr ROLLBACK; antes de reintentar.

USE skyceldb2;
SET SQL_SAFE_UPDATES = 0;

-- ── Qué hay hoy (para comparar antes/después y confirmar que todo es de prueba) ──────────────────────────────────────────
SELECT 'venta' tabla, COUNT(*) n, MIN(fecha_venta) desde, MAX(fecha_venta) hasta FROM venta
UNION ALL SELECT 'sesion_caja', COUNT(*), MIN(fecha_apertura), MAX(fecha_apertura) FROM sesion_caja
UNION ALL SELECT 'movimiento_caja', COUNT(*), MIN(fecha_mov), MAX(fecha_mov) FROM movimiento_caja
UNION ALL SELECT 'orden_servicio', COUNT(*), NULL, NULL FROM orden_servicio
UNION ALL SELECT 'garantia', COUNT(*), NULL, NULL FROM garantia
UNION ALL SELECT 'traspaso', COUNT(*), NULL, NULL FROM traspaso
UNION ALL SELECT 'inventario_auditoria', COUNT(*), NULL, NULL FROM inventario_auditoria
UNION ALL SELECT 'compra', COUNT(*), NULL, NULL FROM compra
UNION ALL SELECT 'cuenta_por_cobrar', COUNT(*), NULL, NULL FROM cuenta_por_cobrar
UNION ALL SELECT 'devolucion', COUNT(*), NULL, NULL FROM devolucion
UNION ALL SELECT 'corte_servicios', COUNT(*), NULL, NULL FROM corte_servicios
UNION ALL SELECT 'nomina_periodo', COUNT(*), NULL, NULL FROM nomina_periodo
UNION ALL SELECT 'empleado_prestamo', COUNT(*), NULL, NULL FROM empleado_prestamo
UNION ALL SELECT 'movimiento_inventario', COUNT(*), NULL, NULL FROM movimiento_inventario
UNION ALL SELECT 'notificacion', COUNT(*), NULL, NULL FROM notificacion;

-- Lo que se CONSERVA (para ver que sigue igual después)
SELECT 'tienda' tabla, COUNT(*) n FROM tienda UNION ALL SELECT 'usuario', COUNT(*) FROM usuario UNION ALL SELECT 'empleado_perfil', COUNT(*) FROM empleado_perfil
UNION ALL SELECT 'caja', COUNT(*) FROM caja UNION ALL SELECT 'cliente', COUNT(*) FROM cliente UNION ALL SELECT 'producto_master', COUNT(*) FROM producto_master
UNION ALL SELECT 'producto', COUNT(*) FROM producto UNION ALL SELECT 'producto_imei', COUNT(*) FROM producto_imei
UNION ALL SELECT 'categoria', COUNT(*) FROM categoria UNION ALL SELECT 'proveedor', COUNT(*) FROM proveedor UNION ALL SELECT 'seccion', COUNT(*) FROM seccion
UNION ALL SELECT 'empleado_descanso', COUNT(*) FROM empleado_descanso UNION ALL SELECT 'empleado_descanso_saldo', COUNT(*) FROM empleado_descanso_saldo;

START TRANSACTION;
SET FOREIGN_KEY_CHECKS = 0;

-- Nómina, préstamos y comisiones
DELETE FROM nomina_deduccion;
DELETE FROM nomina_percepcion;
DELETE FROM nomina_novedad;
DELETE FROM nomina_detalle;
DELETE FROM nomina;
DELETE FROM nomina_periodo;
DELETE FROM prestamo_descuento;
DELETE FROM empleado_prestamo;
DELETE FROM comision_periodo;

-- Descuentos pedidos, devoluciones y cuentas por cobrar
DELETE FROM venta_descuento_solicitud;
DELETE FROM solicitud_descuento;
DELETE FROM venta_devolucion;
DELETE FROM venta_payjoy;
DELETE FROM devolucion_cambio_linea;
DELETE FROM devolucion_detalle;
DELETE FROM devolucion;
DELETE FROM cliente_credito_abono;
DELETE FROM pago_cuenta;
DELETE FROM cuenta_por_cobrar;

-- Garantías y órdenes de servicio
DELETE FROM garantia_historial;
DELETE FROM garantia;
DELETE FROM garantia_aud;
DELETE FROM orden_servicio_anticipo;
DELETE FROM orden_servicio_detalle;
DELETE FROM orden_servicio_historial;
DELETE FROM orden_servicio;
DELETE FROM orden_servicio_aud;

-- Ventas
DELETE FROM venta_pago_detalle;
DELETE FROM venta_detalle;
DELETE FROM venta;
DELETE FROM venta_detalle_aud;
DELETE FROM venta_aud;

-- Caja
DELETE FROM caja_corte_resumen;
DELETE FROM caja_corte;
DELETE FROM movimiento_caja;
DELETE FROM movimiento_caja_aud;
DELETE FROM sesion_caja;

-- Compras
DELETE FROM pago_compra;
DELETE FROM compra_abono;
DELETE FROM compra_detalle;
DELETE FROM compra;

-- Corte de servicios
DELETE FROM corte_servicios_linea;
DELETE FROM corte_servicios;

-- Traspasos
DELETE FROM traspaso_faltante_mov;
DELETE FROM traspaso_detalle;
DELETE FROM traspaso;
DELETE FROM traspaso_aud;

-- Inventarios físicos
DELETE FROM inventario_auditoria_unidad;
DELETE FROM inventario_auditoria_detalle;
DELETE FROM inventario_seccion_control;
DELETE FROM inventario_auditoria;
DELETE FROM inventario_auditoria_detalle_aud;
DELETE FROM inventario_auditoria_aud;

-- Movimientos de inventario (kardex), pedidos de mayoreo y avisos
DELETE FROM movimiento_inventario;
DELETE FROM pedido_mayoreo_detalle;
DELETE FROM pedido_mayoreo;
DELETE FROM notificacion;

SET FOREIGN_KEY_CHECKS = 1;

-- ── Cómo quedó ──────────────────────────────────────────────────────────────────────────────────────────────────────────
SELECT 'venta' tabla, COUNT(*) n FROM venta UNION ALL SELECT 'venta_detalle', COUNT(*) FROM venta_detalle
UNION ALL SELECT 'sesion_caja', COUNT(*) FROM sesion_caja UNION ALL SELECT 'movimiento_caja', COUNT(*) FROM movimiento_caja
UNION ALL SELECT 'orden_servicio', COUNT(*) FROM orden_servicio UNION ALL SELECT 'garantia', COUNT(*) FROM garantia
UNION ALL SELECT 'traspaso', COUNT(*) FROM traspaso UNION ALL SELECT 'inventario_auditoria', COUNT(*) FROM inventario_auditoria
UNION ALL SELECT 'compra', COUNT(*) FROM compra UNION ALL SELECT 'cuenta_por_cobrar', COUNT(*) FROM cuenta_por_cobrar
UNION ALL SELECT 'movimiento_inventario', COUNT(*) FROM movimiento_inventario UNION ALL SELECT 'notificacion', COUNT(*) FROM notificacion
UNION ALL SELECT 'tienda', COUNT(*) FROM tienda UNION ALL SELECT 'usuario', COUNT(*) FROM usuario UNION ALL SELECT 'caja', COUNT(*) FROM caja
UNION ALL SELECT 'producto_master', COUNT(*) FROM producto_master UNION ALL SELECT 'producto', COUNT(*) FROM producto
UNION ALL SELECT 'producto_imei', COUNT(*) FROM producto_imei UNION ALL SELECT 'cliente', COUNT(*) FROM cliente;

ROLLBACK;   -- ← cambiar por COMMIT; para aplicar de verdad

-- ── PARTE B: contadores (correr DESPUÉS del COMMIT; ALTER TABLE confirma solo y no se puede deshacer) ───────────────────
ALTER TABLE venta AUTO_INCREMENT = 1;
ALTER TABLE venta_detalle AUTO_INCREMENT = 1;
ALTER TABLE venta_pago_detalle AUTO_INCREMENT = 1;
ALTER TABLE sesion_caja AUTO_INCREMENT = 1;
ALTER TABLE movimiento_caja AUTO_INCREMENT = 1;
ALTER TABLE orden_servicio AUTO_INCREMENT = 1;
ALTER TABLE garantia AUTO_INCREMENT = 1;
ALTER TABLE traspaso AUTO_INCREMENT = 1;
ALTER TABLE traspaso_detalle AUTO_INCREMENT = 1;
ALTER TABLE inventario_auditoria AUTO_INCREMENT = 1;
ALTER TABLE compra AUTO_INCREMENT = 1;
ALTER TABLE cuenta_por_cobrar AUTO_INCREMENT = 1;
ALTER TABLE devolucion AUTO_INCREMENT = 1;
ALTER TABLE corte_servicios AUTO_INCREMENT = 1;
ALTER TABLE movimiento_inventario AUTO_INCREMENT = 1;
ALTER TABLE notificacion AUTO_INCREMENT = 1;
ALTER TABLE nomina_periodo AUTO_INCREMENT = 1;
