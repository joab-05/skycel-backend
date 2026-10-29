/**
 * Corte de servicios (saldos, pagos de servicios, pines y pagos PayJoy): se operan en los sistemas de los proveedores y NO tocan ventas
 * ni caja. El encargado captura el corte del día por sucursal (operaciones e importe de cada servicio) y el sistema calcula lo que queda de
 * ganancia (% de lo vendido, o comisión al cliente menos lo que cobra el proveedor; PayJoy solo cuenta clientes). El administrador lo confirma
 * contra el reporte del proveedor (si hay diferencia, con un comentario) y ve el resumen mes con mes. Igual que «Corte de Servicios» de JSystem.
 */

/** Cómo gana el negocio con un servicio, en una línea. */
function gananciaDescrita(t) {
  if (t.soloConteo) return 'solo se cuentan los clientes que pagan';
  const partes = [];
  if (Number(t.porcentajeGanancia) > 0) partes.push(`${Number(t.porcentajeGanancia)} % de lo vendido`);
  if (Number(t.comisionPorOperacion) > 0 || Number(t.costoPorOperacion) > 0) {
    partes.push(`${formatoDinero(t.comisionPorOperacion)} al cliente${Number(t.costoPorOperacion) > 0 ? ` − ${formatoDinero(t.costoPorOperacion)} del proveedor = ${formatoDinero(Number(t.comisionPorOperacion) - Number(t.costoPorOperacion))}` : ''} por operación`);
  }
  return partes.length ? partes.join(' + ') : 'sin ganancia definida';
}

/** Lo que queda de ganancia: % de lo vendido + operaciones × (comisión − costo del proveedor). Igual que el servidor. */
function gananciaServicio(t, ops, monto) {
  if (t.soloConteo) return 0;
  return Math.round((monto * Number(t.porcentajeGanancia || 0) / 100 + ops * (Number(t.comisionPorOperacion || 0) - Number(t.costoPorOperacion || 0))) * 100) / 100;
}

const CORTE_ESTADOS = { 1: ['Capturado', 'reparacion'], 2: ['Confirmado', 'lista'], 3: ['Con diferencias', 'error'] };

