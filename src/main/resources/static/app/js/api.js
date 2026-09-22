/**
 * Sesión (token JWT) y llamadas a la API. La página se sirve desde el mismo servidor (mismo origen), así que
 * las rutas son relativas.
 */
const Sesion = (() => {
  const CLAVE = 'skycel_web_session';

  function obtener() {
    try { return JSON.parse(localStorage.getItem(CLAVE)); } catch { return null; }
  }
  function guardar(s) { localStorage.setItem(CLAVE, JSON.stringify(s)); }
  function limpiar() { localStorage.removeItem(CLAVE); }
  function activa() { return !!obtener()?.token; }

  return { obtener, guardar, limpiar, activa };
})();

/** true si el modelo de datos habla de "éxito" (StandardApiResponse envuelto); si no, se usa el JSON tal cual. */
function desenvolver(json) {
  if (json && typeof json === 'object' && typeof json.success === 'boolean') return json.data;
  return json;
}

class ApiError extends Error {
  constructor(message, opts = {}) {
    super(message);
    this.network = !!opts.network;
    this.auth = !!opts.auth;
    this.status = opts.status;
  }
}

async function api(method, ruta, cuerpo) {
  const s = Sesion.obtener();
  const headers = { 'Content-Type': 'application/json' };
  if (s && s.token) headers['Authorization'] = 'Bearer ' + s.token;

  let res;
  try {
    const controlador = typeof AbortSignal !== 'undefined' && AbortSignal.timeout ? AbortSignal.timeout(10000) : undefined;
    res = await fetch(ruta, {
      method,
      headers,
      body: cuerpo !== undefined ? JSON.stringify(cuerpo) : undefined,
      signal: controlador,
    });
  } catch (err) {
    throw new ApiError('Sin conexión con el servidor', { network: true });
  }

  if (res.status === 401) {
    Sesion.limpiar();
    throw new ApiError('La sesión expiró: inicia sesión de nuevo', { auth: true, status: 401 });
  }

  let json = null;
  try { json = await res.json(); } catch { /* respuesta vacía, p. ej. 204 */ }

  if (!res.ok) {
    const msg = (json && (json.message || json.mensaje || json.error)) || ('Error del servidor (' + res.status + ')');
    throw new ApiError(msg, { status: res.status });
  }
  return desenvolver(json);
}
