package com.skycel.backend.shared.qr;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Generador de códigos QR (modo bytes, corrección de errores nivel M, versiones 1 a 10: hasta 213 bytes). Pensado para direcciones
 * cortas como el seguimiento de una orden de servicio. Sin librerías externas.
 */
public final class QrCode {

    private static final int[] ECC_POR_BLOQUE = {-1, 10, 16, 26, 18, 24, 16, 18, 22, 22, 26};
    private static final int[] BLOQUES = {-1, 1, 1, 1, 2, 2, 4, 4, 4, 5, 5};
    public static final int VERSION_MAXIMA = 10;

    private final int version;
    private final int tamano;
    private final boolean[][] modulos;
    private final boolean[][] reservado;

    /** @throws IllegalArgumentException si el texto no cabe */
    public static QrCode codificar(String texto) {
        byte[] datos = texto.getBytes(StandardCharsets.UTF_8);
        for (int v = 1; v <= VERSION_MAXIMA; v++) {
            int capacidadBits = datosCodewords(v) * 8;
            int bitsCuenta = v <= 9 ? 8 : 16;
            if (4 + bitsCuenta + datos.length * 8 <= capacidadBits) return new QrCode(v, datos);
        }
        throw new IllegalArgumentException("El texto es demasiado largo para el código QR.");
    }

    public int tamano() { return tamano; }
    public boolean modulo(int x, int y) { return x >= 0 && y >= 0 && x < tamano && y < tamano && modulos[y][x]; }

    // ── Construcción ─────────────────────────────────────────────────────────

    private QrCode(int version, byte[] datos) {
        this.version = version;
        this.tamano = version * 4 + 17;
        this.modulos = new boolean[tamano][tamano];
        this.reservado = new boolean[tamano][tamano];

        dibujarPatrones();
        byte[] todo = agregarCorreccion(armarDatos(datos));
        dibujarCodewords(todo);

        int mejor = 0, mejorPenal = Integer.MAX_VALUE;
        for (int m = 0; m < 8; m++) {
            aplicarMascara(m);
            dibujarFormato(m);
            int p = penalizacion();
            if (p < mejorPenal) { mejor = m; mejorPenal = p; }
            aplicarMascara(m);   // se deshace (XOR)
        }
        aplicarMascara(mejor);
        dibujarFormato(mejor);
    }

    private byte[] armarDatos(byte[] datos) {
        List<Boolean> bits = new ArrayList<>();
        agregarBits(bits, 0b0100, 4);
        agregarBits(bits, datos.length, version <= 9 ? 8 : 16);
        for (byte b : datos) agregarBits(bits, b & 0xFF, 8);
        int capacidad = datosCodewords(version) * 8;
        agregarBits(bits, 0, Math.min(4, capacidad - bits.size()));
        while (bits.size() % 8 != 0) bits.add(false);
        for (int pad = 0xEC; bits.size() < capacidad; pad ^= 0xEC ^ 0x11) agregarBits(bits, pad, 8);
        byte[] r = new byte[bits.size() / 8];
        for (int i = 0; i < bits.size(); i++) if (bits.get(i)) r[i / 8] |= 1 << (7 - i % 8);
        return r;
    }

    private static void agregarBits(List<Boolean> bits, int valor, int cuantos) {
        for (int i = cuantos - 1; i >= 0; i--) bits.add(((valor >>> i) & 1) != 0);
    }

    private static int bitsCrudos(int version) {
        int r = (16 * version + 128) * version + 64;
        if (version >= 2) {
            int n = version / 7 + 2;
            r -= (25 * n - 10) * n - 55;
            if (version >= 7) r -= 36;
        }
        return r;
    }

    private static int datosCodewords(int version) {
        return bitsCrudos(version) / 8 - ECC_POR_BLOQUE[version] * BLOQUES[version];
    }

