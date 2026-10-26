/**
 * Equipos (celulares y tablets): cada unidad se identifica por su IMEI o, en tablets, su número de serie, y apunta directo a su
 * artículo y a su sucursal. Aquí se ven, se dan de alta, se corrigen, se marcan como rezagados o se dan de baja, y por artículo se
 * ponen el mínimo de existencias y un mismo precio para todas las unidades. Igual que «Equipos» de JSystem.
 */

const CONDICIONES_EQUIPO = [['NUEVO', 'Nuevo'], ['SEMINUEVO', 'Seminuevo'], ['REACONDICIONADO', 'Reacondicionado']];
const nombreCondicionEquipo = (c) => ({ NUEVO: 'Nuevo', SEMINUEVO: 'Seminuevo', USADO: 'Seminuevo', REACONDICIONADO: 'Reacondicionado' })[c] || '';
const claveCondicionEquipo = (t) => { const x = String(t || '').trim().toLowerCase(); return x.startsWith('s') || x.startsWith('u') ? 'SEMINUEVO' : x.startsWith('r') ? 'REACONDICIONADO' : 'NUEVO'; };
/** Un monto escrito en un campo: acepta «$1,200». null si está vacío o no es número. */
const numeroDeCampo = (t) => { if (t == null || String(t).trim() === '') return null; const n = Number(String(t).replace(/[$,\s]/g, '')); return Number.isFinite(n) ? n : null; };

function pastillaEstadoEquipo(u) {
  if (u.estado === 'DISPONIBLE') return u.rezagado ? '<span class="pastilla reparacion">Rezagado</span>' : '<span class="pastilla lista">Disponible</span>';
  if (u.estado === 'EN_GARANTIA') return '<span class="pastilla por-tomar">En garantía</span>';
  return `<span class="pastilla entregada">${escapar(u.estado)}</span>`;
}

