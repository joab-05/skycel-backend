package com.skycel.backend.service;

import com.skycel.backend.domain.entity.ComisionPeriodo;
import com.skycel.backend.domain.entity.ConfigComision;
import com.skycel.backend.domain.entity.EmpleadoPerfil;
import com.skycel.backend.domain.entity.Tienda;
import com.skycel.backend.domain.entity.Usuario;
import com.skycel.backend.domain.enums.Rol;
import com.skycel.backend.dto.comision.ComisionLineaDto;
import com.skycel.backend.dto.comision.ConfigComisionDto;
import com.skycel.backend.dto.comision.VentaPayjoyLineaRow;
import com.skycel.backend.repository.ComisionPeriodoRepository;
import com.skycel.backend.repository.ConfigComisionRepository;
import com.skycel.backend.repository.DevolucionDetalleRepository;
import com.skycel.backend.repository.EmpleadoPerfilRepository;
import com.skycel.backend.repository.TiendaRepository;
import com.skycel.backend.repository.UsuarioRepository;
import com.skycel.backend.repository.VentaDetalleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Comisiones por venta: la mensual de encargado de tienda (2% de las ventas normales de su sucursal, sin PayJoy)
 * y la de PayJoy (por equipo vendido a crédito, cualquiera que lo venda, por tramo de precio). Mientras un período
 * no se marca pagado, se calcula en vivo (refleja ventas y devoluciones del momento); al pagarlo queda congelado.
 */
@Service
@RequiredArgsConstructor
public class ComisionService {

    public static final BigDecimal PAYJOY_UMBRAL_DEFECTO = new BigDecimal("4000.00");
    public static final BigDecimal PAYJOY_TASA_BAJA_DEFECTO = new BigDecimal("2.00");
    public static final BigDecimal PAYJOY_TASA_ALTA_DEFECTO = new BigDecimal("1.50");
    public static final BigDecimal TASA_ENCARGADO_DEFECTO = new BigDecimal("2.00");

    private final ConfigComisionRepository configRepository;
    private final ComisionPeriodoRepository comisionRepository;
    private final VentaDetalleRepository ventaDetalleRepository;
    private final TiendaRepository tiendaRepository;
    private final UsuarioRepository usuarioRepository;
    private final EmpleadoPerfilRepository empleadoPerfilRepository;
    private final DevolucionDetalleRepository devolucionDetalleRepository;

    // ── Configuración ────────────────────────────────────────────────────────

    @Transactional
    public ConfigComisionDto configuracion() {
        return toDto(cargarConfig());
    }

    @Transactional
    public ConfigComisionDto actualizarConfiguracion(ConfigComisionDto dto, String username) {
        ConfigComision c = cargarConfig();
        c.setTasaEncargadoMensual(dto.getTasaEncargadoMensual());
        c.setPayjoyUmbral(dto.getPayjoyUmbral());
        c.setPayjoyTasaBaja(dto.getPayjoyTasaBaja());
        c.setPayjoyTasaAlta(dto.getPayjoyTasaAlta());
        c.setActualizadoPor(username);
        return toDto(configRepository.save(c));
    }

    private ConfigComision cargarConfig() {
        return configRepository.findById(ConfigComision.ID_UNICO).orElseGet(() -> configRepository.save(ConfigComision.builder()
                .id(ConfigComision.ID_UNICO)
                .tasaEncargadoMensual(TASA_ENCARGADO_DEFECTO)
                .payjoyUmbral(PAYJOY_UMBRAL_DEFECTO)
                .payjoyTasaBaja(PAYJOY_TASA_BAJA_DEFECTO)
                .payjoyTasaAlta(PAYJOY_TASA_ALTA_DEFECTO)
                .build()));
    }

    private ConfigComisionDto toDto(ConfigComision c) {
        return ConfigComisionDto.builder()
                .tasaEncargadoMensual(c.getTasaEncargadoMensual()).payjoyUmbral(c.getPayjoyUmbral())
                .payjoyTasaBaja(c.getPayjoyTasaBaja()).payjoyTasaAlta(c.getPayjoyTasaAlta())
                .fechaActualizacion(c.getFechaActualizacion()).actualizadoPor(c.getActualizadoPor()).build();
    }

    // ── Comisión de encargado (mensual, 2% de su tienda) ────────────────────

