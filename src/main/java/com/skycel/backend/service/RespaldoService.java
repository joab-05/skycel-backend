package com.skycel.backend.service;

import com.skycel.backend.domain.enums.Rol;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.GZIPOutputStream;

/**
 * Respaldo diario de la base de datos: cada noche se saca una copia completa (mysqldump), se comprime y se guarda en la carpeta
 * principal y en las demás carpetas configuradas (por ejemplo otro disco). Se conservan las últimas copias diarias y una por mes.
 * Si algo falla, se avisa a los administradores. Se activa con {@code skycel.respaldo.activo=true}.
 */
@Slf4j
@Service
public class RespaldoService {

    private static final DateTimeFormatter SELLO = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm");
    private static final Pattern ARCHIVO = Pattern.compile("^(.+)-(\\d{8})-(\\d{4})\\.sql\\.gz$");

    private final boolean activo;
    private final List<Path> carpetas;
    private final String mysqldump;
    private final int diasConservar;
    private final int mesesConservar;
    private final String url;
    private final String usuario;
    private final String clave;
    private final NotificacionService notificacionService;

    private final ReentrantLock candado = new ReentrantLock();
    private volatile Map<String, Object> ultimo;

    @Autowired
    public RespaldoService(
            @Value("${skycel.respaldo.activo:false}") boolean activo,
            @Value("${skycel.respaldo.carpetas:./respaldos}") String carpetas,
            @Value("${skycel.respaldo.mysqldump:C:/Program Files/MySQL/MySQL Server 8.0/bin/mysqldump.exe}") String mysqldump,
            @Value("${skycel.respaldo.dias:14}") int diasConservar,
            @Value("${skycel.respaldo.meses:6}") int mesesConservar,
            @Value("${spring.datasource.url}") String url,
            @Value("${spring.datasource.username}") String usuario,
            @Value("${spring.datasource.password}") String clave,
            NotificacionService notificacionService) {
        this.activo = activo;
        this.carpetas = Arrays.stream(carpetas.split("[;,]")).map(String::trim).filter(s -> !s.isEmpty()).map(Paths::get).collect(Collectors.toList());
        this.mysqldump = mysqldump;
        this.diasConservar = Math.max(1, diasConservar);
        this.mesesConservar = Math.max(0, mesesConservar);
        this.url = url;
        this.usuario = usuario;
        this.clave = clave;
        this.notificacionService = notificacionService;
    }

    public boolean activo() { return activo; }

    // ── Cada noche ───────────────────────────────────────────────────────────

    // El servidor está encendido de 7:00 a 24:00: el respaldo va al final del día, antes de que se apague
    @Scheduled(cron = "${skycel.respaldo.cron:0 30 23 * * *}")
    public void programado() {
        if (!activo) return;
        Map<String, Object> r = ejecutar();
        if (!Boolean.TRUE.equals(r.get("ok"))) {
            log.error("El respaldo automático falló: {}", r.get("error"));
            try {
                String msg = "El respaldo automático de la base de datos falló: " + r.get("error");
                notificacionService.notificarRol(Rol.ROOT, "RESPALDO_FALLO", msg, "#/inicio");
                notificacionService.notificarRol(Rol.ADMIN, "RESPALDO_FALLO", msg, "#/inicio");
            } catch (RuntimeException e) {
                log.warn("No se pudo avisar del fallo del respaldo", e);
            }
        } else {
            log.info("Respaldo automático listo: {}", r.get("archivo"));
        }
    }

    // ── Hacer el respaldo ────────────────────────────────────────────────────

