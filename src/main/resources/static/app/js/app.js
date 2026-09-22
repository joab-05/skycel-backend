/**
 * Skycel · Web para celular y tablet. Una sola página con ruteo por hash (#/...). Usa las mismas rutas de la
 * API que JSystem, con JWT en localStorage.
 *
 * Alcance de esta primera versión: recepción de equipos (con cola sin conexión), el taller para técnicos, y
 * consulta de órdenes. Todo lo demás (caja, ventas, inventario…) sigue en JSystem.
 */

const PERSONAL = ['ROOT', 'ADMIN', 'ENCARGADO_TIENDA', 'VENDEDOR']; // puede recibir equipos
const TALLER_ROLES = ['ROOT', 'ADMIN', 'TECNICO'];                  // puede trabajar el taller
const SUPERIOR = ['ROOT', 'ADMIN'];                                  // ve todas las tiendas y asigna técnico

const root = document.getElementById('root');
const encabezado = document.getElementById('encabezado');
const navInferior = document.getElementById('nav-inferior');
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
        barraConexion.textContent = pend > 0 ? `🟡 Enviando ${pend} recepción(es) guardada(s)...` : '';
      } else {
        barraConexion.className = '';
        barraConexion.textContent = `🔴 Sin conexión — se guarda en este dispositivo${pend > 0 ? ' · ' + pend + ' por enviar' : ''}`;
      }
      if (err > 0) barraConexion.textContent += `  ·  ⚠️ ${err} para revisar`;
      barraConexion.style.display = (Net.enLinea() && pend === 0 && err === 0) ? 'none' : 'block';
      barraConexion.onclick = () => navegar('#/pendientes');
      barraConexion.style.cursor = 'pointer';
    });
  });
}
Net.agregarOyente(actualizarBarraConexion);

function actualizarEncabezado() {
  const s = Sesion.obtener();
  if (!s) { encabezado.classList.add('oculto'); navInferior.classList.add('oculto'); return; }
  encabezado.classList.remove('oculto');
  navInferior.classList.remove('oculto');
  usuarioActualEl.textContent = `${s.nombreCompleto || s.username} · ${etiquetaRol(s.rol)}`;
  dibujarNavInferior();
}
function etiquetaRol(rol) {
  return ({ ROOT: 'Administrador', ADMIN: 'Administrador', ENCARGADO_TIENDA: 'Encargado', VENDEDOR: 'Vendedor', TECNICO: 'Técnico' })[rol] || rol;
}

function dibujarNavInferior() {
  const s = Sesion.obtener();
  if (!s) return;
  const ruta = location.hash.split('/')[1] || 'menu';
  const items = [{ h: '#/menu', i: '🏠', t: 'Inicio', clave: 'menu' }];
  if (PERSONAL.includes(s.rol)) items.push({ h: '#/recepcion', i: '📥', t: 'Recibir', clave: 'recepcion' });
  if (TALLER_ROLES.includes(s.rol)) items.push({ h: '#/taller', i: '🔧', t: 'Taller', clave: 'taller' });
  items.push({ h: '#/consultar', i: '🔎', t: 'Buscar', clave: 'consultar' });
  navInferior.innerHTML = items.map(it =>
    `<button data-h="${it.h}" class="${ruta === it.clave ? 'activo' : ''}"><span class="icono">${it.i}</span>${it.t}</button>`
  ).join('');
  navInferior.querySelectorAll('button').forEach(b => b.onclick = () => navegar(b.dataset.h));
}

document.getElementById('btn-salir').onclick = () => {
  if (!confirm('¿Cerrar sesión?')) return;
  Sesion.limpiar();
  navegar('#/login');
};

// ── Router ────────────────────────────────────────────────────────────────

