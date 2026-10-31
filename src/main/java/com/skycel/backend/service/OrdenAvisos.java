package com.skycel.backend.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Los mensajes de WhatsApp que se mandan al cliente durante una reparación. Los textos viven aquí para que la web y JSystem digan
 * exactamente lo mismo; el empleado los ve ya escritos y solo pulsa enviar.
 */
public final class OrdenAvisos {

    private OrdenAvisos() {}

    public enum Tipo {
        RECIBIDA("Equipo recibido"), COTIZACION("Cotización para autorizar"), PRECIO_CAMBIO("Cambio de precio"),
        LISTA("Equipo listo"), RECORDATORIO("Recordatorio de recoger"), ENTREGADA("Gracias y garantía");

        private final String etiqueta;
        Tipo(String etiqueta) { this.etiqueta = etiqueta; }
        public String etiqueta() { return etiqueta; }
    }

    /** Todo lo que hace falta para escribir un mensaje. Lo que no aplique va en null. */
    public record Datos(String negocio, String sucursal, String telefonoSucursal, String ubicacionSucursal,
                        String nombreCliente, String equipo, String folio, String diagnostico,
                        BigDecimal total, BigDecimal totalAnterior, BigDecimal anticipos, BigDecimal saldo,
                        LocalDateTime fechaPromesaHora, LocalDate fechaPromesa, LocalDateTime fechaLista,
                        Integer diasGarantia, LocalDate garantiaHasta, String enlace) {}

    private static final Locale MX = Locale.forLanguageTag("es-MX");
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", MX);
    private static final DateTimeFormatter FECHA_LARGA = DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", MX);
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("h:mm", MX);

    public static String texto(Tipo tipo, Datos d) {
        StringBuilder t = new StringBuilder();
        t.append("Hola ").append(primerNombre(d.nombreCliente())).append(", te saludamos de ").append(d.negocio());
        if (d.sucursal() != null && !d.sucursal().isBlank()) t.append(" (").append(d.sucursal()).append(")");
        t.append(". ");
        switch (tipo) {
            case RECIBIDA -> {
                t.append("Recibimos tu ").append(d.equipo()).append(" para reparación. Tu folio es *").append(d.folio()).append("*.");
                String promesa = promesa(d);
                if (promesa != null) t.append(" Te lo prometemos para ").append(promesa).append(".");
                if (positivo(d.anticipos())) t.append(" Dejaste un anticipo de ").append(dinero(d.anticipos())).append(".");
                t.append(seguimiento(d));
            }
            case COTIZACION -> {
                t.append("Ya revisamos tu ").append(d.equipo()).append(" (folio ").append(d.folio()).append(").");
                if (d.diagnostico() != null && !d.diagnostico().isBlank()) t.append(" Encontramos: ").append(d.diagnostico().trim()).append(".");
                t.append(" El costo de la reparación es de *").append(dinero(d.total())).append("*");
                if (positivo(d.anticipos()))
                    t.append(" (ya cuentas con un anticipo de ").append(dinero(d.anticipos())).append(", te quedan por pagar ").append(dinero(d.saldo())).append(")");
                t.append(". ¿Nos autorizas a continuar? Respóndenos *SÍ* o *NO*.");
            }
            case PRECIO_CAMBIO -> {
                t.append("Te avisamos que el costo de la reparación de tu ").append(d.equipo()).append(" (folio ").append(d.folio()).append(") cambió");
                if (d.totalAnterior() != null)
                    t.append(": antes eran ").append(dinero(d.totalAnterior())).append(" y ahora son *").append(dinero(d.total())).append("*");
                else t.append(": ahora son *").append(dinero(d.total())).append("*");
                if (d.diagnostico() != null && !d.diagnostico().isBlank()) t.append(". Diagnóstico: ").append(d.diagnostico().trim());
                t.append(". ¿Nos autorizas a continuar con el nuevo costo? Respóndenos *SÍ* o *NO*.");
            }
            case LISTA -> {
                t.append("¡Buenas noticias! Tu ").append(d.equipo()).append(" ya está *listo* para recoger (folio ").append(d.folio()).append(").");
                if (positivo(d.saldo())) t.append(" Queda por pagar ").append(dinero(d.saldo())).append(".");
                else if (positivo(d.total())) t.append(" Ya está todo pagado.");
                t.append(" Te esperamos en ").append(lugar(d)).append(".");
            }
            case RECORDATORIO -> {
                t.append("Te recordamos que tu ").append(d.equipo()).append(" (folio ").append(d.folio()).append(") sigue listo para recoger");
                if (d.fechaLista() != null) t.append(" desde el ").append(d.fechaLista().format(FECHA));
                t.append(". ");
                if (positivo(d.saldo())) t.append("Queda por pagar ").append(dinero(d.saldo())).append(". ");
                t.append("Te esperamos en ").append(lugar(d)).append(". Si no puedes pasar pronto, avísanos para apartártelo.");
            }
            case ENTREGADA -> {
                t.append("Gracias por confiar en nosotros: ya entregamos tu ").append(d.equipo()).append(" (folio ").append(d.folio()).append(").");
                if (d.garantiaHasta() != null) {
                    t.append(" Tu reparación tiene garantía");
                    if (d.diasGarantia() != null) t.append(" de ").append(d.diasGarantia()).append(" días");
                    t.append(", hasta el ").append(d.garantiaHasta().format(FECHA_LARGA)).append(".");
                }
                t.append(" Cualquier duda, aquí estamos.");
            }
        }
        return t.toString();
    }

