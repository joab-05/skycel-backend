/**
 * Colas de lo hecho sin conexión, guardadas en este dispositivo (IndexedDB). Cada registro se manda con una
 * clave única (claveOffline): reenviarlo no lo duplica en el servidor. Un almacén por tipo de operación
 * (recepciones de equipo, abonos a cuentas por cobrar...).
 */
const DB = (() => {
  const NOMBRE = 'skycel_web';
  const VERSION = 2;
  const ALMACENES = ['recepciones_pendientes', 'abonos_pendientes'];

  function abrir() {
    return new Promise((resolve, reject) => {
      const req = indexedDB.open(NOMBRE, VERSION);
      req.onupgradeneeded = () => {
        const db = req.result;
        for (const almacen of ALMACENES) {
          if (!db.objectStoreNames.contains(almacen)) db.createObjectStore(almacen, { keyPath: 'clave' });
        }
      };
      req.onsuccess = () => resolve(req.result);
      req.onerror = () => reject(req.error);
    });
  }

  async function put(almacen, item) {
    const db = await abrir();
    return new Promise((resolve, reject) => {
      const tx = db.transaction(almacen, 'readwrite');
      tx.objectStore(almacen).put(item);
      tx.oncomplete = () => resolve();
      tx.onerror = () => reject(tx.error);
    });
  }

  async function eliminar(almacen, clave) {
    const db = await abrir();
    return new Promise((resolve, reject) => {
      const tx = db.transaction(almacen, 'readwrite');
      tx.objectStore(almacen).delete(clave);
      tx.oncomplete = () => resolve();
      tx.onerror = () => reject(tx.error);
    });
  }

  async function todas(almacen) {
    try {
      const db = await abrir();
      return await new Promise((resolve, reject) => {
        const tx = db.transaction(almacen, 'readonly');
        const req = tx.objectStore(almacen).getAll();
        req.onsuccess = () => resolve(req.result || []);
        req.onerror = () => reject(req.error);
      });
    } catch {
      return [];
    }
  }

  return { put, eliminar, todas };
})();
