/**
 * Skycel · Web para celular y tablet. Una sola página con ruteo por hash (#/...). Usa las mismas rutas de la
 * API que JSystem, con JWT en localStorage.
 *
 * Cubre recepción de equipos y taller (con cola sin conexión), consulta de órdenes, inventario, caja, empleados,
 * punto de venta, clientes, cuentas por cobrar, garantías, reportes, devoluciones y traspasos. Lo que necesita pago mixto,
 * venta a crédito o cambio de producto por otro sigue en JSystem.
 */

const PERSONAL = ['ROOT', 'ADMIN', 'ENCARGADO_TIENDA', 'VENDEDOR']; // puede recibir equipos, ver/crear clientes, garantías
const TALLER_ROLES = ['ROOT', 'ADMIN', 'TECNICO'];                  // puede trabajar el taller
const SUPERIOR = ['ROOT', 'ADMIN'];                                  // ve todas las tiendas y asigna técnico
const GESTOR_ROLES = ['ROOT', 'ADMIN', 'ENCARGADO_TIENDA'];          // cuentas por cobrar y reportes de ventas

const root = document.getElementById('root');
const encabezado = document.getElementById('encabezado');
const navInferior = document.getElementById('nav-inferior');
const sidebar = document.getElementById('sidebar');
const usuarioActualEl = document.getElementById('usuario-actual');
const barraConexion = document.getElementById('barra-conexion');

// ── Utilidades ──────────────────────────────────────────────────────────────

