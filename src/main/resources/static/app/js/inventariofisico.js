/**
 * Inventario físico en dos manos (igual que en JSystem): el encargado cuenta y lo envía a revisión —contar no cambia las existencias—
 * y el administrador decide qué diferencias se ajustan y cuáles se justifican; al cerrarlo cambian las existencias.
 * Los equipos se cuentan por IMEI o serie; lo que está dentro del alcance y no se contó queda como faltante. Un inventario puede
 * cubrir solo ciertas categorías (solo equipos, solo micas...).
 */

const INV_CONTANDO = 0, INV_EN_REVISION = 1, INV_CERRADO = 2, INV_CANCELADO = 3;
const DET_PENDIENTE = 0, DET_AJUSTAR = 1, DET_JUSTIFICADO = 2;

let invFisico = { codti: null, soloDiferencias: false };

const ifFecha = (iso) => (iso ? String(iso).replace('T', ' ').slice(0, 16) : '');
const ifMensaje = (err) => err.network ? 'Sin conexión con el servidor.' : err.message;

async function pantallaInventarioFisico() {
  const s = Sesion.obtener();
  if (!Sesion.tiene('INVENTARIO_FISICO', 'INVENTARIO_CONFIRMAR')) { root.innerHTML = '<div class="tarjeta">No tienes permiso para esta sección.</div>'; return; }
  const puedeConfirmar = Sesion.tiene('INVENTARIO_CONFIRMAR');
  root.innerHTML = `
    ${cabeceraPantalla(ic('scan-barcode') + ' Inventario físico', 'El encargado cuenta; el administrador confirma y se ajustan las existencias')}
    <div id="if-aviso"></div>
    <div id="if-tienda"></div>
    <div id="if-cuerpo"><div class="vacio">Cargando...</div></div>`;

  let codti = s.codti;
  let tiendas = [];
  const contTienda = document.getElementById('if-tienda');
  if (SUPERIOR.includes(s.rol)) {
    contTienda.innerHTML = '<label class="obligatorio">Sucursal</label><select id="if-codti"><option>Cargando...</option></select>';
    try {
      tiendas = (await api('GET', '/api/tiendas')).filter(t => !t.esAlmacen);
      const sel = document.getElementById('if-codti');
      sel.innerHTML = tiendas.map(t => `<option value="${t.codti}">${escapar(t.nombre)}</option>`).join('');
      codti = tiendas.find(t => t.codti === s.codti)?.codti ?? tiendas[0]?.codti;
      if (codti != null) sel.value = codti;
      sel.onchange = () => { invFisico.codti = Number(sel.value); cargarInventarioFisico(); };
    } catch { contTienda.innerHTML = '<div class="mensaje info">No se pudo cargar la lista de sucursales (sin conexión).</div>'; }
  } else {
    contTienda.innerHTML = '<div class="ayuda">Sucursal: <strong>tu tienda</strong></div>';
  }
  invFisico.codti = codti;
  if (codti == null) { document.getElementById('if-cuerpo').innerHTML = '<div class="mensaje info">No se pudo determinar la sucursal.</div>'; return; }
  cargarInventarioFisico();

  // Un administrador ve de un vistazo qué sucursales ya enviaron su inventario a revisión
  if (puedeConfirmar && tiendas.length > 0) {
    const pendientes = [];
    await Promise.all(tiendas.map(async t => {
      try { const a = await api('GET', `/api/inventario-fisico/tienda/${t.codti}/activa`); if (a.activa !== false && a.estado === INV_EN_REVISION) pendientes.push(a); } catch { /* se omite */ }
    }));
    const aviso = document.getElementById('if-aviso');
    if (aviso && pendientes.length) {
      aviso.innerHTML = `<div class="mensaje info" style="display:block;"><strong>Por revisar:</strong> ${pendientes.map(p =>
        `<button class="chip if-ir" data-codti="${p.codti}">${escapar(p.nombreTienda)} · ${p.totalFaltantes} faltante(s), ${p.totalSobrantes} sobrante(s)</button>`).join(' ')}</div>`;
      aviso.querySelectorAll('.if-ir').forEach(b => b.onclick = () => {
        invFisico.codti = Number(b.dataset.codti);
        const sel = document.getElementById('if-codti'); if (sel) sel.value = b.dataset.codti;
        cargarInventarioFisico();
      });
    }
  }
}

