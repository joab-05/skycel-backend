/**
 * Control de mermas (igual que en JSystem): lo que cambió las existencias fuera de ventas, devoluciones y traspasos —salidas y ajustes
 * manuales, bajas de equipos y ajustes de inventario físico— con quién lo hizo, por qué y cuánto vale a costo.
 */

let mermas = { codti: '', periodo: '30', vista: 'categoria' };

async function pantallaMermas() {
  if (!Sesion.tiene('INVENTARIO_AJUSTAR')) { root.innerHTML = '<div class="tarjeta">No tienes permiso para esta sección.</div>'; return; }
  const s = Sesion.obtener();
  root.innerHTML = `
    ${cabeceraPantalla(ic('triangle-alert') + ' Control de mermas', 'Lo que cambió las existencias fuera de ventas, devoluciones y traspasos: quién, por qué y cuánto vale')}
    <div class="grupo-botones" style="align-items:flex-end;margin-bottom:8px;">
      <div id="mm-tienda"></div>
      <div><label>Período</label>
        <select id="mm-periodo"><option value="7">Últimos 7 días</option><option value="30">Últimos 30 días</option><option value="mes">Este mes</option><option value="pasado">Mes pasado</option></select></div>
    </div>
    <div id="mm-cuerpo"><div class="vacio">Cargando...</div></div>`;
  const selPeriodo = document.getElementById('mm-periodo');
  selPeriodo.value = mermas.periodo;
  selPeriodo.onchange = () => { mermas.periodo = selPeriodo.value; cargarMermas(); };

  if (SUPERIOR.includes(s.rol)) {
    const cont = document.getElementById('mm-tienda');
    cont.innerHTML = '<label>Tienda</label><select id="mm-codti"><option value="">Todas las tiendas</option></select>';
    try {
      const tiendas = (await api('GET', '/api/tiendas')).filter(t => !t.esAlmacen || true);
      const sel = document.getElementById('mm-codti');
      sel.insertAdjacentHTML('beforeend', tiendas.map(t => `<option value="${t.codti}">${escapar(t.nombre)}</option>`).join(''));
      sel.value = mermas.codti;
      sel.onchange = () => { mermas.codti = sel.value; cargarMermas(); };
    } catch { /* sin lista: se ven todas */ }
  }
  cargarMermas();
}

function mermasRango() {
  const hoy = new Date(), pad = n => String(n).padStart(2, '0');
  const f = d => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
  const dias = n => { const d = new Date(hoy); d.setDate(d.getDate() - n); return d; };
  switch (mermas.periodo) {
    case '7': return [f(dias(6)), f(hoy)];
    case 'mes': return [f(new Date(hoy.getFullYear(), hoy.getMonth(), 1)), f(hoy)];
    case 'pasado': return [f(new Date(hoy.getFullYear(), hoy.getMonth() - 1, 1)), f(new Date(hoy.getFullYear(), hoy.getMonth(), 0))];
    default: return [f(dias(29)), f(hoy)];
  }
}

