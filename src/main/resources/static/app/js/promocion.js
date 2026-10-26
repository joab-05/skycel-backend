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

/** Texto corto de lo que da la promoción. */
function resumenPromocion(c) {
  if (!c.activo) return 'Apagada: no se aplica ninguna promoción.';
  const partes = [];
  if (c.idCategoriaChip != null && c.chipsPorEquipo > 0) partes.push(`${c.chipsPorEquipo} chip de regalo (${c.categoriaChip})`);
  if (c.idCategoriaMica != null && c.micasPorEquipo > 0) partes.push(`${c.micasPorEquipo} mica con ${formatoDinero(c.descuentoMica)} de descuento (${c.categoriaMica}); si cuesta eso o menos, de regalo`);
  if (c.idCategoriaProtector != null && c.protectoresPorEquipo > 0)
    partes.push(`${c.protectoresPorEquipo} protector con ${formatoDinero(c.descuentoProtector)} de descuento si cuesta menos de ${formatoDinero(c.protectorPrecioMenorA)} (${c.categoriaProtector})`);
  if (partes.length === 0) return 'Activa, pero sin categorías elegidas: no se aplica nada. Elige las categorías en «Configurar».';
  return 'Por cada equipo (celular o tablet) que se lleva el cliente: ' + partes.join(' · ') + '.';
}

async function dibujarPromocionEquipo(cont) {
  let c;
  try { c = await api('GET', '/api/promociones/equipo'); }
  catch (err) { cont.innerHTML = `<div class="tarjeta"><div class="mensaje error">${escapar(err.network ? 'Sin conexión con el servidor.' : err.message)}</div></div>`; return; }
  cont.innerHTML = `
    <div class="tarjeta">
      <div class="lista-cab"><h2 class="h2-ic" style="margin:0;">${ic('gift')} Promoción al vender un equipo</h2><button id="pe-config" class="btn btn-gris btn-chico">${ic('pencil')} Configurar</button></div>
      <div class="ayuda" style="margin-top:6px;">${escapar(resumenPromocion(c))}</div>
      <div id="pe-form" class="oculto"></div>
    </div>`;
  document.getElementById('pe-config').onclick = () => formularioPromocionEquipo(document.getElementById('pe-form'), c, () => dibujarPromocionEquipo(cont));
}

async function formularioPromocionEquipo(cont, c, alGuardar) {
  cont.classList.remove('oculto');
  cont.innerHTML = '<div class="vacio">Cargando...</div>';
  let categorias = [];
  try { categorias = await cargarCategorias(); } catch { /* sin categorías no se puede elegir */ }
  const porId = new Map(categorias.map(x => [x.idcat, x]));
  const ruta = (x) => { const n = []; for (let k = x; k && n.length < 6; k = porId.get(k.idCategoriaSuperior)) n.unshift(k.nombre); return n.join(' › '); };
  // Las categorías de equipos no entran: la promoción es para lo que se vende junto al equipo
  const opciones = categorias.filter(x => x.tipo !== 'CELULAR' && x.tipo !== 'TABLET').sort((a, b) => ruta(a).localeCompare(ruta(b), 'es'));
  const select = (id, elegida) => `<select id="${id}"><option value="">(ninguna)</option>${opciones.map(x => `<option value="${x.idcat}" ${x.idcat === elegida ? 'selected' : ''}>${escapar(ruta(x))}</option>`).join('')}</select>`;
  cont.innerHTML = `
    <div style="margin-top:12px; border-top:1px solid var(--borde); padding-top:12px;">
      <label class="casilla"><input type="checkbox" id="pf-activo" ${c.activo ? 'checked' : ''}> Promoción activa</label>
      <label>Chips (de regalo)</label>
      <div class="fila">${select('pf-chip', c.idCategoriaChip)}<input id="pf-n-chip" type="number" min="0" max="10" value="${c.chipsPorEquipo}" style="max-width:90px;"></div>
      <label>Micas</label>
      <div class="fila">${select('pf-mica', c.idCategoriaMica)}<input id="pf-n-mica" type="number" min="0" max="10" value="${c.micasPorEquipo}" style="max-width:90px;"></div>
      <label>Descuento en la mica ($)</label>
      <input id="pf-d-mica" type="number" inputmode="decimal" min="0" step="1" value="${c.descuentoMica}">
      <label>Protectores</label>
      <div class="fila">${select('pf-prot', c.idCategoriaProtector)}<input id="pf-n-prot" type="number" min="0" max="10" value="${c.protectoresPorEquipo}" style="max-width:90px;"></div>
      <label>Descuento en el protector ($)</label>
      <input id="pf-d-prot" type="number" inputmode="decimal" min="0" step="1" value="${c.descuentoProtector}">
      <label>Solo si el protector cuesta menos de ($)</label>
      <input id="pf-menor" type="number" inputmode="decimal" min="0" step="1" value="${c.protectorPrecioMenorA}">
      <div class="ayuda">Cada categoría vale también para sus subcategorías. La mica con un precio igual o menor al descuento va de regalo. Las piezas son por cada equipo vendido.</div>
      <div class="grupo-botones"><button id="pf-guardar" class="btn btn-verde">Guardar</button><button id="pf-cancelar" class="btn btn-gris">Cancelar</button></div>
    </div>`;
  document.getElementById('pf-cancelar').onclick = () => { cont.classList.add('oculto'); cont.innerHTML = ''; };
  document.getElementById('pf-guardar').onclick = async (e) => {
    const num = (id) => Number(document.getElementById(id).value || 0);
    const id = (sel) => document.getElementById(sel).value ? Number(document.getElementById(sel).value) : null;
    const dto = {
      activo: document.getElementById('pf-activo').checked,
      idCategoriaChip: id('pf-chip'), idCategoriaMica: id('pf-mica'), idCategoriaProtector: id('pf-prot'),
      chipsPorEquipo: num('pf-n-chip'), micasPorEquipo: num('pf-n-mica'), protectoresPorEquipo: num('pf-n-prot'),
      descuentoMica: num('pf-d-mica'), descuentoProtector: num('pf-d-prot'), protectorPrecioMenorA: num('pf-menor'),
    };
    e.target.disabled = true;
    try { await api('PUT', '/api/promociones/equipo', dto); mostrarMensaje(root, 'Promoción guardada.', 'ok'); alGuardar(); }
    catch (err) { mostrarMensaje(root, err.network ? 'Sin conexión con el servidor.' : err.message, 'error'); e.target.disabled = false; }
  };
}