    @Transactional
    public List<ComisionLineaDto> calcularEncargados(int anio, int mes) {
        ConfigComision config = cargarConfig();
        LocalDateTime[] rango = rangoDelMes(anio, mes);
        List<Tienda> tiendas = tiendaRepository.findAll().stream()
                .filter(t -> Boolean.TRUE.equals(t.getActivo()) && !Boolean.TRUE.equals(t.getEsAlmacen()))
                .sorted(Comparator.comparing(Tienda::getNombre))
                .toList();

        List<ComisionLineaDto> resultado = new ArrayList<>();
        for (Tienda tienda : tiendas) {
            List<Usuario> encargados = usuarioRepository.findByTienda_CodtiAndRolAndActivoTrue(tienda.getCodti(), Rol.ENCARGADO_TIENDA);
            for (Usuario encargado : encargados) {
                EmpleadoPerfil perfil = empleadoPerfilRepository.findByUsuarioIdusuario(encargado.getIdusuario()).orElse(null);
                if (perfil == null) continue;

                Optional<ComisionPeriodo> congelada = comisionRepository.findByTipoAndEmpleado_IdempleadoAndAnioAndMes(
                        ComisionPeriodo.TIPO_ENCARGADO_MENSUAL, perfil.getIdempleado(), anio, mes);
                if (congelada.isPresent() && congelada.get().getEstado() == ComisionPeriodo.ESTADO_PAGADA) {
                    resultado.add(toLineaDto(congelada.get(), encargado.getNombreCompleto(), tienda.getNombre()));
                    continue;
                }

                BigDecimal vendido = ventaDetalleRepository.baseComisionEncargado(tienda.getCodti(), rango[0], rango[1]);
                BigDecimal devuelto = ventaDetalleRepository.devueltoComisionEncargado(tienda.getCodti(), rango[0], rango[1]);
                BigDecimal base = vendido.subtract(devuelto).max(BigDecimal.ZERO);
                BigDecimal comision = base.multiply(config.getTasaEncargadoMensual())
                        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

                resultado.add(ComisionLineaDto.builder()
                        .idempleado(perfil.getIdempleado()).nombreEmpleado(encargado.getNombreCompleto())
                        .nombreTienda(tienda.getNombre()).anio(anio).mes(mes)
                        .ventaBase(base).tasaAplicada(config.getTasaEncargadoMensual()).comision(comision)
                        .pagada(false).build());
            }
        }
        return resultado;
    }

    @Transactional
    public ComisionLineaDto marcarPagadaEncargado(Integer idempleado, int anio, int mes, String username) {
        EmpleadoPerfil perfil = empleadoPerfilRepository.findById(idempleado)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Empleado no encontrado: " + idempleado));
        Usuario encargado = perfil.getUsuario();
        if (encargado.getTienda() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ese empleado no tiene sucursal asignada.");
        }
        Tienda tienda = encargado.getTienda();

        ConfigComision config = cargarConfig();
        LocalDateTime[] rango = rangoDelMes(anio, mes);
        BigDecimal vendido = ventaDetalleRepository.baseComisionEncargado(tienda.getCodti(), rango[0], rango[1]);
        BigDecimal devuelto = ventaDetalleRepository.devueltoComisionEncargado(tienda.getCodti(), rango[0], rango[1]);
        BigDecimal base = vendido.subtract(devuelto).max(BigDecimal.ZERO);
        BigDecimal comision = base.multiply(config.getTasaEncargadoMensual())
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

        ComisionPeriodo registro = marcarPagada(ComisionPeriodo.TIPO_ENCARGADO_MENSUAL, perfil, tienda, anio, mes,
                base, config.getTasaEncargadoMensual(), comision, username);
        return toLineaDto(registro, encargado.getNombreCompleto(), tienda.getNombre());
    }

    // ── Comisión PayJoy (por equipo, quien lo venda) ────────────────────────

    @Transactional
    public List<ComisionLineaDto> calcularPayjoy(int anio, int mes) {
        ConfigComision config = cargarConfig();
        LocalDateTime[] rango = rangoDelMes(anio, mes);
        List<VentaPayjoyLineaRow> lineas = ventaDetalleRepository.lineasPayjoy(rango[0], rango[1]);

        Map<Integer, List<VentaPayjoyLineaRow>> porVendedor = new LinkedHashMap<>();
        for (VentaPayjoyLineaRow l : lineas) {
            porVendedor.computeIfAbsent(l.getIdusuarioVendedor(), k -> new ArrayList<>()).add(l);
        }

        List<ComisionLineaDto> resultado = new ArrayList<>();
        for (Map.Entry<Integer, List<VentaPayjoyLineaRow>> entry : porVendedor.entrySet()) {
            EmpleadoPerfil perfil = empleadoPerfilRepository.findByUsuarioIdusuario(entry.getKey()).orElse(null);
            if (perfil == null) continue;

            Optional<ComisionPeriodo> congelada = comisionRepository.findByTipoAndEmpleado_IdempleadoAndAnioAndMes(
                    ComisionPeriodo.TIPO_VENDEDOR_PAYJOY, perfil.getIdempleado(), anio, mes);
            String nombre = entry.getValue().get(0).getNombreVendedor();
            if (congelada.isPresent() && congelada.get().getEstado() == ComisionPeriodo.ESTADO_PAGADA) {
                resultado.add(toLineaDto(congelada.get(), nombre, null));
                continue;
            }

            BigDecimal base = BigDecimal.ZERO;
            BigDecimal comision = BigDecimal.ZERO;
            for (VentaPayjoyLineaRow l : entry.getValue()) {
                if (fueDevuelta(l.getIddetalleVenta())) continue;
                base = base.add(l.getPrecioUnitarioFinal());
                comision = comision.add(comisionPorLineaPayjoy(l.getPrecioUnitarioFinal(), config));
            }
            resultado.add(ComisionLineaDto.builder()
                    .idempleado(perfil.getIdempleado()).nombreEmpleado(nombre).nombreTienda(null)
                    .anio(anio).mes(mes).ventaBase(base).tasaAplicada(null).comision(comision)
                    .pagada(false).build());
        }
        resultado.sort(Comparator.comparing(ComisionLineaDto::getNombreEmpleado));
        return resultado;
    }