function escapar(s) {
  if (s === null || s === undefined) return '';
  return String(s).replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
}
function navegar(hash) { location.hash = hash; }
function formatoFecha(iso) {
  if (!iso) return '';
  const d = new Date(iso);
  if (isNaN(d.getTime())) return iso;
  return d.toLocaleDateString('es-MX', { day: '2-digit', month: 'short' }) + ' ' +
    d.toLocaleTimeString('es-MX', { hour: '2-digit', minute: '2-digit' });
}
function formatoDinero(n) {
  const v = Number(n || 0);
  return '$' + v.toLocaleString('es-MX', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}
/** Fecha/hora local del dispositivo en el formato que espera el backend (sin 'Z': es hora local, no UTC). */
function fechaLocalISO(d) {
  const pad = n => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`;
}
function uuid() {
  if (crypto.randomUUID) return crypto.randomUUID();
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, c => {
    const r = Math.random() * 16 | 0, v = c === 'x' ? r : (r & 0x3 | 0x8);
    return v.toString(16);
  });
}
function pastillaEstado(estadoDisplay) {
  const claves = { 'Recibida': 'recibida', 'En reparación': 'reparacion', 'Lista para entrega': 'lista', 'Entregada': 'entregada', 'Cancelada': 'cancelada' };
  const c = claves[estadoDisplay] || 'recibida';
  return `<span class="pastilla ${c}">${escapar(estadoDisplay)}</span>`;
}

let mensajeTimeout = null;
function mostrarMensaje(contenedor, texto, tipo) {
  const div = contenedor.querySelector('.mensaje-flotante') || (() => {
    const d = document.createElement('div');
    d.className = 'mensaje-flotante';
    contenedor.prepend(d);
    return d;
  })();
  div.innerHTML = `<div class="mensaje ${tipo}">${escapar(texto)}</div>`;
  clearTimeout(mensajeTimeout);
  if (tipo !== 'error') mensajeTimeout = setTimeout(() => { div.innerHTML = ''; }, 4000);
}

// ── Encabezado / barra de conexión ───────────────────────────────────────────

function actualizarBarraConexion() {
  const s = Sesion.obtener();
  if (!s) { barraConexion.className = 'oculto'; return; }
  Net.cantidadPendiente().then(pend => {
    Net.cantidadConError().then(err => {
      if (Net.enLinea()) {
        barraConexion.className = pend > 0 ? 'enviando' : 'en-linea';
        barraConexion.textContent = pend > 0 ? `Enviando ${pend} recepción(es) guardada(s)...` : '';
      } else {
        barraConexion.className = '';
        barraConexion.textContent = `Sin conexión — se guarda en este dispositivo${pend > 0 ? ' · ' + pend + ' por enviar' : ''}`;
      }
      if (err > 0) barraConexion.textContent += `  ·  ${ic('triangle-alert')} ${err} para revisar`;
      barraConexion.style.display = (Net.enLinea() && pend === 0 && err === 0) ? 'none' : 'block';
      barraConexion.onclick = () => navegar('#/pendientes');
      barraConexion.style.cursor = 'pointer';
    });
  });
}
Net.agregarOyente(actualizarBarraConexion);

async function actualizarEncabezado() {
  const s = Sesion.obtener();
  if (!s) { encabezado.classList.add('oculto'); navInferior.classList.add('oculto'); sidebar.classList.add('oculto'); return; }
  encabezado.classList.remove('oculto');
  navInferior.classList.remove('oculto');
  sidebar.classList.remove('oculto');
  usuarioActualEl.textContent = `${s.nombreCompleto || s.username} · ${etiquetaRol(s.rol)}`;
  dibujarNavInferior();
  await dibujarSidebar();
}
function etiquetaRol(rol) {
  return ({ ROOT: 'Administrador', ADMIN: 'Administrador', ENCARGADO_TIENDA: 'Encargado', VENDEDOR: 'Vendedor', TECNICO: 'Técnico' })[rol] || rol;
}

function dibujarNavInferior() {
  const s = Sesion.obtener();
  if (!s) return;
  const ruta = location.hash.split('/')[1] || 'menu';
  const items = [{ h: '#/menu', i: ic('house'), t: 'Inicio', clave: 'menu' }];
  if (PERSONAL.includes(s.rol)) items.push({ h: '#/recepcion', i: ic('inbox'), t: 'Recibir', clave: 'recepcion' });
  if (TALLER_ROLES.includes(s.rol)) items.push({ h: '#/taller', i: ic('wrench'), t: 'Taller', clave: 'taller' });
  items.push({ h: '#/consultar', i: ic('search'), t: 'Buscar', clave: 'consultar' });
  navInferior.innerHTML = items.map(it =>
    `<button data-h="${it.h}" class="${ruta === it.clave ? 'activo' : ''}"><span class="icono">${it.i}</span>${it.t}</button>`
  ).join('');
  navInferior.querySelectorAll('button').forEach(b => b.onclick = () => navegar(b.dataset.h));
}

/** Cuenta de productos bajo stock para el badge de Inventario — se cachea 60s para no pedirla en cada navegación. */
let bajoStockCache = { codti: null, valor: null, expira: 0 };
async function obtenerBajoStock(codti) {
  if (codti == null) return null;
  if (bajoStockCache.codti === codti && Date.now() < bajoStockCache.expira) return bajoStockCache.valor;
  try {
    const productos = await api('GET', `/api/productos/tienda/${codti}/bajo-stock`);
    bajoStockCache = { codti, valor: productos.length, expira: Date.now() + 60000 };
  } catch { /* se deja el valor cacheado anterior (o null) si falla */ }
  return bajoStockCache.codti === codti ? bajoStockCache.valor : null;
}

/**
 * Lista completa de módulos según el rol, agrupada por área de trabajo — la usan tanto la pantalla de
 * Inicio (celular) como la barra lateral (escritorio). `grupo: null` deja el ítem sin encabezado de grupo.
 */
function itemsMenu(s, pend, err, bajoStock) {
  const items = [];
  if (PERSONAL.includes(s.rol)) items.push({ h: '#/recepcion', i: ic('inbox'), t: 'Recibir equipo', grupo: 'Taller' });
  if (TALLER_ROLES.includes(s.rol)) items.push({ h: '#/taller', i: ic('wrench'), t: 'Taller', grupo: 'Taller' });
  items.push({ h: '#/consultar', i: ic('search'), t: 'Consultar orden', grupo: 'Taller' });
  if (PERSONAL.includes(s.rol)) items.push({ h: '#/pos', i: ic('shopping-cart'), t: 'Punto de venta', grupo: 'Ventas' });
  items.push({ h: '#/inventario', i: ic('package'), t: 'Inventario', badge: bajoStock > 0 ? bajoStock : null, grupo: 'Ventas' });
  if (GESTOR_ROLES.includes(s.rol)) items.push({ h: '#/traspasos', i: ic('truck'), t: 'Traspasos', grupo: 'Ventas' });
  if (PERSONAL.includes(s.rol)) items.push({ h: '#/devoluciones', i: ic('undo-2'), t: 'Devoluciones', grupo: 'Ventas' });
  if (GESTOR_ROLES.includes(s.rol)) items.push({ h: '#/caja', i: ic('receipt'), t: 'Caja', grupo: 'Finanzas' });
  if (GESTOR_ROLES.includes(s.rol)) items.push({ h: '#/cxc', i: ic('credit-card'), t: 'Cuentas por cobrar', grupo: 'Finanzas' });
  if (GESTOR_ROLES.includes(s.rol)) items.push({ h: '#/reportes', i: ic('chart-column'), t: 'Reporte de ventas', grupo: 'Finanzas' });
  items.push({ h: '#/clientes', i: ic('users'), t: 'Clientes', grupo: 'Clientes' });
  if (PERSONAL.includes(s.rol)) items.push({ h: '#/garantias', i: ic('shield-check'), t: 'Garantías', grupo: 'Clientes' });
  if (SUPERIOR.includes(s.rol)) items.push({ h: '#/empleados', i: ic('briefcase'), t: 'Empleados', grupo: 'Administración' });
  items.push({ h: '#/pendientes', i: ic('cloud-upload'), t: 'Guardado en el equipo', badge: (pend + err) > 0 ? (pend + err) : null, grupo: null });
  return items;
}

/** Barra lateral de escritorio: los mismos módulos que la pantalla de Inicio, agrupados, siempre visibles (CSS la oculta en celular/tablet). */
async function dibujarSidebar() {
  const s = Sesion.obtener();
  if (!s) return;
  const pend = await Net.cantidadPendiente();
  const err = await Net.cantidadConError();
  const bajoStock = await obtenerBajoStock(s.codti);
  const items = itemsMenu(s, pend, err, bajoStock);
  const ruta = location.hash.split('/')[1] || 'menu';
  let grupoAnterior;
  const filas = items.map(it => {
    const encabezadoGrupo = it.grupo !== grupoAnterior
      ? (grupoAnterior = it.grupo, it.grupo ? `<div class="sidebar-grupo">${escapar(it.grupo)}</div>` : '<div class="sidebar-separador"></div>')
      : '';
    return encabezadoGrupo + `
      <button data-h="${it.h}" class="g-${claseGrupo(it.grupo)} ${ruta === it.h.slice(2) ? 'activo' : ''}">
        <span class="icono">${it.i}</span><span class="texto">${escapar(it.t)}</span>
        ${it.badge ? `<span class="badge">${it.badge}</span>` : ''}
      </button>`;
  }).join('');
  sidebar.innerHTML = `
    <div class="sidebar-marca" id="sidebar-inicio"><span class="logo">${ic('smartphone')}</span> Skycel</div>
    <div class="sidebar-items">${filas}</div>`;
  sidebar.querySelectorAll('button[data-h]').forEach(b => b.onclick = () => navegar(b.dataset.h));
  document.getElementById('sidebar-inicio').onclick = () => navegar('#/menu');
}

document.getElementById('btn-salir').onclick = () => {
  if (!confirm('¿Cerrar sesión?')) return;
  Sesion.limpiar();
  navegar('#/login');
};
document.getElementById('btn-ajustes').onclick = () => navegar('#/ajustes');
document.getElementById('btn-notificaciones').onclick = () => navegar('#/notificaciones');

// ── Router ────────────────────────────────────────────────────────────────

async function render() {
  const s = Sesion.obtener();
  const hash = location.hash || (s ? '#/menu' : '#/login');
  const [, ruta, param] = hash.split('/');

  if (!s && ruta !== 'login') { navegar('#/login'); return; }
  if (s && ruta === 'login') { navegar('#/menu'); return; }

  await actualizarEncabezado();
  actualizarBarraConexion();

  try {
    if (ruta === 'login') return pantallaLogin();
    if (ruta === 'menu') return pantallaMenu();
    if (ruta === 'recepcion') return pantallaRecepcion();
    if (ruta === 'taller') return pantallaTaller();
    if (ruta === 'consultar') return pantallaConsultar();
    if (ruta === 'pendientes') return pantallaPendientes();
    if (ruta === 'orden' && param) return pantallaOrdenDetalle(param);
    if (ruta === 'clientes') return pantallaClientes();
    if (ruta === 'cliente' && param) return pantallaClienteDetalle(param);
    if (ruta === 'cxc') return pantallaCxC();
    if (ruta === 'cuenta' && param) return pantallaCuentaDetalle(param);
    if (ruta === 'garantias') return pantallaGarantias();
    if (ruta === 'garantia' && param) return pantallaGarantiaDetalle(param);
    if (ruta === 'reportes') return pantallaReportes();
    if (ruta === 'inventario') return pantallaInventario();
    if (ruta === 'producto' && param) return pantallaProductoDetalle(param);
    if (ruta === 'producto-editar' && param) return pantallaProductoEditar(param);
    if (ruta === 'caja') return pantallaCaja();
    if (ruta === 'empleados') return pantallaEmpleados();
    if (ruta === 'empleado' && param) return pantallaEmpleadoDetalle(param);
    if (ruta === 'pos') return pantallaPOS();
    if (ruta === 'traspasos') return pantallaTraspasos();
    if (ruta === 'traspaso' && param) return pantallaTraspasoDetalle(param);
    if (ruta === 'traspaso-nuevo') return pantallaTraspasoNuevo(param);
    if (ruta === 'faltantes') return pantallaFaltantes();
    if (ruta === 'devoluciones') return pantallaDevoluciones();
    if (ruta === 'devolucion' && param) return pantallaDevolucionDetalle(param);
    if (ruta === 'ajustes') return pantallaAjustes();
    if (ruta === 'notificaciones') return pantallaNotificaciones();
    if (ruta === 'catalogos') return pantallaCatalogos();
    navegar('#/menu');
  } catch (err) {
    root.innerHTML = `<div class="tarjeta"><div class="mensaje error">${escapar(err.message || 'Ocurrió un error')}</div>
      <button class="btn btn-gris" onclick="navegar('#/menu')">Ir al inicio</button></div>`;
  }
}
window.addEventListener('hashchange', render);
window.navegar = navegar;

// ── Login ─────────────────────────────────────────────────────────────────

function pantallaLogin() {
  root.innerHTML = `
    <div class="pantalla-centrada">
      <div class="login-marca">
        <div class="login-logo">${ic('smartphone')}</div>
        <h1>Skycel</h1>
        <div class="ayuda">Recepción de equipos y taller</div>
      </div>
      <div class="tarjeta">
        <label class="obligatorio">Usuario</label>
        <input id="li-user" autocomplete="username" autocapitalize="off">
        <label class="obligatorio">Contraseña</label>
        <input id="li-pass" type="password" autocomplete="current-password">
        <button id="li-btn" class="btn btn-azul">Entrar</button>
      </div>
    </div>`;
  const btn = document.getElementById('li-btn');
  const user = document.getElementById('li-user');
  const pass = document.getElementById('li-pass');
  const intentar = async () => {
    if (!user.value.trim() || !pass.value) return;
    btn.disabled = true;
    btn.innerHTML = '<span class="spinner"></span> Entrando...';
    try {
      const r = await api('POST', '/api/auth/login', { username: user.value.trim(), password: pass.value });
      Sesion.guardar({
        token: r.token, idusuario: r.idusuario, username: r.username, nombreCompleto: r.nombreCompleto,
        rol: r.rol, codti: r.codti, tecnicoEncargado: r.tecnicoEncargado,
      });
      navegar('#/menu');
      actualizarBadgeNotificaciones();
    } catch (err) {
      mostrarMensaje(root, err.network ? 'Sin conexión con el servidor.' : (err.message || 'Usuario o contraseña incorrectos'), 'error');
      btn.disabled = false;
      btn.textContent = 'Entrar';
    }
  };
  btn.onclick = intentar;
  pass.addEventListener('keydown', e => { if (e.key === 'Enter') intentar(); });
}

// ── Menú ──────────────────────────────────────────────────────────────────

/** Clase de color por área de trabajo (los iconos del menú y de la barra lateral se tiñen igual). */
function claseGrupo(g) {
  return ({ 'Taller': 'taller', 'Ventas': 'ventas', 'Finanzas': 'finanzas', 'Clientes': 'clientes', 'Administración': 'admin' })[g] || 'otro';
}
/** Agrupa las tarjetas del menú por área conservando el orden; las sueltas (sin grupo) van al final sin título. */
function gruposDeMenu(items) {
  const grupos = [];
  items.forEach(it => {
    const nombre = it.grupo || '';
    let g = grupos.find(x => x.nombre === nombre);
    if (!g) grupos.push(g = { nombre, items: [] });
    g.items.push(it);
  });
  return grupos;
}

async function pantallaMenu() {
  const s = Sesion.obtener();
  const pend = await Net.cantidadPendiente();
  const err = await Net.cantidadConError();
  const bajoStock = await obtenerBajoStock(s.codti);
  const tarjetas = itemsMenu(s, pend, err, bajoStock);

  root.innerHTML = `
    <h1 class="saludo">Hola, ${escapar((s.nombreCompleto || s.username).split(' ')[0])}</h1>
    ${gruposDeMenu(tarjetas).map(g => `
      ${g.nombre ? `<h2 class="menu-grupo">${escapar(g.nombre)}</h2>` : ''}
      <div class="rejilla-menu">
        ${g.items.map(t => `
          <div class="boton-menu g-${claseGrupo(t.grupo)}" data-h="${t.h}">
            <span class="icono">${t.i}</span>
            <span class="texto">${t.t}</span>
            ${t.badge ? `<span class="badge">${t.badge}</span>` : ''}
          </div>`).join('')}
      </div>`).join('')}
    <div class="ayuda" style="text-align:center; margin-top:20px;">
      Para pagos mixtos, ventas a crédito y cambios de producto, usa JSystem en la tienda.
    </div>`;
  root.querySelectorAll('.boton-menu').forEach(b => b.onclick = () => navegar(b.dataset.h));
}

// ── Recepción de equipo ──────────────────────────────────────────────────

async function pantallaRecepcion() {
  const s = Sesion.obtener();
  if (!PERSONAL.includes(s.rol)) { root.innerHTML = '<div class="tarjeta">No tienes permiso para recibir equipos.</div>'; return; }

  root.innerHTML = `
    <h1>${ic('inbox')} Recibir equipo</h1>
    <div class="tarjeta">
      <div id="rc-tienda"></div>

      <h2 style="margin-top:4px;">Cliente</h2>
      <div class="segmentado">
        <button id="rc-seg-existente" class="activo">Ya es cliente</button>
        <button id="rc-seg-nuevo">Cliente nuevo</button>
      </div>
      <div id="rc-cliente-existente">
        <label class="obligatorio">Teléfono</label>
        <div class="fila">
          <input id="rc-tel-buscar" inputmode="tel" placeholder="10 dígitos">
          <button id="rc-buscar-btn" class="btn btn-azul btn-chico" style="flex:0 0 auto;">Buscar</button>
        </div>
        <div id="rc-cliente-encontrado" class="ayuda"></div>
      </div>
      <div id="rc-cliente-nuevo" class="oculto">
        <label class="obligatorio">Nombre completo</label>
        <input id="rc-cli-nombre">
        <label class="obligatorio">Teléfono</label>
        <input id="rc-cli-tel" inputmode="tel">
      </div>

      <h2 style="margin-top:18px;">Equipo</h2>
      <div class="fila">
        <div>
          <label class="obligatorio">Marca</label>
          <input id="rc-marca" placeholder="Samsung, Apple...">
        </div>
        <div>
          <label class="obligatorio">Modelo</label>
          <input id="rc-modelo" placeholder="A54, iPhone 13...">
        </div>
      </div>
      <label>IMEI o serie</label>
      <input id="rc-imei" placeholder="Opcional">
      <label>Accesorios que deja</label>
      <input id="rc-accesorios" placeholder="Chip, funda, cargador... (opcional)">
      <label>Estado físico</label>
      <textarea id="rc-estado-fisico" placeholder="Golpes, rayones, daños visibles (opcional)"></textarea>
      <label class="obligatorio">Falla reportada</label>
      <textarea id="rc-falla" placeholder="Lo que dice el cliente que tiene"></textarea>
      <label>Fecha prometida</label>
      <input id="rc-fecha-promesa" type="date">
      <div id="rc-tecnico-cont"></div>

      <h2 style="margin-top:18px;">Anticipo</h2>
      <label style="display:flex; align-items:center; gap:8px; font-weight:400; text-transform:none;">
        <input type="checkbox" id="rc-anticipo-chk" style="width:auto;"> El cliente deja un anticipo
      </label>
      <div id="rc-anticipo-cont" class="oculto">
        <label class="obligatorio">Monto</label>
        <input id="rc-anticipo-monto" type="number" inputmode="decimal" min="0" step="0.01">
        <label class="obligatorio">Método</label>
        <select id="rc-anticipo-metodo">
          <option value="1">Efectivo</option>
          <option value="2">Tarjeta</option>
          <option value="3">Transferencia</option>
        </select>
        <div id="rc-anticipo-folio-cont" class="oculto">
          <label>Folio del voucher / rastreo</label>
          <input id="rc-anticipo-folio">
        </div>
      </div>

      <button id="rc-btn" class="btn btn-verde">Recibir equipo</button>
    </div>`;

  // Tienda
  const contTienda = document.getElementById('rc-tienda');
  let tiendaSel = s.codti;
  if (SUPERIOR.includes(s.rol)) {
    contTienda.innerHTML = `<label class="obligatorio">Sucursal</label><select id="rc-codti"><option>Cargando...</option></select>`;
    try {
      const tiendas = await api('GET', '/api/tiendas');
      const sel = document.getElementById('rc-codti');
      sel.innerHTML = tiendas.map(t => `<option value="${t.codti}">${escapar(t.nombre)}</option>`).join('');
      sel.onchange = () => { tiendaSel = Number(sel.value); cargarTecnicos(); };
      tiendaSel = tiendas[0]?.codti ?? s.codti;
    } catch {
      contTienda.innerHTML = `<div class="mensaje info">No se pudo cargar la lista de sucursales (sin conexión). Se recibirá en tu sucursal.</div>`;
      tiendaSel = s.codti;
    }
  } else {
    contTienda.innerHTML = `<div class="ayuda">Sucursal: <strong>tu tienda</strong></div>`;
  }

  // Técnico (solo ROOT/ADMIN pueden asignarlo al recibir)
  const contTecnico = document.getElementById('rc-tecnico-cont');
  async function cargarTecnicos() {
    if (!SUPERIOR.includes(s.rol)) return;
    contTecnico.innerHTML = `<label>Técnico asignado</label><select id="rc-tecnico"><option value="">Sin asignar (lo asigna el técnico encargado)</option></select>`;
    try {
      const tecnicos = await api('GET', '/api/usuarios/tecnicos' + (tiendaSel ? '?codti=' + tiendaSel : ''));
      const sel = document.getElementById('rc-tecnico');
      for (const t of tecnicos) sel.insertAdjacentHTML('beforeend', `<option value="${t.idusuario}">${escapar(t.nombreCompleto)}${t.tecnicoEncargado ? ' (encargado)' : ''}</option>`);
    } catch { /* sin conexión: se deja sin asignar */ }
  }
  cargarTecnicos();

  // Cliente: existente / nuevo
  const segExistente = document.getElementById('rc-seg-existente');
  const segNuevo = document.getElementById('rc-seg-nuevo');
  const bloqueExistente = document.getElementById('rc-cliente-existente');
  const bloqueNuevo = document.getElementById('rc-cliente-nuevo');
  let clienteEncontrado = null;
  segExistente.onclick = () => { segExistente.classList.add('activo'); segNuevo.classList.remove('activo'); bloqueExistente.classList.remove('oculto'); bloqueNuevo.classList.add('oculto'); };
  segNuevo.onclick = () => { segNuevo.classList.add('activo'); segExistente.classList.remove('activo'); bloqueNuevo.classList.remove('oculto'); bloqueExistente.classList.add('oculto'); clienteEncontrado = null; };

  document.getElementById('rc-buscar-btn').onclick = async () => {
    const tel = document.getElementById('rc-tel-buscar').value.trim();
    const out = document.getElementById('rc-cliente-encontrado');
    if (!tel) return;
    out.textContent = 'Buscando...';
    try {
      const c = await api('GET', '/api/clientes/telefono/' + encodeURIComponent(tel));
      clienteEncontrado = c;
      out.innerHTML = `${ic('circle-check')} <strong>${escapar(c.nombreCompleto)}</strong>`;
    } catch (err) {
      clienteEncontrado = null;
      out.innerHTML = err.network
        ? ic('circle-alert') + ' Sin conexión: pasa a "Cliente nuevo" y captura nombre y teléfono; se enlazará al enviarse.'
        : `${ic('triangle-alert')} No se encontró. Usa "Cliente nuevo".`;
    }
  };

  // Anticipo
  const chkAnticipo = document.getElementById('rc-anticipo-chk');
  const contAnticipo = document.getElementById('rc-anticipo-cont');
  chkAnticipo.onchange = () => contAnticipo.classList.toggle('oculto', !chkAnticipo.checked);
  const selMetodo = document.getElementById('rc-anticipo-metodo');
  selMetodo.onchange = () => document.getElementById('rc-anticipo-folio-cont').classList.toggle('oculto', selMetodo.value === '1');

  // Enviar
  const btn = document.getElementById('rc-btn');
  btn.onclick = async () => {
    const marca = document.getElementById('rc-marca').value.trim();
    const modelo = document.getElementById('rc-modelo').value.trim();
    const falla = document.getElementById('rc-falla').value.trim();
    if (!marca || !modelo || !falla) { mostrarMensaje(root, 'Marca, modelo y falla reportada son obligatorios.', 'error'); return; }

    const esNuevo = !bloqueNuevo.classList.contains('oculto');
    let idcliente = null, clienteNombre = null, clienteTelefono = null;
    if (esNuevo) {
      clienteNombre = document.getElementById('rc-cli-nombre').value.trim();
      clienteTelefono = document.getElementById('rc-cli-tel').value.trim();
      if (!clienteNombre || !clienteTelefono) { mostrarMensaje(root, 'Captura nombre y teléfono del cliente nuevo.', 'error'); return; }
    } else {
      if (!clienteEncontrado) { mostrarMensaje(root, 'Busca al cliente por teléfono, o usa "Cliente nuevo".', 'error'); return; }
      idcliente = clienteEncontrado.idcliente;
    }

    const codti = SUPERIOR.includes(s.rol) ? Number(document.getElementById('rc-codti')?.value || tiendaSel) : undefined;
    const idTecnicoSel = document.getElementById('rc-tecnico')?.value;

    const payload = {
      codti,
      idcliente,
      clienteNombre, clienteTelefono,
      marca, modelo,
      imei: document.getElementById('rc-imei').value.trim() || null,
      accesoriosDejados: document.getElementById('rc-accesorios').value.trim() || null,
      estadoFisico: document.getElementById('rc-estado-fisico').value.trim() || null,
      fallaReportada: falla,
      fechaPromesa: document.getElementById('rc-fecha-promesa').value || null,
      idTecnico: idTecnicoSel ? Number(idTecnicoSel) : null,
      claveOffline: uuid(),
    };
    if (chkAnticipo.checked) {
      const monto = Number(document.getElementById('rc-anticipo-monto').value);
      if (!monto || monto <= 0) { mostrarMensaje(root, 'Indica el monto del anticipo.', 'error'); return; }
      payload.anticipo = {
        monto,
        metodoPago: Number(selMetodo.value),
        folioOperacion: document.getElementById('rc-anticipo-folio')?.value.trim() || null,
      };
    }

    btn.disabled = true;
    btn.innerHTML = '<span class="spinner"></span> Enviando...';
    try {
      const orden = await api('POST', '/api/ordenes-servicio', payload);
      mostrarMensaje(root, `Equipo recibido: folio ${orden.folio}`, 'ok');
      setTimeout(() => navegar('#/orden/' + orden.idorden), 700);
    } catch (err) {
      if (err.network) {
        // Sin conexión: se guarda en este dispositivo con folio y fecha provisionales
        payload.folioLocal = 'WEB-' + Math.random().toString(16).slice(2, 8).toUpperCase();
        payload.fechaRecepcion = fechaLocalISO(new Date());
        await DB.put('recepciones_pendientes', {
          clave: payload.claveOffline,
          payload,
          estado: 'pendiente',
          creado: new Date().toISOString(),
          resumen: `${marca} ${modelo}`,
          clienteResumen: esNuevo ? clienteNombre : clienteEncontrado.nombreCompleto,
        });
        actualizarBarraConexion();
        mostrarMensaje(root, `Sin conexión: se guardó en este dispositivo (folio provisional ${payload.folioLocal}). Se enviará solo cuando vuelva la conexión.`, 'info');
        setTimeout(() => navegar('#/pendientes'), 900);
      } else if (err.auth) {
        mostrarMensaje(root, 'La sesión expiró. Inicia sesión de nuevo y vuelve a recibir el equipo.', 'error');
        setTimeout(() => navegar('#/login'), 1200);
      } else {
        mostrarMensaje(root, err.message, 'error');
        btn.disabled = false;
        btn.textContent = 'Recibir equipo';
      }
    }
  };
}

// ── Taller ────────────────────────────────────────────────────────────────

async function pantallaTaller() {
  const s = Sesion.obtener();
  if (!TALLER_ROLES.includes(s.rol)) { root.innerHTML = '<div class="tarjeta">No tienes permiso para ver el taller.</div>'; return; }
  root.innerHTML = `<h1>${ic('wrench')} Taller</h1><div id="ta-lista"><div class="vacio">Cargando...</div></div>`;
  const cont = document.getElementById('ta-lista');
  try {
    const ordenes = await api('GET', '/api/ordenes-servicio/taller');
    if (ordenes.length === 0) { cont.innerHTML = '<div class="vacio">No hay órdenes abiertas.</div>'; return; }
    cont.innerHTML = ordenes.map(o => itemOrden(o, s)).join('');
    cont.querySelectorAll('.orden-item').forEach(el => el.onclick = () => navegar('#/orden/' + el.dataset.id));
  } catch (err) {
    cont.innerHTML = `<div class="mensaje error">${escapar(err.network ? 'Sin conexión con el servidor: el taller solo se puede consultar en línea.' : err.message)}</div>`;
  }
}

function itemOrden(o, s) {
  const porTomar = o.estado === 1 && !o.idTecnico;
  return `
    <div class="orden-item" data-id="${o.idorden}">
      <div class="orden-cab">
        <span class="orden-folio">${escapar(o.folio)}</span>
        ${porTomar ? '<span class="pastilla por-tomar">' + ic('hourglass') + ' Por tomar</span>' : pastillaEstado(o.estadoDisplay)}
      </div>
      <div class="orden-equipo">${escapar(o.marca)} ${escapar(o.modelo)}</div>
      <div class="orden-cliente">${escapar(o.nombreCliente)} · ${escapar(o.nombreTienda)}</div>
      <div class="orden-meta">
        <span class="orden-fecha">${formatoFecha(o.fechaIngreso)}</span>
        <span style="font-size:12px; color:var(--gris);">${o.idTecnico ? ic('user') + escapar(o.nombreTecnico) : 'Sin técnico'}</span>
      </div>
    </div>`;
}

// ── Consultar ─────────────────────────────────────────────────────────────

async function pantallaConsultar() {
  const s = Sesion.obtener();
  root.innerHTML = `
    <h1>${ic('search')} Consultar orden</h1>
    <div class="buscador">
      <input id="co-folio" placeholder="Folio (OS-000123)" autocapitalize="characters">
      <button id="co-btn" class="btn btn-azul btn-chico">Buscar</button>
    </div>
    <div id="co-resultado"></div>
    <h2 style="margin-top:18px;">${SUPERIOR.includes(s.rol) ? 'Órdenes de tu sucursal' : 'Órdenes recientes'}</h2>
    <div id="co-lista"><div class="vacio">Cargando...</div></div>`;

  const buscar = async () => {
    const folio = document.getElementById('co-folio').value.trim().toUpperCase();
    const out = document.getElementById('co-resultado');
    if (!folio) return;
    out.innerHTML = '<div class="vacio">Buscando...</div>';
    try {
      const o = await api('GET', '/api/ordenes-servicio/folio/' + encodeURIComponent(folio));
      out.innerHTML = itemOrden(o, s);
      out.querySelector('.orden-item').onclick = () => navegar('#/orden/' + o.idorden);
    } catch (err) {
      out.innerHTML = `<div class="mensaje error">${escapar(err.network ? 'Sin conexión con el servidor.' : err.message)}</div>`;
    }
  };
  document.getElementById('co-btn').onclick = buscar;
  document.getElementById('co-folio').addEventListener('keydown', e => { if (e.key === 'Enter') buscar(); });

  const cont = document.getElementById('co-lista');
  try {
    // v1: solo la propia sucursal (un ROOT/ADMIN sin sucursal fija debe buscar por folio arriba)
    const codti = s.codti;
    if (!codti) { cont.innerHTML = '<div class="vacio">Indica un folio para buscar.</div>'; return; }
    const ordenes = await api('GET', '/api/ordenes-servicio/tienda/' + codti);
    if (ordenes.length === 0) { cont.innerHTML = '<div class="vacio">No hay órdenes.</div>'; return; }
    cont.innerHTML = ordenes.slice(0, 25).map(o => itemOrden(o, s)).join('');
    cont.querySelectorAll('.orden-item').forEach(el => el.onclick = () => navegar('#/orden/' + el.dataset.id));
  } catch (err) {
    cont.innerHTML = `<div class="mensaje error">${escapar(err.network ? 'Sin conexión con el servidor.' : err.message)}</div>`;
  }
}

// ── Detalle de orden ──────────────────────────────────────────────────────

async function pantallaOrdenDetalle(id) {
  const s = Sesion.obtener();
  root.innerHTML = '<div class="vacio">Cargando...</div>';
  let o;
  try {
    o = await api('GET', '/api/ordenes-servicio/' + id);
  } catch (err) {
    root.innerHTML = `<div class="tarjeta"><div class="mensaje error">${escapar(err.network ? 'Sin conexión con el servidor.' : err.message)}</div>
      <button class="btn btn-gris" onclick="navegar('#/menu')">Ir al inicio</button></div>`;
    return;
  }

  const puedeTrabajar = TALLER_ROLES.includes(s.rol) && (SUPERIOR.includes(s.rol) || s.tecnicoEncargado || (s.rol === 'TECNICO' && o.idTecnico === s.idusuario));
  const esTecnicoLibre = s.rol === 'TECNICO' && !o.idTecnico && o.estado === 1;

  root.innerHTML = `
    <h1>${escapar(o.folio)}${o.folioLocal ? ` <span class="ayuda">(${escapar(o.folioLocal)})</span>` : ''}</h1>
    <div class="tarjeta">
      <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:6px;">
        ${pastillaEstado(o.estadoDisplay)}
        <span class="orden-fecha">${formatoFecha(o.fechaIngreso)}</span>
      </div>
      <div class="detalle-fila"><span class="k">Cliente</span><span class="v">${escapar(o.nombreCliente)}</span></div>
      <div class="detalle-fila"><span class="k">Teléfono</span><span class="v">${escapar(o.telefonoCliente || '—')}</span></div>
      <div class="detalle-fila"><span class="k">Sucursal</span><span class="v">${escapar(o.nombreTienda)}</span></div>
      <div class="detalle-fila"><span class="k">Equipo</span><span class="v">${escapar(o.marca)} ${escapar(o.modelo)}</span></div>
      <div class="detalle-fila"><span class="k">IMEI/Serie</span><span class="v">${escapar(o.imei || '—')}</span></div>
      <div class="detalle-fila"><span class="k">Accesorios</span><span class="v">${escapar(o.accesoriosDejados || '—')}</span></div>
      <div class="detalle-fila"><span class="k">Estado físico</span><span class="v">${escapar(o.estadoFisico || '—')}</span></div>
      <div class="detalle-fila"><span class="k">Falla reportada</span><span class="v">${escapar(o.fallaReportada)}</span></div>
      <div class="detalle-fila"><span class="k">Diagnóstico</span><span class="v">${escapar(o.diagnostico || '—')}</span></div>
      <div class="detalle-fila"><span class="k">Técnico</span><span class="v">${escapar(o.nombreTecnico || 'Sin asignar')}</span></div>
      <div class="detalle-fila"><span class="k">Recibió</span><span class="v">${escapar(o.nombreRecibe)}</span></div>
      <div class="detalle-fila"><span class="k">Fecha prometida</span><span class="v">${o.fechaPromesa || '—'}</span></div>
      <div class="detalle-fila"><span class="k">Total</span><span class="v">${formatoDinero(o.total)}</span></div>
      <div class="detalle-fila"><span class="k">Anticipos</span><span class="v">${formatoDinero(o.totalAnticipos)}</span></div>
      <div class="detalle-fila"><span class="k">Saldo</span><span class="v">${formatoDinero(o.saldo)}</span></div>
    </div>

    <div id="dt-acciones" class="tarjeta oculto">
      <h2>Acciones</h2>
      <div class="grupo-botones" id="dt-botones"></div>
      <div id="dt-nota-cont" class="oculto" style="margin-top:10px;">
        <label>Observación</label>
        <textarea id="dt-nota-texto"></textarea>
        <button id="dt-nota-enviar" class="btn btn-azul">Guardar observación</button>
      </div>
    </div>

    <div class="tarjeta">
      <h2>Bitácora</h2>
      ${(o.historial || []).slice().reverse().map(h => `
        <div class="historial-item">
          <div class="historial-comentario">${escapar(h.comentario)}</div>
          <div class="historial-meta">${escapar(h.nombreUsuario)} · ${formatoFecha(h.fecha)}</div>
        </div>`).join('') || '<div class="vacio">Sin movimientos.</div>'}
    </div>`;

  if (!puedeTrabajar && !esTecnicoLibre) return;

  const contAcc = document.getElementById('dt-acciones');
  const botones = document.getElementById('dt-botones');
  contAcc.classList.remove('oculto');
  const acciones = [];

  if (esTecnicoLibre) acciones.push({ t: ic('hand') + ' Tomar', color: 'btn-azul', fn: () => accionSimple(id, 'tomar', 'POST') });
  if (puedeTrabajar && o.estado === 1 && o.idTecnico) acciones.push({ t: '▶️ Iniciar reparación', color: 'btn-verde', fn: () => accionSimple(id, 'iniciar', 'POST') });
  if (puedeTrabajar && o.estado === 2) acciones.push({ t: ic('circle-check') + ' Marcar lista', color: 'btn-verde', fn: () => accionSimple(id, 'lista', 'POST') });
  if (puedeTrabajar) acciones.push({ t: ic('clipboard-pen') + ' Agregar observación', color: 'btn-gris', fn: () => document.getElementById('dt-nota-cont').classList.toggle('oculto') });

  if (acciones.length === 0) { contAcc.classList.add('oculto'); return; }
  botones.innerHTML = acciones.map((a, i) => `<button class="btn ${a.color}" data-i="${i}">${a.t}</button>`).join('');
  botones.querySelectorAll('button').forEach((b, i) => b.onclick = acciones[i].fn);

  document.getElementById('dt-nota-enviar').onclick = async () => {
    const texto = document.getElementById('dt-nota-texto').value.trim();
    if (!texto) return;
    try {
      await api('POST', `/api/ordenes-servicio/${id}/notas`, { comentario: texto });
      pantallaOrdenDetalle(id);
    } catch (err) {
      mostrarMensaje(root, err.network ? 'Sin conexión: esta acción necesita conexión con el servidor.' : err.message, 'error');
    }
  };

  async function accionSimple(idOrden, endpoint, metodo) {
    try {
      await api(metodo, `/api/ordenes-servicio/${idOrden}/${endpoint}`);
      pantallaOrdenDetalle(idOrden);
    } catch (err) {
      mostrarMensaje(root, err.network ? 'Sin conexión: esta acción necesita conexión con el servidor.' : err.message, 'error');
    }
  }
}

// ── Guardado en el equipo (colas sin conexión) ────────────────────────────

async function pantallaPendientes() {
  root.innerHTML = `
    <h1>${ic('cloud-upload')} Guardado en este equipo</h1>
    <div class="grupo-botones" style="margin-bottom:14px;">
      <button id="pe-sincronizar" class="btn btn-verde">${ic('refresh-cw')} Intentar enviar todo ahora</button>
    </div>
    <h2>Equipos recibidos sin conexión</h2>
    <div id="pe-ordenes"><div class="vacio">Cargando...</div></div>
    <h2 style="margin-top:20px;">Abonos sin conexión</h2>
    <div id="pe-abonos"><div class="vacio">Cargando...</div></div>`;

  document.getElementById('pe-sincronizar').onclick = async (e) => {
    e.target.disabled = true;
    e.target.innerHTML = '<span class="spinner"></span> Enviando...';
    await Net.sincronizar();
    pantallaPendientes();
  };

  await dibujarColaOrdenes();
  await dibujarColaAbonos();
}

async function dibujarColaOrdenes() {
  const cont = document.getElementById('pe-ordenes');
  const items = (await DB.todas('recepciones_pendientes')).sort((a, b) => b.creado.localeCompare(a.creado));
  if (items.length === 0) { cont.innerHTML = '<div class="vacio">No hay recepciones guardadas en este equipo.</div>'; return; }

  cont.innerHTML = items.map(it => `
      <div class="orden-item" data-clave="${it.clave}">
        <div class="orden-cab">
          <span class="orden-folio">${escapar(it.payload.folioLocal || it.folio || '—')}</span>
          ${it.estado === 'enviada'
            ? `<span class="pastilla lista">${ic('circle-check')} Enviada${it.folio ? ' (' + escapar(it.folio) + ')' : ''}</span>`
            : it.estado === 'error' ? '<span class="pastilla error">' + ic('triangle-alert') + ' Revisar</span>' : '<span class="pastilla pendiente">' + ic('hourglass') + ' Por enviar</span>'}
        </div>
        <div class="orden-equipo">${escapar(it.resumen)}</div>
        <div class="orden-cliente">${escapar(it.clienteResumen || '')}</div>
        <div class="orden-fecha" style="margin-top:6px;">${formatoFecha(it.creado)}</div>
        ${it.estado === 'error' ? `<div class="mensaje error" style="margin-top:8px;">${escapar(it.error)}</div>
          <div class="grupo-botones"><button class="btn btn-azul btn-chico oe-reintentar" data-clave="${it.clave}">Reintentar</button>
          <button class="btn btn-rojo btn-chico oe-descartar" data-clave="${it.clave}">Descartar</button></div>` : ''}
        ${it.estado === 'enviada' && it.idorden ? `<div style="margin-top:8px;"><button class="enlace oe-ver" data-id="${it.idorden}">Ver la orden →</button></div>` : ''}
      </div>`).join('');

  cont.querySelectorAll('.oe-reintentar').forEach(b => b.onclick = async () => {
    const it = (await DB.todas('recepciones_pendientes')).find(x => x.clave === b.dataset.clave);
    if (it) { it.estado = 'pendiente'; it.error = null; await DB.put('recepciones_pendientes', it); await Net.sincronizar(); pantallaPendientes(); }
  });
  cont.querySelectorAll('.oe-descartar').forEach(b => b.onclick = async () => {
    if (!confirm('¿Descartar esta recepción? No se enviará al servidor.')) return;
    await DB.eliminar('recepciones_pendientes', b.dataset.clave);
    pantallaPendientes();
  });
  cont.querySelectorAll('.oe-ver').forEach(b => b.onclick = () => navegar('#/orden/' + b.dataset.id));
}

async function dibujarColaAbonos() {
  const cont = document.getElementById('pe-abonos');
  const items = (await DB.todas('abonos_pendientes')).sort((a, b) => b.creado.localeCompare(a.creado));
  if (items.length === 0) { cont.innerHTML = '<div class="vacio">No hay abonos guardados en este equipo.</div>'; return; }

  cont.innerHTML = items.map(it => `
      <div class="orden-item" data-clave="${it.clave}">
        <div class="orden-cab">
          <span class="orden-folio">${escapar(it.noFactura)}</span>
          ${it.estado === 'enviada' ? '<span class="pastilla lista">' + ic('circle-check') + ' Enviado</span>'
            : it.estado === 'error' ? '<span class="pastilla error">' + ic('triangle-alert') + ' Revisar</span>' : '<span class="pastilla pendiente">' + ic('hourglass') + ' Por enviar</span>'}
        </div>
        <div class="orden-equipo">${formatoDinero(it.payload.monto)} · ${escapar(it.metodoPago)}</div>
        <div class="orden-cliente">${escapar(it.cliente || '')}</div>
        <div class="orden-fecha" style="margin-top:6px;">${formatoFecha(it.creado)}</div>
        ${it.estado === 'error' ? `<div class="mensaje error" style="margin-top:8px;">${escapar(it.error)}</div>
          <div class="grupo-botones"><button class="btn btn-azul btn-chico ab-reintentar" data-clave="${it.clave}">Reintentar</button>
          <button class="btn btn-rojo btn-chico ab-descartar" data-clave="${it.clave}">Descartar</button></div>` : ''}
      </div>`).join('');

  cont.querySelectorAll('.ab-reintentar').forEach(b => b.onclick = async () => {
    const it = (await DB.todas('abonos_pendientes')).find(x => x.clave === b.dataset.clave);
    if (it) { it.estado = 'pendiente'; it.error = null; await DB.put('abonos_pendientes', it); await Net.sincronizar(); pantallaPendientes(); }
  });
  cont.querySelectorAll('.ab-descartar').forEach(b => b.onclick = async () => {
    if (!confirm('¿Descartar este abono? No se enviará al servidor.')) return;
    await DB.eliminar('abonos_pendientes', b.dataset.clave);
    pantallaPendientes();
  });
}

// ── Clientes ──────────────────────────────────────────────────────────────

async function pantallaClientes() {
  const s = Sesion.obtener();
  const puedeCrear = GESTOR_ROLES.includes(s.rol);
  root.innerHTML = `
    <h1>${ic('users')} Clientes</h1>
    <div class="buscador">
      <input id="cl-buscar" placeholder="Nombre o teléfono">
      ${puedeCrear ? '<button id="cl-nuevo" class="btn btn-verde btn-chico">+ Nuevo</button>' : ''}
    </div>
    <div id="cl-form" class="oculto"></div>
    <div id="cl-lista"><div class="vacio">Cargando...</div></div>`;

  const cont = document.getElementById('cl-lista');
  let clientes = [];
  try {
    clientes = await api('GET', '/api/clientes?soloActivos=true');
  } catch (err) {
    cont.innerHTML = `<div class="mensaje error">${escapar(err.network ? 'Sin conexión con el servidor: la lista de clientes necesita el servidor.' : err.message)}</div>`;
    return;
  }

  const dibujar = (lista) => {
    if (lista.length === 0) { cont.innerHTML = '<div class="vacio">No hay clientes que coincidan.</div>'; return; }
    cont.innerHTML = lista.slice(0, 60).map(c => `
      <div class="orden-item" data-id="${c.idcliente}">
        <div class="orden-cab">
          <span class="orden-folio">${escapar(c.nombreCompleto)}</span>
          <span class="pastilla" style="background:${c.tipoColor}22; color:${c.tipoColor};">${escapar(c.tipoClienteDisplay)}</span>
        </div>
        <div class="orden-cliente">${escapar(c.telefono || 'Sin teléfono')}</div>
      </div>`).join('');
    cont.querySelectorAll('.orden-item').forEach(el => el.onclick = () => navegar('#/cliente/' + el.dataset.id));
  };
  dibujar(clientes);

  document.getElementById('cl-buscar').addEventListener('input', e => {
    const q = e.target.value.trim().toLowerCase();
    dibujar(!q ? clientes : clientes.filter(c => (c.nombreCompleto || '').toLowerCase().includes(q) || (c.telefono || '').includes(q)));
  });

  if (puedeCrear) {
    document.getElementById('cl-nuevo').onclick = () => {
      const cont2 = document.getElementById('cl-form');
      cont2.classList.toggle('oculto');
      if (!cont2.classList.contains('oculto')) dibujarFormularioCliente(cont2, null, () => { navegar('#/clientes'); render(); });
    };
  }
}

/** Formulario para crear o editar un cliente, dentro de {@code cont}. */
function dibujarFormularioCliente(cont, cliente, alGuardar) {
  cont.innerHTML = `
    <div class="tarjeta">
      <h2>${cliente ? 'Editar cliente' : 'Nuevo cliente'}</h2>
      <label class="obligatorio">Nombre completo</label>
      <input id="cf-nombre" value="${escapar(cliente?.nombreCompleto || '')}">
      <label>Teléfono</label>
      <input id="cf-tel" inputmode="tel" value="${escapar(cliente?.telefono || '')}">
      <label>Correo</label>
      <input id="cf-correo" type="email" value="${escapar(cliente?.correo || '')}">
      <label>Dirección</label>
      <input id="cf-dir" value="${escapar(cliente?.direccion || '')}">
      <button id="cf-guardar" class="btn btn-verde">Guardar</button>
    </div>`;
  document.getElementById('cf-guardar').onclick = async (e) => {
    const nombreCompleto = document.getElementById('cf-nombre').value.trim();
    if (!nombreCompleto) { mostrarMensaje(root, 'El nombre es obligatorio.', 'error'); return; }
    const payload = {
      nombreCompleto,
      telefono: document.getElementById('cf-tel').value.trim() || null,
      correo: document.getElementById('cf-correo').value.trim() || null,
      direccion: document.getElementById('cf-dir').value.trim() || null,
    };
    e.target.disabled = true;
    try {
      if (cliente) await api('PUT', '/api/clientes/' + cliente.idcliente, payload);
      else await api('POST', '/api/clientes', payload);
      alGuardar();
    } catch (err) {
      mostrarMensaje(root, err.network ? 'Sin conexión: esta acción necesita conexión con el servidor.' : err.message, 'error');
      e.target.disabled = false;
    }
  };
}

async function pantallaClienteDetalle(id) {
  const s = Sesion.obtener();
  root.innerHTML = '<div class="vacio">Cargando...</div>';
  let c;
  try {
    c = await api('GET', '/api/clientes/' + id);
  } catch (err) {
    root.innerHTML = `<div class="tarjeta"><div class="mensaje error">${escapar(err.network ? 'Sin conexión con el servidor.' : err.message)}</div>
      <button class="btn btn-gris" onclick="navegar('#/clientes')">Ir a clientes</button></div>`;
    return;
  }

  root.innerHTML = `
    <h1>${escapar(c.nombreCompleto)}</h1>
    <div class="tarjeta">
      <span class="pastilla" style="background:${c.tipoColor}22; color:${c.tipoColor};">${escapar(c.tipoClienteDisplay)}</span>
      <div class="detalle-fila"><span class="k">Teléfono</span><span class="v">${escapar(c.telefono || '—')}</span></div>
      <div class="detalle-fila"><span class="k">Correo</span><span class="v">${escapar(c.correo || '—')}</span></div>
      <div class="detalle-fila"><span class="k">Dirección</span><span class="v">${escapar(c.direccion || '—')}</span></div>
      <div class="detalle-fila"><span class="k">Puntos</span><span class="v">${c.puntos ?? 0}</span></div>
      <div class="detalle-fila"><span class="k">Compras</span><span class="v">${c.totalCompras ?? 0}</span></div>
      <div class="detalle-fila"><span class="k">Total gastado</span><span class="v">${formatoDinero(c.totalGastado)}</span></div>
      <div class="detalle-fila"><span class="k">Cliente desde</span><span class="v">${formatoFecha(c.fechaRegistro)}</span></div>
    </div>
    <div id="cd-form"></div>
    <div class="grupo-botones" id="cd-acciones"></div>`;

  const acciones = document.getElementById('cd-acciones');
  const botones = [];
  if (GESTOR_ROLES.includes(s.rol)) botones.push({ t: ic('pencil') + ' Editar', color: 'btn-azul', fn: () => dibujarFormularioCliente(document.getElementById('cd-form'), c, () => pantallaClienteDetalle(id)) });
  if (SUPERIOR.includes(s.rol)) botones.push({ t: ic('trash-2') + ' Desactivar', color: 'btn-rojo', fn: async () => {
    if (!confirm('¿Desactivar a ' + c.nombreCompleto + '? Ya no aparecerá en la lista.')) return;
    try { await api('DELETE', '/api/clientes/' + id); navegar('#/clientes'); }
    catch (err) { mostrarMensaje(root, err.network ? 'Sin conexión con el servidor.' : err.message, 'error'); }
  } });
  if (botones.length) {
    acciones.innerHTML = botones.map((b, i) => `<button class="btn ${b.color}" data-i="${i}">${b.t}</button>`).join('');
    acciones.querySelectorAll('button').forEach((b, i) => b.onclick = botones[i].fn);
  }
}

// ── Cuentas por cobrar ───────────────────────────────────────────────────

const METODOS_ABONO = { 1: 'Efectivo', 2: 'Tarjeta', 3: 'Transferencia', 4: 'PayJoy' };

async function pantallaCxC() {
  const s = Sesion.obtener();
  if (!GESTOR_ROLES.includes(s.rol)) { root.innerHTML = '<div class="tarjeta">No tienes permiso para ver cuentas por cobrar.</div>'; return; }
  root.innerHTML = `
    <h1>${ic('credit-card')} Cuentas por cobrar</h1>
    <div id="cx-resumen"></div>
    <div id="cx-lista" style="margin-top:14px;"><div class="vacio">Cargando...</div></div>`;

  try {
    const resumen = await api('GET', '/api/cuentas-por-cobrar/resumen');
    document.getElementById('cx-resumen').innerHTML = `
      <div class="fila">
        <div class="tarjeta" style="text-align:center; margin-bottom:0;"><div class="ayuda">Pendiente</div><div style="font-size:18px; font-weight:700;">${formatoDinero(resumen.totalPendiente)}</div></div>
        <div class="tarjeta" style="text-align:center; margin-bottom:0;"><div class="ayuda">Vencido</div><div style="font-size:18px; font-weight:700; color:var(--rojo);">${formatoDinero(resumen.totalVencido)}</div></div>
        <div class="tarjeta" style="text-align:center; margin-bottom:0;"><div class="ayuda">Cuentas</div><div style="font-size:18px; font-weight:700;">${resumen.cuentasActivas}</div></div>
      </div>`;
  } catch { /* si falla el resumen, se sigue mostrando la lista */ }

  const cont = document.getElementById('cx-lista');
  try {
    const cuentas = await api('GET', '/api/cuentas-por-cobrar?soloActivas=true');
    if (cuentas.length === 0) { cont.innerHTML = '<div class="vacio">No hay cuentas activas.</div>'; return; }
    cuentas.sort((a, b) => a.diasVencimiento - b.diasVencimiento);
    cont.innerHTML = cuentas.map(c => `
      <div class="orden-item" data-id="${c.idcuenta}">
        <div class="orden-cab">
          <span class="orden-folio">${escapar(c.noFactura)}</span>
          <span class="pastilla" style="background:${c.estadoColor}22; color:${c.estadoColor};">${escapar(c.estadoDisplay)}</span>
        </div>
        <div class="orden-equipo">${escapar(c.nombreCliente)}</div>
        <div class="orden-meta">
          <span class="orden-fecha">${c.diasVencimiento < 0 ? Math.abs(c.diasVencimiento) + ' días vencida' : 'vence en ' + c.diasVencimiento + ' días'}</span>
          <span style="font-weight:700;">${formatoDinero(c.saldoPendiente)}</span>
        </div>
      </div>`).join('');
    cont.querySelectorAll('.orden-item').forEach(el => el.onclick = () => navegar('#/cuenta/' + el.dataset.id));
  } catch (err) {
    cont.innerHTML = `<div class="mensaje error">${escapar(err.network ? 'Sin conexión con el servidor.' : err.message)}</div>`;
  }
}

async function pantallaCuentaDetalle(id) {
  const s = Sesion.obtener();
  root.innerHTML = '<div class="vacio">Cargando...</div>';
  let cuenta;
  try {
    const todas = await api('GET', '/api/cuentas-por-cobrar');
    cuenta = todas.find(c => String(c.idcuenta) === String(id));
    if (!cuenta) throw new ApiError('No se encontró la cuenta.', {});
  } catch (err) {
    root.innerHTML = `<div class="tarjeta"><div class="mensaje error">${escapar(err.network ? 'Sin conexión con el servidor.' : err.message)}</div>
      <button class="btn btn-gris" onclick="navegar('#/cxc')">Ir a cuentas por cobrar</button></div>`;
    return;
  }

  root.innerHTML = `
    <h1>${escapar(cuenta.noFactura)}</h1>
    <div class="tarjeta">
      <span class="pastilla" style="background:${cuenta.estadoColor}22; color:${cuenta.estadoColor};">${escapar(cuenta.estadoDisplay)}</span>
      <div class="detalle-fila"><span class="k">Cliente</span><span class="v">${escapar(cuenta.nombreCliente)}</span></div>
      <div class="detalle-fila"><span class="k">Emisión</span><span class="v">${cuenta.fechaEmision}</span></div>
      <div class="detalle-fila"><span class="k">Vencimiento</span><span class="v">${cuenta.fechaVencimiento}</span></div>
      <div class="detalle-fila"><span class="k">Total</span><span class="v">${formatoDinero(cuenta.montoTotal)}</span></div>
      <div class="detalle-fila"><span class="k">Pagado</span><span class="v">${formatoDinero(cuenta.montoPagado)}</span></div>
      <div class="detalle-fila"><span class="k">Saldo</span><span class="v">${formatoDinero(cuenta.saldoPendiente)}</span></div>
      ${cuenta.observaciones ? `<div class="detalle-fila"><span class="k">Notas</span><span class="v">${escapar(cuenta.observaciones)}</span></div>` : ''}
    </div>
    ${cuenta.saldoPendiente > 0 ? `
    <div class="tarjeta">
      <h2>Registrar abono</h2>
      <label class="obligatorio">Monto</label>
      <input id="ab-monto" type="number" inputmode="decimal" min="0" step="0.01" max="${cuenta.saldoPendiente}">
      <label class="obligatorio">Método</label>
      <select id="ab-metodo">
        <option value="1">Efectivo</option>
        <option value="2">Tarjeta</option>
        <option value="3">Transferencia</option>
        <option value="4">PayJoy</option>
      </select>
      <label>Notas</label>
      <input id="ab-notas" placeholder="Opcional">
      <button id="ab-btn" class="btn btn-verde">Registrar abono</button>
    </div>` : ''}
    <div class="tarjeta">
      <h2>Historial de pagos</h2>
      <div id="ab-historial"><div class="vacio">Cargando...</div></div>
    </div>`;

  cargarHistorialAbonos(id);

  if (cuenta.saldoPendiente > 0) {
    document.getElementById('ab-btn').onclick = async (e) => {
      const monto = Number(document.getElementById('ab-monto').value);
      if (!monto || monto <= 0) { mostrarMensaje(root, 'Indica un monto válido.', 'error'); return; }
      if (monto > cuenta.saldoPendiente) { mostrarMensaje(root, 'El monto no puede ser mayor al saldo pendiente.', 'error'); return; }
      const metodoPago = Number(document.getElementById('ab-metodo').value);
      const notas = document.getElementById('ab-notas').value.trim() || null;
      const claveOffline = uuid();

      e.target.disabled = true;
      e.target.innerHTML = '<span class="spinner"></span> Enviando...';
      const qs = new URLSearchParams({ monto: String(monto), metodoPago: String(metodoPago) });
      if (notas) qs.set('notas', notas);
      qs.set('claveOffline', claveOffline);
      try {
        await api('POST', `/api/cuentas-por-cobrar/${id}/pago?${qs.toString()}`, undefined);
        mostrarMensaje(root, 'Abono registrado.', 'ok');
        setTimeout(() => pantallaCuentaDetalle(id), 700);
      } catch (err) {
        if (err.network) {
          const fechaPago = fechaLocalISO(new Date());
          await DB.put('abonos_pendientes', {
            clave: claveOffline,
            creado: new Date().toISOString(),
            fechaPago,
            idcuenta: Number(id),
            noFactura: cuenta.noFactura,
            cliente: cuenta.nombreCliente,
            metodoPago: METODOS_ABONO[metodoPago],
            estado: 'pendiente',
            payload: { idcuenta: Number(id), monto, metodoPago, notas, claveOffline, fechaPago },
          });
          actualizarBarraConexion();
          mostrarMensaje(root, `Sin conexión: el abono de ${formatoDinero(monto)} se guardó en este equipo y se enviará solo al volver la conexión.`, 'info');
          setTimeout(() => navegar('#/pendientes'), 900);
        } else if (err.auth) {
          mostrarMensaje(root, 'La sesión expiró. Inicia sesión de nuevo.', 'error');
          setTimeout(() => navegar('#/login'), 1200);
        } else {
          mostrarMensaje(root, err.message, 'error');
          e.target.disabled = false;
          e.target.textContent = 'Registrar abono';
        }
      }
    };
  }
}

async function cargarHistorialAbonos(id) {
  const cont = document.getElementById('ab-historial');
  try {
    const pagos = await api('GET', `/api/cuentas-por-cobrar/${id}/historial`);
    cont.innerHTML = pagos.length === 0 ? '<div class="vacio">Sin abonos todavía.</div>' : pagos.map(p => `
      <div class="historial-item">
        <div class="historial-comentario">${formatoDinero(p.monto)} · ${escapar(p.metodoPago)}</div>
        <div class="historial-meta">${escapar(p.usuario)} · ${formatoFecha(p.fechaPago)}${p.notas ? ' · ' + escapar(p.notas) : ''}</div>
      </div>`).join('');
  } catch (err) {
    cont.innerHTML = `<div class="mensaje error">${escapar(err.network ? 'Sin conexión con el servidor.' : err.message)}</div>`;
  }
}

// ── Garantías ────────────────────────────────────────────────────────────

const GARANTIA_TRANSICIONES = {
  1: [2, 8], 2: [3], 3: [4, 8], 4: [5], 5: [6, 7, 8], 6: [9], 7: [9], 8: [9, 10], 9: [10], 10: [11],
};
const GARANTIA_PASOS_SUCURSAL = new Set(['1>2', '1>8', '8>10', '9>10', '10>11']);
const GARANTIA_NOMBRES = {
  1: 'Recibido en sucursal', 2: 'En tránsito a bodega', 3: 'Recibido en bodega', 4: 'En tránsito al proveedor',
  5: 'En el proveedor', 6: 'Reparado', 7: 'Cambio físico', 8: 'Rechazado', 9: 'Viaje de retorno',
  10: 'Listo para entrega', 11: 'Entregado',
};

function pastillaGarantia(g) {
  if (g.vencida) return '<span class="pastilla error">' + ic('triangle-alert') + ' Vencida</span>';
  const clave = g.estado === 11 ? 'entregada' : g.estado === 8 ? 'cancelada' : g.estado >= 9 ? 'lista' : g.estado === 1 ? 'recibida' : 'reparacion';
  return `<span class="pastilla ${clave}">${escapar(g.estadoDisplay)}</span>`;
}

async function pantallaGarantias() {
  const s = Sesion.obtener();
  if (!PERSONAL.includes(s.rol)) { root.innerHTML = '<div class="tarjeta">No tienes permiso para ver garantías.</div>'; return; }
  root.innerHTML = `
    <h1>${ic('shield-check')} Garantías</h1>
    <div class="buscador">
      <input id="ga-folio" placeholder="Folio (GAR-000123)" autocapitalize="characters">
      <button id="ga-buscar" class="btn btn-azul btn-chico">Buscar</button>
    </div>
    <div id="ga-resultado"></div>
    <div class="segmentado" style="margin-top:10px;">
      <button id="ga-seg-abiertas" class="activo">Abiertas</button>
      <button id="ga-seg-todas">Todas</button>
    </div>
    <div id="ga-lista"><div class="vacio">Cargando...</div></div>`;

  const buscar = async () => {
    const folio = document.getElementById('ga-folio').value.trim().toUpperCase();
    const out = document.getElementById('ga-resultado');
    if (!folio) return;
    out.innerHTML = '<div class="vacio">Buscando...</div>';
    try {
      const g = await api('GET', '/api/garantias/folio/' + encodeURIComponent(folio));
      out.innerHTML = itemGarantia(g);
      out.querySelector('.orden-item').onclick = () => navegar('#/garantia/' + g.idgarantia);
    } catch (err) {
      out.innerHTML = `<div class="mensaje error">${escapar(err.network ? 'Sin conexión con el servidor.' : err.message)}</div>`;
    }
  };
  document.getElementById('ga-buscar').onclick = buscar;
  document.getElementById('ga-folio').addEventListener('keydown', e => { if (e.key === 'Enter') buscar(); });

  const segAbiertas = document.getElementById('ga-seg-abiertas');
  const segTodas = document.getElementById('ga-seg-todas');
  const cargarLista = async (soloAbiertas) => {
    const cont = document.getElementById('ga-lista');
    cont.innerHTML = '<div class="vacio">Cargando...</div>';
    try {
      const garantias = await api('GET', '/api/garantias' + (soloAbiertas ? '?soloAbiertas=true' : ''));
      if (garantias.length === 0) { cont.innerHTML = '<div class="vacio">No hay garantías.</div>'; return; }
      cont.innerHTML = garantias.map(itemGarantia).join('');
      cont.querySelectorAll('.orden-item').forEach(el => el.onclick = () => navegar('#/garantia/' + el.dataset.id));
    } catch (err) {
      cont.innerHTML = `<div class="mensaje error">${escapar(err.network ? 'Sin conexión con el servidor.' : err.message)}</div>`;
    }
  };
  segAbiertas.onclick = () => { segAbiertas.classList.add('activo'); segTodas.classList.remove('activo'); cargarLista(true); };
  segTodas.onclick = () => { segTodas.classList.add('activo'); segAbiertas.classList.remove('activo'); cargarLista(false); };
  cargarLista(true);
}

function itemGarantia(g) {
  return `
    <div class="orden-item" data-id="${g.idgarantia}">
      <div class="orden-cab">
        <span class="orden-folio">${escapar(g.folio)}</span>
        ${pastillaGarantia(g)}
      </div>
      <div class="orden-equipo">${escapar(g.nombreProducto)}</div>
      <div class="orden-cliente">${escapar(g.nombreContacto || '')} · ${escapar(g.nombreTienda)}</div>
      <div class="orden-meta">
        <span class="orden-fecha">${formatoFecha(g.fechaIngreso)}</span>
        <span style="font-size:12px; color:var(--gris);">${g.diasRestantes != null ? (g.diasRestantes < 0 ? Math.abs(g.diasRestantes) + ' días vencida' : g.diasRestantes + ' días restantes') : ''}</span>
      </div>
    </div>`;
}

async function pantallaGarantiaDetalle(id) {
  const s = Sesion.obtener();
  root.innerHTML = '<div class="vacio">Cargando...</div>';
  let g;
  try {
    g = await api('GET', '/api/garantias/' + id);
  } catch (err) {
    root.innerHTML = `<div class="tarjeta"><div class="mensaje error">${escapar(err.network ? 'Sin conexión con el servidor.' : err.message)}</div>
      <button class="btn btn-gris" onclick="navegar('#/garantias')">Ir a garantías</button></div>`;
    return;
  }

  root.innerHTML = `
    <h1>${escapar(g.folio)}</h1>
    <div class="tarjeta">
      <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:6px;">
        ${pastillaGarantia(g)}
        <span class="orden-fecha">${formatoFecha(g.fechaIngreso)}</span>
      </div>
      <div class="detalle-fila"><span class="k">Producto</span><span class="v">${escapar(g.nombreProducto)}</span></div>
      <div class="detalle-fila"><span class="k">IMEI/Serie</span><span class="v">${escapar(g.imei || '—')}</span></div>
      <div class="detalle-fila"><span class="k">Contacto</span><span class="v">${escapar(g.nombreContacto || '—')}</span></div>
      <div class="detalle-fila"><span class="k">Teléfono</span><span class="v">${escapar(g.telefonoContacto || '—')}</span></div>
      <div class="detalle-fila"><span class="k">Sucursal</span><span class="v">${escapar(g.nombreTienda)}</span></div>
      <div class="detalle-fila"><span class="k">Falla reportada</span><span class="v">${escapar(g.fallaReportada)}</span></div>
      <div class="detalle-fila"><span class="k">Proveedor</span><span class="v">${escapar(g.proveedor || '—')}</span></div>
      <div class="detalle-fila"><span class="k">Fecha límite</span><span class="v">${g.fechaLimiteSolucion || '—'}</span></div>
      ${g.imeiReemplazo ? `<div class="detalle-fila"><span class="k">IMEI de reemplazo</span><span class="v">${escapar(g.imeiReemplazo)}</span></div>` : ''}
    </div>
    <div id="gd-acciones" class="tarjeta oculto">
      <h2>Avanzar estado</h2>
      <div class="grupo-botones" id="gd-botones"></div>
      <div id="gd-form" class="oculto" style="margin-top:10px;"></div>
    </div>
    <div class="tarjeta">
      <h2>Bitácora</h2>
      ${(g.historial || []).slice().reverse().map(h => `
        <div class="historial-item">
          <div class="historial-comentario">${escapar(h.estadoDisplay)}${h.comentario ? ': ' + escapar(h.comentario) : ''}</div>
          <div class="historial-meta">${escapar(h.nombreUsuario)} · ${formatoFecha(h.fecha)}</div>
        </div>`).join('') || '<div class="vacio">Sin movimientos.</div>'}
    </div>`;

  const siguientes = GARANTIA_TRANSICIONES[g.estado] || [];
  const permitidos = SUPERIOR.includes(s.rol) ? siguientes : siguientes.filter(h => GARANTIA_PASOS_SUCURSAL.has(g.estado + '>' + h));
  if (permitidos.length === 0) return;

  const contAcc = document.getElementById('gd-acciones');
  contAcc.classList.remove('oculto');
  const botones = document.getElementById('gd-botones');
  botones.innerHTML = permitidos.map(h => `<button class="btn btn-azul" data-hacia="${h}">${escapar(GARANTIA_NOMBRES[h])}</button>`).join('');
  botones.querySelectorAll('button').forEach(b => b.onclick = () => mostrarFormularioAvance(id, Number(b.dataset.hacia)));
}

function mostrarFormularioAvance(id, hacia) {
  const necesitaComentario = [6, 7, 8].includes(hacia); // Reparado, Cambio físico, Rechazado
  const esCambioFisico = hacia === 7;
  const cont = document.getElementById('gd-form');
  cont.classList.remove('oculto');
  cont.innerHTML = `
    <label${necesitaComentario ? ' class="obligatorio"' : ''}>${hacia === 8 ? 'Motivo del rechazo' : 'Comentario'}</label>
    <textarea id="gf-comentario" placeholder="${necesitaComentario ? 'Obligatorio para este paso' : 'Opcional'}"></textarea>
    ${esCambioFisico ? '<label class="obligatorio">IMEI/serie del equipo nuevo</label><input id="gf-imei">' : ''}
    <button id="gf-guardar" class="btn btn-verde">Confirmar: ${escapar(GARANTIA_NOMBRES[hacia])}</button>`;
  document.getElementById('gf-guardar').onclick = async (e) => {
    const comentario = document.getElementById('gf-comentario').value.trim();
    if (necesitaComentario && !comentario) { mostrarMensaje(root, 'El comentario es obligatorio para este paso.', 'error'); return; }
    const imeiReemplazo = esCambioFisico ? document.getElementById('gf-imei').value.trim() : null;
    if (esCambioFisico && !imeiReemplazo) { mostrarMensaje(root, 'Indica el IMEI/serie del equipo nuevo.', 'error'); return; }
    e.target.disabled = true;
    try {
      await api('POST', `/api/garantias/${id}/avanzar`, { estado: hacia, comentario: comentario || null, imeiReemplazo });
      pantallaGarantiaDetalle(id);
    } catch (err) {
      mostrarMensaje(root, err.network ? 'Sin conexión: esta acción necesita conexión con el servidor.' : err.message, 'error');
      e.target.disabled = false;
    }
  };
}

// ── Reportes de ventas ───────────────────────────────────────────────────

async function pantallaReportes() {
  const s = Sesion.obtener();
  if (!GESTOR_ROLES.includes(s.rol)) { root.innerHTML = '<div class="tarjeta">No tienes permiso para ver reportes.</div>'; return; }
  root.innerHTML = `
    <h1>${ic('chart-column')} Reporte de ventas</h1>
    <div id="rp-tienda"></div>
    <div class="segmentado">
      <button id="rp-hoy" class="activo">Hoy</button>
      <button id="rp-7dias">7 días</button>
      <button id="rp-mes">Este mes</button>
    </div>
    <div id="rp-resumen"></div>
    <div id="rp-lista" style="margin-top:10px;"></div>`;

  let codti = s.codti;
  const contTienda = document.getElementById('rp-tienda');
  if (SUPERIOR.includes(s.rol)) {
    contTienda.innerHTML = `<label class="obligatorio">Sucursal</label><select id="rp-codti"><option>Cargando...</option></select>`;
    try {
      const tiendas = (await api('GET', '/api/tiendas')).filter(t => !t.esAlmacen);
      const sel = document.getElementById('rp-codti');
      sel.innerHTML = tiendas.map(t => `<option value="${t.codti}">${escapar(t.nombre)}</option>`).join('');
      codti = tiendas.find(t => t.codti === s.codti)?.codti ?? tiendas[0]?.codti;
      if (codti != null) sel.value = codti;
      sel.onchange = () => { codti = Number(sel.value); cargar(); };
    } catch {
      contTienda.innerHTML = '<div class="mensaje info">No se pudo cargar la lista de sucursales (sin conexión).</div>';
    }
  } else {
    contTienda.innerHTML = '<div class="ayuda">Sucursal: <strong>tu tienda</strong></div>';
  }

  const botonesPeriodo = { hoy: document.getElementById('rp-hoy'), '7dias': document.getElementById('rp-7dias'), mes: document.getElementById('rp-mes') };
  let periodo = 'hoy';
  function rangoDe(periodo) {
    const ahora = new Date();
    const hasta = fechaLocalISO(ahora);
    let desde;
    if (periodo === 'hoy') { const d = new Date(ahora); d.setHours(0, 0, 0, 0); desde = fechaLocalISO(d); }
    else if (periodo === '7dias') { const d = new Date(ahora); d.setDate(d.getDate() - 6); d.setHours(0, 0, 0, 0); desde = fechaLocalISO(d); }
    else { const d = new Date(ahora.getFullYear(), ahora.getMonth(), 1); desde = fechaLocalISO(d); }
    return { desde, hasta };
  }

  async function cargar() {
    if (codti == null) return;
    Object.values(botonesPeriodo).forEach(b => b.classList.remove('activo'));
    botonesPeriodo[periodo].classList.add('activo');
    const resumenEl = document.getElementById('rp-resumen');
    const listaEl = document.getElementById('rp-lista');
    resumenEl.innerHTML = '<div class="vacio">Cargando...</div>';
    listaEl.innerHTML = '';
    try {
      const { desde, hasta } = rangoDe(periodo);
      const ventas = await api('GET', `/api/ventas/tienda/${codti}?desde=${encodeURIComponent(desde)}&hasta=${encodeURIComponent(hasta)}`);
      const completadas = ventas.filter(v => v.descripcionEstado !== 'Cancelada');
      const total = completadas.reduce((sum, v) => sum + Number(v.total || 0), 0);
      resumenEl.innerHTML = `
        <div class="fila">
          <div class="tarjeta" style="text-align:center; margin-bottom:0;"><div class="ayuda">Ventas</div><div style="font-size:18px; font-weight:700;">${completadas.length}</div></div>
          <div class="tarjeta" style="text-align:center; margin-bottom:0;"><div class="ayuda">Total</div><div style="font-size:18px; font-weight:700;">${formatoDinero(total)}</div></div>
          <div class="tarjeta" style="text-align:center; margin-bottom:0;"><div class="ayuda">Ticket prom.</div><div style="font-size:18px; font-weight:700;">${formatoDinero(completadas.length ? total / completadas.length : 0)}</div></div>
        </div>`;
      if (ventas.length === 0) { listaEl.innerHTML = '<div class="vacio">Sin ventas en este periodo.</div>'; return; }
      listaEl.innerHTML = ventas.slice(0, 60).map(v => `
        <div class="orden-item">
          <div class="orden-cab">
            <span class="orden-folio">${v.folioLocal || ('Venta #' + v.idventa)}</span>
            <span class="pastilla ${v.descripcionEstado === 'Cancelada' ? 'cancelada' : 'lista'}">${escapar(v.descripcionEstado)}</span>
          </div>
          <div class="orden-equipo">${escapar(v.nombreCliente || 'Sin cliente')} · ${escapar(v.descripcionMetodoPago)}</div>
          <div class="orden-meta">
            <span class="orden-fecha">${formatoFecha(v.fechaVenta)} · ${escapar(v.nombreVendedor || '')}</span>
            <span style="font-weight:700;">${formatoDinero(v.total)}</span>
          </div>
        </div>`).join('');
    } catch (err) {
      resumenEl.innerHTML = `<div class="mensaje error">${escapar(err.network ? 'Sin conexión con el servidor.' : err.message)}</div>`;
    }
  }
  botonesPeriodo.hoy.onclick = () => { periodo = 'hoy'; cargar(); };
  botonesPeriodo['7dias'].onclick = () => { periodo = '7dias'; cargar(); };
  botonesPeriodo.mes.onclick = () => { periodo = 'mes'; cargar(); };
  cargar();
}

// ── Inventario ───────────────────────────────────────────────────────────

const TIPO_ICONO = { CELULAR: ic('smartphone'), TABLET: ic('smartphone'), ACCESORIO: ic('headphones'), SERVICIO: ic('wrench') };

async function pantallaInventario() {
  const s = Sesion.obtener();
  bajoStockCache.expira = 0; // se refresca el badge del menú la próxima vez que se dibuje, por si aquí cambia el stock
  root.innerHTML = `
    <h1>${ic('package')} Inventario</h1>
    <div id="iv-tienda"></div>
    <div class="buscador">
      <input id="iv-buscar" placeholder="Nombre, código, marca o modelo">
    </div>
    <label style="display:flex; align-items:center; gap:8px; font-weight:400; text-transform:none;">
      <input type="checkbox" id="iv-bajo-stock" style="width:auto;"> Solo bajo stock
    </label>
    ${SUPERIOR.includes(s.rol) ? `
      <div class="grupo-botones" style="margin: 10px 0;">
        <button id="iv-nuevo" class="btn btn-verde">${ic('plus')} Nuevo producto</button>
        <button id="iv-catalogos" class="btn btn-gris">${ic('tags')} Catálogos</button>
      </div>
      <div id="iv-form" class="oculto"></div>` : ''}
    <div id="iv-lista" style="margin-top:10px;"><div class="vacio">Cargando...</div></div>`;

  let codti = s.codti;
  let productosInv = [];
  const refrescarListaInventario = () => {
    const q = document.getElementById('iv-buscar').value.trim();
    dibujarInventario(document.getElementById('iv-lista'), filtrarProductos(productosInv, q));
  };
  const contTienda = document.getElementById('iv-tienda');
  const cargar = async () => {
    if (codti == null) return;
    const cont = document.getElementById('iv-lista');
    cont.innerHTML = '<div class="vacio">Cargando...</div>';
    try {
      const soloBajo = document.getElementById('iv-bajo-stock').checked;
      productosInv = await api('GET', `/api/productos/tienda/${codti}${soloBajo ? '/bajo-stock' : ''}`);
      refrescarListaInventario();
    } catch (err) {
      cont.innerHTML = `<div class="mensaje error">${escapar(err.network ? 'Sin conexión con el servidor.' : err.message)}</div>`;
    }
  };

  if (SUPERIOR.includes(s.rol)) {
    contTienda.innerHTML = `<label class="obligatorio">Sucursal</label><select id="iv-codti"><option>Cargando...</option></select>`;
    try {
      const tiendas = await api('GET', '/api/tiendas'); // incluye el almacén: ahí se da de alta la mercancía antes de traspasarla a tiendas
      const sel = document.getElementById('iv-codti');
      sel.innerHTML = tiendas.map(t => `<option value="${t.codti}">${escapar(t.nombre)}</option>`).join('');
      codti = tiendas.find(t => t.codti === s.codti)?.codti ?? tiendas[0]?.codti;
      if (codti != null) sel.value = codti;
      sel.onchange = () => { codti = Number(sel.value); cargar(); };
    } catch {
      contTienda.innerHTML = '<div class="mensaje info">No se pudo cargar la lista de sucursales (sin conexión).</div>';
    }
  } else {
    contTienda.innerHTML = '<div class="ayuda">Sucursal: <strong>tu tienda</strong></div>';
  }

  document.getElementById('iv-buscar').addEventListener('input', refrescarListaInventario);
  document.getElementById('iv-bajo-stock').addEventListener('change', cargar);
  cargar();

  if (SUPERIOR.includes(s.rol)) {
    let categorias = null;
    document.getElementById('iv-nuevo').onclick = async () => {
      const cont = document.getElementById('iv-form');
      cont.classList.toggle('oculto');
      if (cont.classList.contains('oculto')) return;
      if (categorias === null) {
        try {
          const planas = [];
          const aplanar = (lista) => { for (const c of lista) { planas.push(c); if (c.subcategorias) aplanar(c.subcategorias); } };
          aplanar(await api('GET', '/api/categorias'));
          categorias = planas;
        } catch { categorias = []; }
      }
      dibujarFormularioProducto(cont, categorias, () => codti, () => { cont.classList.add('oculto'); cargar(); });
    };
    document.getElementById('iv-catalogos').onclick = () => navegar('#/catalogos');
  }
}

function dibujarFormularioProducto(cont, categorias, obtenerCodti, alGuardar) {
  cont.innerHTML = `
    <div class="tarjeta">
      <h2 style="margin-top:0;">Nuevo producto</h2>
      <div class="segmentado">
        <button id="np-seg-accesorio" class="activo">Accesorio</button>
        <button id="np-seg-equipo">Equipo</button>
        <button id="np-seg-servicio">Servicio</button>
      </div>
      <div id="np-campos-accesorio">
        <label class="obligatorio">Descripción</label>
        <input id="np-descripcion" placeholder="Funda Silicon iPhone 16 Pro">
        <label>Compatible con</label>
        <input id="np-compatibilidad" placeholder="Opcional, ej. iPhone 16 Pro">
        <label class="obligatorio">Stock inicial</label>
        <input id="np-stock" type="number" inputmode="decimal" min="0" step="1" value="1">
      </div>
      <div id="np-campos-equipo" class="oculto">
        <label class="obligatorio">Marca</label>
        <input id="np-marca" placeholder="Samsung, Apple...">
        <label class="obligatorio">Modelo</label>
        <input id="np-modelo" placeholder="A54, iPhone 13...">
        <label class="obligatorio">IMEIs (uno por línea)</label>
        <textarea id="np-imeis" placeholder="Un IMEI o serie por línea. El stock inicial es la cantidad que captures aquí."></textarea>
      </div>
      <div id="np-campos-servicio" class="oculto">
        <label class="obligatorio">Nombre del servicio</label>
        <input id="np-servicio-nombre" placeholder="Cambio de Pantalla">
        <label>Equipo al que aplica</label>
        <input id="np-servicio-equipo" placeholder="Opcional, ej. Samsung A56 5G">
        <label>Minutos estimados</label>
        <input id="np-servicio-tiempo" type="number" inputmode="numeric" min="0" placeholder="Opcional">
      </div>
      <label class="obligatorio">Categoría</label>
      <div class="fila">
        <select id="np-categoria"></select>
        <button id="np-nueva-categoria" class="btn btn-azul btn-chico" style="flex:0 0 auto;">+ Nueva</button>
      </div>
      <div id="np-categoria-form" class="oculto"></div>
      <label class="obligatorio">Precio de compra</label>
      <input id="np-precio-compra" type="number" inputmode="decimal" min="0" step="0.01">
      <label class="obligatorio">Precio de venta</label>
      <input id="np-precio-venta" type="number" inputmode="decimal" min="0" step="0.01">
      <label>Días de garantía</label>
      <input id="np-garantia" type="number" inputmode="numeric" min="0" placeholder="Opcional">
      <button id="np-guardar" class="btn btn-verde">Guardar producto</button>
    </div>`;

  let grupo = 'ACCESORIO'; // ACCESORIO | EQUIPO | SERVICIO

  const segAcc = document.getElementById('np-seg-accesorio');
  const segEquipo = document.getElementById('np-seg-equipo');
  const segServicio = document.getElementById('np-seg-servicio');
  const camposAcc = document.getElementById('np-campos-accesorio');
  const camposEquipo = document.getElementById('np-campos-equipo');
  const camposServicio = document.getElementById('np-campos-servicio');

  function mostrarGrupo() {
    camposAcc.classList.toggle('oculto', grupo !== 'ACCESORIO');
    camposEquipo.classList.toggle('oculto', grupo !== 'EQUIPO');
    camposServicio.classList.toggle('oculto', grupo !== 'SERVICIO');
    segAcc.classList.toggle('activo', grupo === 'ACCESORIO');
    segEquipo.classList.toggle('activo', grupo === 'EQUIPO');
    segServicio.classList.toggle('activo', grupo === 'SERVICIO');
  }

  // El selector de categoría solo muestra las del grupo actual: un Accesorio no debe ver categorías de Equipo ni
  // viceversa. Equipo junta Celular y Tablet en una sola lista — cuál de los dos es se decide por la categoría
  // elegida (cada una ya sabe si es Celular o Tablet), no por otro control aparte.
  // Recuerda la selección de cada grupo por separado, para no perderla al ir y venir entre segmentos.
  const seleccionPorGrupo = {};
  function categoriasDelGrupo() {
    return grupo === 'EQUIPO'
      ? categorias.filter(c => c.tipo === 'CELULAR' || c.tipo === 'TABLET')
      : categorias.filter(c => c.tipo === grupo);
  }
  function renderCategorias() {
    const sel = document.getElementById('np-categoria');
    const delGrupo = categoriasDelGrupo();
    sel.innerHTML = delGrupo.map(c => `<option value="${escapar(c.nombre)}">${escapar(c.nombre)}</option>`).join('') || '<option value="">Sin categorías de este tipo</option>';
    if (seleccionPorGrupo[grupo] && delGrupo.some(c => c.nombre === seleccionPorGrupo[grupo])) {
      sel.value = seleccionPorGrupo[grupo];
    }
    sel.onchange = () => { seleccionPorGrupo[grupo] = sel.value; };
  }
  renderCategorias();

  const cambiarGrupo = (nuevo) => {
    seleccionPorGrupo[grupo] = document.getElementById('np-categoria').value;
    grupo = nuevo;
    mostrarGrupo();
    renderCategorias();
  };
  segAcc.onclick = () => cambiarGrupo('ACCESORIO');
  segEquipo.onclick = () => cambiarGrupo('EQUIPO');
  segServicio.onclick = () => cambiarGrupo('SERVICIO');

  document.getElementById('np-nueva-categoria').onclick = () => {
    const contCat = document.getElementById('np-categoria-form');
    contCat.classList.toggle('oculto');
    if (contCat.classList.contains('oculto')) return;
    contCat.innerHTML = `
      <label class="obligatorio">Nombre</label>
      <input id="nc-nombre" placeholder="Ej. Cargadores">
      ${grupo === 'EQUIPO' ? `
        <label class="obligatorio">Tipo de equipo</label>
        <select id="nc-tipo-equipo"><option value="CELULAR">Celular</option><option value="TABLET">Tablet</option></select>
      ` : ''}
      <label>Código corto</label>
      <input id="nc-codigo" placeholder="Opcional, ej. CAR — para el código de sus productos" maxlength="10">
      <button id="nc-guardar" class="btn btn-azul btn-chico">Guardar categoría</button>`;
    document.getElementById('nc-guardar').onclick = async (e) => {
      const nombreCat = document.getElementById('nc-nombre').value.trim();
      if (!nombreCat) { mostrarMensaje(root, 'Indica el nombre de la categoría.', 'error'); return; }
      e.target.disabled = true;
      try {
        const tipo = grupo === 'EQUIPO' ? document.getElementById('nc-tipo-equipo').value : grupo;
        const nueva = await api('POST', '/api/categorias', {
          nombreCat, tipo, codigo: document.getElementById('nc-codigo').value.trim() || null,
        });
        categorias.push(nueva);
        seleccionPorGrupo[grupo] = nueva.nombre;
        renderCategorias();
        contCat.classList.add('oculto');
        mostrarMensaje(root, `Categoría "${nueva.nombre}" creada.`, 'ok');
      } catch (err) {
        mostrarMensaje(root, err.network ? 'Sin conexión con el servidor.' : err.message, 'error');
        e.target.disabled = false;
      }
    };
  };

  document.getElementById('np-guardar').onclick = async (e) => {
    const categoriaMaster = document.getElementById('np-categoria').value;
    const precioCompra = Number(document.getElementById('np-precio-compra').value);
    const precioVenta = Number(document.getElementById('np-precio-venta').value);
    if (!precioCompra || precioCompra < 0) { mostrarMensaje(root, 'Indica el precio de compra.', 'error'); return; }
    if (!precioVenta || precioVenta <= 0) { mostrarMensaje(root, 'Indica el precio de venta.', 'error'); return; }

    let tipoMaster = grupo;
    if (grupo === 'EQUIPO') {
      const categoriaElegida = categorias.find(c => c.nombre === categoriaMaster);
      if (!categoriaElegida) { mostrarMensaje(root, 'Selecciona o crea primero una categoría (dice si es Celular o Tablet).', 'error'); return; }
      tipoMaster = categoriaElegida.tipo;
    }

    const payload = {
      tipoMaster,
      categoriaMaster,
      codti: obtenerCodti(),
      precioCompra,
      precioVenta,
      diasGarantia: document.getElementById('np-garantia').value ? Number(document.getElementById('np-garantia').value) : null,
    };
    if (grupo === 'EQUIPO') {
      const marca = document.getElementById('np-marca').value.trim();
      const modelo = document.getElementById('np-modelo').value.trim();
      const imeis = document.getElementById('np-imeis').value.split('\n').map(s => s.trim()).filter(Boolean);
      if (!marca || !modelo) { mostrarMensaje(root, 'Indica marca y modelo.', 'error'); return; }
      if (imeis.length === 0) { mostrarMensaje(root, 'Captura al menos un IMEI.', 'error'); return; }
      payload.marca = marca;
      payload.modelo = modelo;
      payload.imeis = imeis;
      payload.stock = imeis.length;
    } else if (grupo === 'SERVICIO') {
      const nombreServicio = document.getElementById('np-servicio-nombre').value.trim();
      if (!nombreServicio) { mostrarMensaje(root, 'Indica el nombre del servicio.', 'error'); return; }
      payload.descripcion = nombreServicio;
      payload.descripcion2 = document.getElementById('np-servicio-equipo').value.trim() || null;
      payload.tiempoEstimadoMin = document.getElementById('np-servicio-tiempo').value ? Number(document.getElementById('np-servicio-tiempo').value) : null;
      payload.stock = 0;
    } else {
      const descripcion = document.getElementById('np-descripcion').value.trim();
      const stock = Number(document.getElementById('np-stock').value);
      if (!descripcion) { mostrarMensaje(root, 'Indica la descripción del producto.', 'error'); return; }
      if (!stock || stock < 0) { mostrarMensaje(root, 'Indica el stock inicial.', 'error'); return; }
      payload.descripcion = descripcion;
      payload.compatibilidad = document.getElementById('np-compatibilidad').value.trim() || null;
      payload.stock = stock;
    }

    e.target.disabled = true;
    e.target.innerHTML = '<span class="spinner"></span> Guardando...';
    try {
      const nuevo = await api('POST', '/api/productos', payload);
      mostrarMensaje(root, `Producto "${nuevo.nombreProductoMaster}" registrado.`, 'ok');
      alGuardar();
    } catch (err) {
      mostrarMensaje(root, err.network ? 'Sin conexión con el servidor.' : err.message, 'error');
      e.target.disabled = false;
      e.target.textContent = 'Guardar producto';
    }
  };
}

const INVENTARIO_MAX_TARJETAS = 100;
function dibujarInventario(cont, productos) {
  if (productos.length === 0) { cont.innerHTML = '<div class="vacio">No hay productos que mostrar.</div>'; return; }
  const aviso = productos.length > INVENTARIO_MAX_TARJETAS
    ? `<div class="ayuda" style="margin:6px 0;">Mostrando ${INVENTARIO_MAX_TARJETAS} de ${productos.length} productos: escribe arriba para buscar el que necesitas.</div>` : '';
  cont.innerHTML = aviso + productos.slice(0, INVENTARIO_MAX_TARJETAS).map(p => {
    return `
    <div class="orden-item" data-codti="${p.codti}~${escapar(p.codpro)}">
      <div class="orden-cab">
        <span class="orden-folio">${TIPO_ICONO[p.tipo] || ''} ${escapar(p.nombreProductoMaster)}</span>
        ${p.bajoStock ? '<span class="pastilla error">' + ic('triangle-alert') + ' Bajo stock</span>' : ''}
      </div>
      <div class="orden-cliente">${escapar(p.codpro)} · ${escapar(p.nombreCategoria || '')}</div>
      <div class="orden-meta">
        <span class="orden-fecha">${formatoDinero(p.preciopub)}</span>
        <span style="font-weight:700;">${p.tipo === 'SERVICIO' ? '' : 'Stock: ' + Number(p.stock)}</span>
      </div>
    </div>`;
  }).join('');
  cont.querySelectorAll('.orden-item').forEach(el => el.onclick = () => navegar('#/producto/' + el.dataset.codti));
}

async function pantallaProductoDetalle(param) {
  const [codti, codpro] = param.split('~');
  const s = Sesion.obtener();
  root.innerHTML = '<div class="vacio">Cargando...</div>';
  let p;
  try {
    const productos = await api('GET', `/api/productos/tienda/${codti}`);
    p = productos.find(x => x.codpro === codpro);
    if (!p) throw new ApiError('No se encontró el producto.', {});
  } catch (err) {
    root.innerHTML = `<div class="tarjeta"><div class="mensaje error">${escapar(err.network ? 'Sin conexión con el servidor.' : err.message)}</div>
      <button class="btn btn-gris" onclick="navegar('#/inventario')">Ir a inventario</button></div>`;
    return;
  }

  const puedeEditar = GESTOR_ROLES.includes(s.rol);      // ajustar stock, ver costos y movimientos
  const puedeEditarDatos = SUPERIOR.includes(s.rol);   // cambiar precios y datos del artículo: vista aparte
  root.innerHTML = `
    <h1>${TIPO_ICONO[p.tipo] || ''} ${escapar(p.nombreProductoMaster)}</h1>
    <div class="tarjeta">
      ${p.bajoStock ? '<span class="pastilla error">' + ic('triangle-alert') + ' Bajo stock</span>' : ''}
      <div class="detalle-fila"><span class="k">Código</span><span class="v">${escapar(p.codpro)}</span></div>
      ${p.marca ? `<div class="detalle-fila"><span class="k">Marca / Modelo</span><span class="v">${escapar(p.marca)} ${escapar(p.modelo)}</span></div>` : ''}
      <div class="detalle-fila"><span class="k">Categoría</span><span class="v">${escapar(p.nombreCategoria || '—')}</span></div>
      ${p.descripcion ? `<div class="detalle-fila"><span class="k">Descripción</span><span class="v">${escapar(p.descripcion)}</span></div>` : ''}
      ${p.compatibilidad ? `<div class="detalle-fila"><span class="k">Compatibilidad</span><span class="v">${escapar(p.compatibilidad)}</span></div>` : ''}
      ${p.tipo !== 'SERVICIO' ? `<div class="detalle-fila"><span class="k">Stock</span><span class="v">${Number(p.stock)}${Number(p.stockMinimo) > 0 ? ' (mínimo ' + Number(p.stockMinimo) + ')' : ''}</span></div>` : ''}
      ${puedeEditar ? `<div class="detalle-fila"><span class="k">Precio compra</span><span class="v">${formatoDinero(p.preciopro)}</span></div>` : ''}
      <div class="detalle-fila"><span class="k">Precio venta</span><span class="v">${formatoDinero(p.preciopub)}</span></div>
      ${p.diasGarantia ? `<div class="detalle-fila"><span class="k">Garantía</span><span class="v">${p.diasGarantia} días</span></div>` : ''}
    </div>

    ${p.tipo === 'CELULAR' && p.imeisDisponibles?.length ? `
    <div class="tarjeta">
      <h2>Unidades disponibles (${p.imeisDisponibles.length})</h2>
      ${p.imeisDisponibles.map(u => `
        <div class="detalle-fila"><span class="k">${escapar(u.imei)} · ${escapar(u.condicion)}</span><span class="v">${formatoDinero(u.precioVenta)}</span></div>
      `).join('')}
    </div>` : ''}

    ${puedeEditarDatos ? `
    <div class="tarjeta">
      <h2>Datos y precios</h2>
      <div class="ayuda">Nombre, categoría, precios, garantía, compatibilidad y stock mínimo se cambian aquí; el punto de venta solo cobra lo que diga Inventario.</div>
      <button id="pd-editar" class="btn btn-azul">${ic('pencil')} Editar producto</button>
    </div>` : ''}
    ${puedeEditar ? `
    ${p.tipo === 'ACCESORIO' ? `
    <div class="tarjeta">
      <h2>Ajustar stock</h2>
      <label class="obligatorio">Tipo de movimiento</label>
      <select id="pd-tipo-mov">
        <option value="ENTRADA">Entrada (suma al stock)</option>
        <option value="SALIDA">Salida (resta del stock)</option>
        <option value="AJUSTE">Ajuste (deja el valor exacto)</option>
      </select>
      <label class="obligatorio">Cantidad</label>
      <input id="pd-cantidad" type="number" inputmode="decimal" min="0" step="1">
      <label class="obligatorio">Motivo</label>
      <input id="pd-comentario" placeholder="Ej. conteo físico, mercancía dañada...">
      <button id="pd-guardar-stock" class="btn btn-verde">Registrar movimiento</button>
    </div>` : ''}
    <div id="pd-historial-cont" class="tarjeta">
      <h2>Últimos movimientos</h2>
      <div id="pd-historial"><div class="vacio">Cargando...</div></div>
    </div>` : ''}`;

  if (puedeEditarDatos) {
    document.getElementById('pd-editar').onclick = () => navegar(`#/producto-editar/${encodeURIComponent(codti)}~${encodeURIComponent(codpro)}`);
  }
  if (puedeEditar) {
    const btnStock = document.getElementById('pd-guardar-stock');
    if (btnStock) {
      btnStock.onclick = async (e) => {
        const cantidad = Number(document.getElementById('pd-cantidad').value);
        const comentario = document.getElementById('pd-comentario').value.trim();
        if (!cantidad || cantidad < 0) { mostrarMensaje(root, 'Indica una cantidad válida.', 'error'); return; }
        if (!comentario) { mostrarMensaje(root, 'Indica el motivo del movimiento.', 'error'); return; }
        e.target.disabled = true;
        try {
          await api('PATCH', `/api/productos/${encodeURIComponent(codpro)}/stock?codti=${encodeURIComponent(codti)}`, {
            tipo: document.getElementById('pd-tipo-mov').value, cantidad, comentario,
          });
          mostrarMensaje(root, 'Movimiento registrado.', 'ok');
          setTimeout(() => pantallaProductoDetalle(param), 600);
        } catch (err) {
          mostrarMensaje(root, err.network ? 'Sin conexión con el servidor.' : err.message, 'error');
          e.target.disabled = false;
        }
      };
    }

    cargarHistorialInventario(codti, codpro);
  }
}