async function pantallaEquipos() {
  const s = Sesion.obtener();
  const puedeAjustar = Sesion.tiene('INVENTARIO_AJUSTAR');
  const puedeEditar = Sesion.tiene('INVENTARIO_EDITAR');
  const esSuperior = SUPERIOR.includes(s.rol);
  let codti = s.codti;
  let unidades = [];
  let verCostos = false;

  root.innerHTML = `
    ${cabeceraPantalla(ic('smartphone') + ' Equipos', 'Celulares y tablets por IMEI o número de serie', `
      <button id="eq-articulos" class="btn btn-gris">${ic('tags')} Por artículo</button>
      ${puedeAjustar ? `<button id="eq-registrar" class="btn btn-azul">${ic('plus')} Registrar equipos</button>` : ''}`)}
    <div id="eq-form" class="oculto"></div>
    <div class="kp-h">
      ${kpiH('c1', 'smartphone', '…', 'Equipos disponibles', 'eq-k-disp')}
      ${kpiH('c3', 'banknote', '…', 'Valor al costo (disponibles)', 'eq-k-valor')}
      ${kpiH('c4', 'triangle-alert', '…', 'Rezagados', 'eq-k-rez')}
    </div>
    <div class="tarjeta iv-filtros">
      <div id="eq-tienda"></div>
      <div class="iv-filtros-fila">
        <div class="buscador buscar-ic">${ic('search')}<input id="eq-buscar" placeholder="Buscar por IMEI, serie o nombre"></div>
        <select id="eq-estado" aria-label="Estado"><option value="DISPONIBLE">Disponibles</option><option value="EN_GARANTIA">En garantía</option><option value="">Todos</option></select>
        <select id="eq-cat" aria-label="Categoría"><option value="">Todas las categorías</option></select>
      </div>
      <div id="eq-lista" style="margin-top:10px;"><div class="vacio">Cargando...</div></div>
    </div>`;

  const contForm = document.getElementById('eq-form');
  const mensaje = (err) => mostrarMensaje(root, err.network ? 'Sin conexión con el servidor.' : err.message, 'error');

  const filtradas = () => {
    const q = quitarAcentos(document.getElementById('eq-buscar').value.trim()).toLowerCase();
    const estado = document.getElementById('eq-estado').value;
    const cat = document.getElementById('eq-cat').value;
    return unidades.filter(u => (!estado || u.estado === estado) && (!cat || u.subcategoria1 === cat)
      && (!q || quitarAcentos(`${u.imei} ${u.nombreArticulo || ''}`).toLowerCase().includes(q)));
  };

  const dibujar = () => {
    const lista = filtradas();
    const disp = unidades.filter(u => u.estado === 'DISPONIBLE');
    document.getElementById('eq-k-disp').textContent = String(disp.length);
    document.getElementById('eq-k-valor').textContent = verCostos ? formatoDinero(disp.filter(u => u.costo != null).reduce((a, u) => a + Number(u.costo), 0)) : '—';
    document.getElementById('eq-k-rez').textContent = String(disp.filter(u => u.rezagado).length);
    const cont = document.getElementById('eq-lista');
    if (lista.length === 0) { cont.innerHTML = '<div class="vacio">No hay equipos que mostrar.</div>'; return; }
    const MAX = 120;
    const mostrados = lista.slice(0, MAX);
    const precio = (u) => Number(u.descuentoAplicado) > 0 ? `${formatoDinero(u.precioFinal)} <span class="ayuda" style="display:inline;">(−${formatoDinero(u.descuentoAplicado)})</span>` : formatoDinero(u.precioVenta);
    const acciones = (u) => `
      <button class="btn btn-gris btn-chico eq-copiar" data-imei="${escapar(u.imei)}" title="Copiar el IMEI o serie">${ic('clipboard-list')}</button>
      ${puedeEditar ? `<button class="btn btn-gris btn-chico eq-editar" data-imei="${escapar(u.imei)}" title="Editar el equipo">${ic('pencil')}</button>` : ''}
      ${puedeAjustar && u.estado === 'DISPONIBLE' ? `
        <button class="btn btn-gris btn-chico eq-rezagado" data-imei="${escapar(u.imei)}" title="Marcar o quitar «rezagado»">${ic('hourglass')}</button>
        <button class="btn btn-rojo btn-chico eq-baja" data-imei="${escapar(u.imei)}" title="Dar de baja">${ic('trash-2')}</button>` : ''}`;
    cont.innerHTML = (lista.length > MAX ? `<div class="ayuda" style="margin:6px 0;">Mostrando ${MAX} de ${lista.length} equipos: escribe arriba para buscar el que necesitas.</div>` : '') + `
      <div class="tabla-cont"><table class="tabla">
        <thead><tr><th>IMEI / Serie</th><th>Artículo</th><th>Condición</th><th>Precio</th>${verCostos ? '<th>Costo</th>' : ''}<th>Días</th><th>Sección</th><th>Estado</th><th></th></tr></thead>
        <tbody>${mostrados.map(u => `
          <tr>
            <td style="font-family:monospace; font-weight:700;">${escapar(u.imei)}</td>
            <td class="tabla-nombre">${escapar(u.nombreArticulo)}</td>
            <td>${escapar(nombreCondicionEquipo(u.condicion))}</td>
            <td class="tabla-precio">${precio(u)}</td>
            ${verCostos ? `<td>${u.costo != null ? formatoDinero(u.costo) : ''}</td>` : ''}
            <td>${u.diasEnExistencia ?? ''}</td>
            <td>${escapar(u.nombreSeccion || '—')}</td>
            <td>${pastillaEstadoEquipo(u)}</td>
            <td style="white-space:nowrap;">${acciones(u)}</td>
          </tr>`).join('')}</tbody>
      </table></div>
      <div class="inv-tarjetas">${mostrados.map(u => `
        <div class="orden-item">
          <div class="orden-cab"><span class="orden-folio">${ic('smartphone')} ${escapar(u.nombreArticulo)}</span>${pastillaEstadoEquipo(u)}</div>
          <div class="orden-cliente" style="font-family:monospace;">${escapar(u.imei)}${u.condicion && u.condicion !== 'NUEVO' ? ' · ' + escapar(nombreCondicionEquipo(u.condicion)) : ''}</div>
          <div class="orden-meta"><span class="orden-fecha">${precio(u)}${verCostos && u.costo != null ? ' · costo ' + formatoDinero(u.costo) : ''}</span><span class="ayuda" style="margin:0;">${u.diasEnExistencia != null ? u.diasEnExistencia + ' días' : ''}${u.nombreSeccion ? ' · ' + escapar(u.nombreSeccion) : ''}</span></div>
          <div class="grupo-botones" style="margin-top:8px;">${acciones(u)}</div>
        </div>`).join('')}</div>`;

    cont.querySelectorAll('.eq-copiar').forEach(b => b.onclick = async () => {
      try { await navigator.clipboard.writeText(b.dataset.imei); mostrarMensaje(root, 'Copiado: ' + b.dataset.imei, 'ok'); }
      catch { mostrarMensaje(root, 'No se pudo copiar: ' + b.dataset.imei, 'info'); }
    });
    const unidad = (b) => unidades.find(x => x.imei === b.dataset.imei);
    cont.querySelectorAll('.eq-editar').forEach(b => b.onclick = () => formularioEditarEquipo(contForm, unidad(b), codti, () => { contForm.classList.add('oculto'); cargar(); }));
    cont.querySelectorAll('.eq-rezagado').forEach(b => b.onclick = async () => {
      const u = unidad(b);
      try { await api('PATCH', `/api/equipos/${encodeURIComponent(u.imei)}/rezagado?rezagado=${!u.rezagado}`); cargar(); } catch (err) { mensaje(err); }
    });
    cont.querySelectorAll('.eq-baja').forEach(b => b.onclick = async () => {
      const u = unidad(b);
      const motivo = (prompt(`Dar de baja el equipo ${u.imei}\n${u.nombreArticulo}\n\nMotivo (robo, daño, error de captura...):`) || '').trim();
      if (!motivo) return;
      if (motivo.length < 3) { mostrarMensaje(root, 'Escribe por qué se da de baja.', 'error'); return; }
      try { await api('POST', `/api/equipos/${encodeURIComponent(u.imei)}/baja`, { motivo }); cargar(); } catch (err) { mensaje(err); }
    });
  };

  const llenarCategorias = () => {
    const sel = document.getElementById('eq-cat');
    const actual = sel.value;
    const cats = [...new Set(unidades.map(u => u.subcategoria1).filter(Boolean))].sort((a, b) => a.localeCompare(b, 'es'));
    sel.innerHTML = '<option value="">Todas las categorías</option>' + cats.map(c => `<option value="${escapar(c)}">${escapar(c)}</option>`).join('');
    sel.value = cats.includes(actual) ? actual : '';
  };

  const cargar = async () => {
    if (codti == null) return;
    document.getElementById('eq-lista').innerHTML = '<div class="vacio">Cargando...</div>';
    try {
      unidades = await api('GET', `/api/equipos/tienda/${codti}`);
      verCostos = unidades.some(u => u.costo != null);
      llenarCategorias();
      dibujar();
    } catch (err) {
      document.getElementById('eq-lista').innerHTML = `<div class="mensaje error">${escapar(err.network ? 'Sin conexión con el servidor.' : err.message)}</div>`;
    }
  };

  const contTienda = document.getElementById('eq-tienda');
  if (esSuperior) {
    contTienda.innerHTML = '<label class="obligatorio">Sucursal</label><select id="eq-codti"><option>Cargando...</option></select>';
    try {
      const tiendas = await api('GET', '/api/tiendas');
      const sel = document.getElementById('eq-codti');
      sel.innerHTML = tiendas.map(t => `<option value="${t.codti}">${escapar(t.nombre)}</option>`).join('');
      codti = tiendas.find(t => t.codti === s.codti)?.codti ?? tiendas[0]?.codti;
      if (codti != null) sel.value = codti;
      sel.onchange = () => { codti = Number(sel.value); cargar(); };
    } catch { contTienda.innerHTML = '<div class="mensaje info">No se pudo cargar la lista de sucursales (sin conexión).</div>'; }
  } else {
    contTienda.innerHTML = '<div class="ayuda">Sucursal: <strong>tu tienda</strong></div>';
  }

  ['input', 'change'].forEach(ev => {
    document.getElementById('eq-buscar').addEventListener(ev, dibujar);
    document.getElementById('eq-estado').addEventListener(ev, dibujar);
    document.getElementById('eq-cat').addEventListener(ev, dibujar);
  });
  document.getElementById('eq-articulos').onclick = () => panelEquiposPorArticulo(contForm, () => codti, puedeEditar, puedeAjustar, cargar);
  const btnReg = document.getElementById('eq-registrar');
  if (btnReg) btnReg.onclick = () => formularioRegistrarEquipos(contForm, () => codti, () => { contForm.classList.add('oculto'); cargar(); });
  cargar();
}