async function cargarInventarioFisico() {
  const codti = invFisico.codti;
  const cont = document.getElementById('if-cuerpo');
  cont.innerHTML = '<div class="vacio">Cargando...</div>';
  try {
    const a = await api('GET', `/api/inventario-fisico/tienda/${codti}/activa`);
    if (a.activa !== false && a.idinventario != null) {
      const inv = await api('GET', `/api/inventario-fisico/${a.idinventario}?soloDiferencias=false`);
      if (codti !== invFisico.codti) return;
      return inv.estado === INV_CONTANDO ? dibujarInventarioContando(cont, inv) : dibujarInventarioResumen(cont, inv, inv.estado === INV_EN_REVISION);
    }
    const historial = await api('GET', `/api/inventario-fisico/tienda/${codti}/historial`);
    if (codti !== invFisico.codti) return;
    dibujarInventarioSinCurso(cont, historial);
  } catch (err) {
    cont.innerHTML = `<div class="mensaje error">${escapar(ifMensaje(err))}</div>`;
  }
}

/** Una operación del inventario con aviso de error y recarga. */
async function ifEjecutar(promesa, alExito) {
  try { const r = await promesa; if (alExito) alExito(r); else cargarInventarioFisico(); }
  catch (err) { mostrarMensaje(root, ifMensaje(err), 'error'); }
}

// ── Qué cubre el inventario ──────────────────────────────────────────────

/** Una casilla por categoría con sus subcategorías debajo: marcar una marca todo lo que contiene; los servicios no se ofrecen (no tienen existencias). */
function ifCasillasDeCategorias(categorias) {
  const hijos = (id) => categorias.filter(c => (c.idCategoriaSuperior ?? null) === id).sort((a, b) => a.nombre.localeCompare(b.nombre, 'es'));
  const fila = (c, nivel) => `
    <div style="padding:2px 0 2px ${nivel * 20}px;">
      <label class="casilla" style="${nivel === 0 ? 'font-weight:700;' : ''}"><input type="checkbox" class="if-cat" value="${c.idcat}" data-padre="${c.idCategoriaSuperior ?? ''}"> ${escapar(c.nombre)}</label>
    </div>${hijos(c.idcat).map(h => fila(h, nivel + 1)).join('')}`;
  return categorias.filter(c => c.idCategoriaSuperior == null && c.tipo !== 'SERVICIO').sort((a, b) => a.nombre.localeCompare(b.nombre, 'es')).map(c => fila(c, 0)).join('');
}

function ifEnlazarCasillas(cont) {
  const todas = [...cont.querySelectorAll('.if-cat')];
  const hijosDe = (id) => todas.filter(c => c.dataset.padre === String(id));
  const descendientes = (id) => hijosDe(id).flatMap(h => [h, ...descendientes(h.value)]);
  todas.forEach(c => c.onchange = () => {
    descendientes(c.value).forEach(d => { d.checked = c.checked; });
    // hacia arriba: un grupo queda marcado solo si todo lo que contiene lo está
    for (let p = todas.find(x => x.value === c.dataset.padre); p; p = todas.find(x => x.value === p.dataset.padre)) {
      p.checked = c.checked && hijosDe(p.value).every(h => h.checked);
    }
  });
}

async function ifDialogoAlcance(cont, alElegir) {
  let categorias = [];
  try { categorias = await cargarCategorias(); } catch (err) { mostrarMensaje(root, ifMensaje(err), 'error'); return; }
  cont.innerHTML = `
    <div class="tarjeta">
      <h2 style="margin-top:0;">¿Qué se va a inventariar?</h2>
      <label class="casilla"><input type="radio" name="if-alcance" id="if-todo" checked> Todo lo que se cuente (se revisan las secciones contadas)</label>
      <label class="casilla"><input type="radio" name="if-alcance" id="if-solo"> Solo estas categorías:</label>
      <div id="if-lista-cat" class="oculto" style="max-height:260px; overflow:auto; border:1px solid var(--borde); border-radius:10px; padding:8px; margin:6px 0;">${ifCasillasDeCategorias(categorias)}</div>
      <div class="ayuda">Los equipos que estén en las categorías elegidas se cuentan por IMEI; lo que está en esas categorías y no se cuente queda como faltante. Lo demás de la tienda no se toca.</div>
      <div class="grupo-botones"><button id="if-iniciar" class="btn btn-verde">${ic('plus')} Aperturar</button><button id="if-no" class="btn btn-gris">Cancelar</button></div>
    </div>`;
  ifEnlazarCasillas(cont);
  const lista = document.getElementById('if-lista-cat');
  document.getElementById('if-todo').onchange = () => lista.classList.add('oculto');
  document.getElementById('if-solo').onchange = () => lista.classList.remove('oculto');
  document.getElementById('if-no').onclick = () => cargarInventarioFisico();
  document.getElementById('if-iniciar').onclick = () => {
    if (document.getElementById('if-todo').checked) return alElegir(null);
    const elegidas = [...cont.querySelectorAll('.if-cat:checked')].map(c => Number(c.value));
    if (elegidas.length === 0) { mostrarMensaje(root, 'Marca al menos una categoría, o elige «Todo lo que se cuente».', 'error'); return; }
    alElegir(elegidas);
  };
}

