/**
 * Punto de venta de la web (mismas reglas que el de JSystem):
 *  - los equipos (celulares y tablets) se venden por IMEI o serie, de contado o a crédito PayJoy (enganche + 3, 6, 9 o 12 meses);
 *  - al vender un equipo, la promoción (chip de regalo, mica y protector con descuento) se aplica sola con la misma regla que el servidor;
 *  - un vendedor puede pedir un descuento a un administrador y verlo aprobado o rechazado aquí mismo;
 *  - la tarjeta solo aparece si el negocio ya la acepta;
 *  - un carrito se parte en una venta por cada forma de cobro (todas con el mismo idOperacion).
 * Funciona con conexión; sin ella, usa JSystem en la tienda.
 */

let posCarrito = [];
let posSondeo = null;
const PLAZOS_PAYJOY = [3, 6, 9, 12];

/** Configuración que el punto de venta necesita: si se acepta tarjeta y la promoción de equipo (con el árbol de categorías). */
const posConfig = { aceptaTarjeta: false, promo: null, padres: new Map() };

async function cargarConfigPOS() {
  try {
    const [negocio, promo, categorias] = await Promise.all([
      api('GET', '/api/configuracion/negocio'),
      api('GET', '/api/promociones/equipo'),
      cargarCategorias(),   // el árbol, ya aplanado
    ]);
    posConfig.aceptaTarjeta = !!negocio?.aceptaTarjeta;
    posConfig.promo = promo || null;
    posConfig.padres = new Map();
    (categorias || []).forEach(c => { if (c.idCategoriaSuperior != null) posConfig.padres.set(c.idcat, c.idCategoriaSuperior); });
  } catch { /* sin la configuración: solo efectivo y transferencia, y sin promoción (el servidor la valida igual al cobrar) */ }
}

/** ¿El negocio ya acepta pagos con tarjeta? (se pregunta una vez; sin conexión se asume que no, como en JSystem). */
let negocioTarjetaCache = null;
async function negocioAceptaTarjeta() {
  if (negocioTarjetaCache === null) {
    try { negocioTarjetaCache = !!(await api('GET', '/api/configuracion/negocio')).aceptaTarjeta; } catch { return false; }
  }
  return negocioTarjetaCache;
}

/** Un abono o un pago a proveedor se hace en efectivo o transferencia (y con tarjeta solo si el negocio ya la acepta); PayJoy no es una forma de pago aquí. */
async function ajustarMetodosDeCobro(idSelect) {
  const sel = document.getElementById(idSelect);
  if (!sel) return;
  sel.querySelector('option[value="4"]')?.remove();
  if (!(await negocioAceptaTarjeta())) sel.querySelector('option[value="2"]')?.remove();
}

/** La categoría y todas las que la contienen, hasta la principal. */
function posRutaCategoria(idCategoria) {
  const ids = new Set();
  for (let c = idCategoria; c != null && !ids.has(c); c = posConfig.padres.get(c)) ids.add(c);
  return ids;
}

// ── Catálogo ─────────────────────────────────────────────────────────────

/** Un artículo de equipos como entrada del catálogo: «EQ…» es solo un identificador interno, no un código de producto. */
function posEquipoComoProducto(a) {
  return {
    codpro: 'EQ' + a.idprodmaster, esEquipo: true,
    idProductoMaster: a.idprodmaster, nombreProductoMaster: a.nombreArticulo,
    tipo: a.tipo, stock: a.disponibles, preciopub: a.precioMinimo != null ? a.precioMinimo : 0,
    idCategoria: a.idCategoria, nombreCategoria: a.subcategoria2 || a.subcategoria1 || a.categoriaPrincipal,
    categoriaPrincipal: a.categoriaPrincipal, subcategoria1: a.subcategoria1, subcategoria2: a.subcategoria2,
    imeisDisponibles: (a.unidades || []).map(u => ({ imei: u.imei, precioVenta: Number(u.precioFinal ?? u.precioVenta), condicion: u.condicion })),
  };
}

/** Los productos con stock de una sucursal y, una entrada por artículo, sus equipos con las unidades disponibles (venta, traspasos, cambios). */
async function catalogoConEquipos(codti) {
  const [productos, equipos] = await Promise.all([
    api('GET', `/api/productos/tienda/${codti}/disponibles`),
    api('GET', `/api/equipos/tienda/${codti}/articulos?conUnidades=true`).catch(() => []),
  ]);
  return productos.filter(p => p.tipo !== 'CELULAR' && p.tipo !== 'TABLET')
    .concat((equipos || []).filter(a => a.disponibles > 0 && (a.unidades || []).length > 0).map(posEquipoComoProducto));
}

/**
 * Catálogo del punto de venta de una sucursal: los productos con stock y, una entrada por artículo, los equipos con sus unidades.
 * Si ya se tenía se devuelve al instante y se refresca en segundo plano (alActualizar recibe la lista nueva); el servidor valida al cobrar.
 */
const posCatalogoCache = { codti: null, productos: null };
async function cargarCatalogoPOS(codti, alActualizar) {
  const pedir = async () => {
    const lista = await catalogoConEquipos(codti);
    posCatalogoCache.codti = codti; posCatalogoCache.productos = lista;
    return lista;
  };
  if (posCatalogoCache.codti === codti && posCatalogoCache.productos) {
    pedir().then(alActualizar).catch(() => { /* se sigue con lo que ya había */ });
    return posCatalogoCache.productos;
  }
  return pedir();
}