/** Alta de equipos: un artículo que ya existe y la lista de IMEI o series (una por línea). */
async function formularioRegistrarEquipos(cont, obtenerCodti, alGuardar) {
  cont.classList.remove('oculto');
  cont.innerHTML = `
    <div class="tarjeta">
      <h2 class="h2-ic" style="margin-top:0;">${ic('plus')} Registrar equipos</h2>
      <label class="obligatorio">Artículo</label>
      <input id="re-filtro" placeholder="Buscar el artículo...">
      <select id="re-articulo"><option value="">Cargando...</option></select>
      <div class="ayuda">¿El modelo o color aún no existe? Se registra en Inventario → Nuevo producto (elige la categoría Equipos y escribe sus IMEI).</div>
      <label class="obligatorio">IMEI o serie (uno por línea)</label>
      <textarea id="re-imeis" rows="6" placeholder="Solo el IMEI:  350000000000011&#10;Un seminuevo con su costo y precio:  350000000000029; seminuevo; 800; 1500"></textarea>
      <div class="fila">
        <div><label>Condición</label><select id="re-condicion">${CONDICIONES_EQUIPO.map(([v, n]) => `<option value="${v}">${n}</option>`).join('')}</select></div>
        <div><label>Costo de cada equipo</label><input id="re-costo" type="number" inputmode="decimal" min="0" step="1"></div>
        <div><label>Precio de venta de cada equipo</label><input id="re-precio" type="number" inputmode="decimal" min="0" step="1"></div>
      </div>
      <label>Proveedor <span class="ayuda" style="display:inline;">(opcional)</span></label>
      <select id="re-proveedor"><option value="">(sin proveedor)</option></select>
      <div class="grupo-botones">
        <button id="re-guardar" class="btn btn-verde">Registrar</button>
        <button id="re-cancelar" class="btn btn-gris">Cancelar</button>
      </div>
    </div>`;
  let catalogo = [];
  const selArt = document.getElementById('re-articulo');
  const pintar = () => {
    const q = quitarAcentos(document.getElementById('re-filtro').value.trim()).toLowerCase();
    const lista = catalogo.filter(a => !q || quitarAcentos(a.nombreArticulo).toLowerCase().includes(q));
    selArt.innerHTML = lista.length ? lista.map(a => `<option value="${a.idprodmaster}">${escapar(a.nombreArticulo)}</option>`).join('') : '<option value="">Sin coincidencias</option>';
  };
  try {
    const [cat, prov] = await Promise.all([api('GET', '/api/equipos/catalogo'), api('GET', '/api/catalogos/proveedores').catch(() => [])]);
    catalogo = cat;
    pintar();
    document.getElementById('re-proveedor').innerHTML = '<option value="">(sin proveedor)</option>' +
      prov.slice().sort((a, b) => (a.nombreCorto || '').localeCompare(b.nombreCorto || '', 'es')).map(p => `<option value="${p.id}">${escapar(p.nombreCorto)}</option>`).join('');
  } catch (err) { selArt.innerHTML = '<option value="">No se pudo cargar el catálogo</option>'; }
  document.getElementById('re-filtro').addEventListener('input', pintar);
  document.getElementById('re-cancelar').onclick = () => { cont.classList.add('oculto'); cont.innerHTML = ''; };
  document.getElementById('re-guardar').onclick = async (e) => {
    if (!selArt.value) { mostrarMensaje(root, 'Elige de qué artículo son los equipos.', 'error'); return; }
    const condGeneral = document.getElementById('re-condicion').value;
    const unidades = [];
    document.getElementById('re-imeis').value.split(/\r?\n/).forEach(linea => {
      if (!linea.trim()) return;
      const p = linea.split(/[;,\t]/);
      const u = { imei: p[0].trim(), condicion: p[1] && p[1].trim() ? claveCondicionEquipo(p[1]) : condGeneral };
      if (p[2] && p[2].trim()) u.costoUnitario = numeroDeCampo(p[2]);
      if (p[3] && p[3].trim()) u.precioVenta = numeroDeCampo(p[3]);
      unidades.push(u);
    });
    if (unidades.length === 0) { mostrarMensaje(root, 'Escribe al menos un IMEI o número de serie.', 'error'); return; }
    const alta = {
      codti: obtenerCodti(), idProductoMaster: Number(selArt.value), unidades, motivo: 'Alta de equipos',
      costo: numeroDeCampo(document.getElementById('re-costo').value), precioVenta: numeroDeCampo(document.getElementById('re-precio').value),
      idProveedor: document.getElementById('re-proveedor').value ? Number(document.getElementById('re-proveedor').value) : null,
    };
    e.target.disabled = true;
    try {
      const r = await api('POST', '/api/equipos', alta);
      mostrarMensaje(root, `${r.length} equipo(s) registrado(s).`, 'ok');
      alGuardar();
    } catch (err) {
      mostrarMensaje(root, err.network ? 'Sin conexión con el servidor.' : err.message, 'error');
      e.target.disabled = false;
    }
  };
}