// ── Sin inventario en curso ──────────────────────────────────────────────

function dibujarInventarioSinCurso(cont, historial) {
  const puedeContar = Sesion.tiene('INVENTARIO_FISICO');
  cont.innerHTML = `
    <div class="tarjeta">
      <h2 style="margin-top:0;">No hay un inventario en curso en esta sucursal</h2>
      <div class="ayuda">Cuenta lo que hay en el local y compáralo con el sistema. Contar no cambia las existencias: al terminar se genera un resumen y el administrador lo revisa y lo confirma.</div>
      ${puedeContar ? `<button id="if-aperturar" class="btn btn-verde" style="margin-top:10px;">${ic('plus')} Aperturar inventario</button>` : ''}
    </div>
    <div id="if-alcance"></div>
    <h2 style="margin:16px 0 6px;">Inventarios anteriores</h2>
    ${historial.length === 0 ? '<div class="vacio">Todavía no hay inventarios anteriores.</div>' : historial.map(a => `
      <div class="orden-item">
        <div class="orden-cab">
          <span class="orden-folio">Inventario #${a.idinventario}</span>
          <span class="pastilla ${a.estado === INV_CERRADO ? 'lista' : a.estado === INV_CANCELADO ? 'cancelada' : 'por-tomar'}">${escapar(a.estadoDisplay)}</span>
        </div>
        <div class="orden-equipo">${escapar(a.alcance || 'Todo lo contado')} · contó ${escapar(a.nombreEncargado || '')}${a.nombreConfirma ? ' · confirmó ' + escapar(a.nombreConfirma) : ''}</div>
        <div class="orden-fecha">${ifFecha(a.fechaInicio)}${a.fechaFin ? ' — ' + ifFecha(a.fechaFin) : ''}</div>
        <div class="ayuda" style="margin-top:4px;">${a.totalFaltantes} faltante(s) · ${a.totalSobrantes} sobrante(s)</div>
        ${a.estado !== INV_CANCELADO ? `<button class="btn btn-gris btn-chico if-ver" data-id="${a.idinventario}" style="margin-top:8px;">${ic('eye')} Ver</button>` : ''}
      </div>`).join('')}`;
  const ap = document.getElementById('if-aperturar');
  if (ap) ap.onclick = () => ifDialogoAlcance(document.getElementById('if-alcance'), (categorias) => {
    const q = categorias ? '&categorias=' + categorias.join(',') : '';
    ifEjecutar(api('POST', `/api/inventario-fisico/aperturar?codti=${invFisico.codti}${q}`), () => cargarInventarioFisico());
  });
  cont.querySelectorAll('.if-ver').forEach(b => b.onclick = () => ifEjecutar(api('GET', `/api/inventario-fisico/${b.dataset.id}?soloDiferencias=false`),
    (inv) => dibujarInventarioResumen(cont, inv, false)));
}

// ── Contando (encargado) ─────────────────────────────────────────────────

function ifBanda(inv, texto) {
  return `<div class="tarjeta">
    <div style="font-weight:700;">Inventario #${inv.idinventario} · ${escapar(inv.nombreTienda || '')} · ${escapar(inv.estadoDisplay || '')}${inv.alcance ? ' · Solo: ' + escapar(inv.alcance) : ''}</div>
    <div class="ayuda">${texto}</div>
    <div class="ayuda">Inició ${ifFecha(inv.fechaInicio)} · ${escapar(inv.nombreEncargado || '')}</div>
  </div>`;
}

