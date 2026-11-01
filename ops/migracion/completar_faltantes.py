#!/usr/bin/env python3
"""
Da de alta en el sistema nuevo lo que el sistema anterior tiene con existencia y el nuevo no (corte del 2026-11-01).

Se usa DESPUÉS de comparar_existencias.sql e igualar-existencias.sql: lo que queda en «FALTA EN NUEVO» son artículos
que no existen en ninguna sucursal (llegaron al sistema anterior después de la carga inicial). Esos se crean por la API
(así pasan por las validaciones, la auditoría y el historial de existencias).

  * Accesorios: POST /api/productos con el código viejo como codpro (igual que cargar_inventario.py).
  * Equipos (celular, tablet, módem): POST /api/equipos por IMEI. Si el artículo (modelo + color) ya existe se usa; si no,
    se crea con el árbol de categorías (Equipos > Celular > marca).

Entradas: --plan (plan_migracion.json de preparar_plan.py con SOLO las filas faltantes) y --credenciales.
Uso:  python completar_faltantes.py --url https://skycelsys.ddns.net --credenciales cred.txt --plan plan.json [--solo accesorios|equipos] [--limite N]
"""
import argparse, collections, importlib.util, json, os, sys

aqui = os.path.dirname(os.path.abspath(__file__))
spec = importlib.util.spec_from_file_location('cargar', os.path.join(aqui, 'cargar_inventario.py'))
cargar = importlib.util.module_from_spec(spec); spec.loader.exec_module(cargar)

# tienda del sistema anterior -> codti del sistema nuevo (por número, no por nombre: los nombres cambiaron)
TIENDA = {1: 1, 2: 2, 6: 3, 7: 4, 8: 5, 9: 6, 10: 7}
CODIGOS_NUEVAS = {'Microfono': 'MCR', 'Reproductor': 'RPR'}


def lista(j):
    d = cargar.desenvolver(j)
    return d if isinstance(d, list) else []


def preparar(api, plan):
    st, j = api.llamar('GET', '/api/catalogos/colores'); colores = {c['nombre'].strip().lower(): c['id'] for c in lista(j)}
    for nombre in sorted({x['color'] for x in plan if x['color']}):
        if nombre.lower() in colores: continue
        st, j = api.llamar('POST', '/api/catalogos/colores', {'nombre': nombre})
        if st not in (200, 201): sys.exit(f'No se pudo crear el color {nombre}: {cargar.mensaje(j)}')
        colores[nombre.lower()] = cargar.desenvolver(j)['id']; print(f'  + color: {nombre}')
    st, j = api.llamar('GET', '/api/catalogos/proveedores'); provs = {p['nombreCorto'].strip().lower(): p['id'] for p in lista(j)}
    for nombre in sorted({x['proveedor'] for x in plan if x['proveedor']}):
        if nombre.lower() in provs: continue
        st, j = api.llamar('POST', '/api/catalogos/proveedores', {'nombreCorto': nombre[:50]})
        if st not in (200, 201): sys.exit(f'No se pudo crear el proveedor {nombre}: {cargar.mensaje(j)}')
        provs[nombre.lower()] = cargar.desenvolver(j)['id']; print(f'  + proveedor: {nombre}')
    return colores, provs


def categorias_accesorio(api, plan):
    """Las categorías de accesorio que el plan usa y todavía no existen (por ejemplo Microfono y Reproductor)."""
    st, j = api.llamar('GET', '/api/categorias'); cats = lista(j) or (j if isinstance(j, list) else [])
    existentes = {c['nombre'].strip().lower() for c in cats if c.get('tipo') == 'ACCESORIO'}
    principal = next((c for c in cats if c.get('tipo') == 'ACCESORIO' and c.get('nivel') == 1), None)
    for x in plan:
        if x['tipo_viejo'] in cargar.TIPOS_EQUIPO: continue
        cat = x['categoria']
        if cat.lower() in existentes: continue
        cuerpo = {'nombreCat': cat, 'tipo': 'ACCESORIO', 'codigo': CODIGOS_NUEVAS.get(cat, cat[:3].upper())}
        if principal: cuerpo['idCategoriaSuperior'] = principal['idcat']
        st, j = api.llamar('POST', '/api/categorias', cuerpo)
        if st not in (200, 201): sys.exit(f'No se pudo crear la categoría {cat}: {cargar.mensaje(j)}')
        existentes.add(cat.lower()); print(f'  + categoría: {cat}')


def accesorios(api, plan, colores, provs, limite):
    filas = [x for x in plan if x['tipo_viejo'] not in cargar.TIPOS_EQUIPO]
    if limite: filas = filas[:limite]
    ok = err = 0
    for r in filas:
        cuerpo = {'codpro': r['codigo'], 'codti': TIENDA[r['codti']], 'stock': r['existencia'],
                  'precioCompra': round(r['costo'], 2), 'precioVenta': round(r['publico'], 2),
                  'idColor': colores.get((r['color'] or '').lower()), 'idProveedor': provs.get((r['proveedor'] or '').lower()), 'stockMinimo': 0,
                  'tipoMaster': 'ACCESORIO', 'categoriaMaster': r['categoria'], 'nombreMaster': r['nombre'], 'descripcion': r['nombre'],
                  'compatibilidad': r['compat'] or None}
        st, j = api.llamar('POST', '/api/productos', cuerpo)
        if st in (200, 201) or (st == 409 and 'Ya existe' in cargar.mensaje(j)): ok += 1
        else:
            err += 1; print('  ERROR', r['codigo'], r['codti'], st, cargar.mensaje(j))
    print(f'Accesorios: {ok} listos, {err} con error (de {len(filas)}).')


