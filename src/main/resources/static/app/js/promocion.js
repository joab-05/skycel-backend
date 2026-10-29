/**
 * Lo que se suma a «Descuentos»:
 *  - las solicitudes de descuento que piden los vendedores en el punto de venta (aprobar, aprobar con otro monto o rechazar);
 *  - la promoción al vender un equipo (chip de regalo, mica y protector con descuento).
 */

/** Cuántas veces por minuto se revisan las solicitudes mientras la pantalla siga abierta. */
const SOLICITUDES_CADA_MS = 10000;
let solicitudesSondeo = null;

async function dibujarSolicitudesDescuento(cont) {
  const pintar = async () => {
    if (!document.body.contains(cont)) { clearInterval(solicitudesSondeo); solicitudesSondeo = null; return; }
    let lista;
    try { lista = await api('GET', '/api/descuentos/solicitudes/pendientes'); }
    catch (err) { cont.innerHTML = `<div class="tarjeta"><h2 class="h2-ic" style="margin-top:0;">${ic('tags')} Solicitudes de descuento</h2><div class="mensaje error">${escapar(err.network ? 'Sin conexión con el servidor.' : err.message)}</div></div>`; return; }
    cont.innerHTML = `
      <div class="tarjeta">
        <div class="lista-cab"><h2 class="h2-ic" style="margin:0;">${ic('tags')} Solicitudes de descuento</h2><span class="pill-items">${lista.length} pendiente${lista.length === 1 ? '' : 's'}</span></div>
        <div class="ayuda">Los vendedores piden un descuento desde el punto de venta; al aprobarlo sirve una vez, para ese artículo, vendedor y sucursal, durante 12 horas.</div>
        ${lista.length === 0 ? '<div class="vacio">No hay solicitudes por atender.</div>' : lista.map(s => `
          <div class="orden-item" data-id="${s.idsolicitud}">
            <div class="orden-cab"><span class="orden-folio">${escapar(s.articulo)}</span><span class="pastilla reparacion">−${formatoDinero(s.descuentoPorPieza)} c/u</span></div>
            <div class="orden-cliente">${escapar(s.nombreTienda)} · ${escapar(s.solicitante)}${s.imei ? ' · IMEI ' + escapar(s.imei) : ''} · ${s.cantidad} pza${s.cantidad === 1 ? '' : 's'} · precio ${formatoDinero(s.precioLista)}</div>
            <div class="orden-equipo">${escapar(s.motivo || '')}</div>
            <div class="grupo-botones">
              <button class="btn btn-verde btn-chico sd-aprobar" data-id="${s.idsolicitud}">${ic('circle-check')} Aprobar</button>
              <button class="btn btn-gris btn-chico sd-otro" data-id="${s.idsolicitud}" data-precio="${s.precioLista}" data-pide="${s.descuentoPorPieza}">Otro monto</button>
              <button class="btn btn-rojo btn-chico sd-rechazar" data-id="${s.idsolicitud}">${ic('circle-x')} Rechazar</button>
            </div>
          </div>`).join('')}
      </div>`;
    const resolver = async (b, ruta) => {
      b.disabled = true;
      try { await api('PATCH', ruta); pintar(); }
      catch (err) { mostrarMensaje(root, err.network ? 'Sin conexión con el servidor.' : err.message, 'error'); b.disabled = false; }
    };
    cont.querySelectorAll('.sd-aprobar').forEach(b => b.onclick = () => resolver(b, `/api/descuentos/solicitudes/${b.dataset.id}/aprobar`));
    cont.querySelectorAll('.sd-otro').forEach(b => b.onclick = () => {
      const txt = prompt(`Descuento por pieza que apruebas (piden ${formatoDinero(b.dataset.pide)}; el precio es ${formatoDinero(b.dataset.precio)}):`, b.dataset.pide);
      if (txt == null) return;
      const m = Number(String(txt).replace(/[$,\s]/g, ''));
      if (!(m > 0) || m >= Number(b.dataset.precio)) { mostrarMensaje(root, 'Indica un descuento mayor a $0 y menor al precio.', 'error'); return; }
      resolver(b, `/api/descuentos/solicitudes/${b.dataset.id}/aprobar?descuentoPorPieza=${m}`);
    });
    cont.querySelectorAll('.sd-rechazar').forEach(b => b.onclick = () => {
      const motivo = prompt('Motivo del rechazo (opcional):');
      if (motivo == null) return;
      resolver(b, `/api/descuentos/solicitudes/${b.dataset.id}/rechazar${motivo.trim() ? '?motivo=' + encodeURIComponent(motivo.trim()) : ''}`);
    });
  };
  await pintar();
  if (solicitudesSondeo) clearInterval(solicitudesSondeo);
  solicitudesSondeo = setInterval(pintar, SOLICITUDES_CADA_MS);
}

