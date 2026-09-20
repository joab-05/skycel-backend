package com.skycel.backend.service;

import com.skycel.backend.domain.entity.*;
import com.skycel.backend.domain.enums.Rol;
import com.skycel.backend.domain.enums.TipoProducto;
import com.skycel.backend.dto.ordenservicio.*;
import com.skycel.backend.dto.venta.VentaDetalleRequestDto;
import com.skycel.backend.dto.venta.VentaPagoDetalleRequestDto;
import com.skycel.backend.dto.venta.VentaRequestDto;
import com.skycel.backend.dto.venta.VentaResponseDto;
import com.skycel.backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Órdenes de servicio del taller.
 *
 * Ciclo: Recibida → En reparación → Lista para entrega → Entregada (o Cancelada antes de entregar).
 *  - Al recibir el equipo se puede dejar un anticipo; solo el efectivo entra a la caja.
 *  - El técnico (rol TECNICO) trabaja únicamente las órdenes que se le asignaron.
 *  - Al entregar se genera una venta con los servicios y las refacciones (que descuentan stock ahí). El anticipo
 *    ya cobrado figura en el desglose de esa venta como una línea de pago ANTICIPO, sin mover la caja otra vez.
 *  - Se calcula la garantía del trabajo con los días de garantía de los servicios.
 */
@Service
@RequiredArgsConstructor
public class OrdenServicioService {

    public static final byte RECIBIDA      = 1;
    public static final byte EN_REPARACION = 2;
    public static final byte LISTA         = 3;
    public static final byte ENTREGADA     = 4;
    public static final byte CANCELADA     = 5;

    /** Contador reservado para los folios de las órdenes (categoria_folio no tiene FK real). */
    private static final short FOLIO_ORDENES = -1;
    private static final byte METODO_EFECTIVO = 1;
    private static final byte METODO_MIXTO    = 4;
    private static final byte METODO_ANTICIPO = 6;
    private static final Set<Byte> METODOS_ANTICIPO = Set.of((byte) 1, (byte) 2, (byte) 3);
    private static final Set<Byte> METODOS_SALDO    = Set.of((byte) 1, (byte) 2, (byte) 3, (byte) 5);

    private final OrdenServicioRepository          ordenRepository;
    private final OrdenServicioDetalleRepository   detalleRepository;
    private final OrdenServicioAnticipoRepository  anticipoRepository;
    private final OrdenServicioHistorialRepository historialRepository;
    private final TiendaRepository                 tiendaRepository;
    private final ClienteRepository                clienteRepository;
    private final UsuarioRepository                usuarioRepository;
    private final ProductoRepository               productoRepository;
    private final VentaRepository                  ventaRepository;
    private final CategoriaFolioRepository         categoriaFolioRepository;
    private final MovimientoCajaService            movimientoCajaService;
    private final VentaService                     ventaService;

    // ── Recibir el equipo ────────────────────────────────────────────────────

    @Transactional
    public OrdenServicioResponseDto crear(OrdenServicioRequestDto dto, String username) {
        Usuario usuario = usuarioPorUsername(username);
        if (usuario.getRol() == Rol.TECNICO) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Un técnico no recibe equipos: lo hace el personal de la tienda.");
        }
        Integer codti = dto.getCodti() != null ? dto.getCodti() : (usuario.getTienda() != null ? usuario.getTienda().getCodti() : null);
        if (codti == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Indique la tienda (codti).");
        }
        validarGestionaTienda(usuario, codti);
        Tienda tienda = tiendaRepository.findById(codti)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tienda no encontrada: " + codti));
        Cliente cliente = clienteRepository.findById(dto.getIdcliente())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cliente no encontrado: " + dto.getIdcliente()));
        validarFechaPromesa(dto.getFechaPromesa());
        String imei = validarImei(dto.getImei());
        Usuario tecnico = dto.getIdTecnico() != null ? tecnicoValido(dto.getIdTecnico()) : null;

        OrdenServicio orden = ordenRepository.save(OrdenServicio.builder()
                .folio(siguienteFolio())
                .tienda(tienda).cliente(cliente).usuarioRecibe(usuario).tecnico(tecnico)
                .marca(dto.getMarca().trim()).modelo(dto.getModelo().trim()).imei(imei)
                .accesoriosDejados(trimOrNull(dto.getAccesoriosDejados()))
                .estadoFisico(trimOrNull(dto.getEstadoFisico()))
                .fallaReportada(dto.getFallaReportada().trim())
                .fechaPromesa(dto.getFechaPromesa())
                .estado(RECIBIDA)
                .build());
        registrar(orden, usuario, "Equipo recibido" + (tecnico != null ? ". Técnico asignado: " + tecnico.getNombreCompleto() : ""));