/**
 * Edición del artículo — solo ROOT/ADMIN. Aquí (y solo aquí) se cambian precios y datos del registro; el punto de venta
 * cobra lo que diga Inventario. Tres bloques: datos del artículo (comunes a todas las sucursales), precios de esta
 * sucursal (con opción de aplicarlos en todas) y, en celulares, cada unidad (condición, costo, precio propio).
 */
async function pantallaProductoEditar(param) {
  const [codti, codpro] = param.split('~').map(decodeURIComponent);
  const s = Sesion.obtener();
  if (!SUPERIOR.includes(s.rol)) { root.innerHTML = '<div class="tarjeta">Solo un administrador puede editar productos.</div>'; return; }
  root.innerHTML = '<div class="vacio">Cargando...</div>';
  let p, categorias = [];
  const volver = `#/producto/${encodeURIComponent(codti)}~${encodeURIComponent(codpro)}`;
  try {
    const productos = await api('GET', `/api/productos/tienda/${codti}`);
    p = productos.find(x => x.codpro === codpro);
    if (!p) throw new ApiError('No se encontró el producto.', {});
    try {
      const aplanar = (lista) => lista.flatMap(c => [c, ...(c.subcategorias ? aplanar(c.subcategorias) : [])]);
      categorias = aplanar(await api('GET', '/api/categorias'));
    } catch { /* sin categorías se puede editar lo demás */ }
  } catch (err) {
    root.innerHTML = `<div class="tarjeta"><div class="mensaje error">${escapar(err.network ? 'Sin conexión con el servidor.' : err.message)}</div>
      <button class="btn btn-gris" onclick="navegar('#/inventario')">Ir a inventario</button></div>`;
    return;
  }

  const esEquipo = p.tipo === 'CELULAR' || p.tipo === 'TABLET';
  const esServicio = p.tipo === 'SERVICIO';
  const catsDelTipo = categorias.filter(c => c.tipo === p.tipo);
  const catActual = catsDelTipo.find(c => c.nombre === p.nombreCategoria);
  const ayudaSucursal = `Precios de <strong>${escapar(p.nombreTienda || 'esta sucursal')}</strong>`;

  root.innerHTML = `
    <button class="enlace" id="pe-volver" style="margin-bottom:6px;">${ic('arrow-left')} Volver al producto</button>
    <h1>${ic('pencil')} Editar producto</h1>
    <div class="ayuda" style="margin:-8px 0 14px 0;">${escapar(p.nombreProductoMaster)} · código <strong>${escapar(p.codpro)}</strong> (el código no cambia)</div>

    <div class="tarjeta">
      <h2>Datos del artículo</h2>
      <div class="ayuda">Se aplican en <strong>todas las sucursales</strong>.</div>
      <label class="obligatorio">Nombre</label>
      <input id="pe-nombre" maxlength="255" value="${escapar(p.nombreProductoMaster)}">
      ${catsDelTipo.length ? `
        <label>Categoría</label>
        <select id="pe-categoria">${catsDelTipo.map(c => `<option value="${c.idcat}" ${catActual && catActual.idcat === c.idcat ? 'selected' : ''}>${escapar(c.nombre)}</option>`).join('')}</select>` : ''}
      ${!esEquipo ? `<label>Compatibilidad</label><input id="pe-compat" maxlength="255" value="${escapar(p.compatibilidad || '')}" placeholder="Modelos con los que sirve">` : ''}
      <label>Días de garantía</label>
      <input id="pe-garantia" type="number" inputmode="numeric" min="0" step="1" value="${p.diasGarantia ?? ''}">
      <button id="pe-guardar-datos" class="btn btn-azul">Guardar datos</button>
    </div>

    <div class="tarjeta">
      <h2>Precios y alerta de stock</h2>
      <div class="ayuda">${ayudaSucursal}.</div>
      <div class="fila">
        <div><label>Precio de compra</label><input id="pe-precompra" type="number" inputmode="decimal" step="0.01" min="0" value="${p.preciopro ?? ''}"></div>
        <div><label class="obligatorio">Precio de venta</label><input id="pe-preventa" type="number" inputmode="decimal" step="0.01" min="0.01" value="${p.preciopub ?? ''}"></div>
      </div>
      ${esServicio ? '' : `<label>Stock mínimo (alerta)</label><input id="pe-stockmin" type="number" inputmode="decimal" step="1" min="0" value="${p.stockMinimo ?? 0}">`}
      <label class="casilla"><input type="checkbox" id="pe-todas"> Aplicar el precio de venta en <strong>todas las sucursales</strong></label>
      <button id="pe-guardar-precios" class="btn btn-azul">Guardar precios</button>
    </div>

    ${esEquipo && p.imeisDisponibles?.length ? `
    <div class="tarjeta">
      <h2>Unidades disponibles (${p.imeisDisponibles.length})</h2>
      <div class="ayuda">Cada unidad puede tener su propio costo y precio (por ejemplo un equipo usado). Sin precio propio se cobra el del modelo.</div>
      ${p.imeisDisponibles.map((u, idx) => `
        <div class="unidad-edit" data-idx="${idx}">
          <div class="unidad-imei">${escapar(u.imei)} ${u.tienePrecioPropio ? '<span class="pastilla reparacion">Precio propio</span>' : ''}</div>
          <div class="fila">
            <div><label>Condición</label>
              <select class="ue-condicion">${['NUEVO', 'USADO', 'REACONDICIONADO'].map(c => `<option ${u.condicion === c ? 'selected' : ''}>${c}</option>`).join('')}</select></div>
            <div><label>Costo</label><input class="ue-costo" type="number" inputmode="decimal" step="0.01" min="0" value="${u.costoUnitario ?? ''}"></div>
            <div><label>Precio</label><input class="ue-precio" type="number" inputmode="decimal" step="0.01" min="0.01" value="${u.precioVenta ?? ''}"></div>
          </div>
          <div class="grupo-botones">
            <button class="btn btn-azul btn-chico ue-guardar">Guardar unidad</button>
            ${u.tienePrecioPropio ? '<button class="btn btn-gris btn-chico ue-quitar">Volver al precio del modelo</button>' : ''}
          </div>
        </div>`).join('')}
    </div>` : ''}`;

  document.getElementById('pe-volver').onclick = () => navegar(volver);
  const errorDe = (err) => err.network ? 'Sin conexión con el servidor.' : err.message;
  const numero = (id) => { const v = document.getElementById(id)?.value; return v === '' || v == null ? null : Number(v); };

  document.getElementById('pe-guardar-datos').onclick = async (e) => {
    const nombre = document.getElementById('pe-nombre').value.trim();
    if (!nombre) { mostrarMensaje(root, 'El nombre no puede quedar vacío.', 'error'); return; }
    const cuerpo = { nombreBase: nombre };
    const cat = document.getElementById('pe-categoria');
    if (cat && cat.value) cuerpo.idCategoria = Number(cat.value);
    const compat = document.getElementById('pe-compat');
    if (compat) cuerpo.compatibilidad = compat.value.trim();
    const gar = numero('pe-garantia');
    if (gar != null) cuerpo.diasGarantia = gar;
    e.target.disabled = true;
    try {
      await api('PATCH', `/api/productos/master/${p.idProductoMaster}`, cuerpo);
      mostrarMensaje(root, 'Datos actualizados en todas las sucursales.', 'ok');
      setTimeout(() => pantallaProductoEditar(param), 700);
    } catch (err) { mostrarMensaje(root, errorDe(err), 'error'); e.target.disabled = false; }
  };

  document.getElementById('pe-guardar-precios').onclick = async (e) => {
    const venta = numero('pe-preventa');
    if (venta == null || venta <= 0) { mostrarMensaje(root, 'Indica un precio de venta mayor a cero.', 'error'); return; }
    const todas = document.getElementById('pe-todas').checked;
    const cuerpo = { precioCompra: numero('pe-precompra'), precioVenta: venta };
    if (!esServicio) cuerpo.stockMinimo = numero('pe-stockmin') ?? 0;
    e.target.disabled = true;
    try {
      await api('PUT', `/api/productos/${encodeURIComponent(codpro)}?codti=${encodeURIComponent(codti)}&aTodasLasSucursales=${todas}`, cuerpo);
      mostrarMensaje(root, todas ? 'Precio actualizado en todas las sucursales.' : 'Precios actualizados.', 'ok');
      setTimeout(() => pantallaProductoEditar(param), 700);
    } catch (err) { mostrarMensaje(root, errorDe(err), 'error'); e.target.disabled = false; }
  };

  root.querySelectorAll('.unidad-edit').forEach(fila => {
    const u = p.imeisDisponibles[Number(fila.dataset.idx)];
    fila.querySelector('.ue-guardar').onclick = async (e) => {
      const precio = Number(fila.querySelector('.ue-precio').value);
      const costoTxt = fila.querySelector('.ue-costo').value;
      if (!(precio > 0)) { mostrarMensaje(root, 'Indica un precio mayor a cero para la unidad.', 'error'); return; }
      e.target.disabled = true;
      try {
        await api('PATCH', `/api/productos/imei/${encodeURIComponent(u.imei)}`, {
          condicion: fila.querySelector('.ue-condicion').value,
          costoUnitario: costoTxt === '' ? null : Number(costoTxt),
        });
        // solo se fija precio propio si cambió respecto al que ya cobraba esa unidad
        if (Math.abs(precio - Number(u.precioVenta)) > 0.004) {
          await api('PATCH', `/api/productos/imei/${encodeURIComponent(u.imei)}/precio`, { precioVenta: precio });
        }
        mostrarMensaje(root, 'Unidad actualizada.', 'ok');
        setTimeout(() => pantallaProductoEditar(param), 700);
      } catch (err) { mostrarMensaje(root, errorDe(err), 'error'); e.target.disabled = false; }
    };
    const quitar = fila.querySelector('.ue-quitar');
    if (quitar) quitar.onclick = async (e) => {
      e.target.disabled = true;
      try {
        await api('PATCH', `/api/productos/imei/${encodeURIComponent(u.imei)}/precio`, { precioVenta: null });
        mostrarMensaje(root, 'La unidad vuelve a cobrar el precio del modelo.', 'ok');
        setTimeout(() => pantallaProductoEditar(param), 700);
      } catch (err) { mostrarMensaje(root, errorDe(err), 'error'); e.target.disabled = false; }
    };
  });
}

