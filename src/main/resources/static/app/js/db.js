/**
 * Cola de recepciones hechas sin conexión, guardada en este dispositivo (IndexedDB). Cada recepción se manda
 * con una clave única (claveOffline): reenviarla no duplica la orden en el servidor.
 */
const DB = (() => {
  const NOMBRE = 'skycel_web';
  const ALMACEN = 'recepciones_pendientes';

  function abrir() {
    return new Promise((resolve, reject) => {
      const req = indexedDB.open(NOMBRE, 1);
      req.onupgradeneeded = () => {
        const db = req.result;
        if (!db.objectStoreNames.contains(ALMACEN)) {
          db.createObjectStore(ALMACEN, { keyPath: 'clave' });
        }
      };
      req.onsuccess = () => resolve(req.result);
      req.onerror = () => reject(req.error);
    });
  }

  async function put(item) {
    const db = await abrir();
    return new Promise((resolve, reject) => {
      const tx = db.transaction(ALMACEN, 'readwrite');
      tx.objectStore(ALMACEN).put(item);
      tx.oncomplete = () => resolve();
      tx.onerror = () => reject(tx.error);
    });
  }

  async function eliminar(clave) {
    const db = await abrir();
    return new Promise((resolve, reject) => {
      const tx = db.transaction(ALMACEN, 'readwrite');
      tx.objectStore(ALMACEN).delete(clave);
      tx.oncomplete = () => resolve();
      tx.onerror = () => reject(tx.error);
    });
  }

  async function todas() {
    try {
      const db = await abrir();
      return await new Promise((resolve, reject) => {
        const tx = db.transaction(ALMACEN, 'readonly');
        const req = tx.objectStore(ALMACEN).getAll();
        req.onsuccess = () => resolve(req.result || []);
        req.onerror = () => reject(req.error);
      });
    } catch {
      return [];
    }
  }

  return { put, eliminar, todas };
})();