def equipos(api, plan, colores, provs, limite):
    filas = [x for x in plan if x['tipo_viejo'] in cargar.TIPOS_EQUIPO]
    st, j = api.llamar('GET', '/api/equipos/catalogo'); catalogo = lista(j)
    por_nombre = {x['nombreArticulo'].strip().lower(): x['idprodmaster'] for x in catalogo}
    st, j = api.llamar('GET', '/api/categorias'); cats = lista(j) or (j if isinstance(j, list) else [])
    marca_cat = {(c['idCategoriaSuperior'], c['nombre'].strip().lower()): c['idcat'] for c in cats if c.get('nivel') == 3}
    raiz = {c['nombre'].strip().lower(): c['idcat'] for c in cats if c.get('nivel') == 2 and c.get('idCategoriaSuperior') == 14}
    grupos = collections.OrderedDict()
    for r in filas:
        grupos.setdefault((r['codti'], r['tipo_viejo'], r['nombre'], r['color'] or ''), []).append(r)
    if limite: grupos = collections.OrderedDict(list(grupos.items())[:limite])
    ok = err = 0; unidades_ok = 0
    for (codti, tipo, nombre, color), lst in grupos.items():
        prefijo = {'TABLET': 'Tablet', 'MODEM': 'Modem'}.get(tipo, 'Celular')
        candidatos = [f'{prefijo} {nombre} {color}'.strip().lower(), f'{nombre} {color}'.strip().lower()]
        idm = next((por_nombre[c] for c in candidatos if c in por_nombre), None)
        costo = cargar.moda([round(x['costo'], 2) for x in lst]); precio = cargar.moda([round(x['publico'], 2) for x in lst])
        unidades = []
        for r in lst:
            u = {'imei': r['codigo']}
            if round(r['costo'], 2) != costo: u['costoUnitario'] = round(r['costo'], 2)
            if round(r['publico'], 2) != precio: u['precioVenta'] = round(r['publico'], 2)
            unidades.append(u)
        cuerpo = {'codti': TIENDA[codti], 'unidades': unidades, 'costo': costo, 'precioVenta': precio,
                  'idProveedor': cargar.moda([provs.get((x['proveedor'] or '').lower()) for x in lst]),
                  'motivo': 'Igualado a la existencia final del sistema anterior (corte 2026-11-01)'}
        if idm: cuerpo['idProductoMaster'] = idm
        else:
            r0 = lst[0]; marca = (r0['marca'] or '').strip().title()
            padre = raiz.get({'TABLET': 'tablet', 'MODEM': 'modem'}.get(tipo, 'celular'))
            idcat = marca_cat.get((padre, marca.lower()))
            if idcat is None:   # la marca no existe todavía: se crea como subcategoría de Celular / Tablet / Modem
                st, j = api.llamar('POST', '/api/categorias', {'nombreCat': marca, 'tipo': 'CELULAR', 'codigo': marca[:3].upper(), 'idCategoriaSuperior': padre})
                if st not in (200, 201): print('  ERROR categoría', marca, st, cargar.mensaje(j)); err += 1; continue
                idcat = cargar.desenvolver(j).get('idcat') or j.get('idcat'); marca_cat[(padre, marca.lower())] = idcat; print(f'  + marca: {marca}')
            sin_marca = nombre[len(r0['marca']):].strip() if r0['marca'] and nombre.lower().startswith(r0['marca'].strip().lower()) else nombre
            cuerpo.update({'idCategoria': idcat, 'nombreProducto': sin_marca, 'idColor': colores.get(color.lower())})
        st, j = api.llamar('POST', '/api/equipos', cuerpo)
        if st in (200, 201): ok += 1; unidades_ok += len(unidades)
        else:
            err += 1; print('  ERROR', nombre, color, 'tienda', codti, st, cargar.mensaje(j))
    print(f'Equipos: {ok} grupos listos ({unidades_ok} unidades), {err} con error (de {len(grupos)} grupos).')


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument('--url', required=True); ap.add_argument('--credenciales', required=True); ap.add_argument('--plan', required=True)
    ap.add_argument('--solo', choices=['accesorios', 'equipos']); ap.add_argument('--limite', type=int)
    a = ap.parse_args()
    usuario, pw = cargar.leer_credenciales(a.credenciales)
    plan = json.load(open(a.plan, encoding='utf-8'))['plan']
    api = cargar.Api(a.url, 0.66)
    st, j = api.llamar('POST', '/api/auth/login', {'username': usuario, 'password': pw})
    if st != 200: sys.exit(f'Login fallido ({st}): {cargar.mensaje(j)}')
    api.token = j['token']; print(f'Sesión iniciada como {j.get("username")} ({j.get("rol")}). {len(plan)} filas en el plan.')
    colores, provs = preparar(api, plan)
    if a.solo != 'equipos':
        categorias_accesorio(api, plan); accesorios(api, plan, colores, provs, a.limite)
    if a.solo != 'accesorios':
        equipos(api, plan, colores, provs, a.limite)


if __name__ == '__main__':
    main()
