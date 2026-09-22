/**
 * Skycel · Web para celular y tablet. Una sola página con ruteo por hash (#/...). Usa las mismas rutas de la
 * API que JSystem, con JWT en localStorage.
 *
 * Alcance de esta primera versión: recepción de equipos (con cola sin conexión), el taller para técnicos, y
 * consulta de órdenes. Todo lo demás (caja, ventas, inventario…) sigue en JSystem.
 */

const PERSONAL = ['ROOT', 'ADMIN', 'ENCARGADO_TIENDA', 'VENDEDOR']; // puede recibir equipos, ver/crear clientes, garantías
const TALLER_ROLES = ['ROOT', 'ADMIN', 'TECNICO'];                  // puede trabajar el taller
const SUPERIOR = ['ROOT', 'ADMIN'];                                  // ve todas las tiendas y asigna técnico
const GESTOR_ROLES = ['ROOT', 'ADMIN', 'ENCARGADO_TIENDA'];          // cuentas por cobrar y reportes de ventas

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
    if (ruta === 'clientes') return pantallaClientes();
    if (ruta === 'cliente' && param) return pantallaClienteDetalle(param);
    if (ruta === 'cxc') return pantallaCxC();
    if (ruta === 'cuenta' && param) return pantallaCuentaDetalle(param);
    if (ruta === 'garantias') return pantallaGarantias();
    if (ruta === 'garantia' && param) return pantallaGarantiaDetalle(param);
    if (ruta === 'reportes') return pantallaReportes();
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
  tarjetas.push({ h: '#/clientes', i: '👥', t: 'Clientes' });
  if (GESTOR_ROLES.includes(s.rol)) tarjetas.push({ h: '#/cxc', i: '💳', t: 'Cuentas por cobrar' });
  if (PERSONAL.includes(s.rol)) tarjetas.push({ h: '#/garantias', i: '🛡️', t: 'Garantías' });
  if (GESTOR_ROLES.includes(s.rol)) tarjetas.push({ h: '#/reportes', i: '📊', t: 'Reporte de ventas' });
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

// ── Guardado en el equipo (colas sin conexión) ────────────────────────────

async function pantallaPendientes() {
  root.innerHTML = `
    <h1>📤 Guardado en este equipo</h1>
    <div class="grupo-botones" style="margin-bottom:14px;">
      <button id="pe-sincronizar" class="btn btn-verde">🔁 Intentar enviar todo ahora</button>
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
            ? `<span class="pastilla lista">✅ Enviada${it.folio ? ' (' + escapar(it.folio) + ')' : ''}</span>`
            : it.estado === 'error' ? '<span class="pastilla error">⚠️ Revisar</span>' : '<span class="pastilla pendiente">⏳ Por enviar</span>'}
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
          ${it.estado === 'enviada' ? '<span class="pastilla lista">✅ Enviado</span>'
            : it.estado === 'error' ? '<span class="pastilla error">⚠️ Revisar</span>' : '<span class="pastilla pendiente">⏳ Por enviar</span>'}
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
    <h1>👥 Clientes</h1>
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
  if (GESTOR_ROLES.includes(s.rol)) botones.push({ t: '✏️ Editar', color: 'btn-azul', fn: () => dibujarFormularioCliente(document.getElementById('cd-form'), c, () => pantallaClienteDetalle(id)) });
  if (SUPERIOR.includes(s.rol)) botones.push({ t: '🗑 Desactivar', color: 'btn-rojo', fn: async () => {
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
    <h1>💳 Cuentas por cobrar</h1>
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
  if (g.vencida) return '<span class="pastilla error">⚠️ Vencida</span>';
  const clave = g.estado === 11 ? 'entregada' : g.estado === 8 ? 'cancelada' : g.estado >= 9 ? 'lista' : g.estado === 1 ? 'recibida' : 'reparacion';
  return `<span class="pastilla ${clave}">${escapar(g.estadoDisplay)}</span>`;
}

async function pantallaGarantias() {
  const s = Sesion.obtener();
  if (!PERSONAL.includes(s.rol)) { root.innerHTML = '<div class="tarjeta">No tienes permiso para ver garantías.</div>'; return; }
  root.innerHTML = `
    <h1>🛡️ Garantías</h1>
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
    <h1>📊 Reporte de ventas</h1>
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

// ── Arranque ──────────────────────────────────────────────────────────────

Net.iniciar();
render();

if ('serviceWorker' in navigator) {
  window.addEventListener('load', () => {
    navigator.serviceWorker.register('/app/sw.js').catch(() => { /* sin HTTPS/localhost no se puede registrar; la app sigue funcionando en línea */ });
  });
}