async function cargarMermas() {
  const cont = document.getElementById('mm-cuerpo');
  cont.innerHTML = '<div class="vacio">Cargando...</div>';
  const [desde, hasta] = mermasRango();
  let d;
  try { d = await api('GET', `/api/reportes/mermas?desde=${desde}&hasta=${hasta}${mermas.codti ? '&codti=' + mermas.codti : ''}`); }
  catch (err) { cont.innerHTML = `<div class="mensaje error">${escapar(err.network ? 'Sin conexión con el servidor.' : err.message)}</div>`; return; }
  const dinero = v => v == null ? '—' : formatoDinero(v);
  const num = v => Math.abs(v - Math.round(v)) < 0.005 ? String(Math.round(v)) : Number(v).toFixed(2);
  const principal = (d.porCategoria || [])[0]?.nombre || '—';
  const resumen = (filas, titulo) => !filas || filas.length === 0 ? '<div class="vacio">No hay movimientos en este período.</div>' : `
    <div class="tabla-cont"><table class="tabla">
      <thead><tr><th>${titulo}</th><th>Movimientos</th><th>Piezas (neto)</th>${d.conCostos ? '<th>Valor a costo (neto)</th>' : ''}</tr></thead>
      <tbody>${filas.map(r => `<tr><td>${escapar(r.nombre)}</td><td>${r.movimientos}</td><td>${num(r.unidades)}</td>${d.conCostos ? `<td>${dinero(r.valor)}</td>` : ''}</tr>`).join('')}</tbody>
    </table></div>`;
  const detalle = () => !d.detalle || d.detalle.length === 0 ? '<div class="vacio">No hay movimientos en este período.</div>' : d.detalle.map(m => `
    <details class="cr-venta">
      <summary><span><strong>${escapar(m.articulo || '')}</strong> · ${formatoFecha(m.fecha)}</span>
        <span style="color:${m.cantidad < 0 ? '#dc2626' : '#059669'};font-weight:700;">${m.cantidad > 0 ? '+' : ''}${num(m.cantidad)}${d.conCostos && m.valor != null ? ' · ' + dinero(m.valor) : ''}</span></summary>
      <div class="detalle-fila"><span class="k">Quién</span><span class="v">${escapar(m.usuario || '')} · ${escapar(m.tienda || '')}</span></div>
      <div class="detalle-fila"><span class="k">${m.imei ? 'IMEI' : 'Código'}</span><span class="v">${escapar(m.imei || m.codigo || '')}</span></div>
      <div class="detalle-fila"><span class="k">Motivo</span><span class="v">${escapar(m.categoria || '')}</span></div>
      ${m.motivo ? `<div class="detalle-fila"><span class="k">Nota</span><span class="v">${escapar(m.motivo)}</span></div>` : ''}
    </details>`).join('');

  const vistas = { categoria: () => resumen(d.porCategoria, 'Motivo'), usuario: () => resumen(d.porUsuario, 'Quién'), tienda: () => resumen(d.porTienda, 'Tienda'), detalle };
  cont.innerHTML = `
    <div class="kp-h">
      ${kpiH('c6', 'trending-down', dinero(d.perdida), 'Pérdida a costo')}
      ${kpiH('c3', 'trending-up', dinero(d.sobrante), 'Ajustes al alza')}
      ${kpiH('c1', 'arrow-left-right', String(d.movimientos), 'Movimientos')}
    </div>
    <div class="ayuda" style="margin:6px 0;">Principal causa: <strong>${escapar(principal)}</strong>${d.conCostos ? '' : ' · no tienes permiso de ver costos'}</div>
    <div class="grupo-botones" style="margin:10px 0;">
      <button class="btn btn-chico btn-azul" data-v="categoria">Por motivo</button>
      <button class="btn btn-chico btn-gris" data-v="usuario">Por persona</button>
      ${d.codti == null ? '<button class="btn btn-chico btn-gris" data-v="tienda">Por tienda</button>' : ''}
      <button class="btn btn-chico btn-gris" data-v="detalle">Detalle (${d.movimientos})</button>
    </div>
    <div id="mm-vista"></div>
    <div class="ayuda" style="margin-top:10px;">Cuenta las salidas y ajustes manuales (con su motivo), las bajas de equipos y los ajustes de inventario físico. Las ventas, devoluciones y traspasos no aparecen aquí. Lo que hace un encargado también le llega al administrador como aviso.</div>`;
  const pintar = () => {
    cont.querySelectorAll('[data-v]').forEach(b => { b.classList.toggle('btn-azul', b.dataset.v === mermas.vista); b.classList.toggle('btn-gris', b.dataset.v !== mermas.vista); });
    document.getElementById('mm-vista').innerHTML = (vistas[mermas.vista] || vistas.categoria)();
  };
  if (!vistas[mermas.vista] || (mermas.vista === 'tienda' && d.codti != null)) mermas.vista = 'categoria';
  cont.querySelectorAll('[data-v]').forEach(b => b.onclick = () => { mermas.vista = b.dataset.v; pintar(); });
  pintar();
}
