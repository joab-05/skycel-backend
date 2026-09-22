/**
 * Estado de la conexión con el servidor de Skycel (no del internet en general) y sincronización de la cola de
 * recepciones guardadas en este dispositivo. Igual que en JSystem: se prueba /api/publico/salud cada tanto.
 */
const Net = (() => {
  let enLinea = navigator.onLine;
  let sincronizando = false;
  const oyentes = [];
  let temporizador = null;

  function agregarOyente(f) { oyentes.push(f); }
  function notificar() { for (const f of oyentes) { try { f(); } catch (e) { console.error(e); } } }

  async function probar() {
    try {
      const controlador = typeof AbortSignal !== 'undefined' && AbortSignal.timeout ? AbortSignal.timeout(4000) : undefined;
      const res = await fetch('/api/publico/salud', { cache: 'no-store', signal: controlador });
      enLinea = res.ok;
    } catch {
      enLinea = false;
    }
    notificar();
    if (enLinea) sincronizar();
    programar();
  }

  function programar() {
    if (temporizador) clearTimeout(temporizador);
    temporizador = setTimeout(probar, enLinea ? 15000 : 6000);
  }

  async function sincronizar() {
    if (sincronizando) return;
    sincronizando = true;
    try {
      const pendientes = (await DB.todas())
        .filter(it => it.estado !== 'enviada')
        .sort((a, b) => a.creado.localeCompare(b.creado));
      for (const it of pendientes) {
        try {
          const orden = await api('POST', '/api/ordenes-servicio', it.payload);
          it.estado = 'enviada';
          it.idorden = orden.idorden;
          it.folio = orden.folio;
          it.error = null;
          await DB.put(it);
          notificar();
        } catch (err) {
          if (err.network) break; // se sigue intentando solo; no se pierde nada
          if (err.auth) break;    // hace falta iniciar sesión de nuevo; se reintenta cuando vuelva a entrar
          it.estado = 'error';
          it.error = err.message;
          await DB.put(it);
          notificar();
        }
      }
    } finally {
      sincronizando = false;
    }
  }

  function iniciar() {
    window.addEventListener('online', probar);
    window.addEventListener('offline', () => { enLinea = false; notificar(); });
    probar();
  }

  async function cantidadPendiente() {
    const items = await DB.todas();
    return items.filter(i => i.estado === 'pendiente').length;
  }
  async function cantidadConError() {
    const items = await DB.todas();
    return items.filter(i => i.estado === 'error').length;
  }

  return {
    agregarOyente,
    iniciar,
    sincronizar,
    enLinea: () => enLinea,
    cantidadPendiente,
    cantidadConError,
  };
})();
