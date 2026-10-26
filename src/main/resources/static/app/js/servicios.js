/**
 * Corte de servicios (recargas, pagos de servicios, pines y pagos PayJoy): se operan en los sistemas de los proveedores y NO tocan ventas
 * ni caja. El encargado captura el corte del día por sucursal (operaciones e importe de cada servicio, con los saldos opcionales) y el
 * administrador lo confirma contra el reporte del proveedor (si hay diferencia, con un comentario). Igual que «Corte de Servicios» de JSystem.
 */

const CORTE_ESTADOS = { 1: ['Capturado', 'reparacion'], 2: ['Confirmado', 'lista'], 3: ['Con diferencias', 'error'] };

async function pantallaServicios() {
  const s = Sesion.obtener();
  if (!Sesion.tiene('CORTE_SERVICIOS_CAPTURAR', 'CORTE_SERVICIOS_CONFIRMAR')) { root.innerHTML = '<div class="tarjeta">No tienes permiso para el corte de servicios.</div>'; return; }
  const puedeConfirmar = Sesion.tiene('CORTE_SERVICIOS_CONFIRMAR');
  root.innerHTML = `
    ${cabeceraPantalla(ic('receipt') + ' Corte de servicios', 'Recargas, pagos de servicios, pines y PayJoy',
      puedeConfirmar ? `<button id="sv-tipos" class="btn btn-gris">${ic('settings')} Servicios</button>` : '')}
    <div id="sv-form-tipos" class="oculto"></div>
    <div class="tarjeta">
      <div id="sv-tienda"></div>
      <label class="obligatorio">Día del corte</label>
      <input id="sv-fecha" type="date" value="${fechaLocalISO(new Date()).slice(0, 10)}">
    </div>
    <div id="sv-cuerpo"><div class="vacio">Cargando...</div></div>`;

  let codti = s.codti;
  const contTienda = document.getElementById('sv-tienda');
  if (SUPERIOR.includes(s.rol)) {
    contTienda.innerHTML = '<label class="obligatorio">Sucursal</label><select id="sv-codti"><option>Cargando...</option></select>';
    try {
      const tiendas = (await api('GET', '/api/tiendas')).filter(t => !t.esAlmacen);
      const sel = document.getElementById('sv-codti');
      sel.innerHTML = tiendas.map(t => `<option value="${t.codti}">${escapar(t.nombre)}</option>`).join('');
      codti = tiendas.find(t => t.codti === s.codti)?.codti ?? tiendas[0]?.codti;
      if (codti != null) sel.value = codti;
      sel.onchange = () => { codti = Number(sel.value); cargar(); };
    } catch { contTienda.innerHTML = '<div class="mensaje info">No se pudo cargar la lista de sucursales (sin conexión).</div>'; }
  } else {
    contTienda.innerHTML = '<div class="ayuda">Sucursal: <strong>tu tienda</strong></div>';
  }
  const inputFecha = document.getElementById('sv-fecha');
  inputFecha.onchange = () => cargar();
  if (document.getElementById('sv-tipos')) document.getElementById('sv-tipos').onclick = () => formularioTiposServicio(document.getElementById('sv-form-tipos'), cargar);

  async function cargar() {
    const cont = document.getElementById('sv-cuerpo');
    if (codti == null || !inputFecha.value) return;
    cont.innerHTML = '<div class="vacio">Cargando...</div>';
    try {
      const [tipos, corte] = await Promise.all([
        api('GET', '/api/cortes-servicios/tipos?incluirInactivos=false'),
        api('GET', `/api/cortes-servicios/dia?fecha=${inputFecha.value}&codti=${codti}`).catch(() => null),
      ]);
      dibujarCorteServicios(cont, tipos, corte, codti, inputFecha.value, puedeConfirmar, cargar);
    } catch (err) {
      cont.innerHTML = `<div class="mensaje error">${escapar(err.network ? 'Sin conexión con el servidor.' : err.message)}</div>`;
    }
  }
  cargar();
}