    private byte[] agregarCorreccion(byte[] datos) {
        int bloques = BLOQUES[version], eccLen = ECC_POR_BLOQUE[version];
        int crudos = bitsCrudos(version) / 8;
        int cortos = bloques - crudos % bloques, largoCorto = crudos / bloques;
        byte[] divisor = divisorRs(eccLen);
        byte[][] bl = new byte[bloques][];
        for (int i = 0, k = 0; i < bloques; i++) {
            int largoDatos = largoCorto - eccLen + (i < cortos ? 0 : 1);
            byte[] dat = java.util.Arrays.copyOfRange(datos, k, k + largoDatos);
            k += largoDatos;
            byte[] ecc = residuoRs(dat, divisor);
            byte[] b = new byte[largoCorto + 1];
            System.arraycopy(dat, 0, b, 0, dat.length);
            System.arraycopy(ecc, 0, b, b.length - eccLen, eccLen);
            bl[i] = b;
        }
        byte[] r = new byte[crudos];
        for (int i = 0, k = 0; i < bl[0].length; i++) {
            for (int j = 0; j < bl.length; j++) {
                if (i != largoCorto - eccLen || j >= cortos) r[k++] = bl[j][i];
            }
        }
        return r;
    }

    // Reed-Solomon sobre GF(2^8) con polinomio 0x11D
    private static byte[] divisorRs(int grado) {
        byte[] r = new byte[grado];
        r[grado - 1] = 1;
        int raiz = 1;
        for (int i = 0; i < grado; i++) {
            for (int j = 0; j < r.length; j++) {
                r[j] = (byte) multiplicar(r[j] & 0xFF, raiz);
                if (j + 1 < r.length) r[j] ^= r[j + 1];
            }
            raiz = multiplicar(raiz, 0x02);
        }
        return r;
    }

    private static byte[] residuoRs(byte[] datos, byte[] divisor) {
        byte[] r = new byte[divisor.length];
        for (byte b : datos) {
            int factor = (b ^ r[0]) & 0xFF;
            System.arraycopy(r, 1, r, 0, r.length - 1);
            r[r.length - 1] = 0;
            for (int i = 0; i < r.length; i++) r[i] ^= multiplicar(divisor[i] & 0xFF, factor);
        }
        return r;
    }

    private static int multiplicar(int x, int y) {
        int z = 0;
        for (int i = 7; i >= 0; i--) {
            z = (z << 1) ^ ((z >>> 7) * 0x11D);
            z ^= ((y >>> i) & 1) * x;
        }
        return z;
    }

    // ── Dibujo ───────────────────────────────────────────────────────────────

    private void funcion(int x, int y, boolean oscuro) {
        modulos[y][x] = oscuro;
        reservado[y][x] = true;
    }