const PROMO_TIPOS = [['REGALO', 'De regalo (gratis)'], ['PORCENTAJE', 'Descuento en %'], ['MONTO', 'Descuento en $'], ['PRECIO_FINAL', 'Precio final fijo ($)']];
const PROMO_REDONDEOS = [['NINGUNO', 'Sin redondeo'], ['PESO_ARRIBA', 'Al peso, hacia arriba'], ['PESO_ABAJO', 'Al peso, hacia abajo'],
  ['CINCO_ARRIBA', 'A múltiplos de $5, hacia arriba'], ['CINCO_ABAJO', 'A múltiplos de $5, hacia abajo'],
  ['DIEZ_ARRIBA', 'A múltiplos de $10, hacia arriba'], ['DIEZ_ABAJO', 'A múltiplos de $10, hacia abajo']];

/** Lo que hace una regla, en una línea. */
function describirReglaPromocion(r) {
  const que = ({ REGALO: 'de regalo', PORCENTAJE: `${Number(r.valor)} % de descuento`, MONTO: `${formatoDinero(r.valor)} de descuento`, PRECIO_FINAL: `a ${formatoDinero(r.valor)}` })[r.tipo] || '';
  const redondeo = r.tipo === 'REGALO' || !r.redondeo || r.redondeo === 'NINGUNO' ? '' : ', redondeado ' + (PROMO_REDONDEOS.find(x => x[0] === r.redondeo)?.[1] || '').toLowerCase();
  const rango = r.precioDesde != null && r.precioHasta != null ? ` de ${formatoDinero(r.precioDesde)} a ${formatoDinero(r.precioHasta)}`
    : r.precioHasta != null ? ` de hasta ${formatoDinero(r.precioHasta)}` : r.precioDesde != null ? ` desde ${formatoDinero(r.precioDesde)}` : '';
  return `${r.piezasPorEquipo} por equipo · ${r.categoria || 'sin categoría'}${rango}: ${que}${redondeo}`;
}

/** Texto corto de lo que da la promoción. */
function resumenPromocion(c) {
  if (!c.activo) return 'Apagada: no se aplica ninguna promoción.';
  const activas = (c.reglas || []).filter(r => r.activa !== false);
  if (activas.length === 0) return 'Activa, pero sin reglas encendidas: no se aplica nada. Agrega reglas en «Configurar».';
  return activas.map(r => '• ' + r.nombre + ' — ' + describirReglaPromocion(r)).join('\n');
}

async function dibujarPromocionEquipo(cont) {
  let c;
  try { c = await api('GET', '/api/promociones/equipo/reglas'); }
  catch (err) { cont.innerHTML = `<div class="tarjeta"><div class="mensaje error">${escapar(err.network ? 'Sin conexión con el servidor.' : err.message)}</div></div>`; return; }
  cont.innerHTML = `
    <div class="tarjeta">
      <div class="lista-cab"><h2 class="h2-ic" style="margin:0;">${ic('gift')} Promoción al vender un equipo</h2><button id="pe-config" class="btn btn-gris btn-chico">${ic('pencil')} Configurar</button></div>
      <div class="ayuda" style="margin-top:6px; white-space:pre-line;">${escapar(resumenPromocion(c))}</div>
      <div id="pe-form" class="oculto"></div>
    </div>`;
  document.getElementById('pe-config').onclick = () => formularioPromocionEquipo(document.getElementById('pe-form'), c, () => dibujarPromocionEquipo(cont));
}