function dibujarCorteServicios(cont, tipos, corte, codti, fecha, puedeConfirmar, recargar) {
  const [estadoTexto, estadoClase] = corte ? (CORTE_ESTADOS[corte.estado] || ['', 'pendiente']) : ['Sin capturar', 'pendiente'];
  const editable = !corte || corte.estado === 1;
  const lineaDe = (idtipo) => (corte?.lineas || []).find(l => l.idtipo === idtipo);
  const num = (v) => (v === null || v === undefined ? '' : v);

  cont.innerHTML = `
    <div class="tarjeta">
      <div class="lista-cab"><h2 style="margin:0;">Corte del ${escapar(fecha)}</h2><span class="pastilla ${estadoClase}">${escapar(estadoTexto)}</span></div>
      ${corte ? `<div class="ayuda">Capturó ${escapar(corte.nombreCaptura || '')} · ${formatoFecha(corte.fechaCaptura)}${corte.nombreConfirma ? ' · Confirmó ' + escapar(corte.nombreConfirma) : ''}</div>` : '<div class="ayuda">Todavía no se captura este día.</div>'}
      ${tipos.length === 0 ? '<div class="vacio">No hay servicios definidos: un administrador los define en «Servicios».</div>' : tipos.map(t => {
        const l = lineaDe(t.idtipo);
        return `
        <div class="orden-item" data-tipo="${t.idtipo}">
          <div class="orden-cab"><span class="orden-folio">${escapar(t.nombre)}</span><span class="ayuda" style="margin:0;">comisión ${formatoDinero(t.comisionPorOperacion)} por operación</span></div>
          <div class="fila">
            <div><label>Operaciones</label><input class="sv-ops" type="number" min="0" inputmode="numeric" value="${num(l?.operaciones)}" ${editable ? '' : 'disabled'}></div>
            <div><label>Importe</label><input class="sv-monto" type="number" min="0" step="1" inputmode="decimal" value="${num(l?.monto)}" ${editable ? '' : 'disabled'}></div>
          </div>
          <details style="margin-top:6px;"><summary class="ayuda" style="cursor:pointer;">Saldos del proveedor (opcional)</summary>
            <div class="fila">
              <div><label>Saldo inicial</label><input class="sv-si" type="number" min="0" step="1" value="${num(l?.saldoInicial)}" ${editable ? '' : 'disabled'}></div>
              <div><label>Fondeo</label><input class="sv-fo" type="number" min="0" step="1" value="${num(l?.fondeo)}" ${editable ? '' : 'disabled'}></div>
              <div><label>Saldo final</label><input class="sv-sf" type="number" min="0" step="1" value="${num(l?.saldoFinal)}" ${editable ? '' : 'disabled'}></div>
            </div>
            ${l?.diferenciaSaldo != null ? `<div class="ayuda">Diferencia de saldo: ${formatoDinero(l.diferenciaSaldo)}</div>` : ''}
          </details>
          ${l && corte.estado !== 1 ? `<div class="ayuda">Proveedor: ${l.montoProveedor != null ? formatoDinero(l.montoProveedor) : '—'}${l.diferencia != null && Number(l.diferencia) !== 0 ? ' · diferencia ' + formatoDinero(l.diferencia) : ''}</div>` : ''}
        </div>`;
      }).join('')}
      <div class="orden-meta" style="margin-top:8px;"><span>Total importe</span><strong id="sv-t-monto">${formatoDinero(corte?.totalMonto || 0)}</strong></div>
      <div class="orden-meta"><span>Comisión</span><strong id="sv-t-comision">${formatoDinero(corte?.totalComision || 0)}</strong></div>
      ${editable ? `
        <label>Observaciones</label><input id="sv-obs" value="${escapar(corte?.observaciones || '')}" placeholder="Opcional">
        ${Sesion.tiene('CORTE_SERVICIOS_CAPTURAR') ? `<button id="sv-guardar" class="btn btn-verde">${ic('save')} ${corte ? 'Guardar cambios' : 'Guardar corte'}</button>` : ''}` :
        (corte?.observaciones ? `<div class="ayuda">Observaciones: ${escapar(corte.observaciones)}</div>` : '')}
      ${corte?.comentarioConfirmacion ? `<div class="mensaje info" style="display:block;">Comentario de la confirmación: ${escapar(corte.comentarioConfirmacion)}</div>` : ''}
    </div>
    ${corte && puedeConfirmar ? `<div id="sv-confirmar"></div>` : ''}`;

  // Los totales se recalculan al escribir (la comisión es la de cada servicio por sus operaciones)
  const recalcular = () => {
    let monto = 0, comision = 0;
    cont.querySelectorAll('[data-tipo]').forEach(el => {
      const t = tipos.find(x => x.idtipo === Number(el.dataset.tipo));
      const ops = Number(el.querySelector('.sv-ops').value || 0);
      monto += Number(el.querySelector('.sv-monto').value || 0);
      comision += ops * Number(t.comisionPorOperacion || 0);
    });
    document.getElementById('sv-t-monto').textContent = formatoDinero(monto);
    document.getElementById('sv-t-comision').textContent = formatoDinero(comision);
  };
  if (editable) cont.querySelectorAll('.sv-ops, .sv-monto').forEach(i => i.addEventListener('input', recalcular));
  if (!corte) recalcular();

  const guardar = document.getElementById('sv-guardar');
  if (guardar) guardar.onclick = async (e) => {
    const lineas = [];
    cont.querySelectorAll('[data-tipo]').forEach(el => {
      const ops = Number(el.querySelector('.sv-ops').value || 0), monto = Number(el.querySelector('.sv-monto').value || 0);
      const n = (sel) => { const v = el.querySelector(sel).value; return v === '' ? null : Number(v); };
      const si = n('.sv-si'), fo = n('.sv-fo'), sf = n('.sv-sf');
      if (ops === 0 && monto === 0 && si == null && fo == null && sf == null) return;
      lineas.push({ idtipo: Number(el.dataset.tipo), operaciones: ops, monto, saldoInicial: si, fondeo: fo, saldoFinal: sf });
    });
    if (lineas.length === 0) { mostrarMensaje(root, 'Captura al menos un servicio.', 'error'); return; }
    e.target.disabled = true;
    try {
      await api('POST', '/api/cortes-servicios', { codti, fecha, observaciones: document.getElementById('sv-obs').value.trim() || null, lineas });
      mostrarMensaje(root, 'Corte guardado.', 'ok');
      recargar();
    } catch (err) { mostrarMensaje(root, err.network ? 'Sin conexión con el servidor.' : err.message, 'error'); e.target.disabled = false; }
  };

  // El administrador confirma contra el reporte del proveedor (o reabre para corregir)
  const contConf = document.getElementById('sv-confirmar');
  if (contConf && corte) {
    if (corte.estado === 1) {
      contConf.innerHTML = `
        <div class="tarjeta">
          <h2 class="h2-ic" style="margin-top:0;">${ic('circle-check')} Confirmar contra el proveedor</h2>
          <div class="ayuda">Escribe el importe que dice el reporte de cada proveedor. Si no coincide, el corte queda «con diferencias» y se pide un comentario.</div>
          ${(corte.lineas || []).map(l => `
            <div class="detalle-fila"><span class="k">${escapar(l.nombre)} <span class="ayuda" style="display:inline;">(capturado ${formatoDinero(l.monto)})</span></span>
              <span class="v"><input class="sv-prov" data-id="${l.idlinea}" type="number" step="1" value="${l.monto}" style="width:120px;"></span></div>`).join('')}
          <label>Comentario <span class="ayuda" style="display:inline;">(obligatorio si hay diferencias)</span></label>
          <input id="sv-comentario">
          <button id="sv-confirmar-btn" class="btn btn-azul">Confirmar corte</button>
        </div>`;
      document.getElementById('sv-confirmar-btn').onclick = async (e) => {
        const lineas = [...contConf.querySelectorAll('.sv-prov')].map(i => ({ idlinea: Number(i.dataset.id), montoProveedor: Number(i.value || 0) }));
        e.target.disabled = true;
        try {
          await api('POST', `/api/cortes-servicios/${corte.idcorte}/confirmar`, { lineas, comentario: document.getElementById('sv-comentario').value.trim() || null });
          mostrarMensaje(root, 'Corte confirmado.', 'ok');
          recargar();
        } catch (err) { mostrarMensaje(root, err.network ? 'Sin conexión con el servidor.' : err.message, 'error'); e.target.disabled = false; }
      };
    } else {
      contConf.innerHTML = `<button id="sv-reabrir" class="btn btn-gris">${ic('undo-2')} Reabrir para corregir</button>`;
      document.getElementById('sv-reabrir').onclick = async (e) => {
        if (!confirm('¿Reabrir este corte? Vuelve a quedar como capturado para corregirlo.')) return;
        e.target.disabled = true;
        try { await api('POST', `/api/cortes-servicios/${corte.idcorte}/reabrir`); recargar(); }
        catch (err) { mostrarMensaje(root, err.network ? 'Sin conexión con el servidor.' : err.message, 'error'); e.target.disabled = false; }
      };
    }
  }
}

