package com.skycel.backend.domain.enums;

import java.util.Set;

import static com.skycel.backend.domain.enums.Rol.ADMIN;
import static com.skycel.backend.domain.enums.Rol.ENCARGADO_TIENDA;
import static com.skycel.backend.domain.enums.Rol.TECNICO;
import static com.skycel.backend.domain.enums.Rol.VENDEDOR;

/**
 * Las acciones del sistema cuyo permiso puede cambiar el usuario root para cada rol (pantalla «Permisos» de JSystem).
 * Cada una trae los roles que la tienen al principio: exactamente lo que cada rol podía hacer antes de que esto fuera
 * configurable. ROOT siempre puede todo y no se configura.
 *
 * <p>El permiso decide <b>si se puede hacer la acción</b>. Qué tienda se ve (un encargado solo la suya) sigue dependiendo
 * del rol, igual que las reglas del negocio dentro de cada módulo.
 *
 * <p>Para agregar una acción: se añade aquí, se protege el endpoint con {@code @PreAuthorize("@permisos.tiene('CODIGO')")}
 * y se siembra sola (con sus roles iniciales) al arrancar. El {@code codigo} se guarda en la base: no se renombra.
 */
public enum Permiso {

    // ── Ventas ────────────────────────────────────────────────────────────────
    VENTA_CREAR("Vender y cobrar", "Ventas", Set.of(ADMIN, ENCARGADO_TIENDA, VENDEDOR)),
    VENTA_CANCELAR("Cancelar una venta", "Ventas", Set.of(ADMIN, ENCARGADO_TIENDA)),
    VENTA_HISTORIAL("Ver el historial de ventas de la tienda y de un cliente", "Ventas", Set.of(ADMIN, ENCARGADO_TIENDA)),
    DEVOLUCION_SOLICITAR("Pedir y consultar devoluciones", "Ventas", Set.of(ADMIN, ENCARGADO_TIENDA, VENDEDOR)),
    DEVOLUCION_APROBAR("Aprobar o rechazar devoluciones", "Ventas", Set.of(ADMIN, ENCARGADO_TIENDA)),

    // ── Caja ──────────────────────────────────────────────────────────────────
    CAJA_OPERAR("Abrir y cerrar la caja y ver los cortes", "Caja", Set.of(ADMIN, ENCARGADO_TIENDA, VENDEDOR)),
    CAJA_MOVIMIENTOS("Registrar entradas y salidas de efectivo y ver el saldo", "Caja", Set.of(ADMIN, ENCARGADO_TIENDA)),
    CAJA_CONFIG("Crear cajas y motivos de caja", "Caja", Set.of(ADMIN)),

    // ── Inventario ────────────────────────────────────────────────────────────
    INVENTARIO_AJUSTAR("Ajustar existencias de accesorios, marcar rezagados y ver los movimientos de inventario", "Inventario", Set.of(ADMIN, ENCARGADO_TIENDA)),
    INVENTARIO_EDITAR("Registrar y editar artículos, precios, códigos e IMEI", "Inventario", Set.of(ADMIN)),
    INVENTARIO_FISICO("Hacer el inventario físico (conteo)", "Inventario", Set.of(ADMIN, ENCARGADO_TIENDA)),
    TRASPASOS("Enviar, pedir y recibir traspasos", "Inventario", Set.of(ADMIN, ENCARGADO_TIENDA)),
    CATALOGOS_EDITAR("Editar categorías, colores, proveedores y demás catálogos", "Inventario", Set.of(ADMIN)),
    COSTOS_VER("Ver cuánto costó la mercancía", "Inventario", Set.of(ADMIN, ENCARGADO_TIENDA)),