/** La lista de reglas de la promoción: se agregan, editan, ordenan, encienden o quitan, y se guardan todas juntas. */
async function formularioPromocionEquipo(cont, c, alGuardar) {
  cont.classList.remove('oculto');
  cont.innerHTML = '<div class="vacio">Cargando...</div>';
  let categorias = [];
  try { categorias = await cargarCategorias(); } catch { /* sin categorías no se puede elegir */ }
  const porId = new Map(categorias.map(x => [x.idcat, x]));
  const ruta = (x) => { const n = []; for (let k = x; k && n.length < 6; k = porId.get(k.idCategoriaSuperior)) n.unshift(k.nombre); return n.join(' › '); };
  // Las categorías de equipos no entran: la promoción es para lo que se vende junto al equipo
  const opciones = categorias.filter(x => x.tipo !== 'CELULAR' && x.tipo !== 'TABLET').sort((a, b) => ruta(a).localeCompare(ruta(b), 'es'));
  const reglas = JSON.parse(JSON.stringify(c.reglas || []));
  reglas.forEach(r => { if (r.idCategoria != null && porId.has(r.idCategoria)) r.categoria = ruta(porId.get(r.idCategoria)); });
  let activo = !!c.activo;

  const pintar = () => {
    cont.innerHTML = `
      <div style="margin-top:12px; border-top:1px solid var(--borde); padding-top:12px;">
        <label class="casilla"><input type="checkbox" id="pf-activo" ${activo ? 'checked' : ''}> Promoción activa (apagada, no se aplica ninguna regla)</label>
        ${reglas.length === 0 ? '<div class="vacio">Sin reglas: agrega la primera.</div>' : reglas.map((r, k) => `
          <div class="orden-item" data-k="${k}" ${r.activa === false ? 'style="opacity:.55;"' : ''}>
            <div class="orden-cab"><span class="orden-folio">${escapar(r.nombre)}</span>${r.activa === false ? '<span class="pastilla cancelada">Apagada</span>' : ''}</div>
            <div class="orden-equipo">${escapar(describirReglaPromocion(r))}</div>
            <div class="grupo-botones">
              <button class="btn btn-gris btn-chico pf-editar" data-k="${k}">${ic('pencil')}</button>
              <button class="btn btn-gris btn-chico pf-subir" data-k="${k}" ${k === 0 ? 'disabled' : ''}>${ic('chevron-up')}</button>
              <button class="btn btn-gris btn-chico pf-bajar" data-k="${k}" ${k === reglas.length - 1 ? 'disabled' : ''}>${ic('chevron-down')}</button>
              <button class="btn btn-gris btn-chico pf-alternar" data-k="${k}">${r.activa === false ? 'Encender' : 'Apagar'}</button>
              <button class="btn btn-rojo btn-chico pf-quitar" data-k="${k}">${ic('x')}</button>
            </div>
          </div>`).join('')}
        <div id="pf-regla"></div>
        <div class="ayuda">Por cada equipo vendido, a cada pieza le toca la primera regla de la lista que le corresponde (su categoría y su precio) y que todavía tiene cupo. Cada categoría vale también para sus subcategorías. Ejemplo: protectores de hasta $249 → 50 % de descuento, redondeado al peso hacia arriba ($249 paga $125; $199 paga $100). Las tiendas con un JSystem anterior deben actualizarse antes de usar descuentos en % o con redondeo.</div>
        <div class="grupo-botones">
          <button id="pf-agregar" class="btn btn-gris">${ic('plus')} Agregar regla</button>
          <button id="pf-guardar" class="btn btn-verde">Guardar</button><button id="pf-cancelar" class="btn btn-gris">Cancelar</button>
        </div>
      </div>`;
    document.getElementById('pf-activo').onchange = (e) => { activo = e.target.checked; };
    document.getElementById('pf-cancelar').onclick = () => { cont.classList.add('oculto'); cont.innerHTML = ''; };
    document.getElementById('pf-agregar').onclick = () => editarReglaPromocion(null);
    cont.querySelectorAll('.pf-editar').forEach(b => b.onclick = () => editarReglaPromocion(Number(b.dataset.k)));
    cont.querySelectorAll('.pf-subir').forEach(b => b.onclick = () => { const k = Number(b.dataset.k); [reglas[k - 1], reglas[k]] = [reglas[k], reglas[k - 1]]; pintar(); });
    cont.querySelectorAll('.pf-bajar').forEach(b => b.onclick = () => { const k = Number(b.dataset.k); [reglas[k + 1], reglas[k]] = [reglas[k], reglas[k + 1]]; pintar(); });
    cont.querySelectorAll('.pf-alternar').forEach(b => b.onclick = () => { const r = reglas[Number(b.dataset.k)]; r.activa = r.activa === false; pintar(); });
    cont.querySelectorAll('.pf-quitar').forEach(b => b.onclick = () => { reglas.splice(Number(b.dataset.k), 1); pintar(); });
    document.getElementById('pf-guardar').onclick = async (e) => {
      e.target.disabled = true;
      const dto = { activo, reglas: reglas.map(r => ({ id: r.id ?? null, nombre: r.nombre, idCategoria: r.idCategoria, precioDesde: r.precioDesde ?? null, precioHasta: r.precioHasta ?? null,
        piezasPorEquipo: r.piezasPorEquipo, tipo: r.tipo, valor: r.valor, redondeo: r.redondeo, activa: r.activa !== false })) };
      try { await api('PUT', '/api/promociones/equipo/reglas', dto); mostrarMensaje(root, 'Promoción guardada.', 'ok'); alGuardar(); }
      catch (err) { mostrarMensaje(root, err.network ? 'Sin conexión con el servidor.' : err.message, 'error'); e.target.disabled = false; }
    };
  };

  /** Una regla (k = null: nueva): a qué artículos aplica, cuánto reciben por equipo y qué descuento llevan, con un ejemplo en vivo. */
  const editarReglaPromocion = (k) => {
    const r = k == null ? { nombre: '', idCategoria: null, precioDesde: null, precioHasta: null, piezasPorEquipo: 1, tipo: 'PORCENTAJE', valor: 0, redondeo: 'NINGUNO', activa: true } : reglas[k];
    const form = document.getElementById('pf-regla');
    form.innerHTML = `
      <div class="tarjeta" style="margin-top:10px; background:var(--gris-claro);">
        <h3 style="margin-top:0;">${k == null ? 'Nueva regla' : 'Editar regla'}</h3>
        <label class="obligatorio">Nombre de la regla</label><input id="pr-nombre" maxlength="80" value="${escapar(r.nombre)}" placeholder="Ej. Protector a la mitad">
        <label class="obligatorio">Categoría de los artículos</label>
        <select id="pr-cat"><option value="">(elige una)</option>${opciones.map(x => `<option value="${x.idcat}" ${x.idcat === r.idCategoria ? 'selected' : ''}>${escapar(ruta(x))}</option>`).join('')}</select>
        <div class="fila">
          <div><label>Precio de lista desde ($)</label><input id="pr-desde" type="number" inputmode="decimal" min="0" step="1" value="${r.precioDesde ?? ''}" placeholder="sin mínimo"></div>
          <div><label>Hasta ($)</label><input id="pr-hasta" type="number" inputmode="decimal" min="0" step="1" value="${r.precioHasta ?? ''}" placeholder="sin máximo"></div>
        </div>
        <label>Piezas por cada equipo</label><input id="pr-piezas" type="number" min="1" max="10" value="${r.piezasPorEquipo}" style="max-width:100px;">
        <label>Descuento</label><select id="pr-tipo">${PROMO_TIPOS.map(([v, n]) => `<option value="${v}" ${v === r.tipo ? 'selected' : ''}>${n}</option>`).join('')}</select>
        <div id="pr-valor-cont"><label>Valor (% o $)</label><input id="pr-valor" type="number" inputmode="decimal" min="0" step="0.5" value="${r.valor || ''}"></div>
        <div id="pr-redondeo-cont"><label>Redondeo del precio que queda</label><select id="pr-redondeo">${PROMO_REDONDEOS.map(([v, n]) => `<option value="${v}" ${v === r.redondeo ? 'selected' : ''}>${n}</option>`).join('')}</select></div>
        <label>Probar con un artículo de ($)</label><input id="pr-ejemplo" type="number" inputmode="decimal" min="0" value="249" style="max-width:120px;">
        <div id="pr-previa" class="mixto-estado ok"></div>
        <div class="grupo-botones"><button id="pr-aceptar" class="btn btn-verde">Aceptar</button><button id="pr-cancelar" class="btn btn-gris">Cancelar</button></div>
      </div>`;
    const leer = () => ({
      tipo: document.getElementById('pr-tipo').value, valor: Number(document.getElementById('pr-valor').value || 0),
      redondeo: document.getElementById('pr-redondeo').value,
    });
    const previa = () => {
      const v = leer();
      document.getElementById('pr-valor-cont').classList.toggle('oculto', v.tipo === 'REGALO');
      document.getElementById('pr-redondeo-cont').classList.toggle('oculto', v.tipo === 'REGALO');
      const precio = Number(document.getElementById('pr-ejemplo').value || 0);
      const queda = posCentavosQueQuedan(v, precio) / 100;
      document.getElementById('pr-previa').textContent = precio > 0 ? `Un artículo de ${formatoDinero(precio)} paga ${formatoDinero(queda)} (descuento de ${formatoDinero(precio - queda)})` : '';
    };
    ['pr-tipo', 'pr-valor', 'pr-redondeo', 'pr-ejemplo'].forEach(id => document.getElementById(id).addEventListener('input', previa));
    previa();
    document.getElementById('pr-cancelar').onclick = () => { form.innerHTML = ''; };
    document.getElementById('pr-aceptar').onclick = () => {
      const v = leer();
      const nombre = document.getElementById('pr-nombre').value.trim();
      const idCat = document.getElementById('pr-cat').value ? Number(document.getElementById('pr-cat').value) : null;
      const desde = document.getElementById('pr-desde').value === '' ? null : Number(document.getElementById('pr-desde').value);
      const hasta = document.getElementById('pr-hasta').value === '' ? null : Number(document.getElementById('pr-hasta').value);
      const piezas = Number(document.getElementById('pr-piezas').value || 0);
      let error = null;
      if (!nombre) error = 'Escribe un nombre para la regla.';
      else if (idCat == null) error = 'Elige la categoría de los artículos.';
      else if (desde != null && hasta != null && desde > hasta) error = 'El precio inicial no puede ser mayor al final.';
      else if (!(piezas >= 1 && piezas <= 10)) error = 'Las piezas por equipo van de 1 a 10.';
      else if (v.tipo === 'PORCENTAJE' && !(v.valor > 0 && v.valor <= 100)) error = 'El porcentaje va de más de 0 a 100.';
      else if (v.tipo === 'MONTO' && !(v.valor > 0)) error = 'El descuento en pesos debe ser mayor a $0.';
      else if (v.tipo === 'PRECIO_FINAL' && document.getElementById('pr-valor').value === '') error = 'Escribe el precio final.';
      if (error) { mostrarMensaje(root, error, 'error'); return; }
      Object.assign(r, { nombre, idCategoria: idCat, categoria: ruta(porId.get(idCat)), precioDesde: desde, precioHasta: hasta, piezasPorEquipo: piezas,
        tipo: v.tipo, valor: v.tipo === 'REGALO' ? 0 : v.valor, redondeo: v.tipo === 'REGALO' ? 'NINGUNO' : v.redondeo });
      if (k == null) reglas.push(r);
      pintar();
    };
  };
  pintar();
}