async function cargarHistorialInventario(codti, codpro) {
  const cont = document.getElementById('pd-historial');
  if (!cont) return;
  try {
    const movs = await api('GET', `/api/productos/tienda/${codti}/movimientos?codpro=${encodeURIComponent(codpro)}`);
    cont.innerHTML = movs.length === 0 ? '<div class="vacio">Sin movimientos en los últimos 30 días.</div>' : movs.slice(0, 20).map(m => `
      <div class="historial-item">
        <div class="historial-comentario">${escapar(m.tipoDisplay)}: ${m.cantidad > 0 ? '+' : ''}${Number(m.cantidad)} (${Number(m.stockAntes)} → ${Number(m.stockDespues)})</div>
        <div class="historial-meta">${escapar(m.usuario)} · ${formatoFecha(m.fecha)}${m.motivo ? ' · ' + escapar(m.motivo) : ''}</div>
      </div>`).join('');
  } catch (err) {
    cont.innerHTML = `<div class="mensaje error">${escapar(err.network ? 'Sin conexión con el servidor.' : err.message)}</div>`;
  }
}

// ── Caja ─────────────────────────────────────────────────────────────────

async function pantallaCaja() {
  const s = Sesion.obtener();
  if (!GESTOR_ROLES.includes(s.rol)) { root.innerHTML = '<div class="tarjeta">No tienes permiso para ver caja.</div>'; return; }
  root.innerHTML = `
    <h1>${ic('receipt')} Caja</h1>
    <div id="cj-tienda"></div>
    <div id="cj-caja"></div>
    <div id="cj-saldo"></div>
    <div class="grupo-botones" style="margin: 10px 0;">
      <button id="cj-nuevo" class="btn btn-verde">+ Nuevo movimiento</button>
    </div>
    <div id="cj-form" class="oculto"></div>
    <h2>Movimientos de hoy</h2>
    <div id="cj-lista"><div class="vacio">Cargando...</div></div>`;

  let codti = s.codti;
  let idCaja = null;
  let motivos = [];

  async function cargarCajaYSaldo() {
    if (codti == null) return;
    const contCaja = document.getElementById('cj-caja');
    try {
      const cajas = await api('GET', '/api/cajas/tienda/' + codti);
      let html = '';
      idCaja = null;
      if (cajas.length === 0) {
        html = '<div class="mensaje info">Esta sucursal no tiene cajas.</div>';
      } else if (cajas.length > 1) {
        html = `<label>Caja</label><select id="cj-idcaja">${cajas.map(c => `<option value="${c.idCaja}">${escapar(c.nombreCaja)}</option>`).join('')}</select>`;
        idCaja = cajas.find(c => c.esCajaPrincipal)?.idCaja ?? cajas[0].idCaja;
      } else {
        idCaja = cajas[0].idCaja;
      }
      if (SUPERIOR.includes(s.rol)) {
        html += `<button id="cj-nueva-caja" class="btn btn-azul btn-chico" style="margin-top:8px;">+ Nueva caja</button><div id="cj-caja-form" class="oculto"></div>`;
      }
      contCaja.innerHTML = html;
      if (cajas.length > 1) {
        document.getElementById('cj-idcaja').value = idCaja;
        document.getElementById('cj-idcaja').onchange = (e) => { idCaja = Number(e.target.value); cargarSaldoYMovimientos(); };
      }
      if (SUPERIOR.includes(s.rol)) {
        document.getElementById('cj-nueva-caja').onclick = () => {
          const contForm = document.getElementById('cj-caja-form');
          contForm.classList.toggle('oculto');
          if (contForm.classList.contains('oculto')) return;
          contForm.innerHTML = `
            <div class="tarjeta">
              <label class="obligatorio">Nombre de la caja</label>
              <input id="ncj-nombre" placeholder="Ej. Caja 2, Caja mostrador">
              <button id="ncj-guardar" class="btn btn-verde">Guardar caja</button>
            </div>`;
          document.getElementById('ncj-guardar').onclick = async (e) => {
            const nombreCaja = document.getElementById('ncj-nombre').value.trim();
            if (!nombreCaja) { mostrarMensaje(root, 'Indica el nombre de la caja.', 'error'); return; }
            e.target.disabled = true;
            try {
              await api('POST', '/api/cajas', { nombreCaja, codti });
              mostrarMensaje(root, `Caja "${nombreCaja}" creada.`, 'ok');
              cargarCajaYSaldo();
            } catch (err) {
              mostrarMensaje(root, err.network ? 'Sin conexión con el servidor.' : err.message, 'error');
              e.target.disabled = false;
            }
          };
        };
      }
      if (idCaja != null) cargarSaldoYMovimientos();
    } catch (err) {
      contCaja.innerHTML = `<div class="mensaje error">${escapar(err.network ? 'Sin conexión con el servidor.' : err.message)}</div>`;
    }
  }

  async function cargarSaldoYMovimientos() {
    if (idCaja == null) return;
    const contSaldo = document.getElementById('cj-saldo');
    const contLista = document.getElementById('cj-lista');
    contSaldo.innerHTML = '<div class="vacio">Cargando...</div>';
    contLista.innerHTML = '<div class="vacio">Cargando...</div>';
    try {
      const saldo = await api('GET', `/api/cajas/${idCaja}/saldo`);
      contSaldo.innerHTML = `
        <div class="fila">
          <div class="tarjeta" style="text-align:center; margin-bottom:0;"><div class="ayuda">Entradas</div><div style="font-size:16px; font-weight:700; color:var(--verde);">${formatoDinero(saldo.totalEntradas)}</div></div>
          <div class="tarjeta" style="text-align:center; margin-bottom:0;"><div class="ayuda">Salidas</div><div style="font-size:16px; font-weight:700; color:var(--rojo);">${formatoDinero(saldo.totalSalidas)}</div></div>
          <div class="tarjeta" style="text-align:center; margin-bottom:0;"><div class="ayuda">Saldo</div><div style="font-size:16px; font-weight:700;">${formatoDinero(saldo.saldo)}</div></div>
        </div>`;
    } catch (err) {
      contSaldo.innerHTML = `<div class="mensaje error">${escapar(err.network ? 'Sin conexión con el servidor.' : err.message)}</div>`;
    }
    try {
      const movs = await api('GET', `/api/cajas/${idCaja}/movimientos`);
      contLista.innerHTML = movs.length === 0 ? '<div class="vacio">Sin movimientos hoy.</div>' : movs.map(m => `
        <div class="historial-item">
          <div class="historial-comentario">${m.tipo === 1 ? ic('plus') : ic('minus')} ${escapar(m.nombreMotivo)} · ${formatoDinero(m.monto)}</div>
          <div class="historial-meta">${escapar(m.nombreEncargado)} · ${formatoFecha(m.fechaMov)}${m.observaciones ? ' · ' + escapar(m.observaciones) : ''}</div>
        </div>`).join('');
    } catch (err) {
      contLista.innerHTML = `<div class="mensaje error">${escapar(err.network ? 'Sin conexión con el servidor.' : err.message)}</div>`;
    }
  }

  const contTienda = document.getElementById('cj-tienda');
  async function cargarTiendas(seleccionar) {
    contTienda.innerHTML = `
      <label class="obligatorio">Sucursal</label>
      <div class="fila">
        <select id="cj-codti"><option>Cargando...</option></select>
        <button id="cj-nueva-sucursal" class="btn btn-azul btn-chico" style="flex:0 0 auto;">+ Nueva</button>
      </div>
      <div id="cj-sucursal-form" class="oculto"></div>`;
    try {
      const tiendas = await api('GET', '/api/tiendas');
      const sel = document.getElementById('cj-codti');
      const visibles = tiendas.filter(t => !t.esAlmacen);
      sel.innerHTML = visibles.map(t => `<option value="${t.codti}">${escapar(t.nombre)}</option>`).join('');
      codti = seleccionar ?? (visibles.find(t => t.codti === s.codti)?.codti ?? visibles[0]?.codti);
      if (codti != null) sel.value = codti;
      sel.onchange = () => { codti = Number(sel.value); cargarCajaYSaldo(); };
    } catch {
      document.getElementById('cj-codti').outerHTML = '<div class="mensaje info">No se pudo cargar la lista de sucursales (sin conexión).</div>';
    }
    document.getElementById('cj-nueva-sucursal').onclick = () => {
      const cont = document.getElementById('cj-sucursal-form');
      cont.classList.toggle('oculto');
      if (cont.classList.contains('oculto')) return;
      cont.innerHTML = `
        <div class="tarjeta">
          <h2 style="margin-top:0;">Nueva sucursal</h2>
          <label class="obligatorio">Nombre</label>
          <input id="ns-nombre">
          <label>Domicilio</label>
          <input id="ns-ubicacion" placeholder="Opcional">
          <label>Teléfono</label>
          <input id="ns-telefono" inputmode="tel" placeholder="Opcional">
          <label style="display:flex; align-items:center; gap:8px; font-weight:400; text-transform:none;">
            <input type="checkbox" id="ns-almacen" style="width:auto;"> Es almacén (sin caja ni venta al público)
          </label>
          <button id="ns-guardar" class="btn btn-verde">Guardar sucursal</button>
        </div>`;
      document.getElementById('ns-guardar').onclick = async (e) => {
        const nombre = document.getElementById('ns-nombre').value.trim();
        if (!nombre) { mostrarMensaje(root, 'El nombre de la sucursal es obligatorio.', 'error'); return; }
        e.target.disabled = true;
        e.target.innerHTML = '<span class="spinner"></span> Guardando...';
        try {
          const nueva = await api('POST', '/api/tiendas', {
            nombre,
            ubicacion: document.getElementById('ns-ubicacion').value.trim() || null,
            telefono: document.getElementById('ns-telefono').value.trim() || null,
            esAlmacen: document.getElementById('ns-almacen').checked,
          });
          mostrarMensaje(root, `Sucursal "${nueva.nombre}" creada.`, 'ok');
          await cargarTiendas(nueva.esAlmacen ? undefined : nueva.codti);
          cargarCajaYSaldo();
        } catch (err) {
          mostrarMensaje(root, err.network ? 'Sin conexión con el servidor.' : err.message, 'error');
          e.target.disabled = false;
          e.target.textContent = 'Guardar sucursal';
        }
      };
    };
  }
  if (SUPERIOR.includes(s.rol)) {
    await cargarTiendas();
  } else {
    contTienda.innerHTML = '<div class="ayuda">Sucursal: <strong>tu tienda</strong></div>';
  }
  cargarCajaYSaldo();

  document.getElementById('cj-nuevo').onclick = async () => {
    const cont = document.getElementById('cj-form');
    cont.classList.toggle('oculto');
    if (cont.classList.contains('oculto')) return;
    if (motivos.length === 0) {
      try { motivos = (await api('GET', '/api/motivos-caja')).filter(m => !m.delSistema); }
      catch { mostrarMensaje(root, 'No se pudieron cargar los motivos (sin conexión).', 'error'); return; }
    }
    cont.innerHTML = `
      <div class="tarjeta">
        <h2>Nuevo movimiento</h2>
        <label class="obligatorio">Motivo</label>
        <select id="cj-motivo">${motivos.map(m => `<option value="${m.idmotivo}">${m.tipoMov === 1 ? '+' : '−'} ${escapar(m.nombre)}</option>`).join('')}</select>
        <label class="obligatorio">Monto</label>
        <input id="cj-monto" type="number" inputmode="decimal" min="0" step="0.01">
        <label>Observaciones</label>
        <input id="cj-obs" placeholder="Opcional">
        <button id="cj-guardar" class="btn btn-verde">Registrar movimiento</button>
      </div>`;
    document.getElementById('cj-guardar').onclick = async (e) => {
      const monto = Number(document.getElementById('cj-monto').value);
      if (!monto || monto <= 0) { mostrarMensaje(root, 'Indica un monto válido.', 'error'); return; }
      e.target.disabled = true;
      try {
        await api('POST', `/api/cajas/${idCaja}/movimientos`, {
          idmotivo: Number(document.getElementById('cj-motivo').value),
          monto,
          observaciones: document.getElementById('cj-obs').value.trim() || null,
        });
        mostrarMensaje(root, 'Movimiento registrado.', 'ok');
        cont.classList.add('oculto');
        cargarSaldoYMovimientos();
      } catch (err) {
        mostrarMensaje(root, err.network ? 'Sin conexión con el servidor.' : err.message, 'error');
        e.target.disabled = false;
      }
    };
  };
}

