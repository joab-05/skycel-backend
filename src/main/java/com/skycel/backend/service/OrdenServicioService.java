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
    private final ClienteService                   clienteService;
    private final NotificacionService              notificacionService;
    private final ConfiguracionNegocioRepository   negocioRepository;
    private final OrdenServicioFotoRepository      fotoRepository;

    /** Dirección pública del servidor: va en el enlace de seguimiento que se manda al cliente. */
    @org.springframework.beans.factory.annotation.Value("${skycel.publico.url:https://skycelsys.ddns.net}")
    private String urlPublica;

    /** Autorización del cliente a la cotización (orden_servicio.autorizacion_estado). */
    public static final byte AUT_PENDIENTE  = 1;
    public static final byte AUT_AUTORIZADA = 2;
    public static final byte AUT_RECHAZADA  = 3;
    private static final Set<String> MEDIOS_AUTORIZACION = Set.of("EN_PERSONA", "LLAMADA", "WHATSAPP", "OTRO");

    // ── Recibir el equipo ────────────────────────────────────────────────────

    @Transactional
    public OrdenServicioResponseDto crear(OrdenServicioRequestDto dto, String username) {
        Usuario usuario = usuarioPorUsername(username);
        if (usuario.getRol() == Rol.TECNICO) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Un técnico no recibe equipos: lo hace el personal de la tienda.");
        }
        String clave = dto.getClaveOffline() != null && !dto.getClaveOffline().isBlank() ? dto.getClaveOffline().trim() : null;
        if (clave != null) {
            // Reenvio de una recepcion ya registrada: se devuelve la misma orden
            var existente = ordenRepository.findByClaveOffline(clave);
            if (existente.isPresent()) return toDto(existente.get());
        }
        java.time.LocalDateTime fechaRecepcion = null;
        if (clave != null && dto.getFechaRecepcion() != null) {
            fechaRecepcion = dto.getFechaRecepcion();
            if (fechaRecepcion.isAfter(java.time.LocalDateTime.now().plusMinutes(10))) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La fecha de recepción no puede ser futura.");
            }
            if (fechaRecepcion.isBefore(java.time.LocalDateTime.now().minusDays(45))) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La recepción es de hace más de 45 días: regístrela manualmente.");
            }
        }
        Integer codti = dto.getCodti() != null ? dto.getCodti() : (usuario.getTienda() != null ? usuario.getTienda().getCodti() : null);
        if (codti == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Indique la tienda (codti).");
        }
        validarGestionaTienda(usuario, codti);
        Tienda tienda = tiendaRepository.findById(codti)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tienda no encontrada: " + codti));
        Cliente cliente;
        if (dto.getIdcliente() != null) {
            cliente = clienteRepository.findById(dto.getIdcliente())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cliente no encontrado: " + dto.getIdcliente()));
        } else if (clave != null && dto.getClienteTelefono() != null && !dto.getClienteTelefono().isBlank()
                && dto.getClienteNombre() != null && !dto.getClienteNombre().isBlank()) {
            // Cliente que no estaba registrado al recibir sin conexion: se busca por telefono y, si no existe, se crea
            cliente = clienteRepository.findByTelefono(dto.getClienteTelefono().trim()).orElseGet(() -> {
                com.skycel.backend.dto.cliente.ClienteRequestDto nuevo = new com.skycel.backend.dto.cliente.ClienteRequestDto();
                nuevo.setNombreCompleto(dto.getClienteNombre().trim());
                nuevo.setTelefono(dto.getClienteTelefono().trim());
                return clienteRepository.findById(clienteService.crear(nuevo).getIdcliente()).orElseThrow();
            });
        } else {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El cliente es obligatorio.");
        }
        validarFechaPromesa(dto.getFechaPromesa());
        Integer horas = validarHoras(dto.getHorasEstimadas());
        LocalDateTime promesaHora = horas != null ? (fechaRecepcion != null ? fechaRecepcion : LocalDateTime.now()).plusHours(horas) : null;
        String imei = validarImei(dto.getImei());
        if (dto.getIdTecnico() != null && !esSuperior(usuario)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "La reparación la asigna el técnico encargado: deje el técnico sin asignar al recibir el equipo.");
        }
        Usuario tecnico = dto.getIdTecnico() != null ? tecnicoValido(dto.getIdTecnico()) : null;

        OrdenServicio orden = ordenRepository.save(OrdenServicio.builder()
                .folio(siguienteFolio())
                .tienda(tienda).cliente(cliente).usuarioRecibe(usuario).tecnico(tecnico)
                .marca(dto.getMarca().trim()).modelo(dto.getModelo().trim()).imei(imei)
                .accesoriosDejados(trimOrNull(dto.getAccesoriosDejados()))
                .estadoFisico(trimOrNull(dto.getEstadoFisico()))
                .checklistRecepcion(OrdenChecklist.aJson(OrdenChecklist.validar(dto.getChecklist())))
                .codigoDesbloqueo(trimOrNull(dto.getCodigoDesbloqueo()))
                .fallaReportada(dto.getFallaReportada().trim())
                .fechaPromesa(promesaHora != null ? promesaHora.toLocalDate() : dto.getFechaPromesa())
                .horasEstimadas(horas)
                .fechaPromesaHora(promesaHora)
                .estado(RECIBIDA)
                .claveOffline(clave)
                .folioLocal(clave != null ? trimOrNull(dto.getFolioLocal()) : null)
                .fechaIngreso(fechaRecepcion)
                .build());
        registrar(orden, usuario, (orden.getFolioLocal() != null ? "Equipo recibido sin conexión (" + orden.getFolioLocal() + ")" : "Equipo recibido") + (tecnico != null ? ". Técnico asignado: " + tecnico.getNombreCompleto() : ""));
        notificacionService.notificarRol(Rol.TECNICO, "ORDEN_NUEVA",
                "Nuevo equipo en el taller: " + orden.getMarca() + " " + orden.getModelo() + " (" + orden.getFolio() + ")",
                "#/orden/" + orden.getIdorden());

        if (dto.getLineas() != null) {
            for (OrdenLineaRequestDto l : dto.getLineas()) nuevaLinea(orden, l, usuario);
        }
        if (dto.getAnticipo() != null) {
            registrarAnticipoInterno(orden, dto.getAnticipo(), usuario, fechaRecepcion);
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
        if (dto.getDiagnostico() != null) exigirTaller(usuario, orden);

        if (dto.getMarca() != null && !dto.getMarca().isBlank()) orden.setMarca(dto.getMarca().trim());
        if (dto.getModelo() != null && !dto.getModelo().isBlank()) orden.setModelo(dto.getModelo().trim());
        if (dto.getImei() != null) orden.setImei(validarImei(dto.getImei()));
        if (dto.getAccesoriosDejados() != null) orden.setAccesoriosDejados(trimOrNull(dto.getAccesoriosDejados()));
        if (dto.getEstadoFisico() != null) orden.setEstadoFisico(trimOrNull(dto.getEstadoFisico()));
        if (dto.getFallaReportada() != null && !dto.getFallaReportada().isBlank()) orden.setFallaReportada(dto.getFallaReportada().trim());
        if (dto.getDiagnostico() != null) orden.setDiagnostico(trimOrNull(dto.getDiagnostico()));
        if (dto.getChecklist() != null) orden.setChecklistRecepcion(OrdenChecklist.aJson(OrdenChecklist.validar(dto.getChecklist())));
        if (dto.getCodigoDesbloqueo() != null) orden.setCodigoDesbloqueo(trimOrNull(dto.getCodigoDesbloqueo()));
        Integer horas = validarHoras(dto.getHorasEstimadas());
        if (horas != null) {
            // Un nuevo estimado se cuenta desde ahora (por ejemplo, tras el diagnóstico)
            LocalDateTime promesaHora = LocalDateTime.now().plusHours(horas);
            orden.setHorasEstimadas(horas);
            orden.setFechaPromesaHora(promesaHora);
            orden.setFechaPromesa(promesaHora.toLocalDate());
        } else if (dto.getFechaPromesa() != null) {
            validarFechaPromesa(dto.getFechaPromesa());
            orden.setFechaPromesa(dto.getFechaPromesa());
            orden.setHorasEstimadas(null);
            orden.setFechaPromesaHora(null);
        }
        registrar(orden, usuario, dto.getDiagnostico() != null ? "Diagnóstico registrado" : "Datos de la orden actualizados");
        return toDto(ordenRepository.save(orden));
    }

    /**
     * Un técnico toma una orden que nadie ha tomado: pasa a la tienda por el equipo y lo lleva al taller. Queda asignada a él y
     * en la bitácora consta la recolección. Sirve también cuando el técnico encargado no está: cualquier técnico puede tomar
     * una orden libre y luego iniciarla.
     */
    @Transactional
    public OrdenServicioResponseDto tomar(Integer id, String username) {
        Usuario usuario = usuarioPorUsername(username);
        if (usuario.getRol() != Rol.TECNICO) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo un técnico toma órdenes; un administrador las asigna.");
        }
        if (!Boolean.TRUE.equals(usuario.getActivo())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Su usuario está inactivo.");
        }
        OrdenServicio orden = orden(id);
        exigirEstado(orden, "tomar", RECIBIDA);
        if (orden.getTecnico() != null) {
            String quien = orden.getTecnico().getIdusuario().equals(usuario.getIdusuario()) ? "usted" : orden.getTecnico().getNombreCompleto();
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La orden " + orden.getFolio() + " ya la tomó " + quien + ".");
        }
        orden.setTecnico(usuario);
        registrar(orden, usuario, "Equipo recibido en el taller por " + usuario.getNombreCompleto() + " (lo envió " + orden.getTienda().getNombre() + ")");
        return toDto(ordenRepository.save(orden));
    }

    /**
     * Las órdenes abiertas del taller, de todas las sucursales. Un administrador y el técnico encargado ven todas; un técnico,
     * las que aún nadie ha tomado y las suyas.
     */
    @Transactional(readOnly = true)
    public List<OrdenServicioResponseDto> taller(String username) {
        Usuario usuario = usuarioPorUsername(username);
        List<OrdenServicio> lista;
        if (esSuperior(usuario) || esTecnicoEncargado(usuario)) lista = ordenRepository.abiertasDeTodas();
        else if (usuario.getRol() == Rol.TECNICO) lista = ordenRepository.abiertasPorTomarODelTecnico(usuario.getIdusuario());
        else throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo un administrador o un técnico consulta el taller.");
        return lista.stream().map(this::resumen).collect(Collectors.toList());
    }

    /** Agrega una observación a la bitácora de la orden (sin cambiar su estado). */
    @Transactional
    public OrdenServicioResponseDto agregarNota(Integer id, ComentarioRequestDto dto, String username) {
        Usuario usuario = usuarioPorUsername(username);
        OrdenServicio orden = orden(id);
        exigirTaller(usuario, orden);
        exigirEstado(orden, "agregar observaciones", RECIBIDA, EN_REPARACION, LISTA);
        registrar(orden, usuario, "Observación: " + dto.getComentario().trim());
        return toDto(orden);
    }

    @Transactional
    public OrdenServicioResponseDto asignarTecnico(Integer id, AsignarTecnicoDto dto, String username) {
        Usuario usuario = usuarioPorUsername(username);
        OrdenServicio orden = orden(id);
        // Asignar la reparación es del técnico encargado o de un administrador (el taller atiende a todas las sucursales)
        if (!(esSuperior(usuario) || esTecnicoEncargado(usuario))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo el técnico encargado o un administrador asigna la reparación a un técnico.");
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
        exigirTaller(usuario, orden);
        exigirEstado(orden, "agregar renglones", RECIBIDA, EN_REPARACION);
        OrdenServicioDetalle linea = nuevaLinea(orden, dto, usuario);
        registrar(orden, usuario, "Se agregó: " + linea.getProductoMaster().getNombreBase() + " ×" + linea.getCantidad()
                + " ($" + linea.getPrecioUnitario() + ")");
        alCambiarElTotal(orden, usuario);
        return toDto(orden);
    }

    @Transactional
    public OrdenServicioResponseDto eliminarLinea(Integer id, Integer iddetalle, String username) {
        Usuario usuario = usuarioPorUsername(username);
        OrdenServicio orden = orden(id);
        exigirTaller(usuario, orden);
        exigirEstado(orden, "quitar renglones", RECIBIDA, EN_REPARACION);
        OrdenServicioDetalle linea = detalleRepository.findById(iddetalle)
                .filter(d -> d.getOrden().getIdorden().equals(id))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "El renglón " + iddetalle + " no existe en la orden " + orden.getFolio() + "."));
        detalleRepository.delete(linea);
        registrar(orden, usuario, "Se quitó: " + linea.getProductoMaster().getNombreBase());
        alCambiarElTotal(orden, usuario);
        return toDto(orden);
    }

    // ── Autorización del cliente a la cotización ─────────────────────────────

    /** El técnico terminó de cotizar: queda esperando que el cliente autorice el total. */
    @Transactional
    public OrdenServicioResponseDto solicitarAutorizacion(Integer id, String username) {
        Usuario usuario = usuarioPorUsername(username);
        OrdenServicio orden = orden(id);
        exigirTaller(usuario, orden);
        exigirEstado(orden, "pedir autorización", RECIBIDA, EN_REPARACION);
        List<OrdenServicioDetalle> lineas = detalleRepository.findByOrden_IdordenOrderByIddetalle(id);
        if (lineas.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Agregue al menos un servicio antes de cotizar al cliente.");
        }
        BigDecimal total = total(lineas);
        if (orden.getAutorizacionEstado() != null && orden.getAutorizacionEstado() == AUT_AUTORIZADA
                && total.compareTo(orden.getAutorizacionTotal()) == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El cliente ya autorizó este total ($" + total + ").");
        }
        orden.setAutorizacionEstado(AUT_PENDIENTE);
        orden.setAutorizacionTotal(total);
        orden.setAutorizacionTotalPrevio(null);
        orden.setAutorizacionMedio(null);
        orden.setAutorizacionNota(null);
        orden.setAutorizacionPor(null);
        orden.setAutorizacionFecha(null);
        registrar(orden, usuario, "Cotización de $" + total + " lista: falta que el cliente la autorice");
        ordenRepository.save(orden);
        notificarCambioOrden(orden, "Cotización de $" + total + " lista: avisa al cliente y registra su respuesta");
        return toDto(orden);
    }

    /** La sucursal registra lo que respondió el cliente (autorizó o no) y por qué medio lo dijo. */
    @Transactional
    public OrdenServicioResponseDto registrarAutorizacion(Integer id, AutorizacionRequestDto dto, String username) {
        Usuario usuario = usuarioPorUsername(username);
        OrdenServicio orden = orden(id);
        exigirGestiona(usuario, orden);
        exigirEstado(orden, "registrar la respuesta del cliente", RECIBIDA, EN_REPARACION);
        if (orden.getAutorizacionEstado() == null || orden.getAutorizacionEstado() != AUT_PENDIENTE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No hay una cotización esperando la respuesta del cliente.");
        }
        String medio = dto.getMedio().trim().toUpperCase().replace(' ', '_');
        if (!MEDIOS_AUTORIZACION.contains(medio)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Indique cómo respondió el cliente: EN_PERSONA, LLAMADA, WHATSAPP u OTRO.");
        }
        boolean autoriza = Boolean.TRUE.equals(dto.getAutorizada());
        orden.setAutorizacionEstado(autoriza ? AUT_AUTORIZADA : AUT_RECHAZADA);
        orden.setAutorizacionMedio(medio);
        orden.setAutorizacionNota(trimOrNull(dto.getNota()));
        orden.setAutorizacionPor(usuario.getNombreCompleto());
        orden.setAutorizacionFecha(LocalDateTime.now());
        if (autoriza) orden.setAutorizacionTotalPrevio(null);
        registrar(orden, usuario, (autoriza ? "El cliente AUTORIZÓ el total de $" : "El cliente NO autorizó el total de $") + orden.getAutorizacionTotal()
                + " (" + medioLegible(medio) + ")" + (orden.getAutorizacionNota() != null ? ": " + orden.getAutorizacionNota() : ""));
        ordenRepository.save(orden);
        String aviso = (autoriza ? "El cliente autorizó $" : "El cliente NO autorizó $") + orden.getAutorizacionTotal();
        notificarCambioOrden(orden, aviso);
        if (orden.getTecnico() != null) {
            notificacionService.notificarUsuario(orden.getTecnico(), "ORDEN_AVANCE",
                    "Orden " + orden.getFolio() + " (" + orden.getMarca() + " " + orden.getModelo() + "): " + aviso, "#/orden/" + orden.getIdorden());
        }
        return toDto(orden);
    }

    /** Si ya había cotización, cambiar los renglones cambia el total: lo autorizado deja de valer y hay que avisar al cliente otra vez. */
    private void alCambiarElTotal(OrdenServicio orden, Usuario usuario) {
        Byte e = orden.getAutorizacionEstado();
        if (e == null) return;
        BigDecimal total = total(detalleRepository.findByOrden_IdordenOrderByIddetalle(orden.getIdorden()));
        if (e == AUT_AUTORIZADA && total.compareTo(orden.getAutorizacionTotal()) != 0) {
            BigDecimal antes = orden.getAutorizacionTotal();
            orden.setAutorizacionTotalPrevio(antes);
            orden.setAutorizacionTotal(total);
            orden.setAutorizacionEstado(AUT_PENDIENTE);
            orden.setAutorizacionMedio(null);
            orden.setAutorizacionNota(null);
            orden.setAutorizacionPor(null);
            orden.setAutorizacionFecha(null);
            registrar(orden, usuario, "El total cambió de $" + antes + " a $" + total + ": falta que el cliente autorice el nuevo precio");
            notificarCambioOrden(orden, "Cambió el precio de $" + antes + " a $" + total + ": avisa al cliente y registra su respuesta");
        } else if (e == AUT_PENDIENTE || e == AUT_RECHAZADA) {
            // La cotización que sigue abierta (o que el cliente rechazó) se reajusta al total actual y vuelve a pedirse
            orden.setAutorizacionEstado(AUT_PENDIENTE);
            orden.setAutorizacionTotal(total);
        }
        ordenRepository.save(orden);
    }

    private static String medioLegible(String medio) {
        return switch (medio) {
            case "EN_PERSONA" -> "en persona";
            case "LLAMADA" -> "por llamada";
            case "WHATSAPP" -> "por WhatsApp";
            default -> "otro medio";
        };
    }

    /** No se termina ni se entrega con una cotización sin respuesta o rechazada. */
    private void exigirAutorizacionVigente(OrdenServicio orden) {
        Byte e = orden.getAutorizacionEstado();
        if (e == null || e == AUT_AUTORIZADA) return;
        if (e == AUT_PENDIENTE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Falta que el cliente autorice el total de $" + orden.getAutorizacionTotal()
                    + ". Avísale por WhatsApp o llámale y registra su respuesta.");
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El cliente no autorizó la reparación. Cancela la orden o vuelve a cotizar y pide su autorización.");
    }

    // ── Avisos al cliente por WhatsApp ───────────────────────────────────────

    /**
     * Arma el mensaje de WhatsApp para el cliente (ya escrito, para que el empleado solo lo envíe) y deja constancia en la bitácora de
     * qué se le avisó. Sin tipo, elige el que corresponde al momento de la orden.
     */
    @Transactional
    public Map<String, Object> avisoWhatsapp(Integer id, String tipoPedido, String username) {
        Usuario usuario = usuarioPorUsername(username);
        OrdenServicio orden = orden(id);
        exigirPuedeTrabajar(usuario, orden);
        if (orden.getEstado() == CANCELADA) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La orden está cancelada: no hay nada que avisar.");
        }
        OrdenAvisos.Tipo tipo;
        if (tipoPedido == null || tipoPedido.isBlank()) {
            tipo = tipoSugerido(orden);
        } else {
            try { tipo = OrdenAvisos.Tipo.valueOf(tipoPedido.trim().toUpperCase()); }
            catch (IllegalArgumentException ex) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tipo de aviso no válido: " + tipoPedido); }
        }
        String telefono = OrdenAvisos.telefonoWhatsApp(orden.getCliente().getTelefono());
        if (telefono == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El cliente no tiene un teléfono válido de 10 dígitos. Corrígelo en Clientes para poder avisarle.");
        }
        List<OrdenServicioDetalle> lineas = detalleRepository.findByOrden_IdordenOrderByIddetalle(id);
        BigDecimal total = total(lineas);
        BigDecimal pagado = anticipoRepository.findByOrden_IdordenOrderByIdanticipo(id).stream()
                .map(OrdenServicioAnticipo::getMonto).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal saldo = total.subtract(pagado).max(BigDecimal.ZERO);
        String negocio = negocioRepository.findAll().stream().findFirst().map(ConfiguracionNegocio::getNombreComercial).orElse("Skycel");
        String base = urlPublica != null ? urlPublica : "https://skycelsys.ddns.net";
        OrdenAvisos.Datos datos = new OrdenAvisos.Datos(negocio, orden.getTienda().getNombre(), orden.getTienda().getTelefono(),
                orden.getTienda().getUbicacion(), orden.getCliente().getNombreCompleto(), orden.getMarca() + " " + orden.getModelo(),
                orden.getFolio(), orden.getDiagnostico(), total, orden.getAutorizacionTotalPrevio(), pagado, saldo,
                orden.getFechaPromesaHora(), orden.getFechaPromesa(), orden.getFechaLista(), orden.getDiasGarantia(), orden.getFechaGarantiaHasta(),
                base + "/seguimiento/?f=" + orden.getFolio(), orden.getEsperaMotivo());
        String texto = OrdenAvisos.texto(tipo, datos);
        registrar(orden, usuario, "Aviso por WhatsApp al cliente: " + tipo.etiqueta());
        if (tipo == OrdenAvisos.Tipo.RECORDATORIO) {
            orden.setUltimoRecordatorio(LocalDateTime.now());
            ordenRepository.save(orden);
        }
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("tipo", tipo.name());
        r.put("tipoDisplay", tipo.etiqueta());
        r.put("telefono", telefono);
        r.put("texto", texto);
        r.put("url", OrdenAvisos.enlaceWhatsApp(telefono, texto));
        return r;
    }

    private OrdenAvisos.Tipo tipoSugerido(OrdenServicio o) {
        if (o.getEstado() == ENTREGADA) return OrdenAvisos.Tipo.ENTREGADA;
        if (o.getEstado() == LISTA) {
            return o.getUltimoRecordatorio() == null && diasSinRecoger(o) < DIAS_PARA_RECORDAR ? OrdenAvisos.Tipo.LISTA : OrdenAvisos.Tipo.RECORDATORIO;
        }
        if (o.getAutorizacionEstado() != null && o.getAutorizacionEstado() == AUT_PENDIENTE) {
            return o.getAutorizacionTotalPrevio() != null ? OrdenAvisos.Tipo.PRECIO_CAMBIO : OrdenAvisos.Tipo.COTIZACION;
        }
        if (o.getEsperaDesde() != null) return OrdenAvisos.Tipo.ESPERA_REFACCION;
        return OrdenAvisos.Tipo.RECIBIDA;
    }

    /** A partir de cuántos días sin recoger conviene recordárselo al cliente (el comprobante le da 30 antes de deslindar a la tienda). */
    public static final int DIAS_PARA_RECORDAR = 7;
    public static final int DIAS_PLAZO_RECOGER = 30;

    static int diasSinRecoger(OrdenServicio o) {
        if (o.getEstado() == null || o.getEstado() != LISTA || o.getFechaLista() == null) return 0;
        return (int) Math.max(0, java.time.temporal.ChronoUnit.DAYS.between(o.getFechaLista().toLocalDate(), LocalDate.now()));
    }

    // ── Equipos listos que nadie recoge ──────────────────────────────────────

    /** Los equipos listos de una tienda, los que llevan más días sin recoger primero. {@code codti} null = todas (solo administradores). */
    @Transactional(readOnly = true)
    public List<OrdenServicioResponseDto> porRecoger(Integer codti, String username) {
        Usuario usuario = usuarioPorUsername(username);
        if (codti == null) {
            if (!(esSuperior(usuario) || esTecnicoEncargado(usuario))) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Indique la tienda (codti).");
            }
        } else if (!esTecnicoEncargado(usuario)) {
            validarGestionaTienda(usuario, codti);
        }
        return ordenRepository.listasSinRecoger(codti).stream().map(this::resumen).collect(Collectors.toList());
    }

    /** El técnico (o el personal) empieza la reparación. Requiere un técnico asignado. */
    @Transactional
    public OrdenServicioResponseDto iniciar(Integer id, String username) {
        Usuario usuario = usuarioPorUsername(username);
        OrdenServicio orden = orden(id);
        exigirTaller(usuario, orden);
        exigirEstado(orden, "iniciar la reparación", RECIBIDA);
        exigirSinEspera(orden, "iniciar la reparación");
        if (orden.getTecnico() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Asigne un técnico antes de iniciar la reparación.");
        }
        orden.setEstado(EN_REPARACION);
        registrar(orden, usuario, "Reparación iniciada");
        OrdenServicioResponseDto resultado = toDto(ordenRepository.save(orden));
        notificarCambioOrden(orden, "Se inició la reparación");
        return resultado;
    }

    /** La reparación terminó: el equipo queda listo para entregar. Exige renglones y refacciones con stock. */
    @Transactional
    public OrdenServicioResponseDto marcarLista(Integer id, String username) {
        Usuario usuario = usuarioPorUsername(username);
        OrdenServicio orden = orden(id);
        exigirTaller(usuario, orden);
        exigirEstado(orden, "marcar como lista", EN_REPARACION);
        exigirSinEspera(orden, "marcarla como lista");
        exigirAutorizacionVigente(orden);

        List<OrdenServicioDetalle> lineas = detalleRepository.findByOrden_IdordenOrderByIddetalle(id);
        if (lineas.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Agregue al menos un servicio antes de marcar la orden como lista.");
        }
        for (OrdenServicioDetalle l : lineas) {
            if (l.getProductoMaster().getTipo() == TipoProducto.ACCESORIO) {
                Tienda deDonde = l.getTiendaStock() != null ? l.getTiendaStock() : orden.getTienda();
                BigDecimal hay = productoRepository.findByCodproAndTienda_Codti(l.getCodpro(), deDonde.getCodti())
                        .map(p -> p.getStock() != null ? p.getStock() : BigDecimal.ZERO).orElse(BigDecimal.ZERO);
                if (hay.compareTo(BigDecimal.valueOf(l.getCantidad())) < 0) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Falta stock de la refacción '"
                            + l.getProductoMaster().getNombreBase() + "' en " + deDonde.getNombre() + ": se necesitan " + l.getCantidad() + " y hay " + hay + ".");
                }
            }
        }
        orden.setEstado(LISTA);
        orden.setFechaLista(LocalDateTime.now());
        registrar(orden, usuario, "Equipo listo para entrega");
        OrdenServicioResponseDto resultado = toDto(ordenRepository.save(orden));
        notificarCambioOrden(orden, "El equipo ya está listo para entrega");
        return resultado;
    }

    /** Vuelve a reparación una orden ya marcada como lista (p. ej. el cliente detectó otra falla). */
    @Transactional
    public OrdenServicioResponseDto reabrir(Integer id, ComentarioRequestDto dto, String username) {
        Usuario usuario = usuarioPorUsername(username);
        OrdenServicio orden = orden(id);
        exigirTaller(usuario, orden);
        exigirEstado(orden, "reabrir", LISTA);
        orden.setEstado(EN_REPARACION);
        orden.setFechaLista(null);
        registrar(orden, usuario, "Reabierta: " + dto.getComentario().trim());
        OrdenServicioResponseDto resultado = toDto(ordenRepository.save(orden));
        notificarCambioOrden(orden, "Se reabrió la reparación: " + dto.getComentario().trim());
        return resultado;
    }

    // ── Espera de refacción ──────────────────────────────────────────────────

    /** Deja la reparación detenida mientras llega una refacción: el cliente la ve como «esperando una refacción». */
    @Transactional
    public OrdenServicioResponseDto esperarRefaccion(Integer id, ComentarioRequestDto dto, String username) {
        Usuario usuario = usuarioPorUsername(username);
        OrdenServicio orden = orden(id);
        exigirTaller(usuario, orden);
        exigirEstado(orden, "detener por una refacción", RECIBIDA, EN_REPARACION);
        if (orden.getEsperaDesde() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La orden " + orden.getFolio() + " ya está esperando una refacción: " + orden.getEsperaMotivo() + ".");
        }
        String motivo = dto.getComentario().trim();
        if (motivo.length() > 255) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Describe la refacción en menos de 255 caracteres.");
        orden.setEsperaMotivo(motivo);
        orden.setEsperaDesde(LocalDateTime.now());
        registrar(orden, usuario, "Esperando una refacción: " + motivo);
        ordenRepository.save(orden);
        notificarCambioOrden(orden, "Esperando una refacción: " + motivo);
        return toDto(orden);
    }

    /** Llegó la refacción: la reparación sigue. El tiempo detenido queda anotado para no contarlo como tardanza del taller. */
    @Transactional
    public OrdenServicioResponseDto reanudarEspera(Integer id, String username) {
        Usuario usuario = usuarioPorUsername(username);
        OrdenServicio orden = orden(id);
        exigirTaller(usuario, orden);
        if (orden.getEsperaDesde() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La orden " + orden.getFolio() + " no está esperando ninguna refacción.");
        }
        cerrarEspera(orden);
        registrar(orden, usuario, "Llegó la refacción: la reparación continúa");
        ordenRepository.save(orden);
        notificarCambioOrden(orden, "Llegó la refacción: la reparación continúa");
        return toDto(orden);
    }

    /** Suma lo que duró la espera al acumulado y deja la orden sin espera. */
    private void cerrarEspera(OrdenServicio orden) {
        if (orden.getEsperaDesde() == null) return;
        long minutos = Math.max(0, java.time.Duration.between(orden.getEsperaDesde(), LocalDateTime.now()).toMinutes());
        orden.setEsperaMinutos((int) Math.min(Integer.MAX_VALUE, (orden.getEsperaMinutos() == null ? 0 : orden.getEsperaMinutos()) + minutos));
        orden.setEsperaDesde(null);
        orden.setEsperaMotivo(null);
    }

    private void exigirSinEspera(OrdenServicio orden, String accion) {
        if (orden.getEsperaDesde() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La orden " + orden.getFolio() + " está esperando una refacción (" + orden.getEsperaMotivo()
                    + "). Cuando llegue, registra «Llegó la refacción» antes de " + accion + ".");
        }
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
        exigirAutorizacionVigente(orden);

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
        orden.setCodigoDesbloqueo(null);   // ya no hace falta y no se debe quedar guardado

        int dias = lineas.stream().map(l -> l.getProductoMaster().getDiasGarantia()).filter(Objects::nonNull)
                .max(Integer::compare).orElse(0);
        if (dias > 0) {
            orden.setDiasGarantia(dias);
            orden.setFechaGarantiaHasta(LocalDate.now().plusDays(dias));
        }
        registrar(orden, usuario, "Equipo entregado. Venta #" + creada.getIdventa() + ", saldo cobrado $" + saldo
                + (dias > 0 ? ". Garantía hasta " + orden.getFechaGarantiaHasta() : ""));
        OrdenServicioResponseDto resultado = toDto(ordenRepository.save(orden));
        notificarCambioOrden(orden, "El equipo fue entregado");
        return resultado;
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
        cerrarEspera(orden);
        orden.setCodigoDesbloqueo(null);
        orden.setMotivoCancelacion(dto.getMotivo().trim());
        registrar(orden, usuario, "Cancelada: " + dto.getMotivo().trim()
                + (devuelto.signum() > 0 ? ". Anticipo devuelto en efectivo: $" + devuelto : ""));
        OrdenServicioResponseDto resultado = toDto(ordenRepository.save(orden));
        notificarCambioOrden(orden, "Se canceló: " + dto.getMotivo().trim());
        return resultado;
    }

    private void notificarCambioOrden(OrdenServicio orden, String mensaje) {
        notificacionService.notificarTienda(orden.getTienda().getCodti(), "ORDEN_AVANCE",
                "Orden " + orden.getFolio() + " (" + orden.getMarca() + " " + orden.getModelo() + "): " + mensaje,
                "#/orden/" + orden.getIdorden());
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
        if (!esTecnicoEncargado(usuario)) validarGestionaTienda(usuario, codti);
        return ordenRepository.buscar(codti, estado, idTecnico).stream().map(this::resumen).collect(Collectors.toList());
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
                .estado(estadoDisplay(o.getEstado())).mensaje(o.getEsperaDesde() != null && o.getEstado() != LISTA && o.getEstado() != ENTREGADA && o.getEstado() != CANCELADA
                        ? "Estamos esperando una refacción para terminar la reparación de tu equipo. En cuanto llegue seguimos y te avisamos."
                        : mensajeParaElCliente(o.getEstado()))
                .fechaIngreso(o.getFechaIngreso()).fechaPromesa(o.getFechaPromesa()).fechaPromesaHora(o.getFechaPromesaHora())
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
        return ordenRepository.abiertasDelTecnico(usuario.getIdusuario()).stream().map(this::resumen).collect(Collectors.toList());
    }

    // ── Fotos del equipo ─────────────────────────────────────────────────────

    public static final int MAX_FOTOS = 8;
    public static final int MAX_BYTES_FOTO = 1_500_000;

    @Transactional(readOnly = true)
    public List<Map<String, Object>> fotos(Integer id, String username) {
        Usuario usuario = usuarioPorUsername(username);
        OrdenServicio orden = orden(id);
        exigirPuedeTrabajar(usuario, orden);
        return listaDeFotos(id);
    }

    /** Guarda una foto (JPEG o PNG ya reducida por la pantalla). Devuelve las fotos de la orden. */
    @Transactional
    public List<Map<String, Object>> agregarFoto(Integer id, byte[] contenido, String descripcion, String username) {
        Usuario usuario = usuarioPorUsername(username);
        OrdenServicio orden = orden(id);
        exigirPuedeTrabajar(usuario, orden);
        exigirEstado(orden, "agregar fotos", RECIBIDA, EN_REPARACION, LISTA);
        if (contenido == null || contenido.length == 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La foto viene vacía.");
        if (contenido.length > MAX_BYTES_FOTO) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "La foto pesa demasiado (máximo " + MAX_BYTES_FOTO / 1_000_000.0 + " MB). Reduzca su tamaño.");
        }
        String tipo = tipoDeImagen(contenido);
        if (tipo == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Solo se aceptan fotos JPEG o PNG.");
        if (fotoRepository.countByOrden_Idorden(id) >= MAX_FOTOS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Una orden admite hasta " + MAX_FOTOS + " fotos. Borre alguna antes de agregar otra.");
        }
        String desc = trimOrNull(descripcion);
        if (desc != null && desc.length() > 120) desc = desc.substring(0, 120);
        fotoRepository.save(OrdenServicioFoto.builder().orden(orden).usuario(usuario).descripcion(desc).tipo(tipo).contenido(contenido).build());
        registrar(orden, usuario, "Foto agregada" + (desc != null ? ": " + desc : ""));
        return listaDeFotos(id);
    }

    @Transactional(readOnly = true)
    public OrdenServicioFoto foto(Integer id, Integer idfoto, String username) {
        Usuario usuario = usuarioPorUsername(username);
        OrdenServicio orden = orden(id);
        exigirPuedeTrabajar(usuario, orden);
        return fotoDeLaOrden(id, idfoto);
    }

    @Transactional
    public List<Map<String, Object>> eliminarFoto(Integer id, Integer idfoto, String username) {
        Usuario usuario = usuarioPorUsername(username);
        OrdenServicio orden = orden(id);
        exigirPuedeTrabajar(usuario, orden);
        exigirEstado(orden, "borrar fotos", RECIBIDA, EN_REPARACION, LISTA);
        OrdenServicioFoto f = fotoDeLaOrden(id, idfoto);
        fotoRepository.delete(f);
        registrar(orden, usuario, "Foto eliminada" + (f.getDescripcion() != null ? ": " + f.getDescripcion() : ""));
        return listaDeFotos(id);
    }

    private OrdenServicioFoto fotoDeLaOrden(Integer id, Integer idfoto) {
        return fotoRepository.findById(idfoto).filter(f -> f.getOrden().getIdorden().equals(id))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "La foto no existe en esta orden."));
    }

    private List<Map<String, Object>> listaDeFotos(Integer id) {
        return fotoRepository.findByOrden_IdordenOrderByIdfoto(id).stream().map(f -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("idfoto", f.getIdfoto());
            m.put("descripcion", f.getDescripcion());
            m.put("tipo", f.getTipo());
            m.put("fecha", f.getFecha());
            return m;
        }).collect(Collectors.toList());
    }

    /** image/jpeg o image/png según los primeros bytes (no se confía en lo que diga el cliente); null si no es ninguna. */
    static String tipoDeImagen(byte[] b) {
        if (b.length > 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) return "image/jpeg";
        if (b.length > 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') return "image/png";
        return null;
    }

    // ── Reporte de tiempos ───────────────────────────────────────────────────

    /** Cuánto tarda el taller en un periodo (por defecto, los últimos 30 días). {@code codti} null = todas las sucursales. */
    @Transactional(readOnly = true)
    public Map<String, Object> reporte(Integer codti, LocalDate desde, LocalDate hasta, String username) {
        Usuario usuario = usuarioPorUsername(username);
        if (codti == null) {
            if (!(esSuperior(usuario) || esTecnicoEncargado(usuario))) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "El reporte de todas las sucursales es del administrador o del técnico encargado.");
            }
        } else if (!esTecnicoEncargado(usuario)) {
            validarGestionaTienda(usuario, codti);
        }
        LocalDate fin = hasta != null ? hasta : LocalDate.now();
        LocalDate ini = desde != null ? desde : fin.minusDays(29);
        if (ini.isAfter(fin)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La fecha inicial no puede ser posterior a la final.");
        if (java.time.temporal.ChronoUnit.DAYS.between(ini, fin) > 366) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El periodo del reporte no puede pasar de un año.");
        }
        List<OrdenServicio> ordenes = ordenRepository.recibidasEntre(ini.atStartOfDay(), fin.plusDays(1).atStartOfDay(), codti);
        Map<String, Object> r = OrdenReporte.calcular(ordenes, LocalDateTime.now());
        // Los equipos que esperan hoy en la tienda, sin importar cuándo se recibieron
        List<OrdenServicio> listas = ordenRepository.listasSinRecoger(codti);
        r.put("listasSinRecoger", listas.size());
        r.put("listasMasDe30Dias", listas.stream().filter(o -> diasSinRecoger(o) >= DIAS_PLAZO_RECOGER).count());
        r.put("desde", ini);
        r.put("hasta", fin);
        r.put("codti", codti);
        return r;
    }

    // ── Piezas internas ──────────────────────────────────────────────────────

    private OrdenServicioDetalle nuevaLinea(OrdenServicio orden, OrdenLineaRequestDto l, Usuario usuario) {
        String codpro = l.getCodpro().trim();
        Tienda deDonde = tiendaDeInventario(orden, usuario);
        // Un servicio (mano de obra) no ocupa inventario: si el taller no lo tiene dado de alta, vale el de la sucursal de la orden
        Producto p = productoRepository.findByCodproAndTienda_Codti(codpro, deDonde.getCodti())
                .filter(x -> Boolean.TRUE.equals(x.getActivo()))
                .or(() -> productoRepository.findByCodproAndTienda_Codti(codpro, orden.getTienda().getCodti())
                        .filter(x -> Boolean.TRUE.equals(x.getActivo()) && x.getProductoMaster().getTipo() == TipoProducto.SERVICIO))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "El producto '" + codpro + "' no existe en el inventario de " + deDonde.getNombre() + "."));
        ProductoMaster master = p.getProductoMaster();
        if (master.getTipo() == TipoProducto.CELULAR || master.getTipo() == TipoProducto.TABLET) {
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
                .tiendaStock(p.getTienda())   // de dónde se tomó: sus códigos pueden no ser los de la sucursal que cobra
                .precioUnitario(precio.setScale(2, java.math.RoundingMode.HALF_UP)).esRegalo(regalo).build());
    }

    private OrdenServicioAnticipo registrarAnticipoInterno(OrdenServicio orden, AnticipoRequestDto dto, Usuario usuario) {
        return registrarAnticipoInterno(orden, dto, usuario, null);
    }

    /** {@code fecha} solo se usa al recibir un equipo sin conexión: el anticipo conserva el momento real, no el de la sincronización. */
    private OrdenServicioAnticipo registrarAnticipoInterno(OrdenServicio orden, AnticipoRequestDto dto, Usuario usuario, LocalDateTime fecha) {
        if (dto.getMetodoPago() == null || !METODOS_ANTICIPO.contains(dto.getMetodoPago())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Método del anticipo no válido: " + dto.getMetodoPago() + " (use 1 Efectivo, 2 Tarjeta o 3 Transferencia).");
        }
        OrdenServicioAnticipo anticipo = anticipoRepository.save(OrdenServicioAnticipo.builder()
                .orden(orden).monto(dto.getMonto()).metodoPago(dto.getMetodoPago())
                .folioOperacion(trimOrNull(dto.getFolioOperacion())).usuario(usuario).fecha(fecha).build());
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
        // La refacción sale del taller; la venta es de la sucursal. Solo se indica cuando son distintas.
        boolean deOtraSucursal = l.getTiendaStock() != null && !l.getTiendaStock().getCodti().equals(l.getOrden().getTienda().getCodti());
        d.setCodtiStock(deOtraSucursal ? l.getTiendaStock().getCodti() : null);
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

    /** Horas estimadas de entrega: de 1 a 720 (30 días); vacío = sin estimado. */
    private Integer validarHoras(Integer horas) {
        if (horas == null) return null;
        if (horas < 1 || horas > 720) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El tiempo estimado de entrega debe ser de 1 a 720 horas.");
        }
        return horas;
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

    // Un vendedor no tiene tienda fija (rota día a día); un encargado sí, solo la suya.
    private boolean puedeGestionarTienda(Usuario u, Integer codti) {
        return esSuperior(u) || u.getRol() == Rol.VENDEDOR || (u.getRol() == Rol.ENCARGADO_TIENDA && mismaTienda(u, codti));
    }

    /** El técnico encargado del equipo: ve todas las órdenes de su tienda y asigna las reparaciones. */
    private boolean esTecnicoEncargado(Usuario u) {
        return u.getRol() == Rol.TECNICO && Boolean.TRUE.equals(u.getTecnicoEncargado());
    }

    /**
     * El trabajo del taller (cambiar el estado, diagnosticar, agregar servicios u observaciones): un administrador, el técnico
     * asignado o el técnico encargado (de cualquier sucursal: el taller atiende a todas). El personal de mostrador no.
     */
    private boolean puedeTrabajarTaller(Usuario u, OrdenServicio o) {
        return esSuperior(u) || esTecnicoAsignado(u, o) || esTecnicoEncargado(u);
    }

    private void exigirTaller(Usuario u, OrdenServicio o) {
        if (!puedeTrabajarTaller(u, o)) {
            if (u.getRol() == Rol.TECNICO && o.getTecnico() == null) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "La orden " + o.getFolio() + " no tiene técnico: primero tómela (recoja el equipo) para trabajarla.");
            }
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Los estados, el diagnóstico, los servicios y las observaciones de la orden " + o.getFolio()
                            + " los registran un administrador o los técnicos.");
        }
    }

    /** El taller de la orden: la sucursal del técnico asignado, o null mientras nadie la ha tomado. */
    private Tienda tallerDe(OrdenServicio o) {
        return o.getTecnico() != null ? o.getTecnico().getTienda() : null;
    }

    /**
     * De qué inventario sale lo que se agrega a la orden. Las refacciones están en el taller (el equipo viaja de la sucursal que lo
     * recibió al taller y regresa a cobrarse), así que sale de la sucursal del técnico asignado; si aún no hay técnico, de la del
     * técnico que está cotizando; y si ninguno, de la sucursal de la orden.
     */
    private Tienda tiendaDeInventario(OrdenServicio orden, Usuario usuario) {
        Tienda taller = tallerDe(orden);
        if (taller == null && usuario.getRol() == Rol.TECNICO) taller = usuario.getTienda();
        return taller != null ? taller : orden.getTienda();
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
        // un técnico también ve las órdenes libres (para decidir cuál tomar)
        boolean libreParaTecnico = u.getRol() == Rol.TECNICO && o.getTecnico() == null;
        if (!puedeGestionarTienda(u, o.getTienda().getCodti()) && !puedeTrabajarTaller(u, o) && !libreParaTecnico) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No tiene acceso a la orden " + o.getFolio() + ".");
        }
    }

    // ── Mapeo ────────────────────────────────────────────────────────────────

    /** Para los listados: igual que el detalle, pero sin el código de desbloqueo (solo se ve al abrir la orden). */
    private OrdenServicioResponseDto resumen(OrdenServicio o) {
        OrdenServicioResponseDto d = toDto(o);
        d.setCodigoDesbloqueo(null);
        return d;
    }

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
                .codtiTaller(tallerDe(o) != null ? tallerDe(o).getCodti() : null).nombreTaller(tallerDe(o) != null ? tallerDe(o).getNombre() : null)
                .marca(o.getMarca()).modelo(o.getModelo()).imei(o.getImei())
                .accesoriosDejados(o.getAccesoriosDejados()).estadoFisico(o.getEstadoFisico())
                .fallaReportada(o.getFallaReportada()).diagnostico(o.getDiagnostico())
                .estado(o.getEstado()).estadoDisplay(estadoDisplay(o.getEstado()))
                .folioLocal(o.getFolioLocal()).fechaIngreso(o.getFechaIngreso()).fechaPromesa(o.getFechaPromesa()).horasEstimadas(o.getHorasEstimadas()).fechaPromesaHora(o.getFechaPromesaHora())
                .fechaLista(o.getFechaLista()).fechaEntrega(o.getFechaEntrega())
                .diasGarantia(o.getDiasGarantia()).fechaGarantiaHasta(o.getFechaGarantiaHasta())
                .idventa(o.getVenta() != null ? o.getVenta().getIdventa() : null)
                .motivoCancelacion(o.getMotivoCancelacion())
                .total(total).totalAnticipos(pagado).saldo(saldo)
                .autorizacionEstado(o.getAutorizacionEstado()).autorizacionDisplay(autorizacionDisplay(o.getAutorizacionEstado()))
                .autorizacionTotal(o.getAutorizacionTotal()).autorizacionTotalPrevio(o.getAutorizacionTotalPrevio())
                .autorizacionMedio(o.getAutorizacionMedio()).autorizacionNota(o.getAutorizacionNota())
                .autorizacionPor(o.getAutorizacionPor()).autorizacionFecha(o.getAutorizacionFecha())
                .esperaMotivo(o.getEsperaMotivo()).esperaDesde(o.getEsperaDesde()).esperaMinutos(o.getEsperaMinutos())
                .diasListaSinRecoger(o.getEstado() == LISTA ? diasSinRecoger(o) : null).ultimoRecordatorio(o.getUltimoRecordatorio())
                .checklist(OrdenChecklist.deJson(o.getChecklistRecepcion())).codigoDesbloqueo(o.getCodigoDesbloqueo())
                .totalFotos((int) fotoRepository.countByOrden_Idorden(o.getIdorden()))
                .lineas(lineas.stream().map(l -> OrdenServicioResponseDto.OrdenLineaResponseDto.builder()
                        .iddetalle(l.getIddetalle()).codpro(l.getCodpro())
                        .nombre(l.getProductoMaster().getNombreBase()).tipoProducto(l.getProductoMaster().getTipo().name())
                        .cantidad(l.getCantidad()).precioUnitario(l.getPrecioUnitario())
                        .subtotal(l.getPrecioUnitario().multiply(BigDecimal.valueOf(l.getCantidad())))
                        .esRegalo(l.getEsRegalo())
                        .codtiStock(l.getTiendaStock() != null ? l.getTiendaStock().getCodti() : null)
                        .nombreTiendaStock(l.getTiendaStock() != null ? l.getTiendaStock().getNombre() : null).build()).collect(Collectors.toList()))
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

    private String autorizacionDisplay(Byte e) {
        if (e == null) return "Sin cotizar";
        return switch (e) {
            case AUT_PENDIENTE -> "Esperando respuesta del cliente";
            case AUT_AUTORIZADA -> "Autorizada por el cliente";
            case AUT_RECHAZADA -> "No autorizada por el cliente";
            default -> "";
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