    @Transactional
    public ComisionLineaDto marcarPagadaPayjoy(Integer idempleado, int anio, int mes, String username) {
        EmpleadoPerfil perfil = empleadoPerfilRepository.findById(idempleado)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Empleado no encontrado: " + idempleado));

        ConfigComision config = cargarConfig();
        LocalDateTime[] rango = rangoDelMes(anio, mes);
        List<VentaPayjoyLineaRow> lineas = ventaDetalleRepository.lineasPayjoy(rango[0], rango[1]).stream()
                .filter(l -> l.getIdusuarioVendedor().equals(perfil.getUsuario().getIdusuario()))
                .toList();

        BigDecimal base = BigDecimal.ZERO;
        BigDecimal comision = BigDecimal.ZERO;
        for (VentaPayjoyLineaRow l : lineas) {
            if (fueDevuelta(l.getIddetalleVenta())) continue;
            base = base.add(l.getPrecioUnitarioFinal());
            comision = comision.add(comisionPorLineaPayjoy(l.getPrecioUnitarioFinal(), config));
        }

        ComisionPeriodo registro = marcarPagada(ComisionPeriodo.TIPO_VENDEDOR_PAYJOY, perfil, null, anio, mes,
                base, null, comision, username);
        return toLineaDto(registro, perfil.getUsuario().getNombreCompleto(), null);
    }

    /** Redondeada siempre hacia arriba al peso entero, a favor del empleado. */
    private BigDecimal comisionPorLineaPayjoy(BigDecimal precio, ConfigComision config) {
        BigDecimal tasa = precio.compareTo(config.getPayjoyUmbral()) < 0 ? config.getPayjoyTasaBaja() : config.getPayjoyTasaAlta();
        return precio.multiply(tasa).divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP).setScale(0, RoundingMode.CEILING);
    }

    /** Un equipo PayJoy siempre se vende con cantidad = 1: cualquier devolución (en trámite o procesada) lo excluye. */
    private boolean fueDevuelta(Integer iddetalleVenta) {
        Long cantidad = devolucionDetalleRepository.cantidadEnTramiteOProcesada(iddetalleVenta);
        return cantidad != null && cantidad > 0;
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private ComisionPeriodo marcarPagada(byte tipo, EmpleadoPerfil empleado, Tienda tienda, int anio, int mes,
                                          BigDecimal base, BigDecimal tasa, BigDecimal comision, String username) {
        Usuario quien = usuarioRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario autenticado no encontrado"));
        ComisionPeriodo registro = comisionRepository.findByTipoAndEmpleado_IdempleadoAndAnioAndMes(tipo, empleado.getIdempleado(), anio, mes)
                .orElseGet(() -> ComisionPeriodo.builder().tipo(tipo).empleado(empleado).anio(anio).mes(mes).build());
        if (registro.getEstado() != null && registro.getEstado() == ComisionPeriodo.ESTADO_PAGADA) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Esa comisión ya estaba marcada como pagada.");
        }
        registro.setTienda(tienda);
        registro.setVentaBase(base);
        registro.setTasaAplicada(tasa);
        registro.setComision(comision);
        registro.setEstado(ComisionPeriodo.ESTADO_PAGADA);
        registro.setFechaPago(LocalDate.now());
        registro.setPagadaPor(quien);
        return comisionRepository.save(registro);
    }

    private ComisionLineaDto toLineaDto(ComisionPeriodo c, String nombreEmpleado, String nombreTienda) {
        return ComisionLineaDto.builder()
                .idempleado(c.getEmpleado().getIdempleado()).nombreEmpleado(nombreEmpleado)
                .nombreTienda(nombreTienda != null ? nombreTienda : (c.getTienda() != null ? c.getTienda().getNombre() : null))
                .anio(c.getAnio()).mes(c.getMes()).ventaBase(c.getVentaBase()).tasaAplicada(c.getTasaAplicada())
                .comision(c.getComision()).pagada(c.getEstado() == ComisionPeriodo.ESTADO_PAGADA)
                .fechaPago(c.getFechaPago()).pagadaPor(c.getPagadaPor() != null ? c.getPagadaPor().getNombreCompleto() : null)
                .build();
    }

    private LocalDateTime[] rangoDelMes(int anio, int mes) {
        YearMonth ym = YearMonth.of(anio, mes);
        return new LocalDateTime[]{ym.atDay(1).atStartOfDay(), ym.atEndOfMonth().atTime(23, 59, 59)};
    }
}