// ── Empleados ────────────────────────────────────────────────────────────

const ROLES_STAFF = ['ROOT', 'ADMIN', 'ENCARGADO_TIENDA', 'VENDEDOR', 'TECNICO'];
const ROL_ETIQUETA = { ROOT: 'Administrador', ADMIN: 'Administrador', ENCARGADO_TIENDA: 'Encargado de tienda', VENDEDOR: 'Vendedor', TECNICO: 'Técnico' };

async function pantallaEmpleados() {
  const s = Sesion.obtener();
  if (!SUPERIOR.includes(s.rol)) { root.innerHTML = '<div class="tarjeta">No tienes permiso para ver empleados.</div>'; return; }
  root.innerHTML = `
    <h1>${ic('briefcase')} Empleados</h1>
    <div class="buscador">
      <input id="em-buscar" placeholder="Nombre o usuario">
      <button id="em-nuevo" class="btn btn-verde btn-chico">+ Nuevo</button>
    </div>
    <div id="em-form" class="oculto"></div>
    <div id="em-lista"><div class="vacio">Cargando...</div></div>`;

  const cont = document.getElementById('em-lista');
  let empleados = [];
  try {
    empleados = await api('GET', '/api/usuarios?soloActivos=true');
  } catch (err) {
    cont.innerHTML = `<div class="mensaje error">${escapar(err.network ? 'Sin conexión con el servidor.' : err.message)}</div>`;
    return;
  }

  const dibujar = (lista) => {
    if (lista.length === 0) { cont.innerHTML = '<div class="vacio">No hay empleados que coincidan.</div>'; return; }
    cont.innerHTML = lista.map(u => `
      <div class="orden-item" data-id="${u.idusuario}">
        <div class="orden-cab">
          <span class="orden-folio">${escapar(u.nombreCompleto)}</span>
          <span class="pastilla recibida">${escapar(ROL_ETIQUETA[u.rol] || u.rol)}</span>
        </div>
        <div class="orden-cliente">@${escapar(u.username)} · ${escapar(u.nombreTienda || 'Sin sucursal')}${u.tecnicoEncargado ? ' · Técnico encargado' : ''}</div>
      </div>`).join('');
    cont.querySelectorAll('.orden-item').forEach(el => el.onclick = () => navegar('#/empleado/' + el.dataset.id));
  };
  dibujar(empleados);

  document.getElementById('em-buscar').addEventListener('input', e => {
    const q = e.target.value.trim().toLowerCase();
    dibujar(!q ? empleados : empleados.filter(u => (u.nombreCompleto || '').toLowerCase().includes(q) || (u.username || '').toLowerCase().includes(q)));
  });

  document.getElementById('em-nuevo').onclick = () => {
    const cont2 = document.getElementById('em-form');
    cont2.classList.toggle('oculto');
    if (!cont2.classList.contains('oculto')) dibujarFormularioEmpleado(cont2, null, () => { navegar('#/empleados'); render(); });
  };
}

async function dibujarFormularioEmpleado(cont, empleado, alGuardar) {
  let tiendas = [];
  try { tiendas = await api('GET', '/api/tiendas'); } catch { /* sin conexión: se deja vacío */ }

  cont.innerHTML = `
    <div class="tarjeta">
      <h2>${empleado ? 'Editar empleado' : 'Nuevo empleado'}</h2>
      ${!empleado ? `
      <label class="obligatorio">Usuario</label>
      <input id="ef-username" autocapitalize="off">
      <label class="obligatorio">Contraseña</label>
      <input id="ef-password" type="password">` : `
      <label>Nueva contraseña</label>
      <input id="ef-password" type="password" placeholder="Déjalo en blanco para no cambiarla">`}
      <label class="obligatorio">Nombre completo</label>
      <input id="ef-nombre" value="${escapar(empleado?.nombreCompleto || '')}">
      <label class="obligatorio">Rol</label>
      <select id="ef-rol">${ROLES_STAFF.map(r => `<option value="${r}" ${empleado?.rol === r ? 'selected' : ''}>${ROL_ETIQUETA[r]}</option>`).join('')}</select>
      <label class="obligatorio">Sucursal</label>
      <select id="ef-tienda">${tiendas.map(t => `<option value="${t.codti}" ${empleado?.codti === t.codti ? 'selected' : ''}>${escapar(t.nombre)}</option>`).join('')}</select>
      <div id="ef-tecnico-cont" class="oculto">
        <label style="display:flex; align-items:center; gap:8px; font-weight:400; text-transform:none;">
          <input type="checkbox" id="ef-tec-encargado" style="width:auto;" ${empleado?.tecnicoEncargado ? 'checked' : ''}> Técnico encargado
        </label>
      </div>
      <label>Teléfono</label>
      <input id="ef-tel" inputmode="tel" value="${escapar(empleado?.telefono || '')}">
      <label>Correo</label>
      <input id="ef-correo" type="email" value="${escapar(empleado?.email || '')}">
      <label>Sueldo base</label>
      <input id="ef-sueldo" type="number" inputmode="decimal" step="0.01" value="${empleado?.sueldoBase ?? ''}">
      <button id="ef-guardar" class="btn btn-verde">Guardar</button>
    </div>`;

  const selRol = document.getElementById('ef-rol');
  const contTec = document.getElementById('ef-tecnico-cont');
  const actualizarTec = () => contTec.classList.toggle('oculto', selRol.value !== 'TECNICO');
  selRol.onchange = actualizarTec;
  actualizarTec();

  document.getElementById('ef-guardar').onclick = async (e) => {
    const nombreCompleto = document.getElementById('ef-nombre').value.trim();
    const codti = Number(document.getElementById('ef-tienda').value);
    if (!nombreCompleto) { mostrarMensaje(root, 'El nombre es obligatorio.', 'error'); return; }
    if (!codti) { mostrarMensaje(root, 'Indica la sucursal.', 'error'); return; }

    const payload = {
      nombreCompleto,
      codti,
      rol: selRol.value,
      tecnicoEncargado: selRol.value === 'TECNICO' ? document.getElementById('ef-tec-encargado').checked : null,
      telefono: document.getElementById('ef-tel').value.trim() || null,
      email: document.getElementById('ef-correo').value.trim() || null,
      sueldoBase: Number(document.getElementById('ef-sueldo').value) || null,
    };
    const password = document.getElementById('ef-password').value;
    if (!empleado) {
      const username = document.getElementById('ef-username').value.trim();
      if (!username || !password) { mostrarMensaje(root, 'Usuario y contraseña son obligatorios.', 'error'); return; }
      payload.username = username;
      payload.password = password;
    } else if (password) {
      payload.password = password;
    }

    e.target.disabled = true;
    try {
      if (empleado) await api('PUT', '/api/usuarios/' + empleado.idusuario, payload);
      else await api('POST', '/api/usuarios', payload);
      alGuardar();
    } catch (err) {
      mostrarMensaje(root, err.network ? 'Sin conexión: esta acción necesita conexión con el servidor.' : err.message, 'error');
      e.target.disabled = false;
    }
  };
}