const etiquetaCondicion = (c) => ({ NUEVO: 'Nuevo', SEMINUEVO: 'Seminuevo', USADO: 'Seminuevo', REACONDICIONADO: 'Reacondicionado' })[c] || '';

// ── Cálculo de la venta ──────────────────────────────────────────────────

const posRedondear = (n) => Math.round(Number(n) * 100) / 100;
const posAprobado = (i) => (i.sol && i.sol.estado === 1 ? Number(i.sol.monto) : 0);
const posPrecioConPromo = (i) => Math.max(0, posRedondear(i.precio - i.descuentoPromo - posAprobado(i)));
const posPrecioSinPromo = (i) => posRedondear(i.precio - posAprobado(i));
/** Lo que vale el renglón completo (aunque sea a crédito). */
const posImporte = (i) => posRedondear(i.piezasPromo * posPrecioConPromo(i) + (i.cantidad - i.piezasPromo) * posPrecioSinPromo(i));
/** Lo que se cobra hoy por el renglón: todo si es de contado; solo el enganche si es a crédito. */
const posCobroHoy = (i) => (i.formaPago === 'PayJoy' ? Number(i.enganche || 0) : posImporte(i));

/**
 * Reparte la promoción de equipo entre los renglones, en el orden en que aparecen: tantas piezas de cada cosa como equipos haya.
 * Es la misma regla del servidor (que la vuelve a calcular al cobrar).
 */
function posAplicarPromocion() {
  posCarrito.forEach(i => { i.piezasPromo = 0; i.descuentoPromo = 0; i.promoTexto = null; });
  const p = posConfig.promo;
  if (!p || !p.activo) return;
  const equipos = posCarrito.filter(i => i.esEquipo).reduce((s, i) => s + i.cantidad, 0);
  if (equipos === 0) return;
  let chips = equipos * (p.chipsPorEquipo || 0), micas = equipos * (p.micasPorEquipo || 0), protectores = equipos * (p.protectoresPorEquipo || 0);
  const enCategoria = (ruta, id) => id != null && ruta.has(id);
  for (const l of posCarrito) {
    if (l.esEquipo || l.precio <= 0) continue;
    const ruta = posRutaCategoria(l.idCategoria);
    if (enCategoria(ruta, p.idCategoriaChip) && chips > 0) {
      l.piezasPromo = Math.min(l.cantidad, chips); chips -= l.piezasPromo; l.descuentoPromo = l.precio;
    } else if (enCategoria(ruta, p.idCategoriaMica) && micas > 0) {
      l.piezasPromo = Math.min(l.cantidad, micas); micas -= l.piezasPromo; l.descuentoPromo = Math.min(l.precio, Number(p.descuentoMica));
    } else if (enCategoria(ruta, p.idCategoriaProtector) && protectores > 0 && l.precio < Number(p.protectorPrecioMenorA)) {
      l.piezasPromo = Math.min(l.cantidad, protectores); protectores -= l.piezasPromo; l.descuentoPromo = Math.min(l.precio, Number(p.descuentoProtector));
    }
    if (l.piezasPromo > 0) {
      const pzas = l.piezasPromo === 1 ? '' : l.piezasPromo + ' pzas · ';
      l.promoTexto = l.descuentoPromo >= l.precio - 0.004 ? `Promoción de equipo: ${pzas}de regalo` : `Promoción de equipo: ${pzas}−${formatoDinero(l.descuentoPromo)} c/u`;
    }
  }
}

function posMetodoCarrito() { return Number(document.getElementById('pv-metodo')?.value || 1); }

/** Los renglones agrupados por forma de cobro: una venta por grupo. Primero las que llevan equipos (la promoción cuenta con ellos). */
function posGrupos() {
  const grupos = new Map();
  for (const i of posCarrito) {
    const clave = i.formaPago === 'PayJoy' ? 'PJ' + i.metodoEnganche : 'C' + posMetodoCarrito();
    if (!grupos.has(clave)) grupos.set(clave, { clave, payjoy: i.formaPago === 'PayJoy', metodo: i.formaPago === 'PayJoy' ? i.metodoEnganche : posMetodoCarrito(), items: [] });
    grupos.get(clave).items.push(i);
  }
  return [...grupos.values()].sort((a, b) => Number(b.items.some(x => x.esEquipo)) - Number(a.items.some(x => x.esEquipo)));
}

function posTotales() {
  const contado = posCarrito.filter(i => i.formaPago !== 'PayJoy').reduce((s, i) => s + posImporte(i), 0);
  const enganches = posCarrito.filter(i => i.formaPago === 'PayJoy').reduce((s, i) => s + Number(i.enganche || 0), 0);
  const efectivo = posCarrito.filter(i => (i.formaPago === 'PayJoy' ? i.metodoEnganche : posMetodoCarrito()) === 1).reduce((s, i) => s + posCobroHoy(i), 0);
  return { contado: posRedondear(contado), enganches: posRedondear(enganches), total: posRedondear(contado + enganches), efectivo: posRedondear(efectivo) };
}