/** Corrección de un equipo (quien puede editar artículos): precio, costo, condición, proveedor, sección, rezagado y, si no se ha movido, su IMEI y artículo. */
async function formularioEditarEquipo(cont, u, codti, alGuardar) {
  cont.classList.remove('oculto');
  const identidad = ['DISPONIBLE', 'DEBAJA', 'DEVUELTO'].includes(u.estado);   // sin ventas, traspasos ni garantías
  cont.innerHTML = `
    <div class="tarjeta">
      <h2 class="h2-ic" style="margin-top:0;">${ic('pencil')} Editar equipo</h2>
      <div class="ayuda">${identidad ? 'Corregir el IMEI o el artículo es para errores de captura.' : 'Este equipo ya figura en una venta, un traspaso o una garantía: su IMEI y su artículo no se pueden cambiar, lo demás sí.'}</div>
      <label>IMEI / Serie</label>
      <input id="ee-imei" value="${escapar(u.imei)}" ${identidad ? '' : 'disabled'}>
      <label>Artículo</label>
      <select id="ee-articulo" ${identidad ? '' : 'disabled'}><option>${escapar(u.nombreArticulo)}</option></select>
      <div class="fila">
        <div><label>Precio de venta</label><input id="ee-precio" type="number" inputmode="decimal" min="0" step="1" value="${u.precioVenta}"></div>
        <div><label>Costo</label><input id="ee-costo" type="number" inputmode="decimal" min="0" step="1" value="${u.costo != null ? u.costo : ''}" ${u.costo == null ? 'disabled' : ''}></div>
      </div>
      <label>Condición</label>
      <select id="ee-condicion">${CONDICIONES_EQUIPO.map(([v, n]) => `<option value="${v}" ${claveCondicionEquipo(u.condicion) === v ? 'selected' : ''}>${n}</option>`).join('')}</select>
      <label>Proveedor</label>
      <select id="ee-proveedor" disabled><option>Cargando...</option></select>
      <label>Sección</label>
      <select id="ee-seccion" disabled><option>Cargando...</option></select>
      <label class="casilla"><input type="checkbox" id="ee-rezagado" ${u.rezagado ? 'checked' : ''}> Rezagado (que no se vende)</label>
      <div class="grupo-botones">
        <button id="ee-guardar" class="btn btn-verde">Guardar cambios</button>
        <button id="ee-cancelar" class="btn btn-gris">Cancelar</button>
      </div>
    </div>`;
  document.getElementById('ee-cancelar').onclick = () => { cont.classList.add('oculto'); cont.innerHTML = ''; };
  (async () => {
    try {
      const [cat, prov, sec] = await Promise.all([api('GET', '/api/equipos/catalogo'), api('GET', '/api/catalogos/proveedores'), api('GET', '/api/catalogos/secciones')]);
      document.getElementById('ee-articulo').innerHTML = cat.map(a => `<option value="${a.idprodmaster}" ${a.idprodmaster === u.idprodmaster ? 'selected' : ''}>${escapar(a.nombreArticulo)}</option>`).join('');
      const sp = document.getElementById('ee-proveedor');
      sp.innerHTML = '<option value="">(sin cambiar)</option>' + prov.slice().sort((a, b) => (a.nombreCorto || '').localeCompare(b.nombreCorto || '', 'es'))
        .map(p => `<option value="${p.id}" ${p.id === u.idProveedor ? 'selected' : ''}>${escapar(p.nombreCorto)}</option>`).join('');
      sp.disabled = false;
      const ss = document.getElementById('ee-seccion');
      ss.innerHTML = `<option value="">${u.idSeccion == null ? '(sin sección)' : '(dejar como está)'}</option>` + sec.filter(x => x.codti === (u.codti ?? codti))
        .sort((a, b) => a.nombre.localeCompare(b.nombre, 'es')).map(x => `<option value="${x.id}" ${x.id === u.idSeccion ? 'selected' : ''}>${escapar(x.nombre)}</option>`).join('');
      ss.disabled = false;
    } catch { /* sin catálogos se puede editar lo demás */ }
  })();
  document.getElementById('ee-guardar').onclick = async (e) => {
    const cambio = {};
    const nuevoImei = document.getElementById('ee-imei').value.trim();
    if (identidad && nuevoImei && nuevoImei.toLowerCase() !== u.imei.toLowerCase()) cambio.nuevoImei = nuevoImei;
    const art = Number(document.getElementById('ee-articulo').value);
    if (identidad && art && art !== u.idprodmaster) cambio.idProductoMaster = art;
    const precio = numeroDeCampo(document.getElementById('ee-precio').value);
    if (precio == null) { mostrarMensaje(root, 'El precio debe ser un número.', 'error'); return; }
    cambio.precioVenta = precio;
    if (!document.getElementById('ee-costo').disabled) {
      const costo = numeroDeCampo(document.getElementById('ee-costo').value);
      if (costo == null) { mostrarMensaje(root, 'El costo debe ser un número.', 'error'); return; }
      cambio.costo = costo;
    }
    cambio.condicion = document.getElementById('ee-condicion').value;
    cambio.rezagado = document.getElementById('ee-rezagado').checked;
    const prov = document.getElementById('ee-proveedor').value;
    if (prov && Number(prov) !== u.idProveedor) cambio.idProveedor = Number(prov);
    const sec = document.getElementById('ee-seccion').value;
    if (sec && Number(sec) !== u.idSeccion) cambio.idSeccion = Number(sec);
    e.target.disabled = true;
    try { await api('PUT', `/api/equipos/${encodeURIComponent(u.imei)}`, cambio); mostrarMensaje(root, 'Equipo actualizado.', 'ok'); alGuardar(); }
    catch (err) { mostrarMensaje(root, err.network ? 'Sin conexión con el servidor.' : err.message, 'error'); e.target.disabled = false; }
  };
}