async function pantallaEmpleadoDetalle(id) {
  const s = Sesion.obtener();
  if (!SUPERIOR.includes(s.rol)) { root.innerHTML = '<div class="tarjeta">No tienes permiso para ver empleados.</div>'; return; }
  root.innerHTML = '<div class="vacio">Cargando...</div>';
  let u;
  try {
    u = await api('GET', '/api/usuarios/' + id);
  } catch (err) {
    root.innerHTML = `<div class="tarjeta"><div class="mensaje error">${escapar(err.network ? 'Sin conexión con el servidor.' : err.message)}</div>
      <button class="btn btn-gris" onclick="navegar('#/empleados')">Ir a empleados</button></div>`;
    return;
  }

  root.innerHTML = `
    <h1>${escapar(u.nombreCompleto)}</h1>
    <div class="tarjeta">
      <span class="pastilla recibida">${escapar(ROL_ETIQUETA[u.rol] || u.rol)}</span>
      <div class="detalle-fila"><span class="k">Usuario</span><span class="v">@${escapar(u.username)}</span></div>
      <div class="detalle-fila"><span class="k">Sucursal</span><span class="v">${escapar(u.nombreTienda || '—')}</span></div>
      ${u.rol === 'TECNICO' ? `<div class="detalle-fila"><span class="k">Técnico encargado</span><span class="v">${u.tecnicoEncargado ? 'Sí' : 'No'}</span></div>` : ''}
      <div class="detalle-fila"><span class="k">Teléfono</span><span class="v">${escapar(u.telefono || '—')}</span></div>
      <div class="detalle-fila"><span class="k">Correo</span><span class="v">${escapar(u.email || '—')}</span></div>
      <div class="detalle-fila"><span class="k">Sueldo base</span><span class="v">${formatoDinero(u.sueldoBase)}</span></div>
      <div class="detalle-fila"><span class="k">Fecha de ingreso</span><span class="v">${u.fechaIngreso || '—'}</span></div>
      <div class="detalle-fila"><span class="k">De alta desde</span><span class="v">${formatoFecha(u.fechaAlta)}</span></div>
    </div>
    <div id="ed-form"></div>
    <div class="grupo-botones">
      <button id="ed-editar" class="btn btn-azul">${ic('pencil')} Editar</button>
      <button id="ed-desactivar" class="btn btn-rojo">${ic('trash-2')} Desactivar</button>
    </div>`;

  document.getElementById('ed-editar').onclick = () => dibujarFormularioEmpleado(document.getElementById('ed-form'), u, () => pantallaEmpleadoDetalle(id));
  document.getElementById('ed-desactivar').onclick = async () => {
    if (!confirm('¿Desactivar a ' + u.nombreCompleto + '? Ya no podrá iniciar sesión.')) return;
    try { await api('DELETE', '/api/usuarios/' + id); navegar('#/empleados'); }
    catch (err) { mostrarMensaje(root, err.network ? 'Sin conexión con el servidor.' : err.message, 'error'); }
  };
}

// ── Punto de venta ───────────────────────────────────────────────────────
// v1: solo en línea (sin cola sin conexión todavía; si no hay servidor, usa JSystem en la tienda).
// Sin pago Mixto ni ventas a crédito — para eso, JSystem.

let posCarrito = [];

// ── Búsqueda de productos y catálogo del POS ─────────────────────────────

function quitarAcentos(t) { return t.normalize('NFD').replace(/[̀-ͯ]/g, ''); }

/** Texto en minúsculas y sin acentos con lo que se puede buscar de un producto (se calcula una vez por producto). */
function textoBusqueda(p) {
  if (p._t === undefined) {
    p._t = quitarAcentos(`${p.nombreProductoMaster || ''} ${p.codpro || ''} ${p.marca || ''} ${p.modelo || ''} ${p.nombreCategoria || ''} ` +
      `${p.color?.nombre || ''} ${(p.imeisDisponibles || []).map(u => u.imei).join(' ')}`).toLowerCase();
  }
  return p._t;
}
/** Todas las palabras escritas deben aparecer (en cualquier orden): "protector a17" encuentra "Protector Antigolpe Samsung A17". */
function filtrarProductos(productos, q) {
  const terminos = quitarAcentos(q).toLowerCase().split(/\s+/).filter(Boolean);
  if (terminos.length === 0) return productos;
  return productos.filter(p => { const t = textoBusqueda(p); return terminos.every(x => t.includes(x)); });
}

/**
 * Catálogo del punto de venta de una sucursal (~2,000 productos). Si ya se tenía se devuelve al instante y se
 * refresca en segundo plano (alActualizar recibe la lista nueva); el servidor valida el stock al cobrar.
 */
const posCatalogoCache = { codti: null, productos: null };
async function cargarCatalogoPOS(codti, alActualizar) {
  const pedir = async () => {
    const productos = await api('GET', `/api/productos/tienda/${codti}/disponibles`);
    posCatalogoCache.codti = codti; posCatalogoCache.productos = productos;
    return productos;
  };
  if (posCatalogoCache.codti === codti && posCatalogoCache.productos) {
    pedir().then(alActualizar).catch(() => { /* se sigue con lo que ya había */ });
    return posCatalogoCache.productos;
  }
  return pedir();
}

async function pantallaPOS() {
  const s = Sesion.obtener();
  if (!PERSONAL.includes(s.rol)) { root.innerHTML = '<div class="tarjeta">No tienes permiso para vender.</div>'; return; }
  const codti = s.codti;
  if (codti == null) { root.innerHTML = '<div class="tarjeta">Tu usuario no tiene una sucursal fija: usa JSystem en la tienda para vender.</div>'; return; }
  posCarrito = [];

  root.innerHTML = `
    <h1>${ic('shopping-cart')} Punto de venta</h1>
    <div class="buscador">
      <input id="pv-buscar" placeholder="Buscar producto...">
    </div>
    <div id="pv-resultados"></div>

    <h2 style="margin-top:16px;">Carrito</h2>
    <div id="pv-carrito"><div class="vacio">Agrega productos arriba.</div></div>
    <div id="pv-total" class="barra-total"></div>

    <div class="tarjeta">
      <h2 style="margin-top:0;">Cliente</h2>
      <label style="display:flex; align-items:center; gap:8px; font-weight:400; text-transform:none;">
        <input type="checkbox" id="pv-con-cliente" style="width:auto;"> Registrar cliente (si no, venta de mostrador)
      </label>
      <div id="pv-cliente-cont" class="oculto"></div>
    </div>

    <div class="tarjeta">
      <h2 style="margin-top:0;">Pago</h2>
      <label class="obligatorio">Método</label>
      <select id="pv-metodo">
        <option value="1">Efectivo</option>
        <option value="2">Tarjeta</option>
        <option value="3">Transferencia</option>
        <option value="5">PayJoy</option>
      </select>
      <div id="pv-efectivo-cont">
        <label>Monto recibido</label>
        <input id="pv-recibido" type="number" inputmode="decimal" min="0" step="0.01">
        <div id="pv-cambio" class="ayuda"></div>
      </div>
      <label>Observaciones</label>
      <input id="pv-obs" placeholder="Opcional">
      <button id="pv-cobrar" class="btn btn-verde btn-grande">${ic('banknote')} Cobrar</button>
    </div>`;

  // El catálogo y la caja se piden a la vez (y el catálogo se usa al instante si ya se tenía: ver cargarCatalogoPOS)
  const promesaCajas = api('GET', '/api/cajas/tienda/' + codti).catch(() => null);
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
  inputBuscar.disabled = false;
  inputBuscar.placeholder = 'Buscar por nombre, código, modelo o IMEI...';

  // Caja de la tienda (se valida de nuevo al cobrar)
  let idCaja = null;
  const cajas = await promesaCajas;
  if (cajas) idCaja = cajas.find(c => c.esCajaPrincipal)?.idCaja ?? cajas[0]?.idCaja ?? null;

  const contResultados = document.getElementById('pv-resultados');
  inputBuscar.addEventListener('input', (e) => {
    const q = e.target.value.trim();
    if (q.length < 2) { contResultados.innerHTML = ''; return; }
    dibujarResultadosPOS(contResultados, filtrarProductos(productos, q).slice(0, 20));
  });

  // Cliente
  const chkCliente = document.getElementById('pv-con-cliente');
  const contCliente = document.getElementById('pv-cliente-cont');
  let clienteSel = null;
  chkCliente.onchange = () => {
    contCliente.classList.toggle('oculto', !chkCliente.checked);
    if (chkCliente.checked && !contCliente.innerHTML) dibujarSelectorClientePOS(contCliente, (c) => { clienteSel = c; });
  };

  // Pago
  const selMetodo = document.getElementById('pv-metodo');
  const contEfectivo = document.getElementById('pv-efectivo-cont');
  const actualizarMetodo = () => contEfectivo.classList.toggle('oculto', selMetodo.value !== '1');
  selMetodo.onchange = actualizarMetodo;
  actualizarMetodo();
  document.getElementById('pv-recibido').addEventListener('input', actualizarCambioPOS);

  actualizarCarritoPOS();

  document.getElementById('pv-cobrar').onclick = async (e) => {
    if (posCarrito.length === 0) { mostrarMensaje(root, 'Agrega al menos un producto.', 'error'); return; }
    if (!idCaja) { mostrarMensaje(root, 'No se encontró una caja para tu sucursal.', 'error'); return; }
    const metodoPago = Number(selMetodo.value);
    const total = posCarrito.reduce((sum, i) => sum + i.cantidad * i.precioUnitarioFinal, 0);
    let montoAbonado = total;
    if (metodoPago === 1) {
      montoAbonado = Number(document.getElementById('pv-recibido').value);
      if (!montoAbonado || montoAbonado < total) { mostrarMensaje(root, 'El monto recibido debe cubrir el total.', 'error'); return; }
    }
    if (chkCliente.checked && !clienteSel) { mostrarMensaje(root, 'Busca o registra al cliente, o desmarca la casilla.', 'error'); return; }

    const payload = {
      codti, idCaja,
      idcliente: clienteSel?.idcliente ?? null,
      tipoComprobante: 1,
      metodoPago,
      montoAbonado,
      observaciones: document.getElementById('pv-obs').value.trim() || null,
      claveOffline: uuid(),
      detalles: posCarrito.map(i => ({
        idprodmaster: i.idprodmaster, codpro: i.codpro, cantidad: i.cantidad,
        precioUnitarioFinal: i.precioUnitarioFinal, imei: i.imei || null, esRegalo: false,
      })),
    };

    e.target.disabled = true;
    e.target.innerHTML = '<span class="spinner"></span> Cobrando...';
    try {
      const venta = await api('POST', '/api/ventas', payload);
      mostrarMensaje(root, `Venta #${venta.idventa} registrada. Total ${formatoDinero(venta.total)}${metodoPago === 1 ? ' · Cambio ' + formatoDinero(venta.cambio) : ''}.`, 'ok');
      posCarrito = [];
      posCatalogoCache.productos = null;   // el stock cambió: el próximo POS pide el catálogo fresco
      setTimeout(() => pantallaPOS(), 1200);
    } catch (err) {
      mostrarMensaje(root, err.network ? 'Sin conexión con el servidor: esta venta no se guardó. Usa JSystem en la tienda mientras tanto.' : err.message, 'error');
      e.target.disabled = false;
      e.target.innerHTML = ic('banknote') + ' Cobrar';
    }
  };
}

function dibujarResultadosPOS(cont, productos) {
  if (productos.length === 0) { cont.innerHTML = '<div class="vacio">Sin resultados.</div>'; return; }
  cont.innerHTML = productos.map(p => `
    <div class="orden-item" data-codpro="${escapar(p.codpro)}">
      <div class="orden-cab">
        <span class="orden-folio">${TIPO_ICONO[p.tipo] || ''} ${escapar(p.nombreProductoMaster)}</span>
        <span style="font-weight:700; white-space:nowrap;">${formatoDinero(p.preciopub)}${p.tipo !== 'CELULAR' ? ' <span class="pv-mas">' + ic('plus') + '</span>' : ''}</span>
      </div>
      <div class="orden-cliente">${escapar(p.codpro)}${p.tipo !== 'SERVICIO' ? ' · Stock: ' + Number(p.stock) : ''}</div>
      ${p.tipo === 'CELULAR' && p.imeisDisponibles?.length ? `
        <div class="grupo-botones" style="margin-top:8px;">
          ${p.imeisDisponibles.map(u => `<button class="btn btn-azul btn-chico pv-agregar-imei" data-imei="${escapar(u.imei)}">${escapar(u.imei)} · ${formatoDinero(u.precioVenta)}</button>`).join('')}
        </div>` : ''}
    </div>`).join('');

  cont.querySelectorAll('.orden-item').forEach(el => {
    const p = productos.find(x => x.codpro === el.dataset.codpro);
    if (p.tipo === 'CELULAR') {
      el.querySelectorAll('.pv-agregar-imei').forEach(btn => btn.onclick = (ev) => {
        ev.stopPropagation();
        const unidad = p.imeisDisponibles.find(u => u.imei === btn.dataset.imei);
        agregarAlCarritoPOS(p, 1, unidad.precioVenta, unidad.imei);
      });
    } else {
      el.onclick = () => agregarAlCarritoPOS(p, 1, p.preciopub, null);
    }
  });
}

function agregarAlCarritoPOS(p, cantidad, precio, imei) {
  const existente = imei ? null : posCarrito.find(i => i.codpro === p.codpro && !i.imei);
  if (existente) { existente.cantidad += cantidad; }
  else {
    posCarrito.push({
      idprodmaster: p.idProductoMaster, codpro: p.codpro, nombre: p.nombreProductoMaster,
      cantidad, precioUnitarioFinal: Number(precio), imei, tipo: p.tipo, stockMax: p.tipo === 'ACCESORIO' ? Number(p.stock) : null,
    });
  }
  actualizarCarritoPOS();
}

function actualizarCarritoPOS() {
  const cont = document.getElementById('pv-carrito');
  const contTotal = document.getElementById('pv-total');
  if (!cont) return;
  if (posCarrito.length === 0) { cont.innerHTML = '<div class="vacio">Agrega productos arriba.</div>'; }
  else {
    cont.innerHTML = posCarrito.map((i, idx) => `
      <div class="orden-item">
        <div class="orden-cab">
          <span class="orden-folio">${escapar(i.nombre)}</span>
          <button class="btn btn-rojo btn-chico pv-quitar" data-idx="${idx}">Quitar</button>
        </div>
        ${i.imei ? `<div class="orden-cliente">IMEI: ${escapar(i.imei)}</div>` : ''}
        <div class="fila" style="margin-top:8px; align-items:center;">
          ${!i.imei ? `<div><label style="margin:0 0 2px 0;">Cantidad</label><input type="number" min="1" ${i.stockMax ? 'max="' + i.stockMax + '"' : ''} value="${i.cantidad}" class="pv-cantidad" data-idx="${idx}"></div>` : ''}
          <div><label style="margin:0 0 2px 0;">Precio unitario</label><div class="precio-fijo" title="El precio lo define Inventario; no se cambia al vender">${ic('lock')} ${formatoDinero(i.precioUnitarioFinal)}</div></div>
        </div>
        <div class="orden-meta"><span class="ayuda" style="margin:0;">${i.cantidad} × ${formatoDinero(i.precioUnitarioFinal)}</span><span class="importe-linea">${formatoDinero(i.cantidad * i.precioUnitarioFinal)}</span></div>
      </div>`).join('');

    cont.querySelectorAll('.pv-quitar').forEach(b => b.onclick = () => { posCarrito.splice(Number(b.dataset.idx), 1); actualizarCarritoPOS(); });
    cont.querySelectorAll('.pv-cantidad').forEach(inp => inp.onchange = () => {
      const idx = Number(inp.dataset.idx);
      let v = Math.max(1, Number(inp.value) || 1);
      if (posCarrito[idx].stockMax) v = Math.min(v, posCarrito[idx].stockMax);
      posCarrito[idx].cantidad = v;
      actualizarCarritoPOS();
    });
  }
  const total = posCarrito.reduce((sum, i) => sum + i.cantidad * i.precioUnitarioFinal, 0);
  const piezas = posCarrito.reduce((sum, i) => sum + i.cantidad, 0);
  contTotal.innerHTML = `<span class="bt-detalle">${piezas} ${piezas === 1 ? 'artículo' : 'artículos'}</span><span class="bt-total"><small>Total</small> ${formatoDinero(total)}</span>`;
  actualizarCambioPOS();
}

function actualizarCambioPOS() {
  const inp = document.getElementById('pv-recibido');
  const contCambio = document.getElementById('pv-cambio');
  if (!inp || !contCambio) return;
  const total = posCarrito.reduce((sum, i) => sum + i.cantidad * i.precioUnitarioFinal, 0);
  const recibido = Number(inp.value) || 0;
  contCambio.textContent = recibido > 0 ? 'Cambio: ' + formatoDinero(Math.max(0, recibido - total)) : '';
}

function dibujarSelectorClientePOS(cont, alSeleccionar) {
  const s = Sesion.obtener();
  const puedeRegistrar = GESTOR_ROLES.includes(s.rol);
  cont.innerHTML = `
    <label>Teléfono</label>
    <div class="fila">
      <input id="pv-cli-tel" inputmode="tel" placeholder="10 dígitos">
      <button id="pv-cli-buscar" class="btn btn-azul btn-chico" style="flex:0 0 auto;">Buscar</button>
    </div>
    <div id="pv-cli-resultado" class="ayuda"></div>
    <div id="pv-cli-nuevo-cont" class="oculto">
      ${puedeRegistrar ? `
        <label class="obligatorio">Nombre del cliente nuevo</label>
        <div class="fila">
          <input id="pv-cli-nombre">
          <button id="pv-cli-registrar" class="btn btn-verde btn-chico" style="flex:0 0 auto;">Registrar</button>
        </div>
      ` : `<div class="ayuda">No tienes permiso para registrar clientes nuevos. Pide a un encargado que lo registre primero, o continúa la venta sin cliente.</div>`}
    </div>`;
  document.getElementById('pv-cli-buscar').onclick = async () => {
    const tel = document.getElementById('pv-cli-tel').value.trim();
    const out = document.getElementById('pv-cli-resultado');
    const contNuevo = document.getElementById('pv-cli-nuevo-cont');
    if (!tel) return;
    out.textContent = 'Buscando...';
    try {
      const c = await api('GET', '/api/clientes/telefono/' + encodeURIComponent(tel));
      out.innerHTML = `${ic('circle-check')} <strong>${escapar(c.nombreCompleto)}</strong>`;
      contNuevo.classList.add('oculto');
      alSeleccionar({ idcliente: c.idcliente });
    } catch {
      out.innerHTML = ic('triangle-alert') + ' No se encontró.';
      contNuevo.classList.remove('oculto');
      alSeleccionar(null);
      const btnRegistrar = document.getElementById('pv-cli-registrar');
      if (btnRegistrar) {
        btnRegistrar.onclick = async () => {
          const nombre = document.getElementById('pv-cli-nombre').value.trim();
          if (!nombre) return;
          btnRegistrar.disabled = true;
          try {
            const nuevo = await api('POST', '/api/clientes', { nombreCompleto: nombre, telefono: tel });
            out.innerHTML = `${ic('circle-check')} <strong>${escapar(nuevo.nombreCompleto)}</strong> (registrado)`;
            contNuevo.classList.add('oculto');
            alSeleccionar({ idcliente: nuevo.idcliente });
          } catch (err) {
            out.innerHTML = `<div class="mensaje error">${escapar(err.network ? 'Sin conexión con el servidor.' : err.message)}</div>`;
            btnRegistrar.disabled = false;
          }
        };
      }
    }
  };
}

// ── Devoluciones ─────────────────────────────────────────────────────────
// v1: solo Reembolso. Los cambios por otro producto se atienden en la tienda con JSystem.

function pastillaDevolucion(estadoDisplay) {
  const claves = { 'Pendiente': 'pendiente', 'Procesada': 'lista', 'Rechazada': 'cancelada' };
  return `<span class="pastilla ${claves[estadoDisplay] || 'pendiente'}">${escapar(estadoDisplay)}</span>`;
}

async function pantallaDevoluciones() {
  const s = Sesion.obtener();
  if (!PERSONAL.includes(s.rol)) { root.innerHTML = '<div class="tarjeta">No tienes permiso para ver devoluciones.</div>'; return; }

  root.innerHTML = `
    <h1>${ic('undo-2')} Devoluciones</h1>
    <div class="tarjeta">
      <h2 style="margin-top:0;">Solicitar devolución</h2>
      <label class="obligatorio">ID de venta</label>
      <div class="fila">
        <input id="dv-idventa" type="number" inputmode="numeric" placeholder="Ej. 51">
        <button id="dv-consultar" class="btn btn-azul btn-chico" style="flex:0 0 auto;">Consultar</button>
      </div>
      <div id="dv-consulta-resultado"></div>
      <div class="ayuda">Solo reembolso. Para cambiar por otro producto, usa JSystem en la tienda.</div>
    </div>

    <h2 style="margin-top:16px;">Historial</h2>
    <div class="segmentado">
      <button id="dv-seg-pendientes" class="activo">Pendientes</button>
      <button id="dv-seg-todas">Todas</button>
    </div>
    <div id="dv-lista"><div class="vacio">Cargando...</div></div>`;

  document.getElementById('dv-consultar').onclick = () => consultarVentaDevolucion();
  document.getElementById('dv-idventa').addEventListener('keydown', e => { if (e.key === 'Enter') consultarVentaDevolucion(); });

  async function consultarVentaDevolucion() {
    const idventa = Number(document.getElementById('dv-idventa').value);
    const cont = document.getElementById('dv-consulta-resultado');
    if (!idventa) return;
    cont.innerHTML = '<div class="vacio">Consultando...</div>';
    try {
      const venta = await api('GET', `/api/devoluciones/venta/${idventa}`);
      dibujarConsultaVenta(cont, venta);
    } catch (err) {
      cont.innerHTML = `<div class="mensaje error">${escapar(err.network ? 'Sin conexión con el servidor.' : err.message)}</div>`;
    }
  }

  function dibujarConsultaVenta(cont, venta) {
    if (!venta.elegible) {
      cont.innerHTML = `<div class="mensaje error">${escapar(venta.motivo || 'Esta venta no admite devoluciones.')}</div>`;
      return;
    }
    const devolvibles = (venta.lineas || []).filter(l => l.disponible > 0);
    if (devolvibles.length === 0) {
      cont.innerHTML = `<div class="mensaje info">Ya se devolvió todo lo de esta venta.</div>`;
      return;
    }
    cont.innerHTML = `
      <div class="ayuda">Venta #${venta.idventa} · ${escapar(venta.nombreCliente || 'Público general')} · ${formatoDinero(venta.total)}
        ${venta.devolucionHasta ? ' · Plazo hasta ' + escapar(venta.devolucionHasta) : ''}</div>
      ${devolvibles.map(l => `
        <div style="border-top:1px solid var(--gris-claro); padding:10px 0;">
          <label style="display:flex; align-items:center; gap:8px; font-weight:400; text-transform:none;">
            <input type="checkbox" class="dv-linea-chk" data-idx="${l.iddetalleVenta}" style="width:auto;">
            ${escapar(l.nombreProducto)}${l.imei ? ' · IMEI ' + escapar(l.imei) : ''} — ${formatoDinero(l.precioUnitario)} c/u (disponible: ${l.disponible})
          </label>
          ${!l.imei && l.disponible > 1 ? `<div style="margin-left:26px; margin-top:4px;"><label>Cantidad a devolver</label>
            <input type="number" class="dv-linea-cant" data-idx="${l.iddetalleVenta}" min="1" max="${l.disponible}" value="${l.disponible}" style="width:80px;" disabled></div>` : ''}
          <label style="display:flex; align-items:center; gap:8px; font-weight:400; text-transform:none; margin-left:26px; margin-top:4px;">
            <input type="checkbox" class="dv-linea-danado" data-idx="${l.iddetalleVenta}" style="width:auto;"> Dañado (no regresa a inventario)
          </label>
        </div>`).join('')}
      <label class="obligatorio" style="margin-top:10px;">Motivo</label>
      <textarea id="dv-motivo" placeholder="Por qué se devuelve"></textarea>
      <label class="obligatorio">Cómo se reembolsa</label>
      <select id="dv-metodo-reembolso">
        <option value="1">Efectivo</option>
        <option value="2">Tarjeta</option>
        <option value="3">Transferencia</option>
      </select>
      <button id="dv-solicitar" class="btn btn-verde" style="margin-top:10px;">Solicitar devolución</button>`;

    cont.querySelectorAll('.dv-linea-chk').forEach(chk => {
      chk.addEventListener('change', () => {
        const cantInput = cont.querySelector(`.dv-linea-cant[data-idx="${chk.dataset.idx}"]`);
        if (cantInput) cantInput.disabled = !chk.checked;
      });
    });

    document.getElementById('dv-solicitar').onclick = async (e) => {
      const lineas = [];
      cont.querySelectorAll('.dv-linea-chk:checked').forEach(chk => {
        const idx = chk.dataset.idx;
        const linea = devolvibles.find(l => String(l.iddetalleVenta) === idx);
        const cantInput = cont.querySelector(`.dv-linea-cant[data-idx="${idx}"]`);
        const cantidad = cantInput ? Number(cantInput.value) : 1;
        const danado = cont.querySelector(`.dv-linea-danado[data-idx="${idx}"]`)?.checked;
        lineas.push({ iddetalleVenta: linea.iddetalleVenta, cantidad, reingresa: !danado });
      });
      const motivo = document.getElementById('dv-motivo').value.trim();
      if (lineas.length === 0) { mostrarMensaje(root, 'Selecciona al menos un producto.', 'error'); return; }
      if (!motivo) { mostrarMensaje(root, 'Escribe el motivo de la devolución.', 'error'); return; }

      const payload = {
        idventa: venta.idventa,
        tipo: 1,
        motivo,
        metodoReembolso: Number(document.getElementById('dv-metodo-reembolso').value),
        lineas,
      };
      e.target.disabled = true;
      e.target.innerHTML = '<span class="spinner"></span> Enviando...';
      try {
        const dev = await api('POST', '/api/devoluciones', payload);
        mostrarMensaje(root, `Devolución ${dev.folio} solicitada. Queda pendiente de aprobación.`, 'ok');
        setTimeout(() => pantallaDevoluciones(), 1200);
      } catch (err) {
        mostrarMensaje(root, err.network ? 'Sin conexión con el servidor.' : err.message, 'error');
        e.target.disabled = false;
        e.target.textContent = 'Solicitar devolución';
      }
    };
  }

  let soloPendientes = true;
  const segPend = document.getElementById('dv-seg-pendientes');
  const segTodas = document.getElementById('dv-seg-todas');
  segPend.onclick = () => { soloPendientes = true; segPend.classList.add('activo'); segTodas.classList.remove('activo'); cargarLista(); };
  segTodas.onclick = () => { soloPendientes = false; segTodas.classList.add('activo'); segPend.classList.remove('activo'); cargarLista(); };

  async function cargarLista() {
    const cont = document.getElementById('dv-lista');
    cont.innerHTML = '<div class="vacio">Cargando...</div>';
    try {
      const lista = await api('GET', '/api/devoluciones' + (soloPendientes ? '?estado=1' : ''));
      if (lista.length === 0) { cont.innerHTML = '<div class="vacio">Sin devoluciones.</div>'; return; }
      cont.innerHTML = lista.map(d => `
        <div class="orden-item" data-id="${d.iddevolucion}">
          <div class="orden-cab">
            <span class="orden-folio">${escapar(d.folio)}</span>
            ${pastillaDevolucion(d.estadoDisplay)}
          </div>
          <div class="orden-equipo">${escapar(d.nombreCliente || 'Público general')} · Venta #${d.idventa} · ${escapar(d.tipoDisplay)}</div>
          <div class="orden-meta">
            <span class="orden-fecha">${formatoFecha(d.fechaSolicitud)}</span>
            <span style="font-weight:700;">${formatoDinero(d.totalDevuelto)}</span>
          </div>
        </div>`).join('');
      cont.querySelectorAll('.orden-item').forEach(el => el.onclick = () => navegar('#/devolucion/' + el.dataset.id));
    } catch (err) {
      cont.innerHTML = `<div class="mensaje error">${escapar(err.network ? 'Sin conexión con el servidor.' : err.message)}</div>`;
    }
  }
  cargarLista();
}

