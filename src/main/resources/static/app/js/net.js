/**
 * Estado de la conexión con el servidor de Skycel (no del internet en general) y sincronización de lo
 * guardado en este dispositivo sin conexión. Igual que en JSystem: se prueba /api/publico/salud cada tanto.
 *
 * Cada tipo de operación que se puede hacer sin conexión tiene su propio almacén en IndexedDB (ver db.js) y
 * su propia forma de reenviarse; COLAS las agrupa para que Net solo tenga que recorrerlas.
 */
const COLAS = [
  {
    almacen: 'recepciones_pendientes',
    async enviar(payload) {
      const orden = await api('POST', '/api/ordenes-servicio', payload);
      return { idorden: orden.idorden, folio: orden.folio };
    },
  },
  {
    almacen: 'abonos_pendientes',
    async enviar(payload) {
      const qs = new URLSearchParams({ monto: String(payload.monto), metodoPago: String(payload.metodoPago) });
      if (payload.idCaja) qs.set('idCaja', String(payload.idCaja));
      if (payload.notas) qs.set('notas', payload.notas);
      if (payload.claveOffline) qs.set('claveOffline', payload.claveOffline);
      if (payload.fechaPago) qs.set('fechaPago', payload.fechaPago);
      await api('POST', `/api/cuentas-por-cobrar/${payload.idcuenta}/pago?${qs.toString()}`, undefined);
      return {};
    },
  },
];

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
      for (const cola of COLAS) {
        const pendientes = (await DB.todas(cola.almacen))
          .filter(it => it.estado !== 'enviada')
          .sort((a, b) => a.creado.localeCompare(b.creado));
        for (const it of pendientes) {
          if (it.estado !== 'pendiente') continue;
          try {
            const resultado = await cola.enviar(it.payload);
            it.estado = 'enviada';
            Object.assign(it, resultado);
            it.error = null;
            await DB.put(cola.almacen, it);
            notificar();
          } catch (err) {
            if (err.network) break; // se sigue intentando solo; no se pierde nada
            if (err.auth) break;    // hace falta iniciar sesión de nuevo; se reintenta cuando vuelva a entrar
            it.estado = 'error';
            it.error = err.message;
            await DB.put(cola.almacen, it);
            notificar();
          }
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
    let total = 0;
    for (const cola of COLAS) total += (await DB.todas(cola.almacen)).filter(i => i.estado === 'pendiente').length;
    return total;
  }
  async function cantidadConError() {
    let total = 0;
    for (const cola of COLAS) total += (await DB.todas(cola.almacen)).filter(i => i.estado === 'error').length;
    return total;
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