    // ── Finanzas ──────────────────────────────────────────────────────────────
    CUENTAS_COBRAR("Cuentas por cobrar: crear cuentas y cobrar abonos", "Finanzas", Set.of(ADMIN, ENCARGADO_TIENDA)),
    COMPRAS("Compras a proveedores y cuentas por pagar", "Finanzas", Set.of(ADMIN, ENCARGADO_TIENDA)),
    DESCUENTOS("Definir las reglas de descuento", "Finanzas", Set.of(ADMIN)),
    REPORTES("Ver los reportes (resultados, gerencial, rezagados)", "Finanzas", Set.of(ADMIN, ENCARGADO_TIENDA)),
    REPORTES_CONSOLIDADOS("Ver la caja de todas las sucursales a la vez", "Finanzas", Set.of(ADMIN)),

    // ── Clientes ──────────────────────────────────────────────────────────────
    CLIENTE_EDITAR("Crear y editar clientes y sumarles puntos", "Clientes", Set.of(ADMIN, ENCARGADO_TIENDA)),
    CLIENTE_DESACTIVAR("Desactivar clientes", "Clientes", Set.of(ADMIN)),
    GARANTIAS("Recibir y dar seguimiento a garantías", "Clientes", Set.of(ADMIN, ENCARGADO_TIENDA, VENDEDOR)),

    // ── Taller y servicios ────────────────────────────────────────────────────
    ORDEN_RECIBIR("Recibir equipos para reparar, cobrar anticipos, entregar y cancelar órdenes", "Taller", Set.of(ADMIN, ENCARGADO_TIENDA, VENDEDOR)),
    ORDEN_CONSULTAR("Consultar y corregir los datos de las órdenes de servicio", "Taller", Set.of(ADMIN, ENCARGADO_TIENDA, VENDEDOR, TECNICO)),
    TALLER_TRABAJAR("Trabajar en el taller: asignar, iniciar, anotar, agregar renglones y marcar lista", "Taller", Set.of(ADMIN, TECNICO)),
    CORTE_SERVICIOS_CAPTURAR("Capturar el corte de servicios (recargas, pagos, pines)", "Taller", Set.of(ADMIN, ENCARGADO_TIENDA)),
    CORTE_SERVICIOS_CONFIRMAR("Confirmar y reabrir cortes de servicios y definir los servicios", "Taller", Set.of(ADMIN)),

    // ── Administración ────────────────────────────────────────────────────────
    EMPLEADOS_ADMIN("Administrar empleados y usuarios", "Administración", Set.of(ADMIN)),
    PRESTAMOS("Otorgar préstamos a empleados", "Administración", Set.of(ADMIN)),
    NOMINA("Nómina y comisiones", "Administración", Set.of(ADMIN)),
    DESCANSOS("Registrar los descansos del personal", "Administración", Set.of(ADMIN, ENCARGADO_TIENDA)),
    NEGOCIO_CONFIG("Cambiar los datos del negocio, el logo y las sucursales", "Administración", Set.of(ADMIN)),
    CONFIG_EQUIPO("Configurar la computadora (servidor, sucursal, caja e impresora)", "Administración", Set.of(ADMIN, ENCARGADO_TIENDA)),
    AUDITORIA("Ver el historial de cambios", "Administración", Set.of(ADMIN));

    private final String etiqueta;
    private final String grupo;
    private final Set<Rol> porOmision;

    Permiso(String etiqueta, String grupo, Set<Rol> porOmision) {
        this.etiqueta = etiqueta;
        this.grupo = grupo;
        this.porOmision = porOmision;
    }

    public String etiqueta() { return etiqueta; }

    public String grupo() { return grupo; }

    /** Roles que tienen este permiso la primera vez (sin contar ROOT, que siempre lo tiene). */
    public Set<Rol> porOmision() { return porOmision; }

    /** Los roles cuyo permiso se puede cambiar. ROOT siempre puede todo; los demás roles no usan estas pantallas. */
    public static final Set<Rol> ROLES_CONFIGURABLES = Set.of(ADMIN, ENCARGADO_TIENDA, VENDEDOR, TECNICO);
}