/** Los renglones que se mandan al servidor por un artículo del carrito (si la promoción alcanza para algunas piezas, van en renglón aparte). */
function posDetalles(i) {
  const base = { idprodmaster: i.idprodmaster, codpro: i.esEquipo ? null : i.codpro, imei: i.imei || null, esRegalo: false };
  if (i.formaPago === 'PayJoy') { base.enganchePayjoy = Number(i.enganche); base.plazoMesesPayjoy = Number(i.plazoMeses); }
  if (i.sol && i.sol.estado === 1) base.idSolicitudDescuento = i.sol.id;
  if (i.piezasPromo <= 0) return [{ ...base, cantidad: i.cantidad, precioUnitarioFinal: posPrecioSinPromo(i) }];
  const conPromo = posPrecioConPromo(i);
  const l = [{ ...base, cantidad: i.piezasPromo, precioUnitarioFinal: conPromo, esRegalo: conPromo < 0.005 }];
  if (i.piezasPromo < i.cantidad) l.push({ ...base, cantidad: i.cantidad - i.piezasPromo, precioUnitarioFinal: posPrecioSinPromo(i) });
  return l;
}

// ── Descuentos que aprueba un administrador ──────────────────────────────

/** Quita la solicitud del renglón y, si todavía sirve, avisa al servidor para que la retire. */
function posReiniciarSolicitud(i, avisarServidor) {
  const sol = i.sol;
  i.sol = null; i.pidiendo = false;
  if (avisarServidor && sol && (sol.estado === 0 || sol.estado === 1)) api('PATCH', `/api/descuentos/solicitudes/${sol.id}/cancelar`).catch(() => {});
}

async function posEnviarSolicitud(i, monto, motivo, codti) {
  const payload = {
    codti, idprodmaster: i.idprodmaster, imei: i.esEquipo ? i.imei : null, codpro: i.esEquipo ? null : i.codpro,
    cantidad: i.cantidad, precioLista: i.precio, descuentoPorPieza: monto, motivo,
  };
  const r = await api('POST', '/api/descuentos/solicitudes', payload);
  i.sol = { id: r.idsolicitud, estado: 0, monto, cantidad: i.cantidad, motivo: null };
  i.pidiendo = false;
}

/** Pregunta al servidor cómo van las solicitudes pendientes del carrito. */
async function posRevisarSolicitudes() {
  if (!document.getElementById('pv-carrito')) { clearInterval(posSondeo); posSondeo = null; return; }
  const pendientes = posCarrito.filter(i => i.sol && i.sol.estado === 0);
  let cambio = false;
  for (const i of pendientes) {
    try {
      const r = await api('GET', `/api/descuentos/solicitudes/${i.sol.id}`);
      if (!i.sol || i.sol.estado !== 0) continue;
      if (r.estado === 1) { i.sol.estado = 1; i.sol.monto = Number(r.descuentoPorPieza); cambio = true; }
      else if (r.estado === 2) { i.sol.estado = 2; i.sol.motivo = r.motivoResolucion; cambio = true; }
      else if (r.estado === 4) { i.sol = null; cambio = true; }
    } catch { /* sin conexión: se vuelve a preguntar en el siguiente ciclo */ }
  }
  if (cambio) actualizarCarritoPOS();
}

// ── Pantalla ─────────────────────────────────────────────────────────────