function dibujarInventarioContando(cont, inv) {
  const items = inv.items || [];
  cont.innerHTML = `
    ${ifBanda(inv, 'Contando: lo que escanees aquí no cambia las existencias.')}
    <div class="tarjeta">
      <div class="fila" style="align-items:flex-end;">
        <div style="flex:3;"><label>Código del producto o IMEI del equipo</label><input id="if-codigo" placeholder="Escanea o escribe" autocomplete="off"></div>
        <div style="flex:1;"><label>Cant.</label><input id="if-cantidad" type="number" min="1" value="1"></div>
      </div>
      <button id="if-contar" class="btn btn-azul">${ic('plus')} Contar</button>
      <div id="if-ultimo" class="ayuda" style="margin-top:6px;">Cada lectura suma una pieza. Los equipos se cuentan uno por uno, por IMEI o serie.</div>
    </div>
    <div class="tarjeta">
      <h2 style="margin-top:0;">Lo contado hasta ahora</h2>
      <div id="if-contado">${items.length === 0 ? '<div class="vacio">Aún no has contado nada.</div>' : ''}</div>
    </div>
    <div class="grupo-botones">
      <button id="if-enviar" class="btn btn-verde">${ic('circle-check')} Enviar a revisión</button>
      <button id="if-cancelar" class="btn btn-gris">${ic('circle-x')} Cancelar inventario</button>
    </div>`;

  const pintar = () => {
    const c = document.getElementById('if-contado');
    if (items.length === 0) { c.innerHTML = '<div class="vacio">Aún no has contado nada.</div>'; return; }
    c.innerHTML = items.map(i => `
      <div class="detalle-fila" data-id="${i.iddetalleInv}">
        <span class="k">${escapar(i.nombreArticulo || i.codpro)}${i.esEquipo ? ' <span class="ayuda" style="display:inline;">(equipos)</span>' : ` <span class="ayuda" style="display:inline;">${escapar(i.codpro || '')}</span>`}${i.nombreSeccion ? ` <span class="ayuda" style="display:inline;">· ${escapar(i.nombreSeccion)}</span>` : ''}</span>
        <span class="v">${i.conteo} pza ${i.esEquipo
          ? `<button class="btn btn-gris btn-chico if-unidades" data-id="${i.iddetalleInv}">${ic('smartphone')}</button>`
          : `<button class="btn btn-gris btn-chico if-corregir" data-id="${i.iddetalleInv}">${ic('pencil')}</button>`}</span>
      </div>
      <div class="if-imeis oculto" id="if-imeis-${i.iddetalleInv}"></div>`).join('');
    c.querySelectorAll('.if-corregir').forEach(b => b.onclick = () => {
      const i = items.find(x => x.iddetalleInv === Number(b.dataset.id));
      const txt = prompt(`${i.nombreArticulo}\nCantidad contada:`, String(i.conteo));
      if (txt == null) return;
      const n = Number(txt);
      if (!Number.isInteger(n) || n < 0) { mostrarMensaje(root, 'Escribe un número entero.', 'error'); return; }
      ifEjecutar(api('PUT', `/api/inventario-fisico/${inv.idinventario}/detalle/${i.iddetalleInv}/conteo?conteo=${n}`), (r) => { i.conteo = r.conteo; i.diferencia = r.diferencia; pintar(); });
    });
    c.querySelectorAll('.if-unidades').forEach(b => b.onclick = () => {
      const i = items.find(x => x.iddetalleInv === Number(b.dataset.id));
      const panel = document.getElementById('if-imeis-' + i.iddetalleInv);
      panel.classList.toggle('oculto');
      if (panel.classList.contains('oculto')) return;
      panel.innerHTML = (i.imeis || []).map(u => `
        <div class="detalle-fila"><span class="k" style="font-family:monospace;">${escapar(u.imei)}${u.enSistema === false ? ' <span class="ayuda" style="display:inline;">(no estaba disponible aquí)</span>' : ''}</span>
          <span class="v"><button class="btn btn-rojo btn-chico if-quitar" data-imei="${escapar(u.imei)}">${ic('trash-2')}</button></span></div>`).join('') || '<div class="vacio">Sin equipos.</div>';
      panel.querySelectorAll('.if-quitar').forEach(q => q.onclick = () => ifEjecutar(api('DELETE', `/api/inventario-fisico/${inv.idinventario}/unidad/${encodeURIComponent(q.dataset.imei)}`), (r) => {
        i.imeis = (i.imeis || []).filter(x => x.imei !== q.dataset.imei); i.conteo = r.conteo; i.diferencia = r.diferencia; pintar();
      }));
    });
  };
  pintar();

  const codigo = document.getElementById('if-codigo');
  const ultimo = document.getElementById('if-ultimo');
  codigo.focus();
  const contar = async () => {
    const cod = codigo.value.trim();
    if (!cod) return;
    const cant = Math.max(1, Math.floor(Number(document.getElementById('if-cantidad').value)) || 1);
    codigo.disabled = true;
    try {
      const r = await api('POST', `/api/inventario-fisico/${inv.idinventario}/escanear?codpro=${encodeURIComponent(cod)}&cantidad=${cant}`);
      let i = items.find(x => x.iddetalleInv === r.iddetalleInv);
      if (!i) { i = { iddetalleInv: r.iddetalleInv, codpro: r.imei ? null : r.codpro, nombreArticulo: r.nombreArticulo || r.codpro, esEquipo: !!r.imei }; items.unshift(i); }
      i.conteo = r.conteo; i.stockSistema = r.stockSistema; i.diferencia = r.diferencia;
      if (r.imei) { i.imeis = i.imeis || []; i.imeis.push({ imei: r.imei, enSistema: r.enSistema }); }
      // lo último contado queda arriba
      items.splice(items.indexOf(i), 1); items.unshift(i);
      ultimo.innerHTML = `${ic(r.advertencia ? 'triangle-alert' : 'circle-check')} ${r.advertencia ? escapar(r.advertencia) + ' · ' : ''}${escapar(r.nombreArticulo || r.codpro)}${r.imei ? ' · ' + escapar(r.imei) : ''} → ${r.conteo} contado(s)`;
      ultimo.style.color = r.advertencia ? '#b45309' : '#15803d'; ultimo.style.fontWeight = '700';
      codigo.value = ''; document.getElementById('if-cantidad').value = '1';
      pintar();
    } catch (err) {
      ultimo.textContent = '✖ ' + ifMensaje(err); ultimo.style.color = '#dc2626'; ultimo.style.fontWeight = '700';
    }
    codigo.disabled = false; codigo.focus();
  };
  document.getElementById('if-contar').onclick = contar;
  codigo.addEventListener('keydown', (e) => { if (e.key === 'Enter') contar(); });
  document.getElementById('if-enviar').onclick = () => {
    if (!confirm('¿Terminaste de contar?\n\nSe genera el resumen y ya no podrás contar más. Lo que no escaneaste en las secciones que contaste quedará como faltante. Las existencias no cambian hasta que el administrador lo confirme.')) return;
    ifEjecutar(api('POST', `/api/inventario-fisico/${inv.idinventario}/cerrar-conteo`));
  };
  document.getElementById('if-cancelar').onclick = () => ifCancelar(inv);
}

