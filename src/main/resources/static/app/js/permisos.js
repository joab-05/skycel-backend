/**
 * Permisos (root, o quien root autorice): qué puede hacer cada tipo de usuario. Cada casilla marca si ese rol puede hacer esa acción; el cambio se guarda
 * al instante y vale para JSystem y para la web. Root siempre puede todo y no se configura. Igual que «Permisos» de JSystem.
 */

const PERMISOS_ROL_NOMBRE = { ADMIN: 'Administrador', ENCARGADO_TIENDA: 'Encargado', VENDEDOR: 'Vendedor', TECNICO: 'Técnico' };

async function pantallaPermisos() {
  const s = Sesion.obtener();
  if (!Sesion.tiene('PERMISOS_ADMIN')) { root.innerHTML = '<div class="tarjeta">Solo root, o quien root autorice, puede cambiar los permisos.</div>'; return; }
  root.innerHTML = `
    ${cabeceraPantalla(ic('lock') + ' Permisos', 'Qué puede hacer cada tipo de usuario')}
    <div class="mensaje info">Marca lo que puede hacer cada rol. El cambio se guarda al instante; la persona lo ve la próxima vez que abra o recargue la pantalla.</div>
    <div id="pm-lista"><div class="vacio">Cargando...</div></div>`;
  let cat;
  try { cat = await api('GET', '/api/permisos/catalogo'); }
  catch (err) { document.getElementById('pm-lista').innerHTML = `<div class="mensaje error">${escapar(err.network ? 'Sin conexión con el servidor.' : err.message)}</div>`; return; }
  const roles = cat.roles;
  const grupos = [...new Set(cat.permisos.map(p => p.grupo))];
  document.getElementById('pm-lista').innerHTML = grupos.map(g => `
    <div class="tarjeta">
      <h2 style="margin-top:0;">${escapar(g)}</h2>
      <div class="tabla-cont"><table class="tabla">
        <thead><tr><th>Acción</th>${roles.map(r => `<th style="text-align:center;">${escapar(PERMISOS_ROL_NOMBRE[r] || r)}</th>`).join('')}</tr></thead>
        <tbody>${cat.permisos.filter(p => p.grupo === g).map(p => `
          <tr>
            <td class="tabla-nombre">${escapar(p.etiqueta)}</td>
            ${roles.map(r => `<td style="text-align:center;"><input type="checkbox" class="pm-casilla" data-codigo="${p.codigo}" data-rol="${r}" ${p.roles.includes(r) ? 'checked' : ''} style="width:auto;"></td>`).join('')}
          </tr>`).join('')}</tbody>
      </table></div>
    </div>`).join('');

  document.querySelectorAll('.pm-casilla').forEach(c => c.onchange = async () => {
    const codigo = c.dataset.codigo;
    const marcados = [...document.querySelectorAll(`.pm-casilla[data-codigo="${codigo}"]`)].filter(x => x.checked).map(x => x.dataset.rol);
    c.disabled = true;
    try {
      await api('PUT', `/api/permisos/${codigo}`, { roles: marcados });
      mostrarMensaje(root, 'Guardado.', 'ok');
    } catch (err) {
      c.checked = !c.checked;   // no se guardó: se deja como estaba
      mostrarMensaje(root, err.network ? 'Sin conexión con el servidor.' : err.message, 'error');
    }
    c.disabled = false;
  });
}