async function pantallaPOS() {
  const s = Sesion.obtener();
  if (!Sesion.tiene('VENTA_CREAR')) { root.innerHTML = '<div class="tarjeta">No tienes permiso para vender.</div>'; return; }
  const codti = s.codti;
  if (codti == null) { root.innerHTML = '<div class="tarjeta">Tu usuario no tiene una sucursal fija: usa JSystem en la tienda para vender.</div>'; return; }
  posCarrito = [];
  const idOperacion = uuid();   // el mismo en todas las ventas en que se parte este carrito (la promoción cuenta el carrito completo)
  const puedeCerrarCaja = Sesion.tiene('CAJA_OPERAR');

  root.innerHTML = `
    <div id="pv-turno"></div>
    ${cabeceraPantalla(ic('shopping-cart') + ' Punto de venta', `Vendedor: ${escapar(s.nombreCompleto || s.username)} · ${new Date().toLocaleDateString('es-MX', { day: 'numeric', month: 'short', year: 'numeric' })}`,
      puedeCerrarCaja ? `<button id="pv-cerrar-caja" class="btn btn-gris">${ic('lock')} Cerrar caja</button>` : '')}
    <div class="pos-grid">
    <div class="pos-izq">
    <div class="tarjeta pos-catalogo">
      <h2 class="h2-ic">${ic('package')} Catálogo</h2>
      <div class="buscador buscar-ic">${ic('search')}<input id="pv-buscar" placeholder="Buscar producto..."></div>
      <div id="pv-chips" class="chips"></div>
      <div id="pv-resultados"></div>
    </div>
    </div>

    <div class="pos-der">
    <div class="tarjeta pos-carrito">
      <div class="lista-cab"><h2 class="h2-ic">${ic('shopping-cart')} Carrito de compra</h2><span class="pill-items" id="pv-items">0 items</span></div>
      <div id="pv-carrito"><div class="vacio">Agrega productos desde el catálogo.</div></div>
      <div id="pv-total" class="barra-total"></div>
    </div>

    <div class="tarjeta">
      <h2 class="h2-ic" style="margin-top:0;">${ic('users')} Cliente</h2>
      <label style="display:flex; align-items:center; gap:8px; font-weight:400; text-transform:none;">
        <input type="checkbox" id="pv-con-cliente" style="width:auto;"> Registrar cliente (si no, venta de mostrador)
      </label>
      <div id="pv-cliente-cont" class="oculto"></div>
    </div>

    <div class="tarjeta">
      <h2 class="h2-ic" style="margin-top:0;">${ic('credit-card')} Método de pago</h2>
      <label class="obligatorio">Método</label>
      <select id="pv-metodo">
        <option value="1">Efectivo</option>
        <option value="3">Transferencia</option>
      </select>
      <div class="ayuda">Aplica a todo lo que se paga de contado. Un equipo a crédito con PayJoy se cobra aparte: solo el enganche.</div>
      <div id="pv-efectivo-cont">
        <label>Monto recibido en efectivo</label>
        <input id="pv-recibido" type="number" inputmode="decimal" min="0" step="0.01">
        <div id="pv-cambio" class="ayuda"></div>
      </div>
      <label>Observaciones</label>
      <input id="pv-obs" placeholder="Opcional">
      <button id="pv-cobrar" class="btn btn-verde btn-dinero btn-grande">${ic('banknote')} Cobrar</button>
    </div>
    </div>
    </div>`;

  // El catálogo, la configuración y la caja se piden a la vez (el catálogo se usa al instante si ya se tenía)
  const promesaCajas = api('GET', '/api/cajas/tienda/' + codti).catch(() => null);
  const promesaConfig = cargarConfigPOS();
  const inputBuscar = document.getElementById('pv-buscar');
  inputBuscar.disabled = true;
  inputBuscar.placeholder = 'Cargando catálogo...';
  let productos = [];
  try {
    productos = await cargarCatalogoPOS(codti, (nuevos) => { productos = nuevos; });
  } catch (err) {
    document.getElementById('pv-resultados').innerHTML = `<div class="mensaje error">${escapar(err.network ? 'Sin conexión con el servidor: el punto de venta necesita conexión.' : err.message)}</div>`;
    inputBuscar.placeholder = 'Sin catálogo';
    return;
  }
  await promesaConfig;
  inputBuscar.disabled = false;
  inputBuscar.placeholder = 'Buscar por nombre, código, modelo o IMEI...';

  // La tarjeta solo si el negocio ya la acepta
  const selMetodo = document.getElementById('pv-metodo');
  selMetodo.innerHTML = [['1', 'Efectivo'], posConfig.aceptaTarjeta ? ['2', 'Tarjeta'] : null, ['3', 'Transferencia']].filter(Boolean)
    .map(([v, n]) => `<option value="${v}">${n}</option>`).join('');

  // Caja de la tienda (se valida de nuevo al cobrar)
  let idCaja = null;
  const cajas = await promesaCajas;
  if (cajas) idCaja = cajas.find(c => c.esCajaPrincipal)?.idCaja ?? cajas[0]?.idCaja ?? null;

  // Para cobrar la caja debe estar abierta: si no lo está, se ofrece abrirla aquí mismo
  const revisarTurno = async () => {
    const cont = document.getElementById('pv-turno');
    if (!cont || idCaja == null) return;
    try {
      const t = await api('GET', `/api/cajas/${idCaja}/sesion`);
      if (t.abierta) { cont.innerHTML = ''; return; }
      cont.innerHTML = '<div class="mensaje info">' + ic('lock') + ' La caja está cerrada: ábrela con su fondo inicial para poder cobrar.</div><div id="pv-turno-form"></div>';
      formularioAbrirCaja(document.getElementById('pv-turno-form'), idCaja, t.nombreCaja, t.fondoSugerido, revisarTurno);
    } catch { /* sin conexión: no se bloquea nada aquí; el servidor decide al cobrar */ }
  };
  revisarTurno();

  const btnCerrarCaja = document.getElementById('pv-cerrar-caja');
  if (btnCerrarCaja) btnCerrarCaja.onclick = async () => {
    if (idCaja == null) { mostrarMensaje(root, 'No se encontró una caja para tu sucursal.', 'error'); return; }
    try {
      const t = await api('GET', `/api/cajas/${idCaja}/sesion`);
      if (!t.abierta) { mostrarMensaje(root, 'La caja ya está cerrada.', 'info'); return; }
      const cont = document.getElementById('pv-turno');
      cont.innerHTML = '<div id="pv-turno-form"></div>';
      formularioCerrarCaja(document.getElementById('pv-turno-form'), idCaja, t, (corte) => { navegar('#/corte/' + corte.idsesionCaja); });
    } catch (err) { mostrarMensaje(root, err.network ? 'Sin conexión con el servidor.' : err.message, 'error'); }
  };

  const contResultados = document.getElementById('pv-resultados');
  // Filtro por categoría: sin escribir nada muestra lo que hay de esa categoría (con existencia primero)
  const contChips = document.getElementById('pv-chips');
  let categoriaSel = null;
  const hayExistencia = (p) => p.tipo === 'SERVICIO' || (p.esEquipo ? (p.imeisDisponibles || []).length > 0 : Number(p.stock) > 0);
  const dibujarChips = () => {
    const cuenta = {};
    productos.filter(hayExistencia).forEach(p => { const c = p.categoriaPrincipal || p.nombreCategoria; if (c) cuenta[c] = (cuenta[c] || 0) + 1; });
    const top = Object.entries(cuenta).sort((a, b) => b[1] - a[1]).slice(0, 12).map(x => x[0]);
    contChips.innerHTML = top.length === 0 ? '' :
      `<button class="chip ${categoriaSel === null ? 'activo' : ''}" data-cat="">Todo</button>` +
      top.map(c => `<button class="chip ${categoriaSel === c ? 'activo' : ''}" data-cat="${escapar(c)}">${escapar(c)}</button>`).join('');
    contChips.querySelectorAll('.chip').forEach(b => b.onclick = () => { categoriaSel = b.dataset.cat || null; dibujarChips(); mostrarResultados(); });
  };
  const mostrarResultados = () => {
    const q = inputBuscar.value.trim();
    // El catálogo no se muestra completo: solo cuando se busca algo o se elige una categoría
    if (q.length < 2 && !categoriaSel) { contResultados.innerHTML = '<div class="vacio">Escribe en el buscador para ver productos.</div>'; return; }
    let lista = q.length >= 2 ? filtrarProductos(productos, q) : productos;
    if (categoriaSel) lista = lista.filter(p => (p.categoriaPrincipal || p.nombreCategoria) === categoriaSel);
    if (q.length < 2) lista = lista.filter(hayExistencia).sort((a, b) => a.nombreProductoMaster.localeCompare(b.nombreProductoMaster, 'es'));
    dibujarResultadosPOS(contResultados, lista.slice(0, 40));
  };
  dibujarChips();
  mostrarResultados();
  inputBuscar.addEventListener('input', mostrarResultados);

  // Cliente
  const chkCliente = document.getElementById('pv-con-cliente');
  const contCliente = document.getElementById('pv-cliente-cont');
  let clienteSel = null;
  chkCliente.onchange = () => {
    contCliente.classList.toggle('oculto', !chkCliente.checked);
    if (chkCliente.checked && !contCliente.innerHTML) dibujarSelectorClientePOS(contCliente, (c) => { clienteSel = c; });
  };

  // Pago
  const actualizarMetodo = () => { actualizarCarritoPOS(); };
  selMetodo.onchange = actualizarMetodo;
  document.getElementById('pv-recibido').addEventListener('input', actualizarCambioPOS);

  actualizarCarritoPOS();
  if (posSondeo) clearInterval(posSondeo);
  posSondeo = setInterval(posRevisarSolicitudes, 5000);

  document.getElementById('pv-cobrar').onclick = async (e) => {
    if (posCarrito.length === 0) { mostrarMensaje(root, 'Agrega al menos un producto.', 'error'); return; }
    if (!idCaja) { mostrarMensaje(root, 'No se encontró una caja para tu sucursal.', 'error'); return; }
    for (const i of posCarrito) {
      if (i.formaPago !== 'PayJoy') continue;
      const precio = posPrecioSinPromo(i);
      if (!(i.enganche > 0)) { mostrarMensaje(root, `Falta el enganche PayJoy de ${i.nombre}.`, 'error'); return; }
      if (i.enganche >= precio) { mostrarMensaje(root, `El enganche de ${i.nombre} debe ser menor a su precio (${formatoDinero(precio)}).`, 'error'); return; }
    }
    if (posCarrito.some(i => i.sol && i.sol.estado === 0)) {
      if (!confirm('Hay un descuento esperando respuesta del administrador. Si cobras ahora se cobra sin ese descuento. ¿Continuar?')) return;
    }
    const tot = posTotales();
    const recibido = Number(document.getElementById('pv-recibido').value);
    if (tot.efectivo > 0.004 && !(recibido + 0.004 >= tot.efectivo)) { mostrarMensaje(root, `El monto recibido debe cubrir ${formatoDinero(tot.efectivo)} en efectivo.`, 'error'); return; }
    if (chkCliente.checked && !clienteSel) { mostrarMensaje(root, 'Busca o registra al cliente, o desmarca la casilla.', 'error'); return; }

    e.target.disabled = true;
    e.target.innerHTML = '<span class="spinner"></span> Cobrando...';
    const grupos = posGrupos();
    const hechas = [];
    try {
      for (const g of grupos) {
        const payload = {
          codti, idCaja,
          idcliente: clienteSel?.idcliente ?? null,
          tipoComprobante: 1,
          metodoPago: g.metodo,
          tipoVenta: g.payjoy ? 1 : 0,
          montoAbonado: posRedondear(g.items.reduce((sum, i) => sum + posCobroHoy(i), 0)),
          observaciones: document.getElementById('pv-obs').value.trim() || null,
          claveOffline: uuid(),
          idOperacion,
          detalles: g.items.flatMap(posDetalles),
        };
        const venta = await api('POST', '/api/ventas', payload);
        hechas.push(venta);
      }
      const ids = hechas.map(v => '#' + v.idventa).join(', ');
      const cambio = tot.efectivo > 0 ? Math.max(0, recibido - tot.efectivo) : 0;
      mostrarMensaje(root, `${hechas.length === 1 ? 'Venta' : 'Ventas'} ${ids} registrada${hechas.length === 1 ? '' : 's'}. Cobrado hoy ${formatoDinero(tot.total)}${tot.efectivo > 0 ? ' · Cambio ' + formatoDinero(cambio) : ''}.`, 'ok');
      posCarrito = [];
      posCatalogoCache.productos = null;   // el stock cambió: el próximo POS pide el catálogo fresco
      setTimeout(() => pantallaPOS(), 1400);
    } catch (err) {
      const parcial = hechas.length > 0 ? ` Ya se registraron ${hechas.length} de ${grupos.length} venta(s) de este carrito: revisa el historial antes de reintentar.` : '';
      mostrarMensaje(root, (err.network ? 'Sin conexión con el servidor: esta venta no se guardó. Usa JSystem en la tienda mientras tanto.' : err.message) + parcial, 'error');
      if (!err.network && /cerrada/.test(err.message || '')) revisarTurno();
      if (hechas.length > 0) posCatalogoCache.productos = null;
      e.target.disabled = false;
      e.target.innerHTML = ic('banknote') + ' Cobrar';
    }
  };
}