async function pantallaDevolucionDetalle(id) {
  const s = Sesion.obtener();
  if (!PERSONAL.includes(s.rol)) { root.innerHTML = '<div class="tarjeta">No tienes permiso para ver devoluciones.</div>'; return; }
  root.innerHTML = '<div class="vacio">Cargando...</div>';
  let d;
  try {
    d = await api('GET', '/api/devoluciones/' + id);
  } catch (err) {
    root.innerHTML = `<div class="tarjeta"><div class="mensaje error">${escapar(err.network ? 'Sin conexión con el servidor.' : err.message)}</div>
      <button class="btn btn-gris" onclick="navegar('#/devoluciones')">Volver</button></div>`;
    return;
  }

  root.innerHTML = `
    <h1>${ic('undo-2')} ${escapar(d.folio)}</h1>
    <div class="tarjeta">
      <div class="orden-cab">
        ${pastillaDevolucion(d.estadoDisplay)}
        <span class="ayuda">${escapar(d.tipoDisplay)}</span>
      </div>
      <div class="ayuda" style="margin-top:6px;">${escapar(d.nombreTienda)} · Venta #${d.idventa}${d.nombreCliente ? ' · ' + escapar(d.nombreCliente) : ''}</div>
      <h2>Motivo</h2>
      <div>${escapar(d.motivo)}</div>
      <h2>Productos</h2>
      ${(d.lineas || []).map(l => `
        <div class="detalle-fila">
          <span class="k">${escapar(l.nombreProducto)}${l.imei ? ' · IMEI ' + escapar(l.imei) : ''} × ${l.cantidad}${l.reingresa === false ? ' (dañado, no regresó)' : ''}</span>
          <span class="v">${formatoDinero(l.subtotal)}</span>
        </div>`).join('')}
      <div class="detalle-fila" style="font-weight:800;">
        <span class="k">Total devuelto</span>
        <span class="v">${formatoDinero(d.totalDevuelto)}</span>
      </div>
      ${d.descripcionMetodoReembolso ? `<div class="ayuda">Reembolso: ${escapar(d.descripcionMetodoReembolso)}</div>` : ''}
      <div class="ayuda" style="margin-top:10px;">Solicitó ${escapar(d.nombreSolicita)} · ${formatoFecha(d.fechaSolicitud)}</div>
      ${d.nombreResuelve ? `<div class="ayuda">Resolvió ${escapar(d.nombreResuelve)} · ${formatoFecha(d.fechaResolucion)}${d.comentarioResolucion ? ': ' + escapar(d.comentarioResolucion) : ''}</div>` : ''}
    </div>
    ${d.estado === 1 && GESTOR_ROLES.includes(s.rol) ? `
      <div class="tarjeta">
        <h2 style="margin-top:0;">Resolver</h2>
        <label>Comentario</label>
        <textarea id="dv-comentario" placeholder="Opcional para aprobar; obligatorio para rechazar"></textarea>
        <div class="fila">
          <button id="dv-aprobar" class="btn btn-verde">${ic('check')} Aprobar</button>
          <button id="dv-rechazar" class="btn btn-rojo">${ic('x')} Rechazar</button>
        </div>
      </div>` : ''}
    <button class="btn btn-gris" onclick="navegar('#/devoluciones')" style="margin-top:10px;">Volver</button>`;

  if (d.estado === 1 && GESTOR_ROLES.includes(s.rol)) {
    document.getElementById('dv-aprobar').onclick = async (e) => {
      e.target.disabled = true;
      try {
        await api('POST', `/api/devoluciones/${id}/aprobar`, { comentario: document.getElementById('dv-comentario').value.trim() || null });
        mostrarMensaje(root, 'Devolución aprobada.', 'ok');
        pantallaDevolucionDetalle(id);
      } catch (err) {
        mostrarMensaje(root, err.network ? 'Sin conexión con el servidor.' : err.message, 'error');
        e.target.disabled = false;
      }
    };
    document.getElementById('dv-rechazar').onclick = async (e) => {
      const comentario = document.getElementById('dv-comentario').value.trim();
      if (!comentario) { mostrarMensaje(root, 'Escribe el motivo del rechazo.', 'error'); return; }
      e.target.disabled = true;
      try {
        await api('POST', `/api/devoluciones/${id}/rechazar`, { comentario });
        mostrarMensaje(root, 'Devolución rechazada.', 'ok');
        pantallaDevolucionDetalle(id);
      } catch (err) {
        mostrarMensaje(root, err.network ? 'Sin conexión con el servidor.' : err.message, 'error');
        e.target.disabled = false;
      }
    };
  }
}

// ── Traspasos ────────────────────────────────────────────────────────────
//
// Envíos (una tienda manda mercancía y la otra confirma al recibirla) y solicitudes (una tienda le pide mercancía a
// otra, que la acepta —eligiendo los IMEI de los equipos— o la rechaza). Solo encargados y administradores
// (los mismos roles que permite la API). Un encargado opera con su tienda; un administrador elige la sucursal.

const esEquipoTipo = tipo => tipo === 'CELULAR' || tipo === 'TABLET';

function pastillaTraspaso(t) {
  const clase = !t.activo ? 'cancelada' : ({ 1: 'recibida', 2: 'lista', 3: 'reparacion', 4: 'lista', 5: 'cancelada' })[t.estado] || 'pendiente';
  return `<span class="pastilla ${clase}">${escapar(t.estadoDisplay)}</span>`;
}

/** Sucursal con la que se opera: el encargado, la suya; un administrador la elige. Devuelve el codti inicial. */
async function dibujarSelectorSucursal(cont, s, alCambiar) {
  if (!SUPERIOR.includes(s.rol)) {
    cont.innerHTML = '<div class="ayuda">Sucursal: <strong>tu tienda</strong></div>';
    return s.codti;
  }
  cont.innerHTML = '<label class="obligatorio">Sucursal</label><select><option>Cargando...</option></select>';
  try {
    const tiendas = await api('GET', '/api/tiendas');
    const sel = cont.querySelector('select');
    sel.innerHTML = tiendas.map(t => `<option value="${t.codti}">${escapar(t.nombre)}</option>`).join('');
    const inicial = tiendas.find(t => t.codti === s.codti)?.codti ?? tiendas[0]?.codti;
    if (inicial != null) sel.value = inicial;
    sel.onchange = () => alCambiar(Number(sel.value));
    return inicial;
  } catch {
    cont.innerHTML = '<div class="mensaje info">No se pudo cargar la lista de sucursales (sin conexión).</div>';
    return null;
  }
}

async function pantallaTraspasos() {
  const s = Sesion.obtener();
  if (!GESTOR_ROLES.includes(s.rol)) { root.innerHTML = '<div class="tarjeta">Los traspasos los manejan el encargado de la tienda y los administradores.</div>'; return; }

  root.innerHTML = `
    <h1>${ic('truck')} Traspasos</h1>
    <div id="tr-tienda"></div>
    <div class="grupo-botones" style="margin:10px 0;">
      <button id="tr-nuevo-envio" class="btn btn-verde">${ic('cloud-upload')} Enviar mercancía</button>
      <button id="tr-nueva-solicitud" class="btn btn-azul">${ic('inbox')} Pedir mercancía</button>
    </div>
    <button id="tr-faltantes" class="btn btn-gris" style="margin-bottom:10px;">${ic('triangle-alert')} Faltantes por resolver <span id="tr-falt-n"></span></button>
    <div class="segmentado">
      <button id="tr-seg-pend" class="activo">Por atender</button>
      <button id="tr-seg-todos">Todos</button>
    </div>
    <div id="tr-lista"><div class="vacio">Cargando...</div></div>`;

  let codti = await dibujarSelectorSucursal(document.getElementById('tr-tienda'), s, (c) => { codti = c; cargar(); });
  let soloPendientes = true;
  const segPend = document.getElementById('tr-seg-pend');
  const segTodos = document.getElementById('tr-seg-todos');
  segPend.onclick = () => { soloPendientes = true; segPend.classList.add('activo'); segTodos.classList.remove('activo'); cargar(); };
  segTodos.onclick = () => { soloPendientes = false; segTodos.classList.add('activo'); segPend.classList.remove('activo'); cargar(); };
  document.getElementById('tr-nuevo-envio').onclick = () => navegar('#/traspaso-nuevo/envio');
  document.getElementById('tr-nueva-solicitud').onclick = () => navegar('#/traspaso-nuevo/solicitud');
  document.getElementById('tr-faltantes').onclick = () => navegar('#/faltantes');

  async function cargar() {
    if (codti == null) return;
    const cont = document.getElementById('tr-lista');
    cont.innerHTML = '<div class="vacio">Cargando...</div>';
    try {
      const lista = await api('GET', `/api/traspasos/tienda/${codti}` + (soloPendientes ? '/pendientes' : ''));
      if (lista.length === 0) {
        cont.innerHTML = `<div class="vacio">${soloPendientes ? 'No tienes traspasos por atender.' : 'Sin traspasos.'}</div>`;
      } else {
        cont.innerHTML = lista.map(t => {
          const porMi = t.activo && t.codtiDestino === codti
            ? (t.tipo === 1 && t.estado === 1 ? ic('package') + ' Por recibir' : (t.tipo === 2 && (t.estado === 1 || t.estado === 3) ? ic('clipboard-pen') + ' Por resolver' : ''))
            : '';
          return `
          <div class="orden-item" data-id="${t.idtraspaso}">
            <div class="orden-cab">
              <span class="orden-folio">#${t.idtraspaso} · ${escapar(t.tipoDisplay)}</span>
              ${pastillaTraspaso(t)}
            </div>
            <div class="orden-equipo">${escapar(t.nombreTiendaOrigen)} → ${escapar(t.nombreTiendaDestino)}</div>
            <div class="orden-meta">
              <span class="orden-fecha">${formatoFecha(t.fechaCreacion)}</span>
              <span>${(t.lineas || []).length} producto(s)${porMi ? ' · <strong>' + porMi + '</strong>' : ''}</span>
            </div>
          </div>`;
        }).join('');
        cont.querySelectorAll('.orden-item').forEach(el => el.onclick = () => navegar('#/traspaso/' + el.dataset.id));
      }
    } catch (err) {
      cont.innerHTML = `<div class="mensaje error">${escapar(err.network ? 'Sin conexión con el servidor.' : err.message)}</div>`;
    }
    api('GET', `/api/traspasos/faltantes?codti=${codti}`).then(f => {
      const n = document.getElementById('tr-falt-n');
      if (n) n.textContent = f.length > 0 ? `(${f.length})` : '';
    }).catch(() => {});
  }
  cargar();
}

async function pantallaTraspasoDetalle(id) {
  const s = Sesion.obtener();
  if (!GESTOR_ROLES.includes(s.rol)) { root.innerHTML = '<div class="tarjeta">Los traspasos los manejan el encargado de la tienda y los administradores.</div>'; return; }
  root.innerHTML = '<div class="vacio">Cargando...</div>';
  let t;
  try {
    t = await api('GET', '/api/traspasos/' + id);
  } catch (err) {
    root.innerHTML = `<div class="tarjeta"><div class="mensaje error">${escapar(err.network ? 'Sin conexión con el servidor.' : err.message)}</div>
      <button class="btn btn-gris" onclick="navegar('#/traspasos')">Volver</button></div>`;
    return;
  }

  const superior = SUPERIOR.includes(s.rol);
  const soyOrigen = superior || s.codti === t.codtiOrigen;
  const soyDestino = superior || s.codti === t.codtiDestino;
  const esEnvio = t.tipo === 1;
  // Una solicitud nueva se marca como leída en cuanto la tienda que surte la abre
  if (!esEnvio && t.activo && t.estado === 1 && soyDestino) {
    try { t = await api('POST', `/api/traspasos/${id}/leer`); } catch { /* no es grave: se puede atender igual */ }
  }
  const abierto = t.activo && (esEnvio ? t.estado === 1 : (t.estado === 1 || t.estado === 3));
  const lineas = t.lineas || [];

  root.innerHTML = `
    <h1>${ic('truck')} Traspaso #${t.idtraspaso}</h1>
    <div class="tarjeta">
      <div class="orden-cab">${pastillaTraspaso(t)}<span class="ayuda">${escapar(t.tipoDisplay)}</span></div>
      <div class="ayuda" style="margin-top:6px;">${escapar(t.nombreTiendaOrigen)} → ${escapar(t.nombreTiendaDestino)}</div>
      <h2>Productos</h2>
      ${lineas.map(l => `
        <div class="detalle-fila">
          <span class="k">${escapar(l.nombreProducto)}<br><span class="ayuda">${escapar(l.codpro)}${(l.imeis || []).length ? ' · ' + l.imeis.map(escapar).join(', ') : ''}</span></span>
          <span class="v">× ${Number(l.cantidad)}${esEnvio && t.estado === 2 && l.cantidadRecibida != null ? ' (llegó ' + Number(l.cantidadRecibida) + ')' : ''}</span>
        </div>
        ${Number(l.faltantePendiente) > 0 ? `<div class="ayuda" style="color:#b91c1c;">${ic('triangle-alert')} Faltan por resolver: ${Number(l.faltantePendiente)}</div>` : ''}`).join('')}
      <div class="ayuda" style="margin-top:10px;">Lo creó ${escapar(t.nombreCrea || '')} · ${formatoFecha(t.fechaCreacion)}</div>
      ${t.nombreValida ? `<div class="ayuda">Lo resolvió ${escapar(t.nombreValida)} · ${formatoFecha(t.fechaActualizacion)}</div>` : ''}
      ${t.motivoRechazo ? `<div class="mensaje error" style="margin-top:8px;">Motivo del rechazo: ${escapar(t.motivoRechazo)}</div>` : ''}
      ${t.comentarioRecepcion ? `<div class="ayuda">Comentario de la recepción: ${escapar(t.comentarioRecepcion)}</div>` : ''}
      ${t.idtraspasoRef ? `<div class="ayuda">Ligado al traspaso <span class="enlace" onclick="navegar('#/traspaso/${t.idtraspasoRef}')">#${t.idtraspasoRef}</span></div>` : ''}
      ${esEnvio && t.conFaltantes ? `<button class="btn btn-ambar btn-chico" onclick="navegar('#/faltantes')" style="margin-top:8px;">${ic('triangle-alert')} Ver faltantes</button>` : ''}
    </div>
    <div id="tr-acciones"></div>
    <button class="btn btn-gris" onclick="navegar('#/traspasos')" style="margin-top:10px;">Volver</button>`;

  const acciones = document.getElementById('tr-acciones');
  const fallo = (err, boton, texto) => {
    mostrarMensaje(root, err.network ? 'Sin conexión con el servidor.' : err.message, 'error');
    if (boton) { boton.disabled = false; if (texto) boton.textContent = texto; }
  };
  const refrescar = (mensaje) => { mostrarMensaje(root, mensaje, 'ok'); setTimeout(() => pantallaTraspasoDetalle(id), 900); };

  // ── Recibir un envío ──
  if (esEnvio && abierto && soyDestino) {
    acciones.insertAdjacentHTML('beforeend', `
      <div class="tarjeta">
        <h2 style="margin-top:0;">Recibir mercancía</h2>
        <div class="ayuda">Confirma que llegó todo. Si algo no llegó, márcalo: queda como faltante por resolver.</div>
        ${lineas.map((l, i) => `
          <div style="border-top:1px solid var(--gris-claro); padding:10px 0;">
            <label style="display:flex; align-items:center; gap:8px; font-weight:400; text-transform:none;">
              <input type="checkbox" class="tr-falta-chk" data-i="${i}" style="width:auto;"> No llegó completo: ${escapar(l.nombreProducto)}
            </label>
            <div class="tr-falta-det oculto" data-i="${i}" style="margin-left:26px;">
              ${esEquipoTipo(l.tipoProducto)
                ? (l.imeis || []).map(im => `<label style="display:flex; align-items:center; gap:8px; font-weight:400; text-transform:none;">
                    <input type="checkbox" class="tr-falta-imei" data-i="${i}" value="${escapar(im)}" style="width:auto;"> ${escapar(im)} no llegó</label>`).join('')
                : `<label>Cantidad que NO llegó (de ${Number(l.cantidad)})</label>
                   <input type="number" class="tr-falta-cant" data-i="${i}" min="1" max="${Number(l.cantidad)}" value="1" style="width:100px;">`}
            </div>
          </div>`).join('')}
        <label>Comentario</label>
        <textarea id="tr-comentario" placeholder="Obligatorio si algo no llegó: qué pasó"></textarea>
        <button id="tr-recibir" class="btn btn-verde">${ic('check')} Confirmar recepción</button>
      </div>`);
    acciones.querySelectorAll('.tr-falta-chk').forEach(chk => chk.onchange = () =>
      acciones.querySelector(`.tr-falta-det[data-i="${chk.dataset.i}"]`).classList.toggle('oculto', !chk.checked));
    document.getElementById('tr-recibir').onclick = async (e) => {
      const faltantes = [];
      for (const chk of acciones.querySelectorAll('.tr-falta-chk:checked')) {
        const l = lineas[Number(chk.dataset.i)];
        if (esEquipoTipo(l.tipoProducto)) {
          const imeis = [...acciones.querySelectorAll(`.tr-falta-imei[data-i="${chk.dataset.i}"]:checked`)].map(x => x.value);
          if (imeis.length === 0) { mostrarMensaje(root, `Marca cuál IMEI no llegó de ${l.nombreProducto}.`, 'error'); return; }
          faltantes.push({ codpro: l.codpro, imeis });
        } else {
          const cantidad = Number(acciones.querySelector(`.tr-falta-cant[data-i="${chk.dataset.i}"]`).value);
          if (!cantidad || cantidad < 1) { mostrarMensaje(root, `Indica cuánto no llegó de ${l.nombreProducto}.`, 'error'); return; }
          faltantes.push({ codpro: l.codpro, cantidad });
        }
      }
      const comentario = document.getElementById('tr-comentario').value.trim();
      if (faltantes.length > 0 && !comentario) { mostrarMensaje(root, 'Escribe en el comentario qué pasó con lo que no llegó.', 'error'); return; }
      e.target.disabled = true;
      try {
        await api('POST', `/api/traspasos/${id}/recibir`, { faltantes, comentario: comentario || null });
        refrescar(faltantes.length ? 'Recepción registrada con faltantes.' : 'Mercancía recibida: ya está en tu inventario.');
      } catch (err) { fallo(err, e.target); }
    };
  }

  // ── Atender una solicitud (la tienda que surte) ──
  if (!esEnvio && abierto && soyDestino) {
    const equipos = lineas.filter(l => esEquipoTipo(l.tipoProducto));
    let catalogo = [];
    if (equipos.length > 0) {
      try { catalogo = await api('GET', `/api/productos/tienda/${t.codtiDestino}/disponibles`); }
      catch { /* se avisa abajo */ }
    }
    acciones.insertAdjacentHTML('beforeend', `
      <div class="tarjeta">
        <h2 style="margin-top:0;">Atender solicitud</h2>
        <div class="ayuda">Acepta para enviar lo pedido${equipos.length ? ' (elige qué equipos mandas)' : ''}, o recházala con un motivo.</div>
        ${equipos.map((l, i) => {
          const prod = catalogo.find(p => p.codpro === l.codpro);
          const unidades = prod?.imeisDisponibles || [];
          return `<div style="border-top:1px solid var(--gris-claro); padding:10px 0;">
            <div><strong>${escapar(l.nombreProducto)}</strong> — elige ${Number(l.cantidad)} equipo(s)</div>
            ${unidades.length === 0 ? '<div class="mensaje error">No hay equipos disponibles en tu inventario.</div>' : unidades.map(u => `
              <label style="display:flex; align-items:center; gap:8px; font-weight:400; text-transform:none;">
                <input type="checkbox" class="tr-imei" data-codpro="${escapar(l.codpro)}" value="${escapar(u.imei)}" style="width:auto;"> ${escapar(u.imei)}
              </label>`).join('')}
          </div>`;
        }).join('')}
        <button id="tr-aceptar" class="btn btn-verde" style="margin-top:6px;">${ic('check')} Aceptar y enviar</button>
        <label style="margin-top:12px;">Motivo (solo si la rechazas)</label>
        <textarea id="tr-motivo" placeholder="Por qué no se puede surtir"></textarea>
        <button id="tr-rechazar" class="btn btn-rojo">${ic('x')} Rechazar</button>
      </div>`);
    document.getElementById('tr-aceptar').onclick = async (e) => {
      const elegidos = [];
      for (const l of equipos) {
        const imeis = [...acciones.querySelectorAll(`.tr-imei[data-codpro="${CSS.escape(l.codpro)}"]:checked`)].map(x => x.value);
        if (imeis.length !== Number(l.cantidad)) { mostrarMensaje(root, `De ${l.nombreProducto} debes elegir exactamente ${Number(l.cantidad)} equipo(s).`, 'error'); return; }
        elegidos.push({ codpro: l.codpro, imeis });
      }
      if (!confirm('¿Aceptar y enviar? El stock sale de tu inventario.')) return;
      e.target.disabled = true;
      try {
        const envio = await api('POST', `/api/traspasos/${id}/aceptar`, { equipos: elegidos });
        mostrarMensaje(root, `Solicitud aceptada. Se creó el envío #${envio.idtraspaso}.`, 'ok');
        setTimeout(() => navegar('#/traspaso/' + envio.idtraspaso), 1000);
      } catch (err) { fallo(err, e.target); }
    };
    document.getElementById('tr-rechazar').onclick = async (e) => {
      const motivo = document.getElementById('tr-motivo').value.trim();
      if (!motivo) { mostrarMensaje(root, 'Escribe el motivo del rechazo.', 'error'); return; }
      e.target.disabled = true;
      try {
        await api('POST', `/api/traspasos/${id}/rechazar`, { motivo });
        refrescar('Solicitud rechazada.');
      } catch (err) { fallo(err, e.target); }
    };
  }

  // ── Anular (quien lo creó, mientras no se resuelva) ──
  if (abierto && soyOrigen) {
    acciones.insertAdjacentHTML('beforeend', `
      <div class="tarjeta">
        <button id="tr-anular" class="btn btn-rojo">${ic('ban')} Anular ${esEnvio ? 'envío' : 'solicitud'}</button>
        <div class="ayuda">${esEnvio ? 'La mercancía vuelve a tu inventario.' : 'La tienda ya no la verá como pendiente.'}</div>
      </div>`);
    document.getElementById('tr-anular').onclick = async (e) => {
      if (!confirm(`¿Anular ${esEnvio ? 'este envío' : 'esta solicitud'}?`)) return;
      e.target.disabled = true;
      try {
        await api('POST', `/api/traspasos/${id}/anular`);
        refrescar('Traspaso anulado.');
      } catch (err) { fallo(err, e.target); }
    };
  }
}