function ifCancelar(inv) {
  if (!confirm('¿Cancelar este inventario?\n\nSe pierde el conteo. Las existencias no cambian.')) return;
  ifEjecutar(api('POST', `/api/inventario-fisico/${inv.idinventario}/cancelar`));
}

// ── Resumen (en revisión o cerrado) ──────────────────────────────────────

function dibujarInventarioResumen(cont, inv, enRevision) {
  const puedeConfirmar = Sesion.tiene('INVENTARIO_CONFIRMAR');
  const decide = enRevision && puedeConfirmar;
  const items = inv.items || [];
  const aviso = enRevision
    ? (puedeConfirmar ? `Lo contó ${escapar(inv.nombreEncargado || '')}. Decide qué diferencias se ajustan y cuáles se justifican; las existencias cambian hasta que lo cierres.`
      : 'Ya enviaste el conteo a revisión. El administrador lo confirmará; las existencias todavía no han cambiado.')
    : inv.estado === INV_CERRADO ? `Cerrado${inv.nombreConfirma ? ' por ' + escapar(inv.nombreConfirma) : ''} el ${ifFecha(inv.fechaFin)}. Los ajustes ya se aplicaron a las existencias.` : escapar(inv.estadoDisplay || '');
  const valor = (v) => v != null ? ' · ' + formatoDinero(Math.abs(v)) : '';
  cont.innerHTML = `
    ${ifBanda(inv, aviso)}
    <div class="kp-h">
      ${kpiH('c1', 'package', String(inv.totalArticulos ?? 0), 'Artículos contados')}
      ${kpiH('c6', 'triangle-alert', (inv.totalFaltantes ?? 0) + valor(inv.valorFaltantes), 'Con faltante')}
      ${kpiH('c3', 'circle-check', (inv.totalSobrantes ?? 0) + valor(inv.valorSobrantes), 'Con sobrante')}
      ${kpiH('c4', 'hourglass', String(inv.totalPendientes ?? 0), enRevision ? 'Por decidir' : 'Sin decidir')}
    </div>
    <div class="tarjeta">
      <label class="casilla"><input type="checkbox" id="if-sd" ${invFisico.soloDiferencias ? 'checked' : ''}> Solo las diferencias</label>
      <div id="if-tabla"></div>
    </div>
    <div class="grupo-botones" id="if-pie"></div>`;

  const decision = (i) => {
    if (i.diferencia === 0) return '';
    if (i.estado === DET_AJUSTAR) return '<span class="pastilla lista">Se ajustará</span>';
    if (i.estado === DET_JUSTIFICADO) return `<span class="pastilla entregada">Justificado: ${escapar(i.motivo || '')}</span>`;
    return enRevision ? '<span class="pastilla pendiente">Pendiente</span>' : '—';
  };
  const pintar = () => {
    const lista = items.filter(i => !invFisico.soloDiferencias || i.diferencia !== 0);
    const c = document.getElementById('if-tabla');
    if (lista.length === 0) { c.innerHTML = '<div class="vacio">Sin diferencias: lo contado coincide con el sistema.</div>'; return; }
    c.innerHTML = lista.map(i => `
      <div class="orden-item">
        <div class="orden-cab"><span class="orden-folio">${escapar(i.nombreArticulo || i.codpro)}${i.esEquipo ? ' (equipos)' : ''}</span>${decision(i)}</div>
        <div class="orden-equipo">${escapar(i.nombreSeccion || '')} Sistema ${i.stockSistema} · Contado ${i.conteo} ·
          <strong style="color:${i.diferencia < 0 ? '#dc2626' : i.diferencia > 0 ? '#059669' : '#64748b'};">${i.diferencia > 0 ? '+' : ''}${i.diferencia}</strong>${i.valorDiferencia != null && i.diferencia !== 0 ? ' · ' + formatoDinero(Math.abs(i.valorDiferencia)) : ''}</div>
        <div class="grupo-botones" style="margin-top:6px;">
          ${i.esEquipo ? `<button class="btn btn-gris btn-chico if-equipos" data-id="${i.iddetalleInv}">${ic('smartphone')} Ver equipos</button>` : ''}
          ${decide && i.diferencia !== 0 ? (i.estado === DET_PENDIENTE
            ? `${!(i.esEquipo && i.diferencia > 0) ? `<button class="btn btn-verde btn-chico if-ajustar" data-id="${i.iddetalleInv}">${ic('circle-check')} Ajustar</button>` : ''}<button class="btn btn-gris btn-chico if-justificar" data-id="${i.iddetalleInv}">${ic('pencil')} Justificar</button>`
            : `<button class="btn btn-gris btn-chico if-deshacer" data-id="${i.iddetalleInv}">${ic('undo-2')} Deshacer</button>`) : ''}
        </div>
        <div class="oculto" id="if-eq-${i.iddetalleInv}"></div>
      </div>`).join('');
    const item = (b) => items.find(x => x.iddetalleInv === Number(b.dataset.id));
    c.querySelectorAll('.if-ajustar').forEach(b => b.onclick = () => ifEjecutar(api('POST', `/api/inventario-fisico/${inv.idinventario}/detalle/${b.dataset.id}/resolver?accion=AJUSTAR`)));
    c.querySelectorAll('.if-deshacer').forEach(b => b.onclick = () => ifEjecutar(api('POST', `/api/inventario-fisico/${inv.idinventario}/detalle/${b.dataset.id}/resolver?accion=PENDIENTE`)));
    c.querySelectorAll('.if-justificar').forEach(b => b.onclick = () => {
      const i = item(b);
      const motivo = (prompt(`${i.nombreArticulo}\nDiferencia: ${i.diferencia > 0 ? '+' : ''}${i.diferencia}. Las existencias no se tocan.\n\nMotivo:`) || '').trim();
      if (!motivo) return;
      ifEjecutar(api('POST', `/api/inventario-fisico/${inv.idinventario}/detalle/${b.dataset.id}/resolver?accion=JUSTIFICAR&motivo=${encodeURIComponent(motivo)}`));
    });
    c.querySelectorAll('.if-equipos').forEach(b => b.onclick = () => {
      const i = item(b);
      const panel = document.getElementById('if-eq-' + i.iddetalleInv);
      panel.classList.toggle('oculto');
      if (panel.classList.contains('oculto')) return;
      panel.innerHTML = `<div class="ayuda" style="margin-top:6px;">
        ${(i.imeis || []).length ? `<strong>Contados (${i.imeis.length}):</strong><br>` + i.imeis.map(u => `<span style="font-family:monospace;">${escapar(u.imei)}</span>${u.enSistema === false ? ' ← no estaba disponible en esta sucursal' : ''}`).join('<br>') : ''}
        ${(i.faltantesImeis || []).length ? `<br><strong>No se encontraron (${i.faltantesImeis.length}):</strong><br>` + i.faltantesImeis.map(u => `<span style="font-family:monospace;">${escapar(u.imei)}</span>`).join('<br>') : ''}</div>`;
    });
  };
  pintar();
  document.getElementById('if-sd').onchange = (e) => { invFisico.soloDiferencias = e.target.checked; pintar(); };

  const pie = document.getElementById('if-pie');
  if (decide) {
    pie.innerHTML = `
      <button id="if-cerrar" class="btn btn-verde">${ic('circle-check')} Confirmar y cerrar inventario</button>
      <button id="if-todo" class="btn btn-gris">${ic('check-check')} Ajustar todo lo pendiente</button>
      <button id="if-devolver" class="btn btn-gris">${ic('undo-2')} Devolver para recontar</button>
      <button id="if-cancelar" class="btn btn-gris">${ic('circle-x')} Cancelar inventario</button>`;
    document.getElementById('if-cerrar').onclick = () => {
      const ajustes = items.filter(i => i.diferencia !== 0 && i.estado === DET_AJUSTAR).length;
      if (!confirm(`¿Confirmar y cerrar el inventario?\n\nSe aplicarán ${ajustes} ajuste(s) a las existencias de ${inv.nombreTienda || 'la sucursal'}. Esto no se puede deshacer.`)) return;
      ifEjecutar(api('POST', `/api/inventario-fisico/${inv.idinventario}/finalizar`), () => { mostrarMensaje(root, 'Inventario cerrado: las existencias quedaron ajustadas.', 'ok'); cargarInventarioFisico(); });
    };
    document.getElementById('if-todo').onclick = () => ifEjecutar(api('POST', `/api/inventario-fisico/${inv.idinventario}/ajustar-todo`), (r) => {
      if (r.sinDecidir > 0) mostrarMensaje(root, `${r.sinDecidir} equipo(s) que sobran no se pueden ajustar solos: justifícalos o regístralos en Equipos.`, 'info');
      cargarInventarioFisico();
    });
    document.getElementById('if-devolver').onclick = () => {
      const c = prompt('Se regresa el conteo al encargado y las decisiones se borran.\n\nQué debe revisar:');
      if (c == null) return;
      ifEjecutar(api('POST', `/api/inventario-fisico/${inv.idinventario}/devolver${c.trim() ? '?comentario=' + encodeURIComponent(c.trim()) : ''}`));
    };
    document.getElementById('if-cancelar').onclick = () => ifCancelar(inv);
  } else if (!enRevision) {
    pie.innerHTML = `<button id="if-volver" class="btn btn-gris">${ic('undo-2')} Volver</button><button id="if-exportar" class="btn btn-gris">${ic('download')} Exportar</button>`;
    document.getElementById('if-volver').onclick = () => cargarInventarioFisico();
    document.getElementById('if-exportar').onclick = () => ifExportar(inv, items);
  }
}

function ifExportar(inv, items) {
  const filas = [['Artículo', 'Código', 'Sección', 'Sistema', 'Contado', 'Diferencia', 'Decisión', 'Motivo']];
  items.forEach(i => filas.push([i.nombreArticulo, i.esEquipo ? 'equipo' : i.codpro, i.nombreSeccion || '', i.stockSistema, i.conteo, i.diferencia,
    i.diferencia === 0 ? '' : i.estado === DET_AJUSTAR ? 'Ajustar' : i.estado === DET_JUSTIFICADO ? 'Justificado' : 'Pendiente', i.motivo || '']));
  descargarCSV('inventario-fisico-' + inv.idinventario + '.csv', filas);
}