function dibujarResultadosPOS(cont, productos) {
  if (productos.length === 0) { cont.innerHTML = '<div class="vacio">Sin resultados.</div>'; return; }
  cont.innerHTML = productos.map(p => {
    const conDescuento = !p.esEquipo && p.precioFinal != null && Number(p.descuentoAplicado) > 0;
    return `
    <div class="orden-item pos-fila" data-codpro="${escapar(p.codpro)}">
      <span class="pos-ic tipo-${escapar((p.tipo || '').toLowerCase())}">${TIPO_ICONO[p.tipo] || ic('package')}</span>
      <div class="pos-fila-cuerpo">
        <div class="orden-folio">${escapar(p.nombreProductoMaster)}</div>
        <div class="orden-cliente">${p.esEquipo ? '' : escapar(p.codpro) + (p.tipo !== 'SERVICIO' ? ' · ' : '')}${p.tipo !== 'SERVICIO' ? (p.esEquipo ? Number(p.stock) + ' disponible' + (Number(p.stock) === 1 ? '' : 's') : 'Stock: ' + Number(p.stock)) : ''}${conDescuento ? ' · ' + ic('tags') + ' con descuento' : ''}</div>
      </div>
      ${p.esEquipo ? '' : `<span class="pos-precio">
        ${conDescuento ? `<span class="ayuda" style="text-decoration:line-through; display:block; font-weight:400;">${formatoDinero(p.preciopub)}</span>` : ''}
        <span style="color:${conDescuento ? 'var(--verde)' : 'inherit'};">${formatoDinero(conDescuento ? p.precioFinal : p.preciopub)}</span> <span class="pv-mas">${ic('plus')}</span>
      </span>`}
      ${p.esEquipo ? `
        <div class="grupo-botones pos-imeis">
          ${p.imeisDisponibles.map(u => `<button class="btn btn-azul btn-chico pv-agregar-imei" data-imei="${escapar(u.imei)}">${escapar(u.imei)}${u.condicion && u.condicion !== 'NUEVO' ? ' · ' + etiquetaCondicion(u.condicion) : ''} · ${formatoDinero(u.precioVenta)}</button>`).join('')}
        </div>` : ''}
    </div>`;
  }).join('');

  cont.querySelectorAll('.orden-item').forEach(el => {
    const p = productos.find(x => x.codpro === el.dataset.codpro);
    if (p.esEquipo) {
      el.querySelectorAll('.pv-agregar-imei').forEach(btn => btn.onclick = (ev) => {
        ev.stopPropagation();
        const unidad = p.imeisDisponibles.find(u => u.imei === btn.dataset.imei);
        agregarAlCarritoPOS(p, 1, unidad.precioVenta, unidad.imei, unidad.condicion);
      });
    } else {
      el.onclick = () => agregarAlCarritoPOS(p, 1, p.precioFinal ?? p.preciopub, null);
    }
  });
}