    public Map<String, Object> ejecutar() {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("fecha", LocalDateTime.now().toString());
        if (!candado.tryLock()) {
            r.put("ok", false);
            r.put("error", "Ya hay un respaldo en curso.");
            return r;
        }
        Path temporal = null;
        try {
            if (carpetas.isEmpty()) throw new IOException("No hay carpeta de respaldos configurada.");
            Path principal = carpetas.get(0);
            Files.createDirectories(principal);
            URI uri = URI.create(url.substring("jdbc:".length()));
            String baseDatos = uri.getPath().replaceFirst("^/", "");
            String host = uri.getHost() == null ? "localhost" : uri.getHost();
            int puerto = uri.getPort() > 0 ? uri.getPort() : 3306;
            String nombre = baseDatos + "-" + LocalDateTime.now().format(SELLO) + ".sql";
            temporal = principal.resolve(nombre + ".tmp");

            List<String> cmd = List.of(mysqldump, "--single-transaction", "--routines", "--hex-blob", "--default-character-set=utf8mb4",
                    "-h", host, "-P", String.valueOf(puerto), "-u", usuario, "--result-file=" + temporal.toAbsolutePath(), baseDatos);
            ProcessBuilder pb = new ProcessBuilder(cmd).redirectErrorStream(true);
            pb.environment().put("MYSQL_PWD", clave);
            Process p = pb.start();
            String salida;
            try (InputStream in = p.getInputStream()) { salida = new String(in.readAllBytes(), StandardCharsets.UTF_8).trim(); }
            if (!p.waitFor(20, TimeUnit.MINUTES)) { p.destroyForcibly(); throw new IOException("mysqldump tardó demasiado."); }
            if (p.exitValue() != 0) throw new IOException("mysqldump terminó con error: " + recortar(salida));
            if (!terminoBien(temporal)) throw new IOException("El respaldo quedó incompleto (no tiene el cierre de mysqldump).");

            Path destino = principal.resolve(nombre + ".gz");
            comprimir(temporal, destino);
            Files.deleteIfExists(temporal);
            long bytes = Files.size(destino);
            r.put("archivo", destino.toString());
            r.put("bytes", bytes);

            List<String> avisos = new ArrayList<>();
            for (Path otra : carpetas.subList(1, carpetas.size())) {
                try {
                    Files.createDirectories(otra);
                    Files.copy(destino, otra.resolve(destino.getFileName()), StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e) {
                    avisos.add("No se pudo copiar a " + otra + ": " + e.getMessage());
                }
            }
            for (Path carpeta : carpetas) {
                try { rotar(carpeta, LocalDate.now(), diasConservar, mesesConservar); }
                catch (IOException e) { avisos.add("No se pudo limpiar " + carpeta + ": " + e.getMessage()); }
            }
            if (!avisos.isEmpty()) { r.put("avisos", avisos); avisos.forEach(a -> log.warn("Respaldo: {}", a)); }
            r.put("ok", true);
        } catch (IOException | RuntimeException e) {
            r.put("ok", false);
            r.put("error", e.getMessage());
            if (temporal != null) try { Files.deleteIfExists(temporal); } catch (IOException ignorado) { /* nada */ }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            r.put("ok", false);
            r.put("error", "Se interrumpió el respaldo.");
        } finally {
            candado.unlock();
        }
        ultimo = r;
        return r;
    }

    private static String recortar(String s) { return s.length() > 300 ? s.substring(0, 300) + "…" : s; }

    /** mysqldump escribe «-- Dump completed on …» al terminar bien. */
    static boolean terminoBien(Path archivo) throws IOException {
        try (RandomAccessFile f = new RandomAccessFile(archivo.toFile(), "r")) {
            long largo = f.length();
            if (largo < 100) return false;
            int n = (int) Math.min(largo, 400);
            byte[] cola = new byte[n];
            f.seek(largo - n);
            f.readFully(cola);
            return new String(cola, StandardCharsets.UTF_8).contains("Dump completed");
        }
    }

    private static void comprimir(Path origen, Path destino) throws IOException {
        Path tmp = destino.resolveSibling(destino.getFileName() + ".tmp");
        try (InputStream in = Files.newInputStream(origen); OutputStream out = new GZIPOutputStream(Files.newOutputStream(tmp))) {
            in.transferTo(out);
        }
        Files.move(tmp, destino, StandardCopyOption.REPLACE_EXISTING);
    }

    // ── Cuáles copias se quedan ──────────────────────────────────────────────

    /** ¿Hay una copia de las últimas 26 horas? Sin respaldo automático activo no se puede exigir, y se da por cumplido. */
    public boolean hayRespaldoReciente() {
        if (!activo) return true;
        if (carpetas.isEmpty() || !Files.isDirectory(carpetas.get(0))) return false;
        try (Stream<Path> s = Files.list(carpetas.get(0))) {
            long limite = System.currentTimeMillis() - 26L * 3600 * 1000;
            return s.filter(f -> ARCHIVO.matcher(f.getFileName().toString()).matches()).anyMatch(f -> f.toFile().lastModified() >= limite);
        } catch (IOException e) {
            return false;
        }
    }

    /** Borra las copias viejas de una carpeta: se queda con las últimas {@code dias} y con la más nueva de cada uno de los últimos {@code meses} meses. */
    static void rotar(Path carpeta, LocalDate hoy, int dias, int meses) throws IOException {
        if (!Files.isDirectory(carpeta)) return;
        List<Path> archivos;
        try (Stream<Path> s = Files.list(carpeta)) { archivos = s.filter(f -> ARCHIVO.matcher(f.getFileName().toString()).matches()).collect(Collectors.toList()); }
        Set<Path> quedan = quedan(archivos, hoy, dias, meses);
        for (Path f : archivos) if (!quedan.contains(f)) Files.deleteIfExists(f);
    }

    static Set<Path> quedan(List<Path> archivos, LocalDate hoy, int dias, int meses) {
        List<Path> ordenados = archivos.stream().sorted(Comparator.comparing((Path f) -> f.getFileName().toString().replaceAll("^.+-(\\d{8}-\\d{4})\\.sql\\.gz$", "$1")).reversed())
                .collect(Collectors.toList());
        Set<Path> quedan = new HashSet<>(ordenados.stream().limit(dias).collect(Collectors.toList()));
        Map<YearMonth, Path> porMes = new LinkedHashMap<>();
        for (Path f : ordenados) {
            Matcher m = ARCHIVO.matcher(f.getFileName().toString());
            if (!m.matches()) continue;
            LocalDate fecha = LocalDate.parse(m.group(2), DateTimeFormatter.BASIC_ISO_DATE);
            porMes.putIfAbsent(YearMonth.from(fecha), f);   // el primero de la lista es el más nuevo de ese mes
        }
        YearMonth limite = YearMonth.from(hoy).minusMonths(meses);
        porMes.forEach((mes, f) -> { if (mes.isAfter(limite)) quedan.add(f); });
        return quedan;
    }

    // ── Estado ───────────────────────────────────────────────────────────────

    public Map<String, Object> estado() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("activo", activo);
        m.put("carpetas", carpetas.stream().map(Path::toString).collect(Collectors.toList()));
        m.put("conservaDias", diasConservar);
        m.put("conservaMeses", mesesConservar);
        m.put("ultimoIntento", ultimo);
        List<Map<String, Object>> copias = new ArrayList<>();
        if (!carpetas.isEmpty() && Files.isDirectory(carpetas.get(0))) {
            try (Stream<Path> s = Files.list(carpetas.get(0))) {
                s.filter(f -> ARCHIVO.matcher(f.getFileName().toString()).matches())
                        .sorted(Comparator.comparing((Path f) -> f.getFileName().toString()).reversed()).limit(30).forEach(f -> {
                            Map<String, Object> c = new LinkedHashMap<>();
                            c.put("archivo", f.getFileName().toString());
                            try { c.put("bytes", Files.size(f)); } catch (IOException e) { c.put("bytes", null); }
                            copias.add(c);
                        });
            } catch (IOException e) { /* sin lista */ }
        }
        m.put("copias", copias);
        return m;
    }
}