async function pantallaServicios() {
  const s = Sesion.obtener();
  if (!Sesion.tiene('CORTE_SERVICIOS_CAPTURAR', 'CORTE_SERVICIOS_CONFIRMAR')) { root.innerHTML = '<div class="tarjeta">No tienes permiso para el corte de servicios.</div>'; return; }
  const puedeConfirmar = Sesion.tiene('CORTE_SERVICIOS_CONFIRMAR');
  root.innerHTML = `
    ${cabeceraPantalla(ic('receipt') + ' Corte de servicios', 'Saldos, pagos de servicios, pines y PayJoy: cuánto se vende y cuánto queda',
      puedeConfirmar ? `<button id="sv-tipos" class="btn btn-gris">${ic('settings')} Servicios</button>` : '')}
    <div id="sv-form-tipos" class="oculto"></div>
    <div class="tarjeta">
      <div id="sv-tienda"></div>
      <label class="obligatorio">Día del corte</label>
      <input id="sv-fecha" type="date" value="${fechaLocalISO(new Date()).slice(0, 10)}">
    </div>
    <div id="sv-cuerpo"><div class="vacio">Cargando...</div></div>
    <div id="sv-resumen"></div>`;

  let codti = s.codti;
  let todasLasSucursales = [];
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
      todasLasSucursales = tiendas;
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
      dibujarResumenServicios(document.getElementById('sv-resumen'), SUPERIOR.includes(s.rol) ? todasLasSucursales : null, codti);
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
          <div class="orden-cab"><span class="orden-folio">${escapar(t.nombre)}</span><span class="ayuda" style="margin:0;">${escapar(gananciaDescrita(t))}</span></div>
          <div class="fila">
            <div><label>${t.soloConteo ? 'Clientes que pagaron' : 'Operaciones'}</label><input class="sv-ops" type="number" min="0" inputmode="numeric" value="${num(l?.operaciones)}" ${editable ? '' : 'disabled'}></div>
            <div><label>${t.soloConteo ? 'Importe (opcional)' : 'Importe vendido'}</label><input class="sv-monto" type="number" min="0" step="1" inputmode="decimal" value="${num(l?.monto)}" ${editable ? '' : 'disabled'}></div>
          </div>
          ${t.soloConteo ? '' : `<div class="orden-meta"><span class="ayuda" style="margin:0;">Ganancia</span><strong class="sv-gan" style="color:var(--verde);">${formatoDinero(l?.ganancia || 0)}</strong></div>`}
          ${l && corte.estado !== 1 ? `<div class="ayuda">Proveedor: ${l.montoProveedor != null ? formatoDinero(l.montoProveedor) : '—'}${l.diferencia != null && Number(l.diferencia) !== 0 ? ' · diferencia ' + formatoDinero(l.diferencia) : ''}</div>` : ''}
        </div>`;
      }).join('')}
      <div class="orden-meta" style="margin-top:8px;"><span>Vendido</span><strong id="sv-t-monto">${formatoDinero(corte?.totalVendido || 0)}</strong></div>
      <div class="orden-meta"><span>Comisiones cobradas</span><strong id="sv-t-comision">${formatoDinero(corte?.totalComision || 0)}</strong></div>
      <div class="orden-meta"><span>Ganancia del día</span><strong id="sv-t-ganancia" style="color:var(--verde);">${formatoDinero(corte?.totalGanancia || 0)}</strong></div>
      ${editable ? `
        <label>Observaciones</label><input id="sv-obs" value="${escapar(corte?.observaciones || '')}" placeholder="Opcional">
        ${Sesion.tiene('CORTE_SERVICIOS_CAPTURAR') ? `<button id="sv-guardar" class="btn btn-verde">${ic('save')} ${corte ? 'Guardar cambios' : 'Guardar corte'}</button>` : ''}` :
        (corte?.observaciones ? `<div class="ayuda">Observaciones: ${escapar(corte.observaciones)}</div>` : '')}
      ${corte?.comentarioConfirmacion ? `<div class="mensaje info" style="display:block;">Comentario de la confirmación: ${escapar(corte.comentarioConfirmacion)}</div>` : ''}
    </div>
    ${corte && puedeConfirmar ? `<div id="sv-confirmar"></div>` : ''}`;

  // Los totales se recalculan al escribir (la ganancia es la de cada servicio por sus operaciones e importe)
  const recalcular = () => {
    let monto = 0, comision = 0, ganancia = 0;
    cont.querySelectorAll('[data-tipo]').forEach(el => {
      const t = tipos.find(x => x.idtipo === Number(el.dataset.tipo));
      const ops = Number(el.querySelector('.sv-ops').value || 0);
      const m = Number(el.querySelector('.sv-monto').value || 0);
      const g = gananciaServicio(t, ops, m);
      if (!t.soloConteo) monto += m;
      comision += t.soloConteo ? 0 : ops * Number(t.comisionPorOperacion || 0);
      ganancia += g;
      const lbl = el.querySelector('.sv-gan');
      if (lbl) lbl.textContent = formatoDinero(g);
    });
    document.getElementById('sv-t-monto').textContent = formatoDinero(monto);
    document.getElementById('sv-t-comision').textContent = formatoDinero(comision);
    document.getElementById('sv-t-ganancia').textContent = formatoDinero(ganancia);
  };
  if (editable) cont.querySelectorAll('.sv-ops, .sv-monto').forEach(i => i.addEventListener('input', recalcular));
  if (!corte) recalcular();

  const guardar = document.getElementById('sv-guardar');
  if (guardar) guardar.onclick = async (e) => {
    const lineas = [];
    cont.querySelectorAll('[data-tipo]').forEach(el => {
      const ops = Number(el.querySelector('.sv-ops').value || 0), monto = Number(el.querySelector('.sv-monto').value || 0);
      if (ops === 0 && monto === 0) return;
      lineas.push({ idtipo: Number(el.dataset.tipo), operaciones: ops, monto });
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

/** Los servicios que se capturan en el corte: nombre, cómo se gana con cada uno (% de lo vendido o comisión menos costo) y en qué orden salen. */
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
          <span class="k">${escapar(t.nombre)} <span class="ayuda" style="display:inline;">· ${escapar(gananciaDescrita(t))}${t.activo ? '' : ' · inactivo'}</span></span>
          <span class="v"><button class="btn btn-gris btn-chico st-editar" data-id="${t.idtipo}">${ic('pencil')}</button></span>
        </div>`).join('') || '<div class="vacio">Aún no hay servicios.</div>'}
      <div id="st-form" style="margin-top:10px;">
        <label class="obligatorio">Nombre</label><input id="st-nombre" maxlength="60" placeholder="Ej. Recargas Telcel">
        <div class="fila"><div><label>Ganancia por lo vendido (%)</label><input id="st-porcentaje" type="number" min="0" max="100" step="0.1" value="0"></div>
          <div><label>Orden</label><input id="st-orden" type="number" min="0" value="0"></div></div>
        <div class="fila"><div><label>Comisión al cliente por operación ($)</label><input id="st-comision" type="number" min="0" step="0.5" value="0"></div>
          <div><label>Lo que cobra el proveedor por operación ($)</label><input id="st-costo" type="number" min="0" step="0.5" value="0"></div></div>
        <label class="casilla"><input type="checkbox" id="st-conteo"> Solo contar las operaciones (sin calcular ganancia), como PayJoy</label>
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
    document.getElementById('st-porcentaje').value = t.porcentajeGanancia ?? 0; document.getElementById('st-costo').value = t.costoPorOperacion ?? 0;
    document.getElementById('st-conteo').checked = !!t.soloConteo;
    document.getElementById('st-activo').checked = !!t.activo;
  });
  document.getElementById('st-guardar').onclick = async (e) => {
    const nombre = document.getElementById('st-nombre').value.trim();
    if (!nombre) { mostrarMensaje(root, 'Escribe el nombre del servicio.', 'error'); return; }
    const porcentaje = Number(document.getElementById('st-porcentaje').value || 0);
    if (porcentaje < 0 || porcentaje > 100) { mostrarMensaje(root, 'El porcentaje va de 0 a 100.', 'error'); return; }
    const cuerpo = { nombre, comisionPorOperacion: Number(document.getElementById('st-comision').value || 0),
      porcentajeGanancia: porcentaje, costoPorOperacion: Number(document.getElementById('st-costo').value || 0),
      soloConteo: document.getElementById('st-conteo').checked, orden: Number(document.getElementById('st-orden').value || 0), activo: document.getElementById('st-activo').checked };
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

/** «2026-10» → «Octubre 2026». */
function nombreMesServicios(aaaamm) {
  const [a, m] = aaaamm.split('-').map(Number);
  const t = new Date(a, m - 1, 1).toLocaleDateString('es-MX', { month: 'long', year: 'numeric' });
  return t.charAt(0).toUpperCase() + t.slice(1);
}

/** Cuánto se vendió en servicios y cuánto quedó de ganancia: por servicio, por sucursal y mes con mes. */
async function dibujarResumenServicios(cont, sucursales, codtiActual) {
  if (!cont) return;
  const hoy = new Date();
  const iso = (d) => fechaLocalISO(d).slice(0, 10);
  const periodos = {
    mes: ['Este mes', () => [new Date(hoy.getFullYear(), hoy.getMonth(), 1), hoy]],
    pasado: ['Mes pasado', () => [new Date(hoy.getFullYear(), hoy.getMonth() - 1, 1), new Date(hoy.getFullYear(), hoy.getMonth(), 0)]],
    dias30: ['Últimos 30 días', () => [new Date(hoy.getTime() - 29 * 86400000), hoy]],
    anio: ['Este año', () => [new Date(hoy.getFullYear(), 0, 1), hoy]],
  };
  cont.innerHTML = `
    <div class="tarjeta">
      <h2 class="h2-ic" style="margin-top:0;">${ic('trending-up')} Cuánto se vende y cuánto queda</h2>
      <div class="fila">
        ${sucursales ? `<div><label>Sucursal</label><select id="rsv-suc"><option value="">Todas las sucursales</option>${sucursales.map(t => `<option value="${t.codti}">${escapar(t.nombre)}</option>`).join('')}</select></div>` : ''}
        <div><label>Periodo</label><select id="rsv-periodo">${Object.entries(periodos).map(([k, [n]]) => `<option value="${k}">${n}</option>`).join('')}</select></div>
      </div>
      <div id="rsv-cuerpo"><div class="vacio">Cargando...</div></div>
    </div>`;
  const cuerpo = document.getElementById('rsv-cuerpo');
  async function cargarResumen() {
    cuerpo.innerHTML = '<div class="vacio">Cargando...</div>';
    const [desde, hasta] = periodos[document.getElementById('rsv-periodo').value][1]();
    const suc = document.getElementById('rsv-suc')?.value || '';
    const filtro = suc ? `&codti=${suc}` : '';
    try {
      const [r, meses] = await Promise.all([
        api('GET', `/api/cortes-servicios/resumen?desde=${iso(desde)}&hasta=${iso(hasta)}${filtro}`),
        api('GET', `/api/cortes-servicios/meses?cantidad=6${filtro}`),
      ]);
      const filaTabla = (cols) => `<tr>${cols.map((c, i) => `<td${i ? ' class="num"' : ''}>${c}</td>`).join('')}</tr>`;
      cuerpo.innerHTML = `
        <div class="kp-h">
          ${kpiH('c3', 'banknote', formatoDinero(r.totalMonto), 'Vendido en el periodo')}
          ${kpiH('c1', 'trending-up', formatoDinero(r.totalGanancia), 'Ganancia')}
          ${kpiH('c4', 'receipt', String(r.cortes), 'Cortes capturados')}
        </div>
        ${r.cortesSinConfirmar ? `<div class="ayuda">${r.cortesSinConfirmar} corte(s) sin confirmar${r.cortesConDiferencias ? ` · ${r.cortesConDiferencias} con diferencias` : ''}.</div>` : ''}
        <h3>Por servicio</h3>
        <div style="overflow-x:auto;"><table class="tabla-simple"><thead><tr><th>Servicio</th><th class="num">Oper.</th><th class="num">Vendido</th><th class="num">Ganancia</th></tr></thead><tbody>
          ${(r.porServicio || []).map(x => filaTabla([x.soloConteo ? `${escapar(x.nombre)}<br><span class="ayuda" style="margin:0;">clientes${Number(x.monto) ? ' · ' + formatoDinero(x.monto) + ' recibido' : ''}</span>` : escapar(x.nombre), x.operaciones, x.soloConteo ? '—' : formatoDinero(x.monto), x.soloConteo ? '—' : formatoDinero(x.ganancia)])).join('') || '<tr><td colspan="4" class="vacio">Sin movimientos.</td></tr>'}
        </tbody></table></div>
        ${(r.porSucursal || []).length > 1 ? `<h3>Por sucursal</h3>
        <div style="overflow-x:auto;"><table class="tabla-simple"><thead><tr><th>Sucursal</th><th class="num">Oper.</th><th class="num">Vendido</th><th class="num">Ganancia</th></tr></thead><tbody>
          ${r.porSucursal.map(x => filaTabla([escapar(x.nombre), x.operaciones, formatoDinero(x.monto), formatoDinero(x.ganancia)])).join('')}
        </tbody></table></div>` : ''}
        <h3>Mes con mes</h3>
        <div style="overflow-x:auto;"><table class="tabla-simple"><thead><tr><th>Mes</th><th class="num">Cortes</th><th class="num">Vendido</th><th class="num">Ganancia</th></tr></thead><tbody>
          ${meses.slice().reverse().map(x => filaTabla([nombreMesServicios(x.mes), x.cortes, formatoDinero(x.monto), formatoDinero(x.ganancia)])).join('')}
        </tbody></table></div>`;
    } catch (err) {
      cuerpo.innerHTML = `<div class="mensaje error">${escapar(err.network ? 'Sin conexión con el servidor.' : err.message)}</div>`;
    }
  }
  document.getElementById('rsv-periodo').onchange = cargarResumen;
  if (document.getElementById('rsv-suc')) {
    document.getElementById('rsv-suc').value = '';
    document.getElementById('rsv-suc').onchange = cargarResumen;
  }
  cargarResumen();
}