function agregarAlCarritoPOS(p, cantidad, precio, imei, condicion) {
  if (imei && posCarrito.some(i => i.imei === imei)) { mostrarMensaje(root, 'Ese equipo ya está en el carrito.', 'info'); return; }
  const existente = p.esEquipo ? null : posCarrito.find(i => i.codpro === p.codpro);
  if (existente) {
    if (p.tipo === 'ACCESORIO' && existente.cantidad + cantidad > Number(p.stock)) { mostrarMensaje(root, 'No hay más unidades disponibles.', 'info'); return; }
    existente.cantidad += cantidad;
  } else {
    posCarrito.push({
      idprodmaster: p.idProductoMaster, codpro: p.esEquipo ? null : p.codpro, nombre: p.nombreProductoMaster, tipo: p.tipo,
      esEquipo: !!p.esEquipo, idCategoria: p.idCategoria, cantidad, precio: Number(precio), imei: imei || null, condicion: condicion || null,
      stockMax: p.tipo === 'ACCESORIO' ? Number(p.stock) : null,
      formaPago: 'Contado', enganche: 0, plazoMeses: 12, metodoEnganche: 1,
      sol: null, pidiendo: false, piezasPromo: 0, descuentoPromo: 0, promoTexto: null,
    });
  }
  actualizarCarritoPOS();
}