/** Nuevo envío ("envio") o nueva solicitud ("solicitud"). */
async function pantallaTraspasoNuevo(tipo) {
  const s = Sesion.obtener();
  if (!GESTOR_ROLES.includes(s.rol)) { root.innerHTML = '<div class="tarjeta">Los traspasos los manejan el encargado de la tienda y los administradores.</div>'; return; }
  const esEnvio = tipo !== 'solicitud';

  root.innerHTML = `
    <h1>${esEnvio ? ic('cloud-upload') + ' Enviar mercancía' : ic('inbox') + ' Pedir mercancía'}</h1>
    <div id="tn-tienda"></div>
    <label class="obligatorio" style="margin-top:8px;">${esEnvio ? 'Enviar a' : 'Pedir a'}</label>
    <select id="tn-otra"><option>Cargando...</option></select>
    <div class="ayuda">${esEnvio
      ? 'El stock sale de tu inventario al enviar; la otra tienda lo confirma cuando lo recibe.'
      : 'La otra tienda revisa tu solicitud y te envía lo que acepte.'}</div>
    <h2>Productos</h2>
    <div class="buscador"><input id="tn-buscar" placeholder="Buscar por nombre, código o IMEI..." disabled></div>
    <div id="tn-resultados"></div>
    <h2>${esEnvio ? 'A enviar' : 'A pedir'}</h2>
    <div id="tn-lineas"><div class="vacio">Busca y toca un producto para agregarlo.</div></div>
    <button id="tn-guardar" class="btn btn-verde" style="margin-top:10px;">${esEnvio ? ic('cloud-upload') + ' Enviar' : ic('inbox') + ' Enviar solicitud'}</button>
    <button class="btn btn-gris" onclick="navegar('#/traspasos')" style="margin-top:8px;">Cancelar</button>`;

  let tiendas = [];
  try { tiendas = await api('GET', '/api/tiendas'); } catch { /* se avisa abajo */ }
  let miCodti = await dibujarSelectorSucursal(document.getElementById('tn-tienda'), s, (c) => { miCodti = c; llenarOtras(); cargarCatalogo(); });
  const selOtra = document.getElementById('tn-otra');
  let catalogo = [];
  let lineas = [];   // { p, cantidad, imeis }

  function llenarOtras() {
    const otras = tiendas.filter(t => t.codti !== miCodti);
    selOtra.innerHTML = otras.length ? otras.map(t => `<option value="${t.codti}">${escapar(t.nombre)}</option>`).join('') : '<option value="">(sin otras sucursales)</option>';
  }
  /** El catálogo sale de la tienda que tiene la mercancía: la propia en un envío, la otra en una solicitud. */
  async function cargarCatalogo() {
    lineas = []; dibujarLineas();
    document.getElementById('tn-resultados').innerHTML = '';
    const buscar = document.getElementById('tn-buscar');
    const fuente = esEnvio ? miCodti : Number(selOtra.value);
    if (!fuente) { buscar.disabled = true; return; }
    buscar.disabled = true; buscar.placeholder = 'Cargando productos...';
    try {
      catalogo = (await api('GET', `/api/productos/tienda/${fuente}/disponibles`)).filter(p => p.tipo !== 'SERVICIO');
      buscar.disabled = false; buscar.placeholder = 'Buscar por nombre, código o IMEI...';
    } catch (err) {
      buscar.placeholder = err.network ? 'Sin conexión con el servidor' : 'No se pudo cargar el catálogo';
    }
  }
  selOtra.onchange = () => { if (!esEnvio) cargarCatalogo(); };
  llenarOtras();
  await cargarCatalogo();

  document.getElementById('tn-buscar').addEventListener('input', (e) => {
    const q = e.target.value.trim();
    const cont = document.getElementById('tn-resultados');
    if (q.length < 2) { cont.innerHTML = ''; return; }
    const encontrados = filtrarProductos(catalogo, q).slice(0, 15);
    cont.innerHTML = encontrados.length === 0 ? '<div class="vacio">Sin resultados.</div>' : encontrados.map(p => `
      <div class="orden-item" data-codpro="${escapar(p.codpro)}">
        <div class="orden-cab"><span class="orden-folio">${TIPO_ICONO[p.tipo] || ''} ${escapar(p.nombreProductoMaster)}</span>
          <span style="font-weight:700;">Stock: ${Number(p.stock)}</span></div>
        <div class="orden-cliente">${escapar(p.codpro)}</div>
      </div>`).join('');
    cont.querySelectorAll('.orden-item').forEach(el => el.onclick = () => {
      const p = catalogo.find(x => x.codpro === el.dataset.codpro);
      if (!lineas.some(l => l.p.codpro === p.codpro)) lineas.push({ p, cantidad: 1, imeis: [] });
      dibujarLineas();
      cont.innerHTML = ''; document.getElementById('tn-buscar').value = '';
    });
  });

  function dibujarLineas() {
    const cont = document.getElementById('tn-lineas');
    if (lineas.length === 0) { cont.innerHTML = '<div class="vacio">Busca y toca un producto para agregarlo.</div>'; return; }
    cont.innerHTML = lineas.map((l, i) => `
      <div class="orden-item">
        <div class="orden-cab">
          <span class="orden-folio">${escapar(l.p.nombreProductoMaster)}</span>
          <button class="btn btn-rojo btn-chico tn-quitar" data-i="${i}">Quitar</button>
        </div>
        <div class="orden-cliente">${escapar(l.p.codpro)} · Stock: ${Number(l.p.stock)}</div>
        ${esEnvio && esEquipoTipo(l.p.tipo)
          ? `<div style="margin-top:6px;">${(l.p.imeisDisponibles || []).map(u => `
              <label style="display:flex; align-items:center; gap:8px; font-weight:400; text-transform:none;">
                <input type="checkbox" class="tn-imei" data-i="${i}" value="${escapar(u.imei)}" ${l.imeis.includes(u.imei) ? 'checked' : ''} style="width:auto;"> ${escapar(u.imei)}
              </label>`).join('')}</div>`
          : `<div class="fila" style="margin-top:8px; align-items:center;">
              <label style="margin:0;">Cantidad</label>
              <input type="number" class="tn-cant" data-i="${i}" min="1" max="${Number(l.p.stock)}" step="1" value="${l.cantidad}" style="width:100px;">
            </div>`}
      </div>`).join('');
    cont.querySelectorAll('.tn-quitar').forEach(b => b.onclick = () => { lineas.splice(Number(b.dataset.i), 1); dibujarLineas(); });
    cont.querySelectorAll('.tn-cant').forEach(inp => inp.oninput = () => { lineas[Number(inp.dataset.i)].cantidad = Number(inp.value); });
    cont.querySelectorAll('.tn-imei').forEach(chk => chk.onchange = () => {
      const l = lineas[Number(chk.dataset.i)];
      l.imeis = chk.checked ? [...l.imeis, chk.value] : l.imeis.filter(x => x !== chk.value);
    });
  }

  document.getElementById('tn-guardar').onclick = async (e) => {
    const otra = Number(selOtra.value);
    if (!otra) { mostrarMensaje(root, 'Elige la otra sucursal.', 'error'); return; }
    if (miCodti == null) { mostrarMensaje(root, 'No se pudo determinar tu sucursal.', 'error'); return; }
    if (lineas.length === 0) { mostrarMensaje(root, 'Agrega al menos un producto.', 'error'); return; }
    const detalle = [];
    for (const l of lineas) {
      if (esEnvio && esEquipoTipo(l.p.tipo)) {
        if (l.imeis.length === 0) { mostrarMensaje(root, `Elige los equipos que envías de ${l.p.nombreProductoMaster}.`, 'error'); return; }
        detalle.push({ codpro: l.p.codpro, cantidad: l.imeis.length, imeis: l.imeis });
      } else {
        if (!l.cantidad || l.cantidad < 1 || l.cantidad > Number(l.p.stock)) {
          mostrarMensaje(root, `La cantidad de ${l.p.nombreProductoMaster} debe estar entre 1 y ${Number(l.p.stock)}.`, 'error'); return;
        }
        detalle.push({ codpro: l.p.codpro, cantidad: l.cantidad });
      }
    }
    // En un envío, "origen" es quien manda; en una solicitud, quien pide (y recibe). La otra tienda va como destino.
    const payload = { codtiOrigen: miCodti, codtiDestino: otra, lineas: detalle };
    e.target.disabled = true;
    e.target.innerHTML = '<span class="spinner"></span> Enviando...';
    try {
      const t = await api('POST', esEnvio ? '/api/traspasos/envios' : '/api/traspasos/solicitudes', payload);
      mostrarMensaje(root, `${esEnvio ? 'Envío' : 'Solicitud'} #${t.idtraspaso} creado.`, 'ok');
      setTimeout(() => navegar('#/traspaso/' + t.idtraspaso), 900);
    } catch (err) {
      mostrarMensaje(root, err.network ? 'Sin conexión con el servidor: no se guardó.' : err.message, 'error');
      e.target.disabled = false;
      e.target.innerHTML = esEnvio ? ic('cloud-upload') + ' Enviar' : ic('inbox') + ' Enviar solicitud';
    }
  };
}

/** Lo que faltó en envíos ya recibidos y sigue sin resolver. */
async function pantallaFaltantes() {
  const s = Sesion.obtener();
  if (!GESTOR_ROLES.includes(s.rol)) { root.innerHTML = '<div class="tarjeta">Los traspasos los manejan el encargado de la tienda y los administradores.</div>'; return; }
  root.innerHTML = `
    <h1>${ic('triangle-alert')} Faltantes</h1>
    <div class="ayuda">Mercancía que salió de una tienda y no llegó completa a la otra. Cada faltante se resuelve: llegó tarde, regresó al origen o se da de baja.</div>
    <div id="fa-lista" style="margin-top:10px;"><div class="vacio">Cargando...</div></div>
    <button class="btn btn-gris" onclick="navegar('#/traspasos')" style="margin-top:10px;">Volver</button>`;
  const superior = SUPERIOR.includes(s.rol);

  async function cargar() {
    const cont = document.getElementById('fa-lista');
    cont.innerHTML = '<div class="vacio">Cargando...</div>';
    let lista;
    try {
      lista = await api('GET', '/api/traspasos/faltantes' + (superior ? '' : `?codti=${s.codti}`));
    } catch (err) {
      cont.innerHTML = `<div class="mensaje error">${escapar(err.network ? 'Sin conexión con el servidor.' : err.message)}</div>`;
      return;
    }
    if (lista.length === 0) { cont.innerHTML = '<div class="vacio">No hay faltantes por resolver.</div>'; return; }
    cont.innerHTML = lista.map(f => {
      const puedeTarde = superior || s.codti === f.codtiDestino;
      const puedeReintegro = superior || s.codti === f.codtiOrigen;
      return `
      <div class="tarjeta" data-id="${f.iddetalle}">
        <div class="orden-cab">
          <span class="orden-folio">${escapar(f.nombreProducto)}</span>
          <span class="pastilla error">Faltan ${Number(f.pendiente)}</span>
        </div>
        <div class="ayuda">${escapar(f.codpro)}${f.imei ? ' · IMEI ' + escapar(f.imei) : ''} · Traspaso <span class="enlace" onclick="navegar('#/traspaso/${f.idtraspaso}')">#${f.idtraspaso}</span></div>
        <div class="ayuda">${escapar(f.nombreTiendaOrigen)} → ${escapar(f.nombreTiendaDestino)} · Enviadas ${Number(f.cantidadEnviada)}, recibidas ${Number(f.cantidadRecibida)}</div>
        ${f.comentarioRecepcion ? `<div class="ayuda">Comentario: ${escapar(f.comentarioRecepcion)}</div>` : ''}
        ${(f.historial || []).map(h => `<div class="historial-meta">${escapar(h.accion)}${h.cantidad ? ' ×' + Number(h.cantidad) : ''} · ${escapar(h.usuario || '')} · ${formatoFecha(h.fecha)}${h.nota ? ' — ' + escapar(h.nota) : ''}</div>`).join('')}
        <label>Cantidad a resolver</label>
        <input type="number" class="fa-cant" min="1" max="${Number(f.pendiente)}" value="${Number(f.pendiente)}" ${f.imei ? 'disabled' : ''} style="width:100px;">
        <label>Nota</label>
        <input class="fa-nota" placeholder="Opcional (obligatoria para dar de baja)">
        <div class="grupo-botones" style="margin-top:8px;">
          ${puedeTarde ? '<button class="btn btn-verde btn-chico fa-accion" data-accion="RECIBIDO_TARDE">' + ic('package') + ' Llegó tarde</button>' : ''}
          ${puedeReintegro ? '<button class="btn btn-azul btn-chico fa-accion" data-accion="REINTEGRADO_ORIGEN">' + ic('undo-2') + ' Regresó al origen</button>' : ''}
          ${superior ? '<button class="btn btn-rojo btn-chico fa-accion" data-accion="BAJA">' + ic('trash-2') + ' Dar de baja</button>' : ''}
        </div>
      </div>`;
    }).join('');
    cont.querySelectorAll('.fa-accion').forEach(btn => btn.onclick = async () => {
      const tarjeta = btn.closest('.tarjeta');
      const accion = btn.dataset.accion;
      const nota = tarjeta.querySelector('.fa-nota').value.trim();
      const cantidad = Number(tarjeta.querySelector('.fa-cant').value);
      if (accion === 'BAJA' && !nota) { mostrarMensaje(root, 'Para dar de baja escribe una nota (qué se perdió o dañó).', 'error'); return; }
      if (!confirm(accion === 'BAJA' ? '¿Dar de baja esta mercancía? No se puede deshacer.' : '¿Registrar esta resolución?')) return;
      btn.disabled = true;
      try {
        await api('POST', `/api/traspasos/faltantes/${tarjeta.dataset.id}/resolver`, { accion, cantidad, nota: nota || null });
        mostrarMensaje(root, 'Faltante resuelto.', 'ok');
        cargar();
      } catch (err) {
        mostrarMensaje(root, err.network ? 'Sin conexión con el servidor.' : err.message, 'error');
        btn.disabled = false;
      }
    });
  }
  cargar();
}

// ── Notificaciones ───────────────────────────────────────────────────────

async function actualizarBadgeNotificaciones() {
  const badge = document.getElementById('notif-badge');
  if (!badge) return;
  const s = Sesion.obtener();
  if (!s) { badge.classList.add('oculto'); return; }
  try {
    const n = await api('GET', '/api/notificaciones/pendientes');
    if (n > 0) { badge.textContent = n > 9 ? '9+' : String(n); badge.classList.remove('oculto'); }
    else badge.classList.add('oculto');
  } catch { /* sin conexión: se deja como estaba */ }
}
setInterval(actualizarBadgeNotificaciones, 45000);

async function pantallaNotificaciones() {
  root.innerHTML = `
    <h1>${ic('bell')} Notificaciones</h1>
    <div class="segmentado">
      <button id="nt-seg-pendientes" class="activo">Pendientes</button>
      <button id="nt-seg-todas">Todas</button>
    </div>
    <div class="grupo-botones" style="margin:10px 0;">
      <button id="nt-marcar-todas" class="btn btn-gris btn-chico">Marcar todas como leídas</button>
    </div>
    <div id="nt-lista"><div class="vacio">Cargando...</div></div>`;

  let soloPendientes = true;
  const segPend = document.getElementById('nt-seg-pendientes');
  const segTodas = document.getElementById('nt-seg-todas');
  segPend.onclick = () => { soloPendientes = true; segPend.classList.add('activo'); segTodas.classList.remove('activo'); cargar(); };
  segTodas.onclick = () => { soloPendientes = false; segTodas.classList.add('activo'); segPend.classList.remove('activo'); cargar(); };

  document.getElementById('nt-marcar-todas').onclick = async (e) => {
    e.target.disabled = true;
    try {
      await api('PATCH', '/api/notificaciones/leer-todas');
      actualizarBadgeNotificaciones();
      cargar();
    } catch (err) {
      mostrarMensaje(root, err.network ? 'Sin conexión con el servidor.' : err.message, 'error');
    }
    e.target.disabled = false;
  };

  async function cargar() {
    const cont = document.getElementById('nt-lista');
    cont.innerHTML = '<div class="vacio">Cargando...</div>';
    try {
      const lista = await api('GET', '/api/notificaciones' + (soloPendientes ? '' : '?todas=true'));
      if (lista.length === 0) {
        cont.innerHTML = `<div class="vacio">${soloPendientes ? 'No tienes notificaciones pendientes.' : 'Sin notificaciones.'}</div>`;
        return;
      }
      cont.innerHTML = lista.map(n => `
        <div class="notif-item ${n.leida ? '' : 'sin-leer'}" data-id="${n.idnotificacion}" data-ruta="${n.ruta ? escapar(n.ruta) : ''}">
          <div class="notif-titulo">${!n.leida ? '<span class="notif-punto"></span>' : ''}${escapar(n.titulo)}</div>
          <div class="ayuda">${formatoFecha(n.fechaCreacion)}</div>
        </div>`).join('');
      cont.querySelectorAll('.notif-item').forEach(el => el.onclick = async () => {
        const id = el.dataset.id;
        const ruta = el.dataset.ruta;
        try { await api('PATCH', `/api/notificaciones/${id}/leer`); actualizarBadgeNotificaciones(); } catch { /* si falla, igual navega */ }
        if (ruta) navegar(ruta);
        else cargar();
      });
    } catch (err) {
      cont.innerHTML = `<div class="mensaje error">${escapar(err.network ? 'Sin conexión con el servidor.' : err.message)}</div>`;
    }
  }
  cargar();
}

// ── Ajustes ──────────────────────────────────────────────────────────────

function pantallaAjustes() {
  root.innerHTML = `
    <h1>${ic('settings')} Ajustes</h1>
    <div class="tarjeta">
      <h2 style="margin-top:0;">Cambiar mi contraseña</h2>
      <label class="obligatorio">Contraseña actual</label>
      <input id="aj-actual" type="password" autocomplete="current-password">
      <label class="obligatorio">Contraseña nueva</label>
      <input id="aj-nueva" type="password" autocomplete="new-password">
      <label class="obligatorio">Confirmar contraseña nueva</label>
      <input id="aj-confirmar" type="password" autocomplete="new-password">
      <div class="ayuda">Al menos 8 caracteres.</div>
      <button id="aj-guardar" class="btn btn-verde">Guardar</button>
    </div>`;

  document.getElementById('aj-guardar').onclick = async (e) => {
    const actual = document.getElementById('aj-actual').value;
    const nueva = document.getElementById('aj-nueva').value;
    const confirmar = document.getElementById('aj-confirmar').value;
    if (!actual || !nueva) { mostrarMensaje(root, 'Completa los dos campos de contraseña.', 'error'); return; }
    if (nueva.length < 8) { mostrarMensaje(root, 'La contraseña nueva debe tener al menos 8 caracteres.', 'error'); return; }
    if (nueva !== confirmar) { mostrarMensaje(root, 'La confirmación no coincide con la contraseña nueva.', 'error'); return; }

    e.target.disabled = true;
    e.target.innerHTML = '<span class="spinner"></span> Guardando...';
    try {
      await api('PATCH', '/api/usuarios/mi-password', { passwordActual: actual, passwordNueva: nueva });
      mostrarMensaje(root, 'Contraseña actualizada.', 'ok');
      document.getElementById('aj-actual').value = '';
      document.getElementById('aj-nueva').value = '';
      document.getElementById('aj-confirmar').value = '';
    } catch (err) {
      mostrarMensaje(root, err.network ? 'Sin conexión con el servidor: esta acción necesita conexión.' : err.message, 'error');
    }
    e.target.disabled = false;
    e.target.textContent = 'Guardar';
  };
}

// ── Catálogos (colores, secciones, proveedores) ─────────────────────────────
// Usa /api/catalogos/*, que ya existía en el backend sin ninguna pantalla.

async function pantallaCatalogos() {
  const s = Sesion.obtener();
  if (!SUPERIOR.includes(s.rol)) { root.innerHTML = '<div class="tarjeta">Los catálogos solo los administra un administrador.</div>'; return; }
  root.innerHTML = `
    <h1>${ic('tags')} Catálogos</h1>
    <div class="segmentado">
      <button id="ct-seg-colores" class="activo">Colores</button>
      <button id="ct-seg-secciones">Secciones</button>
      <button id="ct-seg-proveedores">Proveedores</button>
    </div>
    <div id="ct-contenido"><div class="vacio">Cargando...</div></div>`;

  let pestana = 'colores';
  const segs = {
    colores: document.getElementById('ct-seg-colores'),
    secciones: document.getElementById('ct-seg-secciones'),
    proveedores: document.getElementById('ct-seg-proveedores'),
  };
  const activar = (p) => {
    pestana = p;
    Object.entries(segs).forEach(([k, el]) => el.classList.toggle('activo', k === p));
    dibujar();
  };
  segs.colores.onclick = () => activar('colores');
  segs.secciones.onclick = () => activar('secciones');
  segs.proveedores.onclick = () => activar('proveedores');

  async function dibujar() {
    const cont = document.getElementById('ct-contenido');
    cont.innerHTML = '<div class="vacio">Cargando...</div>';
    if (pestana === 'colores') return dibujarColores(cont);
    if (pestana === 'secciones') return dibujarSecciones(cont);
    return dibujarProveedores(cont);
  }

  async function dibujarColores(cont) {
    try {
      const colores = await api('GET', '/api/catalogos/colores');
      cont.innerHTML = `
        <div class="tarjeta">
          <h2 style="margin-top:0;">Nuevo color</h2>
          <label class="obligatorio">Nombre</label>
          <div class="fila">
            <input id="ct-color-nombre" placeholder="Ej. Verde menta">
            <button id="ct-color-guardar" class="btn btn-verde btn-chico" style="flex:0 0 auto;">Guardar</button>
          </div>
        </div>
        <h2>Colores registrados</h2>
        ${colores.length === 0 ? '<div class="vacio">Sin colores.</div>'
          : colores.map(c => `<span class="pastilla" style="margin:0 6px 6px 0; display:inline-block; background:var(--gris-claro); color:var(--texto);">${escapar(c.nombre)}</span>`).join('')}`;
      document.getElementById('ct-color-guardar').onclick = async (e) => {
        const nombre = document.getElementById('ct-color-nombre').value.trim();
        if (!nombre) { mostrarMensaje(root, 'Indica el nombre del color.', 'error'); return; }
        e.target.disabled = true;
        try {
          await api('POST', '/api/catalogos/colores', { nombre });
          mostrarMensaje(root, `Color "${nombre}" creado.`, 'ok');
          dibujarColores(cont);
        } catch (err) {
          mostrarMensaje(root, err.network ? 'Sin conexión con el servidor.' : err.message, 'error');
          e.target.disabled = false;
        }
      };
    } catch (err) {
      cont.innerHTML = `<div class="mensaje error">${escapar(err.network ? 'Sin conexión con el servidor.' : err.message)}</div>`;
    }
  }

  async function dibujarSecciones(cont) {
    try {
      const [secciones, tiendas] = await Promise.all([
        api('GET', '/api/catalogos/secciones'),
        api('GET', '/api/tiendas'),
      ]);
      let codti = tiendas.find(t => t.codti === s.codti)?.codti ?? tiendas[0]?.codti;
      const render = () => {
        const deLaTienda = secciones.filter(sec => sec.codti === codti);
        cont.innerHTML = `
          <div class="tarjeta">
            <h2 style="margin-top:0;">Nueva sección</h2>
            <label class="obligatorio">Sucursal</label>
            <select id="ct-seccion-tienda">${tiendas.map(t => `<option value="${t.codti}">${escapar(t.nombre)}</option>`).join('')}</select>
            <label class="obligatorio">Nombre</label>
            <div class="fila">
              <input id="ct-seccion-nombre" placeholder="Ej. Vitrina 2, Bodega">
              <button id="ct-seccion-guardar" class="btn btn-verde btn-chico" style="flex:0 0 auto;">Guardar</button>
            </div>
          </div>
          <h2>Secciones de ${escapar(tiendas.find(t => t.codti === codti)?.nombre || '')}</h2>
          ${deLaTienda.length === 0 ? '<div class="vacio">Sin secciones.</div>'
            : deLaTienda.map(sec => `<div class="detalle-fila"><span class="k">${escapar(sec.nombre)}</span></div>`).join('')}`;
        document.getElementById('ct-seccion-tienda').value = codti;
        document.getElementById('ct-seccion-tienda').onchange = (e) => { codti = Number(e.target.value); render(); };
        document.getElementById('ct-seccion-guardar').onclick = async (e) => {
          const nombre = document.getElementById('ct-seccion-nombre').value.trim();
          if (!nombre) { mostrarMensaje(root, 'Indica el nombre de la sección.', 'error'); return; }
          e.target.disabled = true;
          try {
            await api('POST', '/api/catalogos/secciones', { codti, nombre });
            mostrarMensaje(root, `Sección "${nombre}" creada.`, 'ok');
            dibujarSecciones(cont);
          } catch (err) {
            mostrarMensaje(root, err.network ? 'Sin conexión con el servidor.' : err.message, 'error');
            e.target.disabled = false;
          }
        };
      };
      render();
    } catch (err) {
      cont.innerHTML = `<div class="mensaje error">${escapar(err.network ? 'Sin conexión con el servidor.' : err.message)}</div>`;
    }
  }

  async function dibujarProveedores(cont) {
    try {
      const proveedores = await api('GET', '/api/catalogos/proveedores');
      cont.innerHTML = `
        <div class="tarjeta">
          <h2 style="margin-top:0;">Nuevo proveedor</h2>
          <label class="obligatorio">Nombre corto</label>
          <input id="ct-prov-corto" placeholder="Ej. Mayorista Movil MX">
          <label>Razón social</label>
          <input id="ct-prov-fiscal" placeholder="Opcional">
          <label>RFC / Tax ID</label>
          <input id="ct-prov-rfc" placeholder="Opcional">
          <label>Teléfono</label>
          <input id="ct-prov-tel" inputmode="tel" placeholder="Opcional">
          <label>Días de crédito</label>
          <input id="ct-prov-credito" type="number" inputmode="numeric" min="0" placeholder="Opcional">
          <button id="ct-prov-guardar" class="btn btn-verde">Guardar proveedor</button>
        </div>
        <h2>Proveedores registrados</h2>
        ${proveedores.length === 0 ? '<div class="vacio">Sin proveedores.</div>' : proveedores.map(p => `
          <div class="orden-item">
            <div class="orden-cab"><span class="orden-folio">${escapar(p.nombreCorto)}</span></div>
            ${p.telefono || p.rfcTaxid ? `<div class="orden-equipo">${[p.telefono, p.rfcTaxid].filter(Boolean).map(escapar).join(' · ')}</div>` : ''}
          </div>`).join('')}`;
      document.getElementById('ct-prov-guardar').onclick = async (e) => {
        const nombreCorto = document.getElementById('ct-prov-corto').value.trim();
        if (!nombreCorto) { mostrarMensaje(root, 'Indica el nombre corto del proveedor.', 'error'); return; }
        e.target.disabled = true;
        try {
          await api('POST', '/api/catalogos/proveedores', {
            nombreCorto,
            nombreFiscal: document.getElementById('ct-prov-fiscal').value.trim() || null,
            rfcTaxid: document.getElementById('ct-prov-rfc').value.trim() || null,
            telefono: document.getElementById('ct-prov-tel').value.trim() || null,
            diasCredito: document.getElementById('ct-prov-credito').value ? Number(document.getElementById('ct-prov-credito').value) : null,
          });
          mostrarMensaje(root, `Proveedor "${nombreCorto}" creado.`, 'ok');
          dibujarProveedores(cont);
        } catch (err) {
          mostrarMensaje(root, err.network ? 'Sin conexión con el servidor.' : err.message, 'error');
          e.target.disabled = false;
        }
      };
    } catch (err) {
      cont.innerHTML = `<div class="mensaje error">${escapar(err.network ? 'Sin conexión con el servidor.' : err.message)}</div>`;
    }
  }

  dibujar();
}

// ── Arranque ──────────────────────────────────────────────────────────────

Net.iniciar();
render();
actualizarBadgeNotificaciones();

if ('serviceWorker' in navigator) {
  window.addEventListener('load', () => {
    navigator.serviceWorker.register('/app/sw.js').catch(() => { /* sin HTTPS/localhost no se puede registrar; la app sigue funcionando en línea */ });
  });
}