/** Los artículos de la sucursal con cuántas unidades hay de cada uno, su mínimo y su rango de precios; permite poner un mismo precio a todas las unidades. */
async function panelEquiposPorArticulo(cont, obtenerCodti, puedeEditar, puedeAjustar, alCambiar) {
  cont.classList.remove('oculto');
  cont.innerHTML = '<div class="tarjeta"><div class="vacio">Cargando...</div></div>';
  const codti = obtenerCodti();
  let arts;
  try { arts = await api('GET', `/api/equipos/tienda/${codti}/articulos?conUnidades=false`); }
  catch (err) { cont.innerHTML = `<div class="tarjeta"><div class="mensaje error">${escapar(err.network ? 'Sin conexión con el servidor.' : err.message)}</div></div>`; return; }
  const rango = (a) => a.precioMinimo == null ? '—' : (a.precioMinimo === a.precioMaximo ? formatoDinero(a.precioMinimo) : `${formatoDinero(a.precioMinimo)} – ${formatoDinero(a.precioMaximo)}`);
  cont.innerHTML = `
    <div class="tarjeta">
      <div class="lista-cab"><h2 class="h2-ic">${ic('tags')} Equipos por artículo</h2><button class="enlace" id="pa-cerrar">${ic('x')} Cerrar</button></div>
      ${arts.length === 0 ? '<div class="vacio">No hay equipos en esta sucursal.</div>' : `
      <div class="tabla-cont"><table class="tabla">
        <thead><tr><th>Artículo</th><th>Disponibles</th><th>Mínimo</th><th>Precio</th><th></th></tr></thead>
        <tbody>${arts.map(a => `
          <tr>
            <td class="tabla-nombre">${escapar(a.nombreArticulo)}</td>
            <td>${a.disponibles}${a.enGarantia > 0 ? ` <span class="ayuda" style="display:inline;">(+${a.enGarantia} en garantía)</span>` : ''}</td>
            <td style="${a.bajoStock ? 'color:#b91c1c; font-weight:700;' : ''}">${a.stockMinimo > 0 ? a.stockMinimo : '—'}</td>
            <td>${rango(a)}</td>
            <td style="white-space:nowrap;">
              ${puedeEditar ? `<button class="btn btn-gris btn-chico pa-precio" data-id="${a.idprodmaster}">${ic('tags')} Mismo precio</button>` : ''}
              ${puedeAjustar ? `<button class="btn btn-gris btn-chico pa-min" data-id="${a.idprodmaster}">${ic('triangle-alert')} Mínimo</button>` : ''}
            </td>
          </tr>`).join('')}</tbody>
      </table></div>`}
    </div>`;
  document.getElementById('pa-cerrar').onclick = () => { cont.classList.add('oculto'); cont.innerHTML = ''; };
  const art = (b) => arts.find(a => a.idprodmaster === Number(b.dataset.id));
  cont.querySelectorAll('.pa-precio').forEach(b => b.onclick = async () => {
    const a = art(b);
    const txt = prompt(`${a.nombreArticulo}\nSe aplica a las ${a.disponibles} unidad(es) disponibles de esta sucursal.\n\nPrecio de venta:`);
    if (txt == null) return;
    const p = numeroDeCampo(txt);
    if (p == null || p <= 0) { mostrarMensaje(root, 'Escribe un precio mayor a $0.', 'error'); return; }
    try { await api('PUT', `/api/equipos/articulo/${a.idprodmaster}/precio?codti=${codti}`, { precioVenta: p }); mostrarMensaje(root, 'Precio actualizado.', 'ok'); alCambiar(); panelEquiposPorArticulo(cont, obtenerCodti, puedeEditar, puedeAjustar, alCambiar); }
    catch (err) { mostrarMensaje(root, err.network ? 'Sin conexión con el servidor.' : err.message, 'error'); }
  });
  cont.querySelectorAll('.pa-min').forEach(b => b.onclick = async () => {
    const a = art(b);
    const txt = prompt(`${a.nombreArticulo}\nCuando queden esta cantidad o menos, se avisa. 0 quita el aviso.\n\nMínimo:`, String(a.stockMinimo));
    if (txt == null) return;
    const m = Number(txt);
    if (!Number.isInteger(m) || m < 0) { mostrarMensaje(root, 'Escribe un número entero.', 'error'); return; }
    try { await api('PUT', `/api/equipos/articulo/${a.idprodmaster}/stock-minimo`, { minimo: m }); mostrarMensaje(root, 'Mínimo actualizado.', 'ok'); panelEquiposPorArticulo(cont, obtenerCodti, puedeEditar, puedeAjustar, alCambiar); }
    catch (err) { mostrarMensaje(root, err.network ? 'Sin conexión con el servidor.' : err.message, 'error'); }
  });
}
