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
function tareaTextoDias(dias) {
  if (!dias || !dias.length) return '';
  if (dias.length === 7) return 'Todos los días';
  return dias.map(d => TAREAS_DIAS[d - 1]).join(', ');
}
/** [texto, clase de pastilla] del estado de una tarea. */
function tareaEstado(t) {
  if (t.estado === 'HECHA') return t.tarde ? ['Hecha (tarde)', 'reparacion'] : ['Hecha', 'lista'];
  return t.atrasada ? ['Atrasada', 'cancelada'] : ['Pendiente', 'pendiente'];
}

async function pantallaTareas() {
  const s = Sesion.obtener();
  const veEquipo = Sesion.tiene('TAREAS_EQUIPO');
  const armaRutinas = Sesion.tiene('TAREAS_RUTINAS');
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
    const grupos = TAREAS_MOMENTOS.map(([clave, nombre]) => {
      const lista = d.tareas.filter(t => t.momento === clave);
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
  }

  function filaMia(t) {
    const etiquetas = (t.horaLimite ? `<span class="pastilla ${t.atrasada ? 'cancelada' : 'pendiente'}">Antes de ${escapar(t.horaLimite)}</span> ` : '') +
      (t.rolDestino ? `<span class="pastilla recibida">${escapar(tareaTextoRol(t.rolDestino))}</span>` : '');
    const desc = t.descripcion ? `<div class="ayuda">${escapar(t.descripcion)}</div>` : '';
    if (t.estado === 'HECHA') {
      const mia = t.idusuarioHecha != null && t.idusuarioHecha === s.idusuario;
      return `<div class="tr-fila" style="padding:10px 0; border-top:1px solid var(--borde);">
        <div><strong style="color:var(--gris);">✔ ${escapar(t.titulo)}</strong> ${etiquetas}</div>${desc}
        <div class="ayuda" style="color:${t.tarde ? '#d97706' : '#059669'};font-weight:700;">Hecha por ${escapar(t.hechaPor)} a las ${escapar(tareaHora(t.hechaEl))}${t.tarde ? ' · fuera de hora' : ''}</div>
        ${t.nota ? `<div>Nota: ${escapar(t.nota)}</div>` : ''}
        ${mia || veEquipo ? `<button class="btn btn-gris btn-chico tr-deshacer" data-id="${t.idtarea}" style="margin-top:6px;">Deshacer</button>` : ''}
      </div>`;
    }
    return `<div class="tr-fila" style="padding:10px 0; border-top:1px solid var(--borde);">
      <label style="display:flex;gap:12px;align-items:flex-start;font-size:16px;font-weight:700;">
        <input type="checkbox" class="tr-hecha" data-id="${t.idtarea}" style="width:26px;height:26px;margin-top:0;flex:none;">
        <span>${escapar(t.titulo)} ${etiquetas}</span>
      </label>${desc}
      <input class="tr-nota" data-id="${t.idtarea}" placeholder="Nota (opcional)" maxlength="500" style="margin-top:6px;">
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
      </div>
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
            ${kpiH('c5', 'clock', d.pendientes, 'Pendientes')}
            ${kpiH('c3', 'triangle-alert', d.atrasadas, 'Atrasadas')}
          </div>
          ${d.tareas.length === 0 ? '<div class="vacio">Sin tareas ese día.</div>' : d.tareas.map(t => {
            const [txt, clase] = tareaEstado(t);
            return `<div class="tarjeta">
              <div class="orden-cab"><strong>${escapar(t.titulo)}</strong><span class="pastilla ${clase}">${txt}</span></div>
              <div class="ayuda">${escapar(t.momentoEtiqueta)} · ${escapar(tareaTextoRol(t.rolDestino))}${t.horaLimite ? ' · antes de ' + escapar(t.horaLimite) : ''}</div>
              ${t.hechaPor ? `<div>${escapar(t.hechaPor)} · ${escapar(tareaHora(t.hechaEl))}</div>` : ''}
              ${t.nota ? `<div>Nota: ${escapar(t.nota)}</div>` : ''}
              ${t.estado === 'HECHA' && t.fecha === tareaHoyISO() ? `<button class="btn btn-gris btn-chico te-deshacer" data-id="${t.idtarea}" style="margin-top:6px;">Deshacer la marca</button>` : ''}
            </div>`;
          }).join('')}`;
        datos.querySelectorAll('.te-deshacer').forEach(b => b.onclick = async () => {
          b.disabled = true;
          try { await api('POST', `/api/tareas/${b.dataset.id}/deshacer`); } catch (err) { alert(errorDe(err)); }
          ver();
        });
      } catch (err) {
        datos.innerHTML = `<div class="mensaje error">${escapar(errorDe(err))}</div>`;
      }
    };
    document.getElementById('te-tienda').onchange = ver;
    document.getElementById('te-fecha').onchange = ver;
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
            <div class="ayuda">${t.hechas} de ${t.total} tareas hechas (${t.aTiempo} a tiempo)${t.pendientesHoy ? ' · ' + t.pendientesHoy + ' abiertas hoy, que aún no cuentan' : ''}.</div>
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
            <div class="ayuda">${escapar(tareaTextoDias(r.dias))}${r.horaLimite ? ' · antes de ' + escapar(r.horaLimite) : ''}</div>
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