/** Los cambios que no necesitan volver a dibujar los renglones: el total, lo que se debe en efectivo y el cambio. */
function actualizarTotalesPOS() {
  const contTotal = document.getElementById('pv-total');
  if (!contTotal) return;
  const t = posTotales();
  const piezas = posCarrito.reduce((sum, i) => sum + i.cantidad, 0);
  const pill = document.getElementById('pv-items');
  if (pill) pill.textContent = `${piezas} ${piezas === 1 ? 'item' : 'items'}`;
  const hayPayjoy = t.enganches > 0 || posCarrito.some(i => i.formaPago === 'PayJoy');
  contTotal.innerHTML = `<span class="bt-detalle">${hayPayjoy ? `Contado ${formatoDinero(t.contado)} · Enganches ${formatoDinero(t.enganches)}` : `${piezas} ${piezas === 1 ? 'artículo' : 'artículos'}`}</span><span class="bt-total"><small>${hayPayjoy ? 'Total hoy' : 'Total'}</small> ${formatoDinero(t.total)}</span>`;
  document.getElementById('pv-efectivo-cont')?.classList.toggle('oculto', t.efectivo <= 0.004);
  actualizarCambioPOS();
}

function actualizarCarritoPOS() {
  const cont = document.getElementById('pv-carrito');
  if (!cont) return;
  // Un descuento aprobado (o pedido) es para cierta cantidad de piezas: si ésta cambia, hay que pedirlo de nuevo
  posCarrito.forEach(i => { if (i.sol && i.cantidad !== i.sol.cantidad) posReiniciarSolicitud(i, true); });
  posAplicarPromocion();
  if (posCarrito.length === 0) { cont.innerHTML = '<div class="vacio">Agrega productos desde el catálogo.</div>'; }
  else {
    const metodosEnganche = [[1, 'Efectivo'], [3, 'Transferencia']].concat(posConfig.aceptaTarjeta ? [[2, 'Tarjeta']] : []);
    cont.innerHTML = posCarrito.map((i, idx) => {
      const payjoy = i.formaPago === 'PayJoy';
      const sol = i.sol;
      const precioMostrado = posPrecioSinPromo(i);
      return `
      <div class="orden-item" data-idx="${idx}">
        <div class="orden-cab">
          <span class="orden-folio">${escapar(i.nombre)}</span>
          <button class="btn btn-rojo btn-chico pv-quitar" data-idx="${idx}">Quitar</button>
        </div>
        ${i.imei ? `<div class="orden-cliente">IMEI: ${escapar(i.imei)}${i.condicion && i.condicion !== 'NUEVO' ? ` · <span class="pastilla reparacion">${escapar(etiquetaCondicion(i.condicion))}</span>` : ''}</div>` : ''}
        ${i.promoTexto ? `<div class="orden-cliente" style="color:var(--verde); font-weight:600;">${ic('gift')} ${escapar(i.promoTexto)}</div>` : ''}
        ${i.esEquipo ? `
        <div class="segmentado">
          <button type="button" class="pv-forma ${!payjoy ? 'activo' : ''}" data-idx="${idx}" data-forma="Contado">Al contado</button>
          <button type="button" class="pv-forma ${payjoy ? 'activo' : ''}" data-idx="${idx}" data-forma="PayJoy">A crédito (PayJoy)</button>
        </div>` : ''}
        ${payjoy ? `
        <div class="fila" style="align-items:flex-end;">
          <div><label style="margin:0 0 2px 0;">Enganche</label><input type="number" inputmode="decimal" min="0" step="1" class="pv-enganche" data-idx="${idx}" value="${i.enganche || ''}" placeholder="$"></div>
          <div><label style="margin:0 0 2px 0;">Plazo</label>
            <select class="pv-plazo" data-idx="${idx}">${PLAZOS_PAYJOY.map(m => `<option value="${m}" ${m === i.plazoMeses ? 'selected' : ''}>${m} meses</option>`).join('')}</select></div>
        </div>
        <div class="fila">
          <div><label style="margin:6px 0 2px 0;">El enganche se paga con</label>
            <select class="pv-met-enganche" data-idx="${idx}">${metodosEnganche.map(([v, n]) => `<option value="${v}" ${v === i.metodoEnganche ? 'selected' : ''}>${n}</option>`).join('')}</select></div>
        </div>` : ''}
        <div class="fila" style="margin-top:8px; align-items:center;">
          ${!i.esEquipo ? `<div><label style="margin:0 0 2px 0;">Cantidad</label><input type="number" min="1" ${i.stockMax ? 'max="' + i.stockMax + '"' : ''} value="${i.cantidad}" class="pv-cantidad" data-idx="${idx}"></div>` : ''}
          <div><label style="margin:0 0 2px 0;">Precio unitario</label><div class="precio-fijo" title="El precio lo define Inventario; no se cambia al vender">${ic('lock')} ${formatoDinero(precioMostrado)}</div></div>
        </div>
        <div class="orden-meta"><span class="ayuda" style="margin:0;">${payjoy ? `Precio ${formatoDinero(posImporte(i))} · hoy el enganche` : `${i.cantidad} × ${formatoDinero(precioMostrado)}`}</span><span class="importe-linea">${formatoDinero(posCobroHoy(i))}</span></div>
        ${!sol && !i.pidiendo ? `<button class="btn btn-gris btn-chico pv-pedir" data-idx="${idx}" style="margin-top:8px;">${ic('tags')} Pedir descuento</button>` : ''}
        ${i.pidiendo ? `
        <div class="mensaje info" style="display:block;">
          <label style="margin:0 0 2px 0;">Descuento por pieza ($)</label>
          <input type="number" inputmode="decimal" min="0" step="1" class="pv-sol-monto" data-idx="${idx}">
          <label style="margin:6px 0 2px 0;">¿Por qué se pide? (obligatorio)</label>
          <input class="pv-sol-motivo" data-idx="${idx}" maxlength="200">
          <div class="ayuda">Un administrador recibe el aviso y la aprueba o la rechaza; si la aprueba, la verás aquí y se aplica al cobrar.</div>
          <div class="grupo-botones"><button class="btn btn-azul btn-chico pv-sol-enviar" data-idx="${idx}">Enviar solicitud</button><button class="btn btn-gris btn-chico pv-sol-cancelar" data-idx="${idx}">Cancelar</button></div>
        </div>` : ''}
        ${sol && sol.estado === 0 ? `<div class="mensaje info" style="display:block;">${ic('hourglass')} Esperando respuesta del administrador: −${formatoDinero(sol.monto)} c/u. <button class="enlace pv-sol-retirar" data-idx="${idx}">Cancelar solicitud</button></div>` : ''}
        ${sol && sol.estado === 1 ? `<div class="mensaje ok" style="display:block;">${ic('circle-check')} Descuento aprobado: −${formatoDinero(sol.monto)} c/u. <button class="enlace pv-sol-retirar" data-idx="${idx}">Quitar</button></div>` : ''}
        ${sol && sol.estado === 2 ? `<div class="mensaje error" style="display:block;">${ic('circle-x')} Descuento rechazado${sol.motivo ? ': ' + escapar(sol.motivo) : ''}. <button class="enlace pv-sol-retirar" data-idx="${idx}">Pedir otro</button></div>` : ''}
      </div>`;
    }).join('');

    const item = (el) => posCarrito[Number(el.dataset.idx)];
    cont.querySelectorAll('.pv-quitar').forEach(b => b.onclick = () => { const i = item(b); posReiniciarSolicitud(i, true); posCarrito.splice(Number(b.dataset.idx), 1); actualizarCarritoPOS(); });
    cont.querySelectorAll('.pv-cantidad').forEach(inp => inp.onchange = () => {
      const i = item(inp);
      let v = Math.max(1, Math.floor(Number(inp.value)) || 1);
      if (i.stockMax) v = Math.min(v, i.stockMax);
      i.cantidad = v;
      actualizarCarritoPOS();
    });
    cont.querySelectorAll('.pv-forma').forEach(b => b.onclick = () => { item(b).formaPago = b.dataset.forma; actualizarCarritoPOS(); });
    cont.querySelectorAll('.pv-enganche').forEach(inp => inp.oninput = () => {
      item(inp).enganche = Number(inp.value) || 0;
      actualizarTotalesPOS();
      // el importe del renglón también cambia: se actualiza sin volver a dibujar (para no perder el cursor)
      const fila = inp.closest('.orden-item'); const imp = fila?.querySelector('.importe-linea'); if (imp) imp.textContent = formatoDinero(posCobroHoy(item(inp)));
    });
    cont.querySelectorAll('.pv-plazo').forEach(sel => sel.onchange = () => { item(sel).plazoMeses = Number(sel.value); });
    cont.querySelectorAll('.pv-met-enganche').forEach(sel => sel.onchange = () => { item(sel).metodoEnganche = Number(sel.value); actualizarTotalesPOS(); });
    cont.querySelectorAll('.pv-pedir').forEach(b => b.onclick = () => { item(b).pidiendo = true; actualizarCarritoPOS(); });
    cont.querySelectorAll('.pv-sol-cancelar').forEach(b => b.onclick = () => { item(b).pidiendo = false; actualizarCarritoPOS(); });
    cont.querySelectorAll('.pv-sol-retirar').forEach(b => b.onclick = () => { posReiniciarSolicitud(item(b), true); actualizarCarritoPOS(); });
    cont.querySelectorAll('.pv-sol-enviar').forEach(b => b.onclick = async () => {
      const i = item(b);
      const fila = b.closest('.orden-item');
      const monto = Number(fila.querySelector('.pv-sol-monto').value);
      const motivo = fila.querySelector('.pv-sol-motivo').value.trim();
      if (!(monto > 0) || monto >= i.precio) { mostrarMensaje(root, 'Indica un descuento mayor a $0 y menor al precio del artículo.', 'error'); return; }
      if (!motivo) { mostrarMensaje(root, 'Escribe el motivo del descuento.', 'error'); return; }
      b.disabled = true;
      try { await posEnviarSolicitud(i, monto, motivo, Sesion.obtener().codti); mostrarMensaje(root, 'Solicitud enviada: un administrador la revisará.', 'ok'); }
      catch (err) { mostrarMensaje(root, err.network ? 'Los descuentos se piden a un administrador y para eso hace falta conexión.' : err.message, 'error'); b.disabled = false; return; }
      actualizarCarritoPOS();
    });
  }
  actualizarTotalesPOS();
}

function actualizarCambioPOS() {
  const inp = document.getElementById('pv-recibido');
  const contCambio = document.getElementById('pv-cambio');
  if (!inp || !contCambio) return;
  const efectivo = posTotales().efectivo;
  const recibido = Number(inp.value) || 0;
  contCambio.textContent = recibido > 0 ? (recibido >= efectivo ? 'Cambio: ' + formatoDinero(recibido - efectivo) : 'Faltan ' + formatoDinero(efectivo - recibido)) : (efectivo > 0 ? 'Por cobrar en efectivo: ' + formatoDinero(efectivo) : '');
}