        if (dto.getLineas() != null) {
            for (OrdenLineaRequestDto l : dto.getLineas()) nuevaLinea(orden, l, usuario);
        }
        if (dto.getAnticipo() != null) {
            registrarAnticipoInterno(orden, dto.getAnticipo(), usuario);
        }
        return toDto(orden);
    }

    // ── Trabajo del taller ───────────────────────────────────────────────────

    @Transactional
    public OrdenServicioResponseDto actualizar(Integer id, OrdenActualizarDto dto, String username) {
        Usuario usuario = usuarioPorUsername(username);
        OrdenServicio orden = orden(id);
        exigirPuedeTrabajar(usuario, orden);
        exigirEstado(orden, "modificar", RECIBIDA, EN_REPARACION, LISTA);

        if (dto.getMarca() != null && !dto.getMarca().isBlank()) orden.setMarca(dto.getMarca().trim());
        if (dto.getModelo() != null && !dto.getModelo().isBlank()) orden.setModelo(dto.getModelo().trim());
        if (dto.getImei() != null) orden.setImei(validarImei(dto.getImei()));
        if (dto.getAccesoriosDejados() != null) orden.setAccesoriosDejados(trimOrNull(dto.getAccesoriosDejados()));
        if (dto.getEstadoFisico() != null) orden.setEstadoFisico(trimOrNull(dto.getEstadoFisico()));
        if (dto.getFallaReportada() != null && !dto.getFallaReportada().isBlank()) orden.setFallaReportada(dto.getFallaReportada().trim());
        if (dto.getDiagnostico() != null) orden.setDiagnostico(trimOrNull(dto.getDiagnostico()));
        if (dto.getFechaPromesa() != null) {
            validarFechaPromesa(dto.getFechaPromesa());
            orden.setFechaPromesa(dto.getFechaPromesa());
        }
        registrar(orden, usuario, dto.getDiagnostico() != null ? "Diagnóstico registrado" : "Datos de la orden actualizados");
        return toDto(ordenRepository.save(orden));
    }

    @Transactional
    public OrdenServicioResponseDto asignarTecnico(Integer id, AsignarTecnicoDto dto, String username) {
        Usuario usuario = usuarioPorUsername(username);
        OrdenServicio orden = orden(id);
        // Asignar técnicos es del encargado de la tienda o de un administrador (no de un vendedor)
        if (!(esSuperior(usuario) || (usuario.getRol() == Rol.ENCARGADO_TIENDA && mismaTienda(usuario, orden.getTienda().getCodti())))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo un encargado de la tienda o un administrador asigna técnicos.");
        }
        exigirEstado(orden, "asignar técnico", RECIBIDA, EN_REPARACION, LISTA);
        Usuario tecnico = tecnicoValido(dto.getIdTecnico());
        orden.setTecnico(tecnico);
        registrar(orden, usuario, "Técnico asignado: " + tecnico.getNombreCompleto());
        return toDto(ordenRepository.save(orden));
    }

    @Transactional
    public OrdenServicioResponseDto agregarLinea(Integer id, OrdenLineaRequestDto dto, String username) {
        Usuario usuario = usuarioPorUsername(username);
        OrdenServicio orden = orden(id);
        exigirPuedeTrabajar(usuario, orden);
        exigirEstado(orden, "agregar renglones", RECIBIDA, EN_REPARACION);
        OrdenServicioDetalle linea = nuevaLinea(orden, dto, usuario);
        registrar(orden, usuario, "Se agregó: " + linea.getProductoMaster().getNombreBase() + " ×" + linea.getCantidad()
                + " ($" + linea.getPrecioUnitario() + ")");
        return toDto(orden);
    }

    @Transactional
    public OrdenServicioResponseDto eliminarLinea(Integer id, Integer iddetalle, String username) {
        Usuario usuario = usuarioPorUsername(username);
        OrdenServicio orden = orden(id);
        exigirPuedeTrabajar(usuario, orden);
        exigirEstado(orden, "quitar renglones", RECIBIDA, EN_REPARACION);
        OrdenServicioDetalle linea = detalleRepository.findById(iddetalle)
                .filter(d -> d.getOrden().getIdorden().equals(id))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "El renglón " + iddetalle + " no existe en la orden " + orden.getFolio() + "."));
        detalleRepository.delete(linea);
        registrar(orden, usuario, "Se quitó: " + linea.getProductoMaster().getNombreBase());
        return toDto(orden);
    }

    /** El técnico (o el personal) empieza la reparación. Requiere un técnico asignado. */
    @Transactional
    public OrdenServicioResponseDto iniciar(Integer id, String username) {
        Usuario usuario = usuarioPorUsername(username);
        OrdenServicio orden = orden(id);
        exigirPuedeTrabajar(usuario, orden);
        exigirEstado(orden, "iniciar la reparación", RECIBIDA);
        if (orden.getTecnico() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Asigne un técnico antes de iniciar la reparación.");
        }
        orden.setEstado(EN_REPARACION);
        registrar(orden, usuario, "Reparación iniciada");
        return toDto(ordenRepository.save(orden));
    }

    /** La reparación terminó: el equipo queda listo para entregar. Exige renglones y refacciones con stock. */
    @Transactional
    public OrdenServicioResponseDto marcarLista(Integer id, String username) {
        Usuario usuario = usuarioPorUsername(username);
        OrdenServicio orden = orden(id);
        exigirPuedeTrabajar(usuario, orden);
        exigirEstado(orden, "marcar como lista", EN_REPARACION);

        List<OrdenServicioDetalle> lineas = detalleRepository.findByOrden_IdordenOrderByIddetalle(id);
        if (lineas.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Agregue al menos un servicio antes de marcar la orden como lista.");
        }
        for (OrdenServicioDetalle l : lineas) {
            if (l.getProductoMaster().getTipo() == TipoProducto.ACCESORIO) {
                BigDecimal hay = productoRepository.findByCodproAndTienda_Codti(l.getCodpro(), orden.getTienda().getCodti())
                        .map(p -> p.getStock() != null ? p.getStock() : BigDecimal.ZERO).orElse(BigDecimal.ZERO);
                if (hay.compareTo(BigDecimal.valueOf(l.getCantidad())) < 0) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Falta stock de la refacción '"
                            + l.getProductoMaster().getNombreBase() + "': se necesitan " + l.getCantidad() + " y hay " + hay + ".");
                }
            }
        }
        orden.setEstado(LISTA);
        orden.setFechaLista(LocalDateTime.now());
        registrar(orden, usuario, "Equipo listo para entrega");
        return toDto(ordenRepository.save(orden));
    }

    /** Vuelve a reparación una orden ya marcada como lista (p. ej. el cliente detectó otra falla). */
    @Transactional
    public OrdenServicioResponseDto reabrir(Integer id, ComentarioRequestDto dto, String username) {
        Usuario usuario = usuarioPorUsername(username);
        OrdenServicio orden = orden(id);
        exigirPuedeTrabajar(usuario, orden);
        exigirEstado(orden, "reabrir", LISTA);
        orden.setEstado(EN_REPARACION);
        orden.setFechaLista(null);
        registrar(orden, usuario, "Reabierta: " + dto.getComentario().trim());
        return toDto(ordenRepository.save(orden));
    }

    // ── Dinero ───────────────────────────────────────────────────────────────

    @Transactional
    public OrdenServicioResponseDto registrarAnticipo(Integer id, AnticipoRequestDto dto, String username) {
        Usuario usuario = usuarioPorUsername(username);
        OrdenServicio orden = orden(id);
        exigirGestiona(usuario, orden);
        exigirEstado(orden, "recibir anticipos", RECIBIDA, EN_REPARACION, LISTA);
        registrarAnticipoInterno(orden, dto, usuario);
        return toDto(orden);
    }

    /**
     * Entrega el equipo y cobra el saldo. Genera la venta de la orden (servicios y refacciones): las refacciones
     * descuentan stock ahí, y el anticipo ya cobrado entra en el desglose como línea ANTICIPO sin mover la caja.
     */
    @Transactional
    public OrdenServicioResponseDto entregar(Integer id, EntregaRequestDto dto, String username) {
        Usuario usuario = usuarioPorUsername(username);
        OrdenServicio orden = orden(id);
        exigirGestiona(usuario, orden);
        exigirEstado(orden, "entregar", LISTA);

        List<OrdenServicioDetalle> lineas = detalleRepository.findByOrden_IdordenOrderByIddetalle(id);
        BigDecimal total = total(lineas);
        BigDecimal pagado = anticipoRepository.findByOrden_IdordenOrderByIdanticipo(id).stream()
                .map(OrdenServicioAnticipo::getMonto).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (pagado.compareTo(total) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Los anticipos ($" + pagado + ") superan el total de la orden ($"
                    + total + "). Ajuste los renglones o cancele la orden devolviendo el anticipo.");
        }
        BigDecimal saldo = total.subtract(pagado);
        Caja caja = movimientoCajaService.cajaParaTienda(dto.getIdCaja(), orden.getTienda());

        VentaRequestDto venta = new VentaRequestDto();
        venta.setCodti(orden.getTienda().getCodti());
        venta.setIdCaja(caja.getIdCaja());
        venta.setIdcliente(orden.getCliente().getIdcliente());
        venta.setTipoComprobante((byte) 1);
        venta.setObservaciones("Orden de servicio " + orden.getFolio() + (trimOrNull(dto.getObservaciones()) != null ? ". " + dto.getObservaciones().trim() : ""));
        venta.setDetalles(lineas.stream().map(this::lineaDeVenta).collect(Collectors.toList()));

        if (pagado.signum() == 0) {
            venta.setMetodoPago(metodoDelSaldo(dto));
            if (dto.getMontoRecibido() != null) venta.setMontoAbonado(dto.getMontoRecibido());
        } else {
            venta.setMetodoPago(METODO_MIXTO);
            List<VentaPagoDetalleRequestDto> pagos = new ArrayList<>();
            pagos.add(lineaDePago(METODO_ANTICIPO, pagado, null));
            if (saldo.signum() > 0) pagos.add(lineaDePago(metodoDelSaldo(dto), saldo, trimOrNull(dto.getFolioOperacion())));
            venta.setPagos(pagos);
        }

        VentaResponseDto creada = ventaService.crearDesdeOrdenServicio(venta, usuario.getIdusuario());
        orden.setVenta(ventaRepository.findById(creada.getIdventa())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "No se encontró la venta generada.")));
        orden.setEstado(ENTREGADA);
        orden.setFechaEntrega(LocalDateTime.now());

        int dias = lineas.stream().map(l -> l.getProductoMaster().getDiasGarantia()).filter(Objects::nonNull)
                .max(Integer::compare).orElse(0);
        if (dias > 0) {
            orden.setDiasGarantia(dias);
            orden.setFechaGarantiaHasta(LocalDate.now().plusDays(dias));
        }
        registrar(orden, usuario, "Equipo entregado. Venta #" + creada.getIdventa() + ", saldo cobrado $" + saldo
                + (dias > 0 ? ". Garantía hasta " + orden.getFechaGarantiaHasta() : ""));
        return toDto(ordenRepository.save(orden));
    }

    @Transactional
    public OrdenServicioResponseDto cancelar(Integer id, CancelarOrdenRequestDto dto, String username) {
        Usuario usuario = usuarioPorUsername(username);
        OrdenServicio orden = orden(id);
        exigirGestiona(usuario, orden);
        exigirEstado(orden, "cancelar", RECIBIDA, EN_REPARACION, LISTA);

        BigDecimal devuelto = BigDecimal.ZERO;
        if (Boolean.TRUE.equals(dto.getDevolverAnticipo())) {
            for (OrdenServicioAnticipo a : anticipoRepository.findByOrden_IdordenOrderByIdanticipo(id)) {
                if (a.getMetodoPago() == METODO_EFECTIVO && a.getIdmovimientoCaja() != null) {
                    movimientoCajaService.registrarDevolucionAnticipo(orden, a, usuario);
                    devuelto = devuelto.add(a.getMonto());
                }
            }
        }
        orden.setEstado(CANCELADA);
        orden.setMotivoCancelacion(dto.getMotivo().trim());
        registrar(orden, usuario, "Cancelada: " + dto.getMotivo().trim()
                + (devuelto.signum() > 0 ? ". Anticipo devuelto en efectivo: $" + devuelto : ""));
        return toDto(ordenRepository.save(orden));
    }

    // ── Consultas ────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public OrdenServicioResponseDto obtener(Integer id, String username) {
        Usuario usuario = usuarioPorUsername(username);
        OrdenServicio orden = orden(id);
        exigirPuedeTrabajar(usuario, orden);
        return toDto(orden);
    }

    @Transactional(readOnly = true)
    public OrdenServicioResponseDto obtenerPorFolio(String folio, String username) {
        Usuario usuario = usuarioPorUsername(username);
        OrdenServicio orden = ordenRepository.findByFolio(folio.trim().toUpperCase())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Orden no encontrada: " + folio));
        exigirPuedeTrabajar(usuario, orden);
        return toDto(orden);
    }

    /** Órdenes de una tienda (para el personal). Un técnico usa misOrdenes. */
    @Transactional(readOnly = true)
    public List<OrdenServicioResponseDto> listar(Integer codti, Byte estado, Integer idTecnico, String username) {
        Usuario usuario = usuarioPorUsername(username);
        validarGestionaTienda(usuario, codti);
        return ordenRepository.buscar(codti, estado, idTecnico).stream().map(this::toDto).collect(Collectors.toList());
    }

    /**
     * Consulta del cliente, sin iniciar sesión: folio + últimos 4 dígitos del teléfono del cliente de la orden. Cualquier
     * falla (folio que no existe, teléfono que no coincide o cliente sin teléfono) da la misma respuesta 404. Solo se
     * muestran los cambios de estado de la bitácora, nunca sus anotaciones internas (anticipos, diagnóstico...).
     */
    @Transactional(readOnly = true)
    public OrdenServicioPublicaResponseDto consultaPublica(String folio, String telefono) {
        String ultimos = ultimosCuatro(telefono);
        OrdenServicio o = ordenRepository.findByFolio(folio == null ? "" : folio.trim().toUpperCase())
                .filter(x -> ultimos != null && ultimos.equals(ultimosCuatro(x.getCliente().getTelefono())))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No encontramos una orden con ese folio y teléfono."));

        List<OrdenServicioPublicaResponseDto.PasoDto> avance = new ArrayList<>();
        Byte anterior = null;
        for (OrdenServicioHistorial h : historialRepository.findByOrden_IdordenOrderByIdhistorial(o.getIdorden())) {
            if (!h.getEstado().equals(anterior)) {
                avance.add(OrdenServicioPublicaResponseDto.PasoDto.builder().estado(estadoDisplay(h.getEstado())).fecha(h.getFecha()).build());
                anterior = h.getEstado();
            }
        }
        String imei = o.getImei() == null ? null
                : (o.getImei().length() > 4 ? "•".repeat(o.getImei().length() - 4) + o.getImei().substring(o.getImei().length() - 4) : o.getImei());
        boolean entregada = o.getEstado() == ENTREGADA;
        return OrdenServicioPublicaResponseDto.builder()
                .folio(o.getFolio()).equipo(o.getMarca() + " " + o.getModelo()).imei(imei)
                .sucursal(o.getTienda().getNombre())
                .estado(estadoDisplay(o.getEstado())).mensaje(mensajeParaElCliente(o.getEstado()))
                .fechaIngreso(o.getFechaIngreso()).fechaPromesa(o.getFechaPromesa())
                .fechaLista(o.getFechaLista()).fechaEntrega(o.getFechaEntrega())
                .diasGarantia(entregada ? o.getDiasGarantia() : null).garantiaHasta(entregada ? o.getFechaGarantiaHasta() : null)
                .avance(avance)
                .build();
    }

    /** Las órdenes abiertas asignadas al técnico que consulta. */
    @Transactional(readOnly = true)
    public List<OrdenServicioResponseDto> misOrdenes(String username) {
        Usuario usuario = usuarioPorUsername(username);
        if (usuario.getRol() != Rol.TECNICO) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo un técnico tiene órdenes asignadas.");
        }
        return ordenRepository.abiertasDelTecnico(usuario.getIdusuario()).stream().map(this::toDto).collect(Collectors.toList());
    }

    // ── Piezas internas ──────────────────────────────────────────────────────

    private OrdenServicioDetalle nuevaLinea(OrdenServicio orden, OrdenLineaRequestDto l, Usuario usuario) {
        String codpro = l.getCodpro().trim();
        Producto p = productoRepository.findByCodproAndTienda_Codti(codpro, orden.getTienda().getCodti())
                .filter(x -> Boolean.TRUE.equals(x.getActivo()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "El producto '" + codpro + "' no existe en la tienda de la orden."));
        ProductoMaster master = p.getProductoMaster();
        if (master.getTipo() == TipoProducto.CELULAR) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Un equipo no se agrega a una orden de servicio: solo servicios y refacciones (accesorios).");
        }
        short cantidad = l.getCantidad() != null ? l.getCantidad() : 1;

        boolean regalo = Boolean.TRUE.equals(l.getEsRegalo());
        BigDecimal precio;
        if (regalo) {
            if (l.getPrecioUnitario() != null && l.getPrecioUnitario().signum() > 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Un artículo de regalo debe tener precio $0: '" + master.getNombreBase() + "'.");
            }
            if (usuario.getRol() == Rol.VENDEDOR || usuario.getRol() == Rol.TECNICO) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo un encargado o un administrador puede autorizar un regalo.");
            }
            precio = BigDecimal.ZERO;
        } else {
            precio = l.getPrecioUnitario() != null ? l.getPrecioUnitario() : p.getPreciopub();
            if (precio.signum() == 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Un precio de $0 solo se permite en artículos marcados como regalo (esRegalo): '" + master.getNombreBase() + "'.");
            }
        }
        return detalleRepository.save(OrdenServicioDetalle.builder()
                .orden(orden).productoMaster(master).codpro(codpro).cantidad(cantidad)
                .precioUnitario(precio.setScale(2, java.math.RoundingMode.HALF_UP)).esRegalo(regalo).build());
    }

    private OrdenServicioAnticipo registrarAnticipoInterno(OrdenServicio orden, AnticipoRequestDto dto, Usuario usuario) {
        if (dto.getMetodoPago() == null || !METODOS_ANTICIPO.contains(dto.getMetodoPago())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Método del anticipo no válido: " + dto.getMetodoPago() + " (use 1 Efectivo, 2 Tarjeta o 3 Transferencia).");
        }
        OrdenServicioAnticipo anticipo = anticipoRepository.save(OrdenServicioAnticipo.builder()
                .orden(orden).monto(dto.getMonto()).metodoPago(dto.getMetodoPago())
                .folioOperacion(trimOrNull(dto.getFolioOperacion())).usuario(usuario).build());
        if (dto.getMetodoPago() == METODO_EFECTIVO) {
            // Solo el efectivo entra a la caja; se guarda el movimiento para poder devolverlo si se cancela
            Integer idMovimiento = movimientoCajaService.registrarAnticipoServicio(dto.getIdCaja(), orden, anticipo, usuario);
            anticipo.setIdmovimientoCaja(idMovimiento);
            anticipoRepository.save(anticipo);
        }
        registrar(orden, usuario, "Anticipo de $" + dto.getMonto() + " (" + descripcionMetodo(dto.getMetodoPago()) + ")");
        return anticipo;
    }

    private Byte metodoDelSaldo(EntregaRequestDto dto) {
        if (dto.getMetodoPago() == null || !METODOS_SALDO.contains(dto.getMetodoPago())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Indique cómo se paga el saldo (metodoPago: 1 Efectivo, 2 Tarjeta, 3 Transferencia o 5 PayJoy).");
        }
        return dto.getMetodoPago();
    }

    private VentaDetalleRequestDto lineaDeVenta(OrdenServicioDetalle l) {
        VentaDetalleRequestDto d = new VentaDetalleRequestDto();
        d.setIdprodmaster(l.getProductoMaster().getIdprodmaster());
        d.setCodpro(l.getCodpro());
        d.setCantidad(l.getCantidad());
        d.setPrecioUnitarioFinal(l.getPrecioUnitario());
        d.setEsRegalo(Boolean.TRUE.equals(l.getEsRegalo()));
        return d;
    }

    private VentaPagoDetalleRequestDto lineaDePago(Byte metodo, BigDecimal monto, String folio) {
        VentaPagoDetalleRequestDto p = new VentaPagoDetalleRequestDto();
        p.setMetodoPago(metodo);
        p.setMonto(monto);
        p.setFolioOperacion(folio);
        return p;
    }

    private BigDecimal total(List<OrdenServicioDetalle> lineas) {
        return lineas.stream().map(l -> l.getPrecioUnitario().multiply(BigDecimal.valueOf(l.getCantidad())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private void registrar(OrdenServicio orden, Usuario usuario, String comentario) {
        historialRepository.save(OrdenServicioHistorial.builder()
                .orden(orden).usuario(usuario).estado(orden.getEstado()).comentario(comentario).build());
    }

    private String siguienteFolio() {
        categoriaFolioRepository.incrementar(FOLIO_ORDENES);
        return "OS-" + String.format("%06d", categoriaFolioRepository.obtenerUltimoFolioGenerado(FOLIO_ORDENES));
    }

    private OrdenServicio orden(Integer id) {
        return ordenRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Orden de servicio no encontrada: " + id));
    }

    private void exigirEstado(OrdenServicio orden, String accion, byte... permitidos) {
        for (byte e : permitidos) {
            if (orden.getEstado() == e) return;
        }
        throw new ResponseStatusException(HttpStatus.CONFLICT,
                "La orden " + orden.getFolio() + " está " + estadoDisplay(orden.getEstado()).toLowerCase() + ": no se puede " + accion + ".");
    }

    private Usuario tecnicoValido(Integer idTecnico) {
        Usuario t = usuarioRepository.findById(idTecnico)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Técnico no encontrado: " + idTecnico));
        if (t.getRol() != Rol.TECNICO) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El usuario " + t.getUsername() + " no tiene el rol TECNICO.");
        }
        if (!Boolean.TRUE.equals(t.getActivo())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El técnico " + t.getUsername() + " está inactivo.");
        }
        return t;
    }

    private void validarFechaPromesa(LocalDate fecha) {
        if (fecha != null && fecha.isBefore(LocalDate.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La fecha prometida no puede ser anterior a hoy.");
        }
    }

    private String validarImei(String imei) {
        String v = trimOrNull(imei);
        if (v != null && !v.matches("[A-Za-z0-9]{5,20}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El IMEI o número de serie debe tener entre 5 y 20 caracteres alfanuméricos.");
        }
        return v;
    }

    private String trimOrNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private Usuario usuarioPorUsername(String username) {
        return usuarioRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario autenticado no encontrado"));
    }

    // ── Permisos ─────────────────────────────────────────────────────────────
    // ROOT/ADMIN: todo. Encargado y vendedor: las órdenes de su tienda. Técnico: solo las que tiene asignadas.

    private boolean esSuperior(Usuario u) {
        return u.getRol() == Rol.ROOT || u.getRol() == Rol.ADMIN;
    }

    private boolean mismaTienda(Usuario u, Integer codti) {
        return u.getTienda() != null && u.getTienda().getCodti().equals(codti);
    }

    private boolean puedeGestionarTienda(Usuario u, Integer codti) {
        return esSuperior(u) || ((u.getRol() == Rol.ENCARGADO_TIENDA || u.getRol() == Rol.VENDEDOR) && mismaTienda(u, codti));
    }

    private boolean esTecnicoAsignado(Usuario u, OrdenServicio o) {
        return u.getRol() == Rol.TECNICO && o.getTecnico() != null && o.getTecnico().getIdusuario().equals(u.getIdusuario());
    }

    private void validarGestionaTienda(Usuario u, Integer codti) {
        if (!puedeGestionarTienda(u, codti)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No tiene permiso para operar las órdenes de la tienda " + codti + ".");
        }
    }

    /** Recibir dinero, entregar y cancelar: el personal de la tienda, no el técnico. */
    private void exigirGestiona(Usuario u, OrdenServicio o) {
        validarGestionaTienda(u, o.getTienda().getCodti());
    }

    /** Ver y trabajar la orden: el personal de la tienda o el técnico asignado. */
    private void exigirPuedeTrabajar(Usuario u, OrdenServicio o) {
        if (!puedeGestionarTienda(u, o.getTienda().getCodti()) && !esTecnicoAsignado(u, o)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No tiene acceso a la orden " + o.getFolio() + ".");
        }
    }

    // ── Mapeo ────────────────────────────────────────────────────────────────

    private OrdenServicioResponseDto toDto(OrdenServicio o) {
        List<OrdenServicioDetalle> lineas = detalleRepository.findByOrden_IdordenOrderByIddetalle(o.getIdorden());
        List<OrdenServicioAnticipo> anticipos = anticipoRepository.findByOrden_IdordenOrderByIdanticipo(o.getIdorden());
        BigDecimal total = total(lineas);
        BigDecimal pagado = anticipos.stream().map(OrdenServicioAnticipo::getMonto).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal saldo = total.subtract(pagado);
        if (saldo.signum() < 0) saldo = BigDecimal.ZERO;

        return OrdenServicioResponseDto.builder()
                .idorden(o.getIdorden()).folio(o.getFolio())
                .codti(o.getTienda().getCodti()).nombreTienda(o.getTienda().getNombre())
                .idcliente(o.getCliente().getIdcliente()).nombreCliente(o.getCliente().getNombreCompleto())
                .telefonoCliente(o.getCliente().getTelefono())
                .idusuarioRecibe(o.getUsuarioRecibe().getIdusuario()).nombreRecibe(o.getUsuarioRecibe().getNombreCompleto())
                .idTecnico(o.getTecnico() != null ? o.getTecnico().getIdusuario() : null)
                .nombreTecnico(o.getTecnico() != null ? o.getTecnico().getNombreCompleto() : null)
                .marca(o.getMarca()).modelo(o.getModelo()).imei(o.getImei())
                .accesoriosDejados(o.getAccesoriosDejados()).estadoFisico(o.getEstadoFisico())
                .fallaReportada(o.getFallaReportada()).diagnostico(o.getDiagnostico())
                .estado(o.getEstado()).estadoDisplay(estadoDisplay(o.getEstado()))
                .fechaIngreso(o.getFechaIngreso()).fechaPromesa(o.getFechaPromesa())
                .fechaLista(o.getFechaLista()).fechaEntrega(o.getFechaEntrega())
                .diasGarantia(o.getDiasGarantia()).fechaGarantiaHasta(o.getFechaGarantiaHasta())
                .idventa(o.getVenta() != null ? o.getVenta().getIdventa() : null)
                .motivoCancelacion(o.getMotivoCancelacion())
                .total(total).totalAnticipos(pagado).saldo(saldo)
                .lineas(lineas.stream().map(l -> OrdenServicioResponseDto.OrdenLineaResponseDto.builder()
                        .iddetalle(l.getIddetalle()).codpro(l.getCodpro())
                        .nombre(l.getProductoMaster().getNombreBase()).tipoProducto(l.getProductoMaster().getTipo().name())
                        .cantidad(l.getCantidad()).precioUnitario(l.getPrecioUnitario())
                        .subtotal(l.getPrecioUnitario().multiply(BigDecimal.valueOf(l.getCantidad())))
                        .esRegalo(l.getEsRegalo()).build()).collect(Collectors.toList()))
                .anticipos(anticipos.stream().map(a -> OrdenServicioResponseDto.AnticipoResponseDto.builder()
                        .idanticipo(a.getIdanticipo()).monto(a.getMonto()).metodoPago(a.getMetodoPago())
                        .descripcionMetodoPago(descripcionMetodo(a.getMetodoPago())).folioOperacion(a.getFolioOperacion())
                        .nombreUsuario(a.getUsuario().getNombreCompleto()).fecha(a.getFecha())
                        .idmovimientoCaja(a.getIdmovimientoCaja()).build()).collect(Collectors.toList()))
                .historial(historialRepository.findByOrden_IdordenOrderByIdhistorial(o.getIdorden()).stream()
                        .map(h -> OrdenServicioResponseDto.HistorialResponseDto.builder()
                                .estado(h.getEstado()).estadoDisplay(estadoDisplay(h.getEstado()))
                                .comentario(h.getComentario())
                                .nombreUsuario(h.getUsuario() != null ? h.getUsuario().getNombreCompleto() : null)
                                .fecha(h.getFecha()).build()).collect(Collectors.toList()))
                .build();
    }

    private String estadoDisplay(Byte estado) {
        return switch (estado) {
            case RECIBIDA      -> "Recibida";
            case EN_REPARACION -> "En reparación";
            case LISTA         -> "Lista para entrega";
            case ENTREGADA     -> "Entregada";
            case CANCELADA     -> "Cancelada";
            default            -> "Desconocido";
        };
    }

    private String mensajeParaElCliente(Byte estado) {
        return switch (estado) {
            case RECIBIDA      -> "Recibimos tu equipo y lo revisaremos pronto.";
            case EN_REPARACION -> "Tu equipo está en reparación.";
            case LISTA         -> "Tu equipo está listo. Pasa a recogerlo a la sucursal.";
            case ENTREGADA     -> "Tu equipo ya fue entregado.";
            case CANCELADA     -> "Tu orden fue cancelada. Pasa a la sucursal si tienes dudas.";
            default            -> "";
        };
    }

    /** Los últimos 4 dígitos de un teléfono, o null si tiene menos de 4. */
    private String ultimosCuatro(String telefono) {
        String digitos = telefono == null ? "" : telefono.replaceAll("\\D", "");
        return digitos.length() < 4 ? null : digitos.substring(digitos.length() - 4);
    }

    private String descripcionMetodo(Byte metodo) {
        return switch (metodo) {
            case 2 -> "Tarjeta";
            case 3 -> "Transferencia";
            case 5 -> "PayJoy";
            case 6 -> "Anticipo";
            default -> "Efectivo";
        };
    }
}
