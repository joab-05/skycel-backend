/**
 * Rastreo de equipos (igual que en JSystem): se escribe el IMEI o serie y se ve dónde está hoy y todo lo que le ha pasado desde que entró
 * (ingreso, traspasos entre tiendas, venta y a quién, devoluciones, garantías, bajas), en orden y con quién lo hizo.
 */

const RASTRO_ESTADOS = {
  DISPONIBLE: ['Disponible', 'lista'], VENDIDO: ['Vendido', 'recibida'], TRASPASADO: ['En traspaso', 'entregada'],
  EN_GARANTIA: ['En garantía', 'reparacion'], DEVUELTO: ['Devuelto', 'entregada'], DEBAJA: ['De baja', 'cancelada'],
};

const RASTRO_COLOR_TIPO = {
  ALTA: '#059669', ENTRADA: '#059669', TRASPASO_SALIDA: '#7c3aed', TRASPASO_ENTRADA: '#7c3aed', TRASPASO_ANULADO: '#7c3aed',
  TRASPASO_FALTANTE: '#7c3aed', VENTA: '#2563eb', CANCELACION_VENTA: '#0891b2', DEVOLUCION: '#0891b2', GARANTIA: '#d97706',
  GARANTIA_VITRINA: '#d97706', SALIDA: '#dc2626', AJUSTE: '#64748b',
};

function rastroFecha(iso) {
  const d = new Date(iso);
  if (isNaN(d.getTime())) return iso || '';
  return d.toLocaleDateString('es-MX', { day: '2-digit', month: 'short', year: 'numeric' }) + ' ' +
    d.toLocaleTimeString('es-MX', { hour: '2-digit', minute: '2-digit', hour12: true });
}

function rastroTextoHoy(e) {
  const donde = e.nombreTienda || '—';
  switch (e.estado) {
    case 'DISPONIBLE': return `Hoy está disponible en ${donde}${e.nombreSeccion ? ' (' + e.nombreSeccion + ')' : ''}.`;
    case 'VENDIDO': return `Ya se vendió. Su última tienda fue ${donde}.`;
    case 'TRASPASADO': return `Hoy va en camino de un traspaso (salió de ${donde}).`;
    case 'EN_GARANTIA': return `Hoy está apartado por una garantía (${donde}).`;
    case 'DEVUELTO': return `Lo devolvió un cliente; está en ${donde}.`;
    case 'DEBAJA': return `Está dado de baja (${donde}): ya no cuenta en las existencias.`;
    default: return `Está en ${donde}.`;
  }
}

async function pantallaRastro(imeiInicial) {
  root.innerHTML = `
    ${cabeceraPantalla(ic('route') + ' Rastreo de equipos', 'Desde que entra hasta dónde está hoy: traspasos, venta, garantías y bajas')}
    <div class="tarjeta">
      <label for="rs-imei">IMEI o número de serie</label>
      <div class="grupo-botones">
        <input id="rs-imei" placeholder="Escanéalo o escríbelo" autocomplete="off" inputmode="text" style="flex:1; min-width:180px;">
        <button id="rs-buscar" class="btn btn-azul">${ic('search')} Rastrear</button>
      </div>
    </div>
    <div id="rs-cuerpo"><div class="vacio">Escribe el IMEI y pulsa Rastrear.</div></div>`;
  const campo = document.getElementById('rs-imei');
  const cuerpo = document.getElementById('rs-cuerpo');
  let numero = 0;

  const rastrear = async () => {
    const imei = campo.value.trim().replace(/[\s-]/g, '');
    if (imei.length < 5) { cuerpo.innerHTML = '<div class="vacio">Escribe el IMEI (15 dígitos) o el número de serie completo.</div>'; return; }
    const esta = ++numero;
    cuerpo.innerHTML = '<div class="vacio">Buscando...</div>';
    try {
      const r = await api('GET', `/api/equipos/${encodeURIComponent(imei)}/rastro`);
      if (esta !== numero) return;
      pintar(r);
    } catch (err) {
      if (esta !== numero) return;
      cuerpo.innerHTML = `<div class="mensaje error">${escapar(err.network ? 'Sin conexión con el servidor.' : err.message)}</div>`;
    }
  };

  const pintar = (r) => {
    const e = r.equipo;
    const [nombreEstado, color] = RASTRO_ESTADOS[e.estado] || [e.estado, 'gris'];
    const eventos = r.eventos || [];
    cuerpo.innerHTML = `
      <div class="tarjeta">
        <div class="orden-cab"><h2 style="margin:0;">${escapar(e.nombreArticulo || 'Equipo')}</h2><span class="pastilla ${color}">${escapar(nombreEstado)}</span></div>
        <div class="orden-cliente" style="font-family:monospace;">${e.tipoIdentificador === 'SERIE' ? 'Serie' : 'IMEI'} ${escapar(e.imei)}</div>
        <p style="margin:8px 0 0;">${escapar(rastroTextoHoy(e))}</p>
      </div>
      <div class="kp-h">
        ${kpiH('c1', 'history', r.diasEnInventario == null ? '—' : r.diasEnInventario, r.vendido ? 'Días en inventario (hasta su venta)' : 'Lleva en inventario')}
        ${kpiH('c5', 'truck', r.traspasos, 'Traspasos')}
        ${kpiH('c4', 'shield-check', r.garantias, 'Garantías')}
      </div>
      <div class="tarjeta">
        <h2 style="margin-top:0;">Recorrido</h2>
        <div>${(r.recorrido || []).length ? r.recorrido.map(t => `<span class="rs-tienda">${escapar(t)}</span>`).join('<span class="rs-flecha">→</span>') : '<span class="ayuda">—</span>'}</div>
      </div>
      <div class="tarjeta">
        <h2 style="margin-top:0;">Lo que le ha pasado</h2>
        ${eventos.length === 0 ? '<div class="vacio">Este equipo todavía no tiene movimientos.</div>' : `<div class="rs-linea">${eventos.map(ev => `
          <div class="rs-evento">
            <span class="rs-punto" style="background:${RASTRO_COLOR_TIPO[ev.tipo] || '#64748b'};"></span>
            <div class="rs-fecha">${escapar(rastroFecha(ev.fecha))}</div>
            <div class="rs-texto">
              <strong>${escapar(ev.titulo)}</strong>
              ${ev.detalle ? `<div>${escapar(ev.detalle)}</div>` : ''}
              <div class="ayuda">${[ev.usuario ? 'Lo registró ' + escapar(ev.usuario) : '', ev.referencia ? escapar(ev.referencia) : '', ev.monto != null ? formatoDinero(ev.monto) : ''].filter(Boolean).join(' · ')}</div>
            </div>
          </div>`).join('')}</div>`}
      </div>`;
  };

  document.getElementById('rs-buscar').onclick = rastrear;
  campo.addEventListener('keydown', (ev) => { if (ev.key === 'Enter') rastrear(); });
  if (imeiInicial) { campo.value = decodeURIComponent(imeiInicial); rastrear(); } else campo.focus();
}