    private static String seguimiento(Datos d) {
        return d.enlace() == null ? "" : " Consulta el avance cuando quieras aquí: " + d.enlace() + " (te pedirá los últimos 4 dígitos de tu teléfono).";
    }

    private static String lugar(Datos d) {
        StringBuilder s = new StringBuilder(d.sucursal() != null && !d.sucursal().isBlank() ? d.sucursal() : d.negocio());
        if (d.ubicacionSucursal() != null && !d.ubicacionSucursal().isBlank()) s.append(" (").append(d.ubicacionSucursal().trim()).append(")");
        if (d.telefonoSucursal() != null && !d.telefonoSucursal().isBlank()) s.append(", tel. ").append(d.telefonoSucursal().trim());
        return s.toString();
    }

    private static String promesa(Datos d) {
        if (d.fechaPromesaHora() != null) {
            LocalDateTime p = d.fechaPromesaHora();
            int h = p.getHour();
            String parte = h >= 19 ? "de la noche" : h >= 12 ? "de la tarde" : "de la mañana";
            return "el " + p.format(FECHA) + " a las " + p.format(HORA) + " " + parte;
        }
        return d.fechaPromesa() != null ? "el " + d.fechaPromesa().format(FECHA) : null;
    }

    private static boolean positivo(BigDecimal v) { return v != null && v.signum() > 0; }

    public static String dinero(BigDecimal v) {
        BigDecimal x = v == null ? BigDecimal.ZERO : v;
        return "$" + String.format(MX, "%,d", x.setScale(0, RoundingMode.HALF_UP).longValue());
    }

    static String primerNombre(String nombre) {
        if (nombre == null || nombre.isBlank()) return "";
        String p = nombre.trim().split("\\s+")[0];
        return p.isEmpty() ? "" : p.substring(0, 1).toUpperCase(MX) + p.substring(1).toLowerCase(MX);
    }

    /** Teléfono a 12 dígitos de México (52 + 10); null si no se puede armar uno válido. */
    public static String telefonoWhatsApp(String telefono) {
        if (telefono == null) return null;
        String d = telefono.replaceAll("\\D", "");
        if (d.startsWith("521") && d.length() == 13) d = "52" + d.substring(3);
        if (d.length() == 10) return "52" + d;
        if (d.length() == 12 && d.startsWith("52")) return d;
        return null;
    }

    public static String enlaceWhatsApp(String telefono, String texto) {
        return "https://wa.me/" + telefono + "?text=" + URLEncoder.encode(texto, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
