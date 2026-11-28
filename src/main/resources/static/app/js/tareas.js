/**
 * Tareas (igual que en JSystem): lo que le toca hacer a cada quien hoy (apertura, durante el día y cierre), lo que ha hecho todo el equipo
 * de la tienda, el cumplimiento y las rutinas que arma el administrador. Cada pestaña aparece solo si el rol tiene el permiso.
 */

const TAREAS_MOMENTOS = [['APERTURA', 'Apertura'], ['DURANTE', 'Durante el día'], ['CIERRE', 'Cierre']];
const TAREAS_ROLES = [['', 'Cualquiera de la tienda'], ['ENCARGADO_TIENDA', 'Encargado'], ['VENDEDOR', 'Apoyo (vendedor)'], ['TECNICO', 'Técnico']];
const TAREAS_DIAS = ['Lun', 'Mar', 'Mié', 'Jue', 'Vie', 'Sáb', 'Dom'];

function tareaTextoRol(rol) {
  const r = TAREAS_ROLES.find(x => x[0] === (rol || ''));
  return r ? (rol ? r[1] : 'Cualquiera') : rol;
}
function tareaHora(iso) {
  const d = new Date(iso);
  return isNaN(d.getTime()) ? (iso || '') : d.toLocaleTimeString('es-MX', { hour: '2-digit', minute: '2-digit', hour12: false });
}
function tareaFechaLarga(iso) {
  const d = new Date(iso + 'T12:00:00');
  if (isNaN(d.getTime())) return iso || '';
  const s = d.toLocaleDateString('es-MX', { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' });
  return s.charAt(0).toUpperCase() + s.slice(1);
}
function tareaHoyISO() { return fechaLocalISO(new Date()).slice(0, 10); }
function tareaFechaCorta(iso) {
  const d = new Date(iso + 'T12:00:00');
  return isNaN(d.getTime()) ? (iso || '') : d.toLocaleDateString('es-MX', { day: 'numeric', month: 'short' });
}
function tareaPara(t) { return t.asignadoA || tareaTextoRol(t.rolDestino); }
function tareaTextoDias(dias) {
  if (!dias || !dias.length) return '';
  if (dias.length === 7) return 'Todos los días';
  return dias.map(d => TAREAS_DIAS[d - 1]).join(', ');
}
/** [texto, clase de pastilla] del estado de una tarea. */
function tareaEstado(t) {
  if (t.estado === 'APROBADA') return ['Aprobada', 'lista'];
  if (t.estado === 'POR_REVISAR') return ['Por revisar', 'recibida'];
  if (t.estado === 'HECHA') return t.tarde ? ['Hecha (tarde)', 'reparacion'] : ['Hecha', 'lista'];
  return t.atrasada ? ['Atrasada', 'cancelada'] : ['Pendiente', 'pendiente'];
}

/** Abre o cierra, dentro de {@code cont}, las fotos de una tarea. {@code puedeBorrar}: muestra «Quitar» en cada una. */
async function tareaMostrarFotos(cont, idtarea, puedeBorrar, alCambiar) {
  if (!cont) return;
  if (cont.dataset.abierto === '1') { cont.innerHTML = ''; cont.dataset.abierto = ''; return; }
  cont.dataset.abierto = '1';
  cont.innerHTML = '<div class="vacio">Cargando...</div>';
  const falla = (err) => err.network ? 'Sin conexión con el servidor.' : err.message;
  try {
    const fotos = await api('GET', `/api/tareas/${idtarea}/fotos`);
    const urls = await Promise.all(fotos.map(f => apiImagenUrl(`/api/tareas/${idtarea}/fotos/${f.idfoto}`).catch(() => '')));
    cont.innerHTML = fotos.length === 0 ? '<div class="ayuda">Sin fotos.</div>' : fotos.map((f, i) => `
      <figure style="margin:8px 0;">
        <img src="${urls[i]}" alt="Foto de la tarea" style="width:100%;max-width:420px;border-radius:10px;border:1px solid var(--borde);display:block;">
        <figcaption class="ayuda">${escapar(f.usuario || '')}${puedeBorrar ? ` <button type="button" class="btn btn-gris btn-chico tr-quitar-foto" data-foto="${f.idfoto}">Quitar</button>` : ''}</figcaption>
      </figure>`).join('');
    cont.querySelectorAll('.tr-quitar-foto').forEach(b => b.onclick = async () => {
      b.disabled = true;
      try { await api('DELETE', `/api/tareas/${idtarea}/fotos/${b.dataset.foto}`); } catch (err) { alert(falla(err)); }
      alCambiar();
    });
  } catch (err) {
    cont.innerHTML = `<div class="mensaje error">${escapar(falla(err))}</div>`;
  }
}

async function pantallaTareas() {
  const s = Sesion.obtener();
  const veEquipo = Sesion.tiene('TAREAS_EQUIPO');
  const armaRutinas = Sesion.tiene('TAREAS_RUTINAS');
  const veAsigna = Sesion.tiene('TAREAS_ASIGNAR');
  const esAdmin = SUPERIOR.includes(s.rol);
  const pestanas = [['mis', 'Mis tareas']];
  if (veEquipo) pestanas.push(['equipo', 'Equipo'], ['cump', 'Cumplimiento']);
  if (armaRutinas) pestanas.push(['rutinas', 'Rutinas']);

  root.innerHTML = `
    ${cabeceraPantalla(ic('clipboard-check') + ' Tareas', 'Lo que toca hacer hoy en la tienda, por apertura, durante el día y cierre')}
    ${pestanas.length > 1 ? `<div class="segmentado">${pestanas.map(([k, t], i) => `<button data-p="${k}" class="${i === 0 ? 'activo' : ''}">${t}</button>`).join('')}</div>` : ''}
    <div id="tr-cuerpo"><div class="vacio">Cargando...</div></div>`;
  const cuerpo = document.getElementById('tr-cuerpo');
  const errorDe = (err) => err.network ? 'Sin conexión con el servidor.' : err.message;
  let tiendas = null;
  const cargarTiendas = async () => {
    if (!tiendas) tiendas = (await api('GET', '/api/tiendas')).filter(t => t.activo !== false);
    return tiendas;
  };
  const opcionesTiendas = (lista, elegida, conTodas) =>
    (conTodas ? `<option value="">Todas las sucursales</option>` : '') +
    lista.map(t => `<option value="${t.codti}" ${String(t.codti) === String(elegida) ? 'selected' : ''}>${escapar(t.nombre)}</option>`).join('');
  /** Los administradores eligen cualquier sucursal; los demás, la suya (el servidor lo exige igual). */
  const tiendasDisponibles = async () => {
    const todas = await cargarTiendas();
    return esAdmin || s.codti == null ? todas : todas.filter(t => t.codti === s.codti);
  };

  let activa = 'mis';
  const mostrar = (p) => {
    activa = p;
    document.querySelectorAll('.segmentado button').forEach(b => b.classList.toggle('activo', b.dataset.p === p));
    cuerpo.innerHTML = '<div class="vacio">Cargando...</div>';
    ({ mis: dibujarMis, equipo: dibujarEquipo, cump: dibujarCumplimiento, rutinas: dibujarRutinas })[p]().catch(err => {
      cuerpo.innerHTML = `<div class="mensaje error">${escapar(errorDe(err))}</div>`;
    });
  };
  document.querySelectorAll('.segmentado button').forEach(b => b.onclick = () => mostrar(b.dataset.p));

  // ── Mis tareas ───────────────────────────────────────────────────────────
  async function dibujarMis() {
    if (s.codti == null) {
      cuerpo.innerHTML = '<div class="tarjeta">Elige primero la sucursal en la que trabajas (en Inicio).</div>';
      return;
    }
    const d = await api('GET', '/api/tareas/mias?codti=' + s.codti);
    if (activa !== 'mis') return;
    const puntuales = d.tareas.filter(t => t.puntual);
    const tarjetaPuntuales = puntuales.length ? `<div class="tarjeta"><h2 style="margin-top:0;">Tareas puntuales</h2>${puntuales.map(filaMia).join('')}</div>` : '';
    const grupos = tarjetaPuntuales + TAREAS_MOMENTOS.map(([clave, nombre]) => {
      const lista = d.tareas.filter(t => !t.puntual && t.momento === clave);
      return lista.length ? `<div class="tarjeta"><h2 style="margin-top:0;">${nombre}</h2>${lista.map(filaMia).join('')}</div>` : '';
    }).join('');
    cuerpo.innerHTML = `
      <div class="tarjeta">
        <h2 style="margin:0;">${escapar(d.nombreTienda)}</h2>
        <div class="ayuda">${escapar(tareaFechaLarga(d.fecha))}</div>
        <div style="margin-top:8px;"><strong>${d.total === 0 ? 'Hoy no hay tareas' : d.hechas + ' de ' + d.total + ' hechas'}</strong>
          ${d.atrasadas > 0 ? `<span class="pastilla cancelada" style="margin-left:6px;">${d.atrasadas} ${d.atrasadas === 1 ? 'atrasada' : 'atrasadas'}</span>` : ''}</div>
        <progress max="${d.total || 1}" value="${d.hechas}" style="width:100%;margin-top:8px;"></progress>
      </div>
      ${d.total === 0 ? `<div class="vacio">Todavía no hay tareas para tu puesto hoy.${armaRutinas ? ' Arma las actividades en la pestaña «Rutinas».' : ''}</div>` : grupos}`;
    cuerpo.querySelectorAll('.tr-hecha').forEach(c => c.onchange = async () => {
      if (!c.checked) return;
      c.disabled = true;
      const nota = (cuerpo.querySelector(`.tr-nota[data-id="${c.dataset.id}"]`)?.value || '').trim();
      try { await api('POST', `/api/tareas/${c.dataset.id}/hecha`, nota ? { nota } : {}); }
      catch (err) { alert(errorDe(err)); }
      mostrar('mis');
    });
    cuerpo.querySelectorAll('.tr-deshacer').forEach(b => b.onclick = async () => {
      b.disabled = true;
      try { await api('POST', `/api/tareas/${b.dataset.id}/deshacer`); }
      catch (err) { alert(errorDe(err)); }
      mostrar('mis');
    });
    cuerpo.querySelectorAll('.tr-foto').forEach(i => i.onchange = async () => {
      const archivo = i.files[0];
      if (!archivo) return;
      i.disabled = true;
      try { await apiSubirImagen(`/api/tareas/${i.dataset.id}/fotos`, await reducirFoto(archivo)); }
      catch (err) { alert(errorDe(err)); }
      mostrar('mis');
    });
    cuerpo.querySelectorAll('.tr-ver-fotos').forEach(b => b.onclick = () =>
      tareaMostrarFotos(cuerpo.querySelector(`.tr-fotos[data-id="${b.dataset.id}"]`), b.dataset.id, b.dataset.puede === '1', () => mostrar('mis')));
  }

  function filaMia(t) {
    const limite = t.puntual ? `<span class="pastilla ${t.atrasada ? 'cancelada' : 'reparacion'}">Para el ${escapar(tareaFechaCorta(t.fecha))}${t.horaLimite ? ' antes de ' + escapar(t.horaLimite) : ''}</span> `
      : (t.horaLimite ? `<span class="pastilla ${t.atrasada ? 'cancelada' : 'pendiente'}">Antes de ${escapar(t.horaLimite)}</span> ` : '');
    const etiquetas = limite + (t.rolDestino && !t.puntual ? `<span class="pastilla recibida">${escapar(tareaTextoRol(t.rolDestino))}</span> ` : '') +
      (t.requiereRevision ? '<span class="pastilla pendiente">Se revisa</span> ' : '') +
      (t.requiereFoto ? '<span class="pastilla pendiente">Pide foto</span>' : '');
    const desc = t.descripcion ? `<div class="ayuda">${escapar(t.descripcion)}</div>` : '';
    if (t.estado !== 'PENDIENTE') {
      const mia = t.idusuarioHecha != null && t.idusuarioHecha === s.idusuario;
      const revisando = t.estado === 'POR_REVISAR';
      return `<div class="tr-fila" style="padding:10px 0; border-top:1px solid var(--borde);">
        <div><strong style="color:var(--gris);">${revisando ? '⏳' : '✔'} ${escapar(t.titulo)}</strong> ${etiquetas}</div>${desc}
        <div class="ayuda" style="color:${revisando || t.tarde ? '#d97706' : '#059669'};font-weight:700;">Hecha por ${escapar(t.hechaPor)} a las ${escapar(tareaHora(t.hechaEl))}${t.tarde ? ' · fuera de hora' : ''}${revisando ? ' · espera revisión' : ''}${t.estado === 'APROBADA' ? ' · aprobada por ' + escapar(t.revisadaPor) : ''}</div>
        ${t.nota ? `<div>Nota: ${escapar(t.nota)}</div>` : ''}
        ${t.fotos > 0 ? `<button type="button" class="btn btn-gris btn-chico tr-ver-fotos" data-id="${t.idtarea}" style="margin-top:6px;">${ic('eye')} Ver fotos (${t.fotos})</button><div class="tr-fotos" data-id="${t.idtarea}"></div>` : ''}
        ${t.estado !== 'APROBADA' && (mia || veEquipo) ? `<button class="btn btn-gris btn-chico tr-deshacer" data-id="${t.idtarea}" style="margin-top:6px;">Deshacer</button>` : ''}
      </div>`;
    }
    const regreso = t.comentarioRevision ? `<div style="color:#dc2626;font-weight:700;margin:4px 0;">La regresó ${escapar(t.revisadaPor || 'el encargado')}: ${escapar(t.comentarioRevision)}</div>` : '';
    return `<div class="tr-fila" style="padding:10px 0; border-top:1px solid var(--borde);">
      <label style="display:flex;gap:12px;align-items:flex-start;font-size:16px;font-weight:700;">
        <input type="checkbox" class="tr-hecha" data-id="${t.idtarea}" style="width:26px;height:26px;margin-top:0;flex:none;">
        <span>${escapar(t.titulo)} ${etiquetas}</span>
      </label>${desc}${regreso}
      <input class="tr-nota" data-id="${t.idtarea}" placeholder="Nota (opcional)" maxlength="500" style="margin-top:6px;">
      <div style="margin-top:6px;display:flex;gap:8px;align-items:center;flex-wrap:wrap;">
        <label class="btn btn-gris btn-chico" style="margin:0;">${ic('camera')} ${t.fotos > 0 ? 'Agregar otra foto' : 'Foto'}<input type="file" accept="image/*" capture="environment" class="tr-foto" data-id="${t.idtarea}" hidden></label>
        ${t.fotos > 0 ? `<button type="button" class="btn btn-gris btn-chico tr-ver-fotos" data-id="${t.idtarea}" data-puede="1">${ic('eye')} Ver fotos (${t.fotos})</button>`
          : (t.requiereFoto ? '<span class="ayuda" style="color:#d97706;font-weight:700;">Pide una foto antes de marcarla.</span>' : '')}
      </div>
      <div class="tr-fotos" data-id="${t.idtarea}"></div>
    </div>`;
  }

  // ── Equipo ───────────────────────────────────────────────────────────────
  async function dibujarEquipo() {
    const lista = await tiendasDisponibles();
    if (activa !== 'equipo') return;
    cuerpo.innerHTML = `
      <div class="tarjeta">
        <label for="te-tienda">Sucursal</label><select id="te-tienda">${opcionesTiendas(lista, s.codti, false)}</select>
        <label for="te-fecha">Día</label><input id="te-fecha" type="date" value="${tareaHoyISO()}" max="${tareaHoyISO()}">
        ${veAsigna ? `<button id="te-nueva" class="btn btn-azul" style="margin-top:12px;">${ic('plus')} Nueva tarea</button>` : ''}
      </div>
      <div id="te-form"></div>
      <div id="te-datos"><div class="vacio">Cargando...</div></div>`;
    const ver = async () => {
      const datos = document.getElementById('te-datos');
      const codti = document.getElementById('te-tienda').value;
      const fecha = document.getElementById('te-fecha').value || tareaHoyISO();
      if (!codti) { datos.innerHTML = ''; return; }
      datos.innerHTML = '<div class="vacio">Cargando...</div>';
      try {
        const d = await api('GET', `/api/tareas/equipo?codti=${codti}&fecha=${fecha}`);
        datos.innerHTML = `
          <div class="kp-h">
            ${kpiH('c1', 'clipboard-list', d.total, 'Tareas')}
            ${kpiH('c4', 'circle-check', d.hechas, 'Hechas')}
            ${kpiH('c5', 'clock', d.tareas.filter(x => x.estado === 'PENDIENTE').length, 'Pendientes')}
            ${kpiH('c2', 'eye', d.tareas.filter(x => x.estado === 'POR_REVISAR').length, 'Por revisar')}
            ${kpiH('c3', 'triangle-alert', d.atrasadas, 'Atrasadas')}
          </div>
          ${d.tareas.length === 0 ? '<div class="vacio">Sin tareas ese día.</div>' : d.tareas.map(t => {
            const [txt, clase] = tareaEstado(t);
            return `<div class="tarjeta">
              <div class="orden-cab"><strong>${escapar(t.titulo)}</strong><span class="pastilla ${clase}">${txt}</span></div>
              <div class="ayuda">${t.puntual ? 'Puntual · para el ' + escapar(tareaFechaCorta(t.fecha)) : escapar(t.momentoEtiqueta)} · ${escapar(tareaPara(t))}${t.horaLimite ? ' · antes de ' + escapar(t.horaLimite) : ''}</div>
              ${t.hechaPor ? `<div>${escapar(t.hechaPor)} · ${escapar(tareaHora(t.hechaEl))}</div>` : ''}
              ${t.nota ? `<div>Nota: ${escapar(t.nota)}</div>` : ''}
              ${t.comentarioRevision ? `<div style="color:#dc2626;font-weight:700;">Se regresó: ${escapar(t.comentarioRevision)}</div>` : ''}
              <div class="grupo-botones">
                ${t.fotos > 0 ? `<button class="btn btn-gris btn-chico te-ver-fotos" data-id="${t.idtarea}">${ic('eye')} Ver fotos (${t.fotos})</button>` : (t.requiereFoto && t.estado === 'PENDIENTE' ? '<span class="ayuda">Pide foto</span>' : '')}
                ${veAsigna && t.estado === 'POR_REVISAR' ? `<button class="btn btn-verde btn-chico te-aprobar" data-id="${t.idtarea}">${ic('check')} Aprobar</button><button class="btn btn-gris btn-chico te-regresar" data-id="${t.idtarea}">${ic('undo-2')} Regresar</button>` : ''}
                ${veAsigna && t.puntual && t.estado === 'PENDIENTE' ? `<button class="btn btn-gris btn-chico te-editar" data-id="${t.idtarea}">${ic('pencil')} Editar</button><button class="btn btn-rojo btn-chico te-quitar" data-id="${t.idtarea}">${ic('trash-2')} Quitar</button>` : ''}
                ${(t.estado === 'HECHA' || t.estado === 'POR_REVISAR') && t.hechaEl && t.hechaEl.slice(0, 10) === tareaHoyISO() ? `<button class="btn btn-gris btn-chico te-deshacer" data-id="${t.idtarea}">Deshacer la marca</button>` : ''}
              </div>
              <div class="te-fotos" data-id="${t.idtarea}"></div>
            </div>`;
          }).join('')}`;
        datos.querySelectorAll('.te-deshacer').forEach(b => b.onclick = async () => {
          b.disabled = true;
          try { await api('POST', `/api/tareas/${b.dataset.id}/deshacer`); } catch (err) { alert(errorDe(err)); }
          ver();
        });
        const tarea = (b) => d.tareas.find(x => String(x.idtarea) === String(b.dataset.id));
        datos.querySelectorAll('.te-ver-fotos').forEach(b => b.onclick = () =>
          tareaMostrarFotos(datos.querySelector(`.te-fotos[data-id="${b.dataset.id}"]`), b.dataset.id, veAsigna, ver));
        datos.querySelectorAll('.te-aprobar').forEach(b => b.onclick = async () => {
          b.disabled = true;
          try { await api('POST', `/api/tareas/${b.dataset.id}/aprobar`); } catch (err) { alert(errorDe(err)); }
          ver();
        });
        datos.querySelectorAll('.te-regresar').forEach(b => b.onclick = async () => {
          const c = (prompt(`«${tarea(b).titulo}»\n\n¿Qué falta o qué hay que corregir?`) || '').trim();
          if (!c) return;
          b.disabled = true;
          try { await api('POST', `/api/tareas/${b.dataset.id}/rechazar`, { comentario: c }); } catch (err) { alert(errorDe(err)); }
          ver();
        });
        datos.querySelectorAll('.te-editar').forEach(b => b.onclick = () => formularioPuntual(tarea(b)));
        datos.querySelectorAll('.te-quitar').forEach(b => b.onclick = async () => {
          if (!confirm(`«${tarea(b).titulo}» se quita para todos.\n\n¿Quitarla?`)) return;
          try { await api('DELETE', `/api/tareas/${b.dataset.id}`); } catch (err) { alert(errorDe(err)); }
          ver();
        });
      } catch (err) {
        datos.innerHTML = `<div class="mensaje error">${escapar(errorDe(err))}</div>`;
      }
    };
    document.getElementById('te-tienda').onchange = ver;
    document.getElementById('te-fecha').onchange = ver;

    /** El formulario de una tarea puntual: nueva (null) o la que se edita. */
    async function formularioPuntual(t) {
      const codti = document.getElementById('te-tienda').value;
      if (!codti) return;
      const cont = document.getElementById('te-form');
      cont.innerHTML = '<div class="vacio">Cargando...</div>';
      let personal = [];
      try { personal = await api('GET', '/api/tareas/personal?codti=' + codti); }
      catch (err) { cont.innerHTML = `<div class="mensaje error">${escapar(errorDe(err))}</div>`; return; }
      const sel = t ? (t.idusuarioAsignado != null ? 'p' + t.idusuarioAsignado : (t.rolDestino ? 'r' + t.rolDestino : '')) : '';
      cont.innerHTML = `
        <div class="tarjeta">
          <h2 style="margin-top:0;">${t ? 'Editar tarea' : 'Nueva tarea'}</h2>
          <label class="obligatorio" for="tp-titulo">Tarea</label><input id="tp-titulo" maxlength="150" placeholder="Por ejemplo: Llamar al cliente de la orden OS-0231" value="${escapar(t ? t.titulo : '')}">
          <label for="tp-desc">Detalle</label><textarea id="tp-desc" rows="2" maxlength="500" placeholder="Opcional">${escapar(t && t.descripcion ? t.descripcion : '')}</textarea>
          <label for="tp-para">Para</label>
          <select id="tp-para">
            <option value="">Cualquiera de la tienda</option>
            ${TAREAS_ROLES.filter(r => r[0]).map(([k, n]) => `<option value="r${k}" ${sel === 'r' + k ? 'selected' : ''}>Todo el puesto: ${n}</option>`).join('')}
            ${personal.map(p => `<option value="p${p.idusuario}" ${sel === 'p' + p.idusuario ? 'selected' : ''}>${escapar(p.nombre)} · ${escapar(tareaTextoRol(p.rol))}</option>`).join('')}
          </select>
          <label class="obligatorio" for="tp-fecha">Fecha límite</label><input id="tp-fecha" type="date" value="${escapar(t ? t.fecha : tareaHoyISO())}">
          <label for="tp-hora">Hora límite</label><input id="tp-hora" type="time" value="${escapar(t && t.horaLimite ? t.horaLimite : '')}">
          <label style="display:flex;gap:8px;align-items:center;"><input type="checkbox" id="tp-rev" style="width:auto;" ${t && t.requiereRevision ? 'checked' : ''}> Requiere que el encargado la revise</label>
          <label style="display:flex;gap:8px;align-items:center;"><input type="checkbox" id="tp-foto" style="width:auto;" ${t && t.requiereFoto ? 'checked' : ''}> Pide una foto de evidencia</label>
          <div id="tp-error"></div>
          <div class="grupo-botones">
            <button id="tp-guardar" class="btn btn-verde">${ic('save')} Guardar</button>
            <button id="tp-cancelar" class="btn btn-gris">Cancelar</button>
          </div>
        </div>`;
      document.getElementById('tp-cancelar').onclick = () => { cont.innerHTML = ''; };
      document.getElementById('tp-guardar').onclick = async () => {
        const falla = document.getElementById('tp-error');
        const titulo = document.getElementById('tp-titulo').value.trim();
        const fecha = document.getElementById('tp-fecha').value;
        if (!titulo) { falla.innerHTML = '<div class="mensaje error">Escribe qué hay que hacer.</div>'; return; }
        if (!fecha) { falla.innerHTML = '<div class="mensaje error">Elige la fecha límite.</div>'; return; }
        const para = document.getElementById('tp-para').value;
        const cuerpoReq = {
          codti: Number(codti), titulo, descripcion: document.getElementById('tp-desc').value.trim() || null, fecha,
          horaLimite: document.getElementById('tp-hora').value || null, requiereRevision: document.getElementById('tp-rev').checked, requiereFoto: document.getElementById('tp-foto').checked,
          idusuarioAsignado: para.startsWith('p') ? Number(para.slice(1)) : null, rolDestino: para.startsWith('r') ? para.slice(1) : null,
        };
        const boton = document.getElementById('tp-guardar');
        boton.disabled = true;
        try {
          await (t ? api('PUT', '/api/tareas/' + t.idtarea, cuerpoReq) : api('POST', '/api/tareas', cuerpoReq));
          cont.innerHTML = '';
          ver();
        } catch (err) {
          boton.disabled = false;
          falla.innerHTML = `<div class="mensaje error">${escapar(errorDe(err))}</div>`;
        }
      };
    }
    const nueva = document.getElementById('te-nueva');
    if (nueva) nueva.onclick = () => formularioPuntual(null);
    ver();
  }

  // ── Cumplimiento ─────────────────────────────────────────────────────────
  async function dibujarCumplimiento() {
    const lista = await tiendasDisponibles();
    if (activa !== 'cump') return;
    const hoy = new Date();
    const hace6 = new Date(hoy.getTime() - 6 * 86400000);
    cuerpo.innerHTML = `
      <div class="tarjeta">
        <label for="tc-tienda">Sucursal</label><select id="tc-tienda">${opcionesTiendas(lista, esAdmin ? '' : s.codti, esAdmin)}</select>
        <label for="tc-desde">Desde</label><input id="tc-desde" type="date" value="${fechaLocalISO(hace6).slice(0, 10)}">
        <label for="tc-hasta">Hasta</label><input id="tc-hasta" type="date" value="${tareaHoyISO()}" max="${tareaHoyISO()}">
        <button id="tc-ver" class="btn btn-azul" style="margin-top:12px;">${ic('search')} Ver</button>
      </div>
      <div id="tc-datos"><div class="vacio">Cargando...</div></div>`;
    const ver = async () => {
      const datos = document.getElementById('tc-datos');
      const q = new URLSearchParams();
      const codti = document.getElementById('tc-tienda').value;
      if (codti) q.set('codti', codti);
      q.set('desde', document.getElementById('tc-desde').value);
      q.set('hasta', document.getElementById('tc-hasta').value);
      datos.innerHTML = '<div class="vacio">Cargando...</div>';
      try {
        const c = await api('GET', '/api/tareas/cumplimiento?' + q.toString());
        if (!c.tiendas.length) { datos.innerHTML = '<div class="vacio">No hay tareas en esas fechas.</div>'; return; }
        datos.innerHTML = c.tiendas.map(t => `
          <div class="tarjeta">
            <h2 style="margin-top:0;">${escapar(t.nombre)}</h2>
            <div class="kp-h">
              ${kpiH('c4', 'circle-check', t.pctHechas == null ? '—' : t.pctHechas + ' %', 'Hechas')}
              ${kpiH('c1', 'clock', t.pctATiempo == null ? '—' : t.pctATiempo + ' %', 'A tiempo')}
              ${kpiH('c3', 'triangle-alert', t.noHechas, 'No hechas')}
            </div>
            <div class="ayuda">${t.hechas} de ${t.total} tareas hechas (${t.aTiempo} a tiempo)${t.porRevisar ? ' · ' + t.porRevisar + ' esperan revisión' : ''}${t.pendientesHoy ? ' · ' + t.pendientesHoy + ' abiertas hoy, que aún no cuentan' : ''}.</div>
          </div>`).join('') +
          (c.personas.length ? `<div class="tarjeta"><h2 style="margin-top:0;">Quién las hizo</h2>${c.personas.map(p =>
            `<div class="orden-cab" style="padding:6px 0;"><span>${escapar(p.nombre)}</span><span><strong>${p.hechas}</strong>${p.tarde ? ` <span class="ayuda">(${p.tarde} fuera de hora)</span>` : ''}</span></div>`).join('')}</div>` : '');
      } catch (err) {
        datos.innerHTML = `<div class="mensaje error">${escapar(errorDe(err))}</div>`;
      }
    };
    document.getElementById('tc-ver').onclick = ver;
    ver();
  }

  // ── Rutinas ──────────────────────────────────────────────────────────────
  async function dibujarRutinas() {
    const lista = await cargarTiendas();
    if (activa !== 'rutinas') return;
    cuerpo.innerHTML = `
      <div class="tarjeta">
        <label for="tu-tienda">Sucursal</label><select id="tu-tienda">${opcionesTiendas(lista, '', true)}</select>
        <button id="tu-nueva" class="btn btn-azul" style="margin-top:12px;">${ic('plus')} Nueva actividad</button>
      </div>
      <div id="tu-form"></div>
      <div id="tu-lista"><div class="vacio">Cargando...</div></div>`;
    let rutinas = [];
    const recargar = async () => {
      const cont = document.getElementById('tu-lista');
      const codti = document.getElementById('tu-tienda').value;
      try {
        rutinas = await api('GET', '/api/tareas/rutinas' + (codti ? '?codti=' + codti : ''));
        cont.innerHTML = rutinas.length === 0 ? '<div class="vacio">Todavía no hay actividades. Pulsa «Nueva actividad».</div>' : rutinas.map(r => `
          <div class="tarjeta" ${r.activa ? '' : 'style="opacity:.6;"'}>
            <div class="orden-cab"><strong>${escapar(r.titulo)}</strong><span class="pastilla ${r.activa ? 'recibida' : 'pendiente'}">${r.activa ? escapar(r.momentoEtiqueta) : 'Inactiva'}</span></div>
            <div class="ayuda">${escapar(r.nombreTienda || 'Todas las sucursales')} · ${escapar(r.momentoEtiqueta)} · ${escapar(tareaTextoRol(r.rolDestino))}</div>
            <div class="ayuda">${escapar(tareaTextoDias(r.dias))}${r.horaLimite ? ' · antes de ' + escapar(r.horaLimite) : ''}${r.requiereRevision ? ' · se revisa' : ''}${r.requiereFoto ? ' · pide foto' : ''}</div>
            <div class="grupo-botones">
              <button class="btn btn-gris btn-chico tu-editar" data-id="${r.idrutina}">${ic('pencil')} Editar</button>
              ${r.activa ? `<button class="btn btn-gris btn-chico tu-subir" data-id="${r.idrutina}">▲</button>
              <button class="btn btn-gris btn-chico tu-bajar" data-id="${r.idrutina}">▼</button>
              <button class="btn btn-rojo btn-chico tu-quitar" data-id="${r.idrutina}">${ic('trash-2')} Quitar</button>` : ''}
            </div>
          </div>`).join('');
        const por = (id) => rutinas.find(r => String(r.idrutina) === String(id));
        cont.querySelectorAll('.tu-editar').forEach(b => b.onclick = () => formulario(por(b.dataset.id)));
        const accion = (clase, fn) => cont.querySelectorAll(clase).forEach(b => b.onclick = async () => {
          b.disabled = true;
          try { await fn(por(b.dataset.id)); } catch (err) { alert(errorDe(err)); }
          recargar();
        });
        accion('.tu-subir', r => api('POST', `/api/tareas/rutinas/${r.idrutina}/mover?direccion=-1`));
        accion('.tu-bajar', r => api('POST', `/api/tareas/rutinas/${r.idrutina}/mover?direccion=1`));
        accion('.tu-quitar', async r => {
          if (confirm(`«${r.titulo}» dejará de generarse desde hoy. Lo que ya se hizo se conserva.\n\n¿Quitarla?`)) await api('DELETE', '/api/tareas/rutinas/' + r.idrutina);
        });
      } catch (err) {
        cont.innerHTML = `<div class="mensaje error">${escapar(errorDe(err))}</div>`;
      }
    };

    function formulario(r) {
      const sel = document.getElementById('tu-tienda').value;
      const codtiPrevio = r ? r.codti : (sel ? Number(sel) : null);
      document.getElementById('tu-form').innerHTML = `
        <div class="tarjeta">
          <h2 style="margin-top:0;">${r ? 'Editar actividad' : 'Nueva actividad'}</h2>
          <label for="tf-tienda">Sucursal</label><select id="tf-tienda">${opcionesTiendas(lista, codtiPrevio, true)}</select>
          <label class="obligatorio" for="tf-titulo">Actividad</label><input id="tf-titulo" maxlength="150" placeholder="Por ejemplo: Abrir la caja" value="${escapar(r ? r.titulo : '')}">
          <label for="tf-desc">Detalle</label><textarea id="tf-desc" rows="2" maxlength="500" placeholder="Opcional">${escapar(r && r.descripcion ? r.descripcion : '')}</textarea>
          <label for="tf-momento">Momento</label><select id="tf-momento">${TAREAS_MOMENTOS.map(([k, n]) => `<option value="${k}" ${r && r.momento === k ? 'selected' : ''}>${n}</option>`).join('')}</select>
          <label for="tf-rol">Le toca a</label><select id="tf-rol">${TAREAS_ROLES.map(([k, n]) => `<option value="${k}" ${r && (r.rolDestino || '') === k ? 'selected' : ''}>${n}</option>`).join('')}</select>
          <label>Días</label>
          <div class="grupo-botones">${TAREAS_DIAS.map((n, i) => `<label style="display:flex;gap:4px;align-items:center;font-weight:600;"><input type="checkbox" class="tf-dia" value="${i + 1}" style="width:auto;" ${!r || (r.dias || []).includes(i + 1) ? 'checked' : ''}> ${n}</label>`).join('')}</div>
          <label for="tf-hora">Hora límite</label><input id="tf-hora" type="time" value="${escapar(r && r.horaLimite ? r.horaLimite : '')}">
          <label style="display:flex;gap:8px;align-items:center;"><input type="checkbox" id="tf-rev" style="width:auto;" ${r && r.requiereRevision ? 'checked' : ''}> Requiere que el encargado la revise</label>
          <label style="display:flex;gap:8px;align-items:center;"><input type="checkbox" id="tf-foto" style="width:auto;" ${r && r.requiereFoto ? 'checked' : ''}> Pide una foto de evidencia</label>
          ${r ? `<label style="display:flex;gap:8px;align-items:center;"><input type="checkbox" id="tf-activa" style="width:auto;" ${r.activa ? 'checked' : ''}> Activa</label>` : ''}
          <div id="tf-error"></div>
          <div class="grupo-botones">
            <button id="tf-guardar" class="btn btn-verde">${ic('save')} Guardar</button>
            <button id="tf-cancelar" class="btn btn-gris">Cancelar</button>
          </div>
        </div>`;
      document.getElementById('tf-cancelar').onclick = () => { document.getElementById('tu-form').innerHTML = ''; };
      document.getElementById('tf-guardar').onclick = async () => {
        const dias = [...document.querySelectorAll('.tf-dia')].filter(c => c.checked).map(c => Number(c.value));
        const titulo = document.getElementById('tf-titulo').value.trim();
        const falla = document.getElementById('tf-error');
        if (!titulo) { falla.innerHTML = '<div class="mensaje error">Escribe qué hay que hacer.</div>'; return; }
        if (!dias.length) { falla.innerHTML = '<div class="mensaje error">Elige al menos un día de la semana.</div>'; return; }
        const cuerpoReq = {
          codti: document.getElementById('tf-tienda').value ? Number(document.getElementById('tf-tienda').value) : null,
          titulo, descripcion: document.getElementById('tf-desc').value.trim() || null,
          momento: document.getElementById('tf-momento').value, rolDestino: document.getElementById('tf-rol').value || null,
          dias, horaLimite: document.getElementById('tf-hora').value || null,
          activa: r ? document.getElementById('tf-activa').checked : true, orden: r ? r.orden : null,
          requiereRevision: document.getElementById('tf-rev').checked, requiereFoto: document.getElementById('tf-foto').checked,
        };
        const boton = document.getElementById('tf-guardar');
        boton.disabled = true;
        try {
          await (r ? api('PUT', '/api/tareas/rutinas/' + r.idrutina, cuerpoReq) : api('POST', '/api/tareas/rutinas', cuerpoReq));
          document.getElementById('tu-form').innerHTML = '';
          recargar();
        } catch (err) {
          boton.disabled = false;
          falla.innerHTML = `<div class="mensaje error">${escapar(errorDe(err))}</div>`;
        }
      };
    }
    document.getElementById('tu-nueva').onclick = () => formulario(null);
    document.getElementById('tu-tienda').onchange = recargar;
    recargar();
  }

  mostrar('mis');
}
