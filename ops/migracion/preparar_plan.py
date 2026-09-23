"""
Prepara la migración del inventario: lee la exportación del sistema anterior y las correcciones de catálogos
del usuario (Excel de revisión) y genera
  * nombres_articulos.xlsx  -> hoja para que el usuario revise/corrija los nombres de los artículos
  * plan_migracion.json     -> una entrada por fila con existencia, lista para cargar_inventario.py
Ver README.md de esta carpeta.
"""
import csv, re, json, collections as C, os, sys
from openpyxl import Workbook, load_workbook
from openpyxl.styles import Font, PatternFill, Alignment, Border, Side
from openpyxl.utils import get_column_letter

# Carpeta de trabajo (fuera de Git: contiene datos del negocio). Se puede cambiar con MIG_DIR.
DIR = os.environ.get('MIG_DIR', r'C:\Users\PC\Documents\Migracion Skycel')
SRC = os.environ.get('MIG_EXPORT', os.path.join(DIR, 'inventario_existente.tsv'))          # salida de exportar_inventario.sql
REV = os.environ.get('MIG_REVISION', os.path.join(DIR, 'revision_catalogos_skycel.xlsx'))  # catálogos revisados por el usuario
OUT = os.path.join(DIR, 'nombres_articulos.xlsx')
PLAN = os.path.join(DIR, 'plan_migracion.json')

H = ['codigo','codti','clase','tipo','marca','modelo','color','dist','exist','costo','publico','ingreso','rez']
R = []
for r in csv.reader(open(SRC, encoding='utf-8'), delimiter='\t'):
    if len(r) < 13: continue
    d = dict(zip(H, r))
    for k in ('tipo','marca','modelo','color','dist','codigo'): d[k] = d[k].strip()
    d['codti'] = int(d['codti']); d['exist'] = float(d['exist']); d['costo'] = float(d['costo'] or 0); d['publico'] = float(d['publico'] or 0)
    R.append(d)

# ---------- lo que el usuario corrigió en el Excel de revisión ----------
wb = load_workbook(REV)
def hoja(i): return wb[wb.sheetnames[i]]
TIPOS = {}   # tipo viejo -> (categoria, codigo corto, tipo nuevo)
for r in hoja(1).iter_rows(min_row=2, values_only=True):
    if r[0] and r[0] != 'TOTAL': TIPOS[r[0]] = (r[5].strip(), r[6].strip(), r[7].strip())
# Aclaración del usuario (2026-09-23): las baterías externas con existencia son power banks -> Bateria Portatil.
TIPOS['BATERIA EXTERNA'] = ('Bateria Portatil', 'BPO', 'Accesorio')
COLORES = {}  # color viejo (upper) -> (color limpio o None, estilo o None)
for r in hoja(2).iter_rows(min_row=2, values_only=True):
    if r[0] and r[0] != 'TOTAL':
        k = '' if r[0] == '(vacío)' else r[0].strip().upper()
        col = (r[4] or '').strip(); est = (r[5] or '').strip()
        COLORES[k] = (None if col in ('', '(SIN COLOR)', '(Sin Color)') else col, est or None)
PROVS = {}
for r in hoja(3).iter_rows(min_row=2, values_only=True):
    if r[0] and r[0] != 'TOTAL':
        k = '' if r[0] == '(vacío)' else r[0].strip()
        lim = (r[4] or '').strip()
        PROVS[k] = None if lim in ('', '(SIN PROVEEDOR)') else lim
DECIS = {}    # codigo -> costo corregido
for r in hoja(6).iter_rows(min_row=2, values_only=True):
    if r[0] and r[10]:
        m = re.search(r'costo a \$?\s*([\d.]+)', str(r[10]), re.I)
        if m: DECIS[str(r[0]).strip()] = float(m.group(1))

# ---------- normalización de textos ----------
LOWER = {'en','de','del','con','sin','y','a','al','por'}
MODEL_ALIAS = [(r'^MOTO\b', 'MOTOROLA'), (r'^SAM\b', 'SAMSUNG')]

SIGLAS = {'HTC','ZTE','TCL','OLED','LED'}

def titulo(s):
    s = re.sub(r'\s+', ' ', s.strip())
    return ' '.join(w.upper() if w.upper() in SIGLAS else w for w in s.title().split(' '))

def conectores(nombre):
    ws = nombre.split(' ')
    return ' '.join(w.lower() if (i > 0 and w.lower() in LOWER) else w for i, w in enumerate(ws))