/** Los servicios que se capturan en el corte: nombre, cuánto se gana por operación y en qué orden salen. */
async function formularioTiposServicio(cont, alCambiar) {
  cont.classList.remove('oculto');
  let tipos;
  try { tipos = await api('GET', '/api/cortes-servicios/tipos?incluirInactivos=true'); }
  catch (err) { cont.innerHTML = `<div class="tarjeta"><div class="mensaje error">${escapar(err.network ? 'Sin conexión con el servidor.' : err.message)}</div></div>`; return; }
  cont.innerHTML = `
    <div class="tarjeta">
      <div class="lista-cab"><h2 class="h2-ic" style="margin:0;">${ic('settings')} Servicios del corte</h2><button class="enlace" id="st-cerrar">${ic('x')} Cerrar</button></div>
      ${tipos.map(t => `
        <div class="detalle-fila">
          <span class="k">${escapar(t.nombre)} <span class="ayuda" style="display:inline;">· ${formatoDinero(t.comisionPorOperacion)} por operación${t.activo ? '' : ' · inactivo'}</span></span>
          <span class="v"><button class="btn btn-gris btn-chico st-editar" data-id="${t.idtipo}">${ic('pencil')}</button></span>
        </div>`).join('') || '<div class="vacio">Aún no hay servicios.</div>'}
      <div id="st-form" style="margin-top:10px;">
        <label class="obligatorio">Nombre</label><input id="st-nombre" maxlength="60" placeholder="Ej. Recargas Telcel">
        <div class="fila"><div><label>Comisión por operación</label><input id="st-comision" type="number" min="0" step="0.5" value="0"></div>
          <div><label>Orden</label><input id="st-orden" type="number" min="0" value="0"></div></div>
        <label class="casilla"><input type="checkbox" id="st-activo" checked> Activo</label>
        <input type="hidden" id="st-id">
        <div class="grupo-botones"><button id="st-guardar" class="btn btn-verde">Guardar servicio</button></div>
      </div>
    </div>`;
  document.getElementById('st-cerrar').onclick = () => { cont.classList.add('oculto'); cont.innerHTML = ''; };
  cont.querySelectorAll('.st-editar').forEach(b => b.onclick = () => {
    const t = tipos.find(x => x.idtipo === Number(b.dataset.id));
    document.getElementById('st-id').value = t.idtipo; document.getElementById('st-nombre').value = t.nombre;
    document.getElementById('st-comision').value = t.comisionPorOperacion; document.getElementById('st-orden').value = t.orden ?? 0;
    document.getElementById('st-activo').checked = !!t.activo;
  });
  document.getElementById('st-guardar').onclick = async (e) => {
    const nombre = document.getElementById('st-nombre').value.trim();
    if (!nombre) { mostrarMensaje(root, 'Escribe el nombre del servicio.', 'error'); return; }
    const cuerpo = { nombre, comisionPorOperacion: Number(document.getElementById('st-comision').value || 0), orden: Number(document.getElementById('st-orden').value || 0), activo: document.getElementById('st-activo').checked };
    const id = document.getElementById('st-id').value;
    e.target.disabled = true;
    try {
      await (id ? api('PUT', `/api/cortes-servicios/tipos/${id}`, cuerpo) : api('POST', '/api/cortes-servicios/tipos', cuerpo));
      mostrarMensaje(root, 'Servicio guardado.', 'ok');
      alCambiar();
      formularioTiposServicio(cont, alCambiar);
    } catch (err) { mostrarMensaje(root, err.network ? 'Sin conexión con el servidor.' : err.message, 'error'); e.target.disabled = false; }
  };
}
