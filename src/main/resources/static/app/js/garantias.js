/**
 * Recibir un producto por garantía (igual que en JSystem): de una venta de este sistema (por IMEI o código), de una venta del sistema
 * anterior (con la fecha de compra y los días de garantía que tenía) o un equipo de vitrina con una falla (aún sin vender).
 * El seguimiento (bodega, proveedor, entrega) está en el detalle de cada garantía.
 */

function formularioNuevaGarantia(cont, alGuardar) {
  cont.classList.remove('oculto');
  let origen = 'venta';
  cont.innerHTML = `
    <div class="tarjeta">
      <h2 class="h2-ic" style="margin-top:0;">${ic('shield-check')} Recibir una garantía</h2>
      <label class="obligatorio">¿De dónde viene el producto?</label>
      <div class="segmentado">
        <button type="button" id="gn-o-venta" class="activo">Venta de este sistema</button>
        <button type="button" id="gn-o-anterior">Venta anterior</button>
        <button type="button" id="gn-o-vitrina">Equipo de vitrina</button>
      </div>
      <div id="gn-venta">
        <label class="obligatorio">Número de venta</label>
        <input id="gn-idventa" type="number" inputmode="numeric" placeholder="Ej. 51">
        <div class="fila">
          <div><label>IMEI o serie (equipos)</label><input id="gn-imei-v"></div>
          <div><label>Código (accesorios)</label><input id="gn-codpro"></div>
        </div>
        <button id="gn-elegible" class="btn btn-gris btn-chico">Revisar si todavía tiene garantía</button>
        <div id="gn-elegible-res"></div>
      </div>
      <div id="gn-anterior" class="oculto">
        <div class="ayuda">Para algo que se vendió antes de este sistema: pide la fecha del ticket y los días de garantía que traía.</div>
        <label class="obligatorio">Producto</label>
        <input id="gn-nombre" maxlength="255" placeholder="Ej. Samsung A15 128gb">
        <div class="fila">
          <div><label class="obligatorio">Fecha de compra</label><input id="gn-fecha" type="date"></div>
          <div><label class="obligatorio">Días de garantía</label><input id="gn-dias" type="number" min="1" inputmode="numeric"></div>
        </div>
        <div class="fila">
          <div><label>IMEI o serie</label><input id="gn-imei-a"></div>
          <div><label>Ticket anterior</label><input id="gn-folio" maxlength="40" placeholder="Opcional"></div>
        </div>
      </div>
      <div id="gn-vitrina" class="oculto">
        <div class="ayuda">Un equipo que aún no se vende y se le detectó una falla: se aparta de las existencias mientras va con el proveedor y vuelve al entregarse.</div>
        <label class="obligatorio">IMEI o serie del equipo</label>
        <input id="gn-imei-vitrina">
      </div>
      <label class="obligatorio">Falla reportada</label>
      <textarea id="gn-falla" placeholder="Qué le pasa"></textarea>
      <label>Estado al recibirlo</label>
      <input id="gn-diag" placeholder="Golpes, rayones o cualquier detalle (opcional)">
      <div id="gn-contacto">
        <div class="fila">
          <div><label>Nombre de quien lo trae</label><input id="gn-contacto-nombre" maxlength="150"></div>
          <div><label>Teléfono</label><input id="gn-contacto-tel" inputmode="tel" maxlength="20"></div>
        </div>
        <div class="ayuda">Si la venta tiene cliente se toman sus datos; indícalos si no o para avisarle a otra persona.</div>
      </div>
      <div class="grupo-botones">
        <button id="gn-guardar" class="btn btn-verde">Recibir garantía</button>
        <button id="gn-cancelar" class="btn btn-gris">Cancelar</button>
      </div>
    </div>`;

  const ver = (id, v) => document.getElementById(id).classList.toggle('oculto', !v);
  const elegir = (o) => {
    origen = o;
    ['venta', 'anterior', 'vitrina'].forEach(k => document.getElementById('gn-o-' + k).classList.toggle('activo', k === o));
    ver('gn-venta', o === 'venta'); ver('gn-anterior', o === 'anterior'); ver('gn-vitrina', o === 'vitrina');
    ver('gn-contacto', o !== 'vitrina');
  };
  ['venta', 'anterior', 'vitrina'].forEach(k => document.getElementById('gn-o-' + k).onclick = () => elegir(k));
  document.getElementById('gn-cancelar').onclick = () => { cont.classList.add('oculto'); cont.innerHTML = ''; };

  document.getElementById('gn-elegible').onclick = async () => {
    const res = document.getElementById('gn-elegible-res');
    const idventa = Number(document.getElementById('gn-idventa').value);
    const imei = document.getElementById('gn-imei-v').value.trim(), codpro = document.getElementById('gn-codpro').value.trim();
    if (!idventa || (!imei && !codpro)) { res.innerHTML = '<div class="mensaje error">Indica la venta y el IMEI o el código.</div>'; return; }
    try {
      const e = await api('GET', `/api/garantias/elegibilidad?idventa=${idventa}${imei ? '&imei=' + encodeURIComponent(imei) : ''}${codpro ? '&codpro=' + encodeURIComponent(codpro) : ''}`);
      res.innerHTML = e.elegible
        ? `<div class="mensaje ok">${ic('circle-check')} ${escapar(e.nombreProducto || '')}: tiene garantía hasta ${escapar(e.garantiaVigenteHasta || '')} (${e.diasRestantes} días).</div>`
        : `<div class="mensaje error">${escapar(e.motivo || 'Ya no tiene garantía.')}</div>`;
    } catch (err) { res.innerHTML = `<div class="mensaje error">${escapar(err.network ? 'Sin conexión con el servidor.' : err.message)}</div>`; }
  };

  document.getElementById('gn-guardar').onclick = async (e) => {
    const v = (id) => document.getElementById(id).value.trim();
    const falla = v('gn-falla');
    if (!falla) { mostrarMensaje(root, 'Escribe la falla reportada.', 'error'); return; }
    // se recibe en la sucursal con la que se está trabajando (un administrador o un vendedor pueden estar en otra distinta a la de su alta)
    const dto = { codti: Sesion.obtener().codti, fallaReportada: falla, diagnosticoInicial: v('gn-diag') || null };
    if (origen === 'vitrina') {
      if (!v('gn-imei-vitrina')) { mostrarMensaje(root, 'Indica el IMEI o serie del equipo de vitrina.', 'error'); return; }
      dto.vitrina = true; dto.imei = v('gn-imei-vitrina');
    } else {
      dto.nombreContacto = v('gn-contacto-nombre') || null; dto.telefonoContacto = v('gn-contacto-tel') || null;
      if (origen === 'venta') {
        if (!Number(v('gn-idventa'))) { mostrarMensaje(root, 'Indica el número de venta.', 'error'); return; }
        if (!v('gn-imei-v') && !v('gn-codpro')) { mostrarMensaje(root, 'Indica el IMEI o el código del producto.', 'error'); return; }
        dto.idventa = Number(v('gn-idventa')); dto.imei = v('gn-imei-v') || null; dto.codpro = v('gn-codpro') || null;
      } else {
        if (!v('gn-nombre') || !v('gn-fecha') || !Number(v('gn-dias'))) { mostrarMensaje(root, 'Indica el producto, la fecha de compra y los días de garantía.', 'error'); return; }
        dto.ventaAnterior = true; dto.nombreProducto = v('gn-nombre'); dto.fechaCompra = v('gn-fecha'); dto.diasGarantia = Number(v('gn-dias'));
        dto.imei = v('gn-imei-a') || null; dto.folioVentaAnterior = v('gn-folio') || null;
      }
    }
    e.target.disabled = true;
    try {
      const g = await api('POST', '/api/garantias', dto);
      mostrarMensaje(root, `Garantía ${g.folio} recibida.`, 'ok');
      setTimeout(() => { if (alGuardar) alGuardar(g); }, 700);
    } catch (err) {
      mostrarMensaje(root, err.network ? 'Sin conexión con el servidor.' : err.message, 'error');
      e.target.disabled = false;
    }
  };
}