def formato_final(nombre, cat):
    """mAh, "Tipo V8" y conectores en minúscula; se aplica al nombre ya en Título."""
    def mah(m):
        n = m.group(1).replace(',', '')
        if cat in ('Bateria Portatil', 'Panel Solar') and len(n) >= 4: return f'{int(n):,}mAh'   # 30,000mAh
        if cat == 'Bateria': return m.group(1) + ' mAh'                                          # 1500 mAh
        return n + 'mAh'
    n = re.sub(r'(?i)(\d[\d,]*)\s*mah\b', mah, nombre)
    if cat == 'Bateria Portatil' and 'mAh' not in n:      # "Universe 20,000" -> "Universe 20,000mAh"
        n = re.sub(r'\b(\d{1,3}(?:,\d{3})+|\d{4,6})\b', lambda m: f"{int(m.group(1).replace(',', '')):,}mAh", n, count=1)
    n = re.sub(r'(?<![\w-])V8\b', 'Tipo V8', n)
    n = re.sub(r'\bTipo Tipo V8\b', 'Tipo V8', n)
    return conectores(re.sub(r'\s+', ' ', n).strip())

def unidades(s, cat):
    s = re.sub(r'(?i)\bDUAL\s*-?\s*SIM\b', '', s)
    s = re.sub(r'(?i)(\d)\s*GB\b', r'\1Gb', s)          # "128 GB" / "128GB" -> "128Gb"
    return re.sub(r'\s+', ' ', s).strip()

def alias_modelo(s):
    for pat, rep in MODEL_ALIAS:
        s = re.sub(pat, rep, s.upper())
    return s

def limpia(s, cat):
    return titulo(unidades(s, cat))

def dedupe(marca, modelo):
    """Si el modelo ya empieza con las palabras de la marca (o viceversa) no se repiten."""
    mt = marca.lower().split(); ot = modelo.lower().split()
    if marca and modelo and ot[:len(mt)] == mt: return '', modelo
    if marca and modelo and mt[:len(ot)] == ot: return marca, ''
    if marca and modelo:
        # "Nyx" + "Nyx1400A65X55": el primer token del modelo empieza con la marca
        if len(mt) == 1 and ot and ot[0].startswith(mt[0]) and len(ot[0]) > len(mt[0]): return '', modelo
    return marca, modelo

EQUIPOS = {'CELULAR','TABLET','MODEM'}
NOMBRE_ESTILO = {'Dama','Caballero','Matte','Brillos','Privacidad'}   # 'Diseños' no se agrega al nombre
COMPAT = {'Mica','Protector','Pantalla','Tapa','Marco','Bateria'}
PREFIJO = {'Control': 'Control Remoto'}
# El nombre lleva el producto real aunque la categoría agrupe varios tipos viejos
PREFIJO_TIPO = {'LLAVERO': 'Llavero', 'PULSERA': 'Pulsera', 'ACCESORIO': '', 'BATERIA EXTERNA': 'Bateria Portatil', 'MINI BATERIA PORTATIL': 'Bateria Portatil'}

def clasifica(r):
    cat, cod, tn = TIPOS[r['tipo']]
    col, est = COLORES.get(r['color'].upper(), (None, None))
    return cat, col, est

def nombre_de(r):
    cat, col, est = clasifica(r)
    modelo = unidades(alias_modelo(r['modelo']), cat)
    marca = unidades(r['marca'], cat)
    if r['tipo'] in EQUIPOS:
        mrc, mod = dedupe(titulo(marca), titulo(modelo))
        pre = cat if cat in ('Tablet', 'Modem') else ''
        return formato_final(' '.join(x for x in (pre, mrc, mod) if x).strip(), cat), '', col, est
    if cat == 'Mica':   # GLASS es "Cristal" en el nombre; "Glass 5D/9D" pasa a "5D/9D"
        marca = re.sub(r'(?i)^GLASS\s+(\d)', r'\1', marca)
        marca = re.sub(r'(?i)^GLASS\b', 'Cristal', marca)
    mrc, mod = dedupe(titulo(marca), titulo(modelo))
    if cat == 'Chip' and marca.upper() == 'EXPRESS': mrc, mod = dedupe('', titulo(modelo))
    pref = PREFIJO_TIPO.get(r['tipo'], PREFIJO.get(cat, cat))
    partes = [pref, mrc, mod]
    if est and est in NOMBRE_ESTILO and est.lower() not in ' '.join(partes).lower(): partes.append(est)
    nom = formato_final(' '.join(p for p in partes if p), 'Bateria Portatil' if r['tipo'] in ('BATERIA EXTERNA','MINI BATERIA PORTATIL') else cat)
    compat = titulo(modelo) if cat in COMPAT else ''
    return nom, compat, col, est

masters = C.OrderedDict()
for r in R:
    nom, compat, col, est = nombre_de(r)
    key = (TIPOS[r['tipo']][0], r['marca'], r['modelo'], est or '')
    m = masters.setdefault(nom, dict(nombre=nom, cat=TIPOS[r['tipo']][0], compat=compat, keys=set(), filas=0, unid=0, ej=r['codigo'], marcas=set(), modelos=set()))
    m['keys'].add(key); m['filas'] += 1; m['unid'] += int(r['exist']); m['marcas'].add(r['marca']); m['modelos'].add(r['modelo'])