    private void dibujarPatrones() {
        for (int i = 0; i < tamano; i++) {
            funcion(6, i, i % 2 == 0);
            funcion(i, 6, i % 2 == 0);
        }
        buscador(3, 3);
        buscador(tamano - 4, 3);
        buscador(3, tamano - 4);
        int[] pos = posicionesAlineacion();
        int n = pos.length;
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                if ((i == 0 && j == 0) || (i == 0 && j == n - 1) || (i == n - 1 && j == 0)) continue;
                alineacion(pos[i], pos[j]);
            }
        }
        dibujarFormato(0);   // reserva el lugar
        dibujarVersion();
    }

    private void buscador(int cx, int cy) {
        for (int dy = -4; dy <= 4; dy++) {
            for (int dx = -4; dx <= 4; dx++) {
                int d = Math.max(Math.abs(dx), Math.abs(dy)), x = cx + dx, y = cy + dy;
                if (x >= 0 && x < tamano && y >= 0 && y < tamano) funcion(x, y, d != 2 && d != 4);
            }
        }
    }

    private void alineacion(int cx, int cy) {
        for (int dy = -2; dy <= 2; dy++)
            for (int dx = -2; dx <= 2; dx++) funcion(cx + dx, cy + dy, Math.max(Math.abs(dx), Math.abs(dy)) != 1);
    }

    private int[] posicionesAlineacion() {
        if (version == 1) return new int[0];
        int n = version / 7 + 2;
        int paso = (version * 4 + n * 2 + 1) / (n * 2 - 2) * 2;
        int[] r = new int[n];
        r[0] = 6;
        for (int i = n - 1, pos = tamano - 7; i >= 1; i--, pos -= paso) r[i] = pos;
        return r;
    }

    private void dibujarFormato(int mascara) {
        int datos = (0 << 3) | mascara;   // nivel M = 00
        int rem = datos;
        for (int i = 0; i < 10; i++) rem = (rem << 1) ^ ((rem >>> 9) * 0x537);
        int bits = ((datos << 10) | rem) ^ 0x5412;
        for (int i = 0; i <= 5; i++) funcion(8, i, bit(bits, i));
        funcion(8, 7, bit(bits, 6));
        funcion(8, 8, bit(bits, 7));
        funcion(7, 8, bit(bits, 8));
        for (int i = 9; i < 15; i++) funcion(14 - i, 8, bit(bits, i));
        for (int i = 0; i < 8; i++) funcion(tamano - 1 - i, 8, bit(bits, i));
        for (int i = 8; i < 15; i++) funcion(8, tamano - 15 + i, bit(bits, i));
        funcion(8, tamano - 8, true);
    }

    private void dibujarVersion() {
        if (version < 7) return;
        int rem = version;
        for (int i = 0; i < 12; i++) rem = (rem << 1) ^ ((rem >>> 11) * 0x1F25);
        int bits = (version << 12) | rem;
        for (int i = 0; i < 18; i++) {
            boolean b = bit(bits, i);
            int a = tamano - 11 + i % 3, c = i / 3;
            funcion(a, c, b);
            funcion(c, a, b);
        }
    }

    private static boolean bit(int x, int i) { return ((x >>> i) & 1) != 0; }

    private void dibujarCodewords(byte[] datos) {
        int i = 0;
        for (int derecha = tamano - 1; derecha >= 1; derecha -= 2) {
            if (derecha == 6) derecha = 5;
            for (int vert = 0; vert < tamano; vert++) {
                for (int j = 0; j < 2; j++) {
                    int x = derecha - j;
                    boolean arriba = ((derecha + 1) & 2) == 0;
                    int y = arriba ? tamano - 1 - vert : vert;
                    if (!reservado[y][x] && i < datos.length * 8) {
                        modulos[y][x] = bit(datos[i >>> 3] & 0xFF, 7 - (i & 7));
                        i++;
                    }
                }
            }
        }
    }

    private void aplicarMascara(int m) {
        for (int y = 0; y < tamano; y++) {
            for (int x = 0; x < tamano; x++) {
                boolean invertir = switch (m) {
                    case 0 -> (x + y) % 2 == 0;
                    case 1 -> y % 2 == 0;
                    case 2 -> x % 3 == 0;
                    case 3 -> (x + y) % 3 == 0;
                    case 4 -> (x / 3 + y / 2) % 2 == 0;
                    case 5 -> x * y % 2 + x * y % 3 == 0;
                    case 6 -> (x * y % 2 + x * y % 3) % 2 == 0;
                    default -> ((x + y) % 2 + x * y % 3) % 2 == 0;
                };
                if (!reservado[y][x] && invertir) modulos[y][x] = !modulos[y][x];
            }
        }
    }

    private int penalizacion() {
        int p = 0;
        // reglas 1 (rachas de 5 o más) y 3 (parecido a un buscador), por filas y por columnas
        for (int pasada = 0; pasada < 2; pasada++) {
            for (int a = 0; a < tamano; a++) {
                int racha = 1;
                for (int b = 1; b < tamano; b++) {
                    boolean igual = pasada == 0 ? modulos[a][b] == modulos[a][b - 1] : modulos[b][a] == modulos[b - 1][a];
                    if (igual) { racha++; if (racha == 5) p += 3; else if (racha > 5) p++; } else racha = 1;
                }
                for (int b = 0; b + 10 < tamano; b++) {
                    int v = 0;
                    for (int k = 0; k < 11; k++) v = (v << 1) | ((pasada == 0 ? modulos[a][b + k] : modulos[b + k][a]) ? 1 : 0);
                    if (v == 0b10111010000 || v == 0b00001011101) p += 40;
                }
            }
        }
        // regla 2: bloques de 2 x 2
        for (int y = 0; y + 1 < tamano; y++)
            for (int x = 0; x + 1 < tamano; x++)
                if (modulos[y][x] == modulos[y][x + 1] && modulos[y][x] == modulos[y + 1][x] && modulos[y][x] == modulos[y + 1][x + 1]) p += 3;
        // regla 4: proporción de módulos oscuros
        int oscuros = 0;
        for (boolean[] fila : modulos) for (boolean b : fila) if (b) oscuros++;
        int total = tamano * tamano;
        int k = (Math.abs(oscuros * 20 - total * 10) + total - 1) / total - 1;
        return p + Math.max(0, k) * 10;
    }
}