async function render() {
  const s = Sesion.obtener();
  const hash = location.hash || (s ? '#/menu' : '#/login');
  const [, ruta, param] = hash.split('/');

  if (!s && ruta !== 'login') { navegar('#/login'); return; }
  if (s && ruta === 'login') { navegar('#/menu'); return; }

  actualizarEncabezado();
  actualizarBarraConexion();

  try {
    if (ruta === 'login') return pantallaLogin();
    if (ruta === 'menu') return pantallaMenu();
    if (ruta === 'recepcion') return pantallaRecepcion();
    if (ruta === 'taller') return pantallaTaller();
    if (ruta === 'consultar') return pantallaConsultar();
    if (ruta === 'pendientes') return pantallaPendientes();
    if (ruta === 'orden' && param) return pantallaOrdenDetalle(param);
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
      <div style="text-align:center; margin-bottom:22px;">
        <div style="font-size:40px;">📱</div>
        <h1 style="margin-bottom:0;">Skycel</h1>
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

async function pantallaMenu() {
  const s = Sesion.obtener();
  const pend = await Net.cantidadPendiente();
  const err = await Net.cantidadConError();
  const tarjetas = [];
  if (PERSONAL.includes(s.rol)) tarjetas.push({ h: '#/recepcion', i: '📥', t: 'Recibir equipo' });
  if (TALLER_ROLES.includes(s.rol)) tarjetas.push({ h: '#/taller', i: '🔧', t: 'Taller' });
  tarjetas.push({ h: '#/consultar', i: '🔎', t: 'Consultar orden' });
  tarjetas.push({ h: '#/pendientes', i: '📤', t: 'Guardado en el equipo', badge: (pend + err) > 0 ? (pend + err) : null });

  root.innerHTML = `
    <h1>Hola, ${escapar((s.nombreCompleto || s.username).split(' ')[0])}</h1>
    <div class="rejilla-menu">
      ${tarjetas.map(t => `
        <div class="boton-menu" data-h="${t.h}">
          <span class="icono">${t.i}</span>
          <span class="texto">${t.t}</span>
          ${t.badge ? `<span class="badge">${t.badge}</span>` : ''}
        </div>`).join('')}
    </div>
    <div class="ayuda" style="text-align:center; margin-top:20px;">
      Para ventas, caja e inventario sigue usando el sistema de la tienda.
    </div>`;
  root.querySelectorAll('.boton-menu').forEach(b => b.onclick = () => navegar(b.dataset.h));
}

// ── Recepción de equipo ──────────────────────────────────────────────────

async function pantallaRecepcion() {
  const s = Sesion.obtener();
  if (!PERSONAL.includes(s.rol)) { root.innerHTML = '<div class="tarjeta">No tienes permiso para recibir equipos.</div>'; return; }

  root.innerHTML = `
    <h1>📥 Recibir equipo</h1>
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
      out.innerHTML = `✅ <strong>${escapar(c.nombreCompleto)}</strong>`;
    } catch (err) {
      clienteEncontrado = null;
      out.innerHTML = err.network
        ? '🔴 Sin conexión: pasa a "Cliente nuevo" y captura nombre y teléfono; se enlazará al enviarse.'
        : `⚠️ No se encontró. Usa "Cliente nuevo".`;
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
        await DB.put({
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
  root.innerHTML = `<h1>🔧 Taller</h1><div id="ta-lista"><div class="vacio">Cargando...</div></div>`;
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
        ${porTomar ? '<span class="pastilla por-tomar">⏳ Por tomar</span>' : pastillaEstado(o.estadoDisplay)}
      </div>
      <div class="orden-equipo">${escapar(o.marca)} ${escapar(o.modelo)}</div>
      <div class="orden-cliente">${escapar(o.nombreCliente)} · ${escapar(o.nombreTienda)}</div>
      <div class="orden-meta">
        <span class="orden-fecha">${formatoFecha(o.fechaIngreso)}</span>
        <span style="font-size:12px; color:var(--gris);">${o.idTecnico ? '👤 ' + escapar(o.nombreTecnico) : 'Sin técnico'}</span>
      </div>
    </div>`;
}

// ── Consultar ─────────────────────────────────────────────────────────────

async function pantallaConsultar() {
  const s = Sesion.obtener();
  root.innerHTML = `
    <h1>🔎 Consultar orden</h1>
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

  if (esTecnicoLibre) acciones.push({ t: '🖐 Tomar', color: 'btn-azul', fn: () => accionSimple(id, 'tomar', 'POST') });
  if (puedeTrabajar && o.estado === 1 && o.idTecnico) acciones.push({ t: '▶️ Iniciar reparación', color: 'btn-verde', fn: () => accionSimple(id, 'iniciar', 'POST') });
  if (puedeTrabajar && o.estado === 2) acciones.push({ t: '✅ Marcar lista', color: 'btn-verde', fn: () => accionSimple(id, 'lista', 'POST') });
  if (puedeTrabajar) acciones.push({ t: '📝 Agregar observación', color: 'btn-gris', fn: () => document.getElementById('dt-nota-cont').classList.toggle('oculto') });

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

// ── Guardado en el equipo (cola sin conexión) ────────────────────────────

async function pantallaPendientes() {
  root.innerHTML = `<h1>📤 Guardado en este equipo</h1><div id="pe-lista"><div class="vacio">Cargando...</div></div>`;
  const cont = document.getElementById('pe-lista');
  const items = (await DB.todas()).sort((a, b) => b.creado.localeCompare(a.creado));
  if (items.length === 0) { cont.innerHTML = '<div class="vacio">No hay recepciones guardadas en este equipo.</div>'; return; }

  cont.innerHTML = `
    <div class="grupo-botones" style="margin-bottom:14px;">
      <button id="pe-sincronizar" class="btn btn-verde">🔁 Intentar enviar ahora</button>
    </div>
    ${items.map(it => `
      <div class="orden-item" data-clave="${it.clave}">
        <div class="orden-cab">
          <span class="orden-folio">${escapar(it.payload.folioLocal || it.folio || '—')}</span>
          ${it.estado === 'enviada'
            ? `<span class="pastilla lista">✅ Enviada${it.folio ? ' (' + escapar(it.folio) + ')' : ''}</span>`
            : it.estado === 'error' ? '<span class="pastilla error">⚠️ Revisar</span>' : '<span class="pastilla pendiente">⏳ Por enviar</span>'}
        </div>
        <div class="orden-equipo">${escapar(it.resumen)}</div>
        <div class="orden-cliente">${escapar(it.clienteResumen || '')}</div>
        <div class="orden-fecha" style="margin-top:6px;">${formatoFecha(it.creado)}</div>
        ${it.estado === 'error' ? `<div class="mensaje error" style="margin-top:8px;">${escapar(it.error)}</div>
          <div class="grupo-botones"><button class="btn btn-azul btn-chico pe-reintentar" data-clave="${it.clave}">Reintentar</button>
          <button class="btn btn-rojo btn-chico pe-descartar" data-clave="${it.clave}">Descartar</button></div>` : ''}
        ${it.estado === 'enviada' && it.idorden ? `<div style="margin-top:8px;"><button class="enlace pe-ver" data-id="${it.idorden}">Ver la orden →</button></div>` : ''}
      </div>`).join('')}`;

  document.getElementById('pe-sincronizar').onclick = async (e) => {
    e.target.disabled = true;
    e.target.innerHTML = '<span class="spinner"></span> Enviando...';
    await Net.sincronizar();
    pantallaPendientes();
  };
  cont.querySelectorAll('.pe-reintentar').forEach(b => b.onclick = async () => {
    const items2 = await DB.todas();
    const it = items2.find(x => x.clave === b.dataset.clave);
    if (it) { it.estado = 'pendiente'; it.error = null; await DB.put(it); await Net.sincronizar(); pantallaPendientes(); }
  });
  cont.querySelectorAll('.pe-descartar').forEach(b => b.onclick = async () => {
    if (!confirm('¿Descartar esta recepción? No se enviará al servidor.')) return;
    await DB.eliminar(b.dataset.clave);
    pantallaPendientes();
  });
  cont.querySelectorAll('.pe-ver').forEach(b => b.onclick = () => navegar('#/orden/' + b.dataset.id));
}

// ── Arranque ──────────────────────────────────────────────────────────────

Net.iniciar();
render();

if ('serviceWorker' in navigator) {
  window.addEventListener('load', () => {
    navigator.serviceWorker.register('/app/sw.js').catch(() => { /* sin HTTPS/localhost no se puede registrar; la app sigue funcionando en línea */ });
  });
}