# ---------- Excel de nombres ----------
F = 'Arial'
fh = Font(name=F, bold=True, color='FFFFFF', size=10); fill_h = PatternFill('solid', start_color='1F3A5F')
fill_y = PatternFill('solid', start_color='FFF2A8'); fn = Font(name=F, size=10); fb = Font(name=F, size=10, bold=True)
thin = Side(style='thin', color='CCCCCC'); bd = Border(left=thin, right=thin, top=thin, bottom=thin)
out = Workbook()
ws = out.active; ws.title = 'Léeme'
ws.column_dimensions['A'].width = 30; ws.column_dimensions['B'].width = 110
info = [
 ('Revisión de NOMBRES de artículos', ''),
 ('Qué es', f'Cada fila es un artículo del sistema nuevo ({len(masters):,} en total), armado con las reglas que usaste en tu revisión: primera letra en mayúscula, conectores en minúscula (en, de, con, sin, para), 128Gb pegado, mAh, GLASS = Cristal, Moto/Sam = Motorola/Samsung.'),
 ('Cómo revisarlo', 'Corrige SOLO la columna amarilla "NOMBRE". Puedes filtrar por categoría y usar Buscar y reemplazar (Ctrl+L) para arreglar un mismo error en todas las filas de una vez. No borres filas.'),
 ('Qué NO va en el nombre', 'El color (es un campo aparte del producto) ni el proveedor. Sí van Dama, Caballero, Matte, Brillos y Privacidad porque distinguen un producto de otro. "Diseños" no se agrega.'),
 ('Ejemplo', 'Protector "Antigolpe Dama" para Samsung A17 → "Protector Antigolpe Dama Samsung A17".'),
 ('Ojo', 'Dos filas con el mismo nombre serían el MISMO artículo (se unen). Si dos productos distintos quedaron con el mismo nombre, diferéncialos aquí.'),
]
for i, (a, b) in enumerate(info, 1):
    ws.cell(row=i, column=1, value=a).font = Font(name=F, bold=True, size=12 if i == 1 else 10)
    c = ws.cell(row=i, column=2, value=b); c.font = fn; c.alignment = Alignment(wrap_text=True, vertical='top')
w2 = out.create_sheet('Nombres')
cols = ['Categoría','NOMBRE (editar)','Marca vieja','Modelo viejo','Códigos (filas)','Unidades','Ejemplo de código','Nombre propuesto (referencia, no editar)']
w2.append(cols)
for j in range(1, len(cols)+1):
    c = w2.cell(row=1, column=j); c.font = fh; c.fill = fill_h; c.border = bd; c.alignment = Alignment(wrap_text=True, vertical='center')
ordenados = sorted(masters.values(), key=lambda m: (m['cat'], m['nombre']))
for m in ordenados:
    w2.append([m['cat'], m['nombre'], ' | '.join(sorted(m['marcas']))[:120], ' | '.join(sorted(m['modelos']))[:120], m['filas'], m['unid'], m['ej'], m['nombre']])
for row in w2.iter_rows(min_row=2, max_row=w2.max_row):
    for c in row:
        c.font = fn; c.border = bd
        if c.column == 2: c.fill = fill_y
        if c.column in (5, 6): c.number_format = '#,##0'
        if c.column == 8: c.font = Font(name=F, size=9, color='808080')
for j, w in enumerate([18, 62, 34, 40, 10, 10, 22, 50], 1): w2.column_dimensions[get_column_letter(j)].width = w
w2.freeze_panes = 'C2'; w2.auto_filter.ref = f'A1:H{w2.max_row}'; w2.row_dimensions[1].height = 30
if os.path.exists(OUT) and '--forzar-hoja' not in sys.argv:
    print(f'AVISO: {OUT} ya existe (puede tener correcciones del usuario) y NO se sobrescribió. Use --forzar-hoja para regenerarla.')
else:
    out.save(OUT)

# ---------- plan de migración (una entrada por fila vieja) ----------
plan = []
for r in R:
    nom, compat, col, est = nombre_de(r)
    cat, cod, tn = TIPOS[r['tipo']]
    costo = DECIS.get(r['codigo'], r['costo'])
    plan.append(dict(codigo=r['codigo'], codti=r['codti'], tipo_viejo=r['tipo'], categoria=cat, tipo_nuevo=tn,
                     nombre=nom, compat=compat, color=col, proveedor=PROVS.get(r['dist']),
                     marca=r['marca'], modelo=r['modelo'], existencia=int(r['exist']), costo=costo, costo_original=r['costo'],
                     publico=r['publico'], rezagado=r['rez'] == '1', ingreso=r['ingreso']))
json.dump(dict(plan=plan, categorias=sorted({(c, k, t) for c, k, t in TIPOS.values()})), open(PLAN, 'w', encoding='utf-8'), ensure_ascii=False)
print('masters', len(masters), 'plan', len(plan), 'correcciones de costo', len(DECIS), DECIS)
print('categorias', sorted({c for c, k, t in TIPOS.values()}))
print('colores limpios', sorted({v[0] for v in COLORES.values() if v[0]}))
print('largo max nombre', max(len(m['nombre']) for m in masters.values()))
