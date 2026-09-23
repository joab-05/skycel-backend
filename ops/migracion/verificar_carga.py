#!/usr/bin/env python3
"""
Compara lo que quedó en skycel-backend contra el plan de migración, por tienda:
unidades, códigos (accesorios), IMEI/serie (equipos), costo y precio.

  set SKYCEL_PASSWORD=...
  python verificar_carga.py --url https://skycelsys.ddns.net --usuario root --plan plan.json [--tienda 8]

Termina con código 0 solo si no hay diferencias.
"""
import argparse, collections, json, os, sys, getpass
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from cargar_inventario import Api, MAPA_TIENDAS, TIPOS_EQUIPO, mensaje, aplicar_nombres, leer_credenciales


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--url', required=True); ap.add_argument('--usuario')
    ap.add_argument('--credenciales'); ap.add_argument('--password-file'); ap.add_argument('--plan', required=True); ap.add_argument('--nombres'); ap.add_argument('--tienda', type=int)
    a = ap.parse_args()
    pw = os.environ.get('SKYCEL_PASSWORD')
    if a.password_file: pw = open(a.password_file, encoding='utf-8').readline().strip()
    if a.credenciales: a.usuario, pw = leer_credenciales(a.credenciales)
    if not a.usuario: sys.exit('Indique --usuario o --credenciales.')
    if not pw: pw = getpass.getpass('Contraseña: ')
    datos = json.load(open(a.plan, encoding='utf-8'))
    if a.nombres: aplicar_nombres(datos, a.nombres)
    plan = datos['plan']
    api = Api(a.url, 0.3)
    st, j = api.llamar('POST', '/api/auth/login', {'username': a.usuario, 'password': pw})
    if st != 200: sys.exit(f'Login fallido: {mensaje(j)}')
    api.token = j['token']
    st, tiendas = api.llamar('GET', '/api/tiendas')
    cod = {t['nombre'].strip().lower(): t['codti'] for t in tiendas}
    diferencias = 0
    for viejo, nombre in MAPA_TIENDAS.items():
        if a.tienda and viejo != a.tienda: continue
        filas = [x for x in plan if x['codti'] == viejo]
        if not filas: continue
        st, prods = api.llamar('GET', f'/api/productos/tienda/{cod[nombre.lower()]}')
        if st != 200: print(f'{nombre}: no se pudo leer ({mensaje(prods)})'); diferencias += 1; continue
        # esperado
        esp_acc = {x['codigo']: x for x in filas if x['tipo_viejo'] not in TIPOS_EQUIPO}
        esp_imei = {x['codigo']: x for x in filas if x['tipo_viejo'] in TIPOS_EQUIPO}
        # obtenido
        got_acc = {p['codpro']: p for p in prods if p.get('tipo') == 'ACCESORIO'}
        got_imei = {}
        for p in prods:
            for u in (p.get('imeisDisponibles') or []):
                got_imei[u['imei']] = (p, u)
        problemas = []
        for c, x in esp_acc.items():
            p = got_acc.get(c)
            if not p: problemas.append(f'falta el código {c} ({x["nombre"]})'); continue
            if abs(float(p['stock']) - x['existencia']) > 1e-6: problemas.append(f'{c}: stock {p["stock"]} ≠ {x["existencia"]}')
            if abs(float(p['preciopro']) - x['costo']) > 0.005: problemas.append(f'{c}: costo {p["preciopro"]} ≠ {x["costo"]}')
            if abs(float(p['preciopub']) - x['publico']) > 0.005: problemas.append(f'{c}: precio {p["preciopub"]} ≠ {x["publico"]}')
            if p.get('nombreProductoMaster') != x['nombre']: problemas.append(f'{c}: nombre "{p.get("nombreProductoMaster")}" ≠ "{x["nombre"]}"')
        for c in got_acc:
            if c not in esp_acc: problemas.append(f'sobra el código {c}')
        for imei, x in esp_imei.items():
            if imei not in got_imei: problemas.append(f'falta el IMEI/serie {imei} ({x["nombre"]})')
        for imei in got_imei:
            if imei not in esp_imei: problemas.append(f'sobra el IMEI {imei}')
        unidades_esp = sum(x['existencia'] for x in filas)
        unidades_got = sum(float(p['stock']) for p in prods if p.get('tipo') in ('ACCESORIO', 'CELULAR', 'TABLET'))
        if abs(unidades_esp - unidades_got) > 1e-6: problemas.append(f'unidades totales {unidades_got:g} ≠ {unidades_esp}')
        print(f'{nombre}: {len(esp_acc)} códigos + {len(esp_imei)} equipos esperados; unidades {unidades_got:g}/{unidades_esp} '
              f'→ {"OK" if not problemas else str(len(problemas)) + " diferencias"}')
        for p in problemas[:15]: print('   -', p)
        if len(problemas) > 15: print(f'   … y {len(problemas) - 15} más')
        diferencias += len(problemas)
    print('SIN DIFERENCIAS' if not diferencias else f'{diferencias} diferencias')
    sys.exit(0 if not diferencias else 1)


if __name__ == '__main__':
    main()
