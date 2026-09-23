/**
 * Service worker de la web de Skycel. Solo cachea el "cascarón" de la app (html/css/js/íconos) para que abra
 * sin conexión; los datos (todo /api/...) siempre van a la red — nunca se sirven de la caché, para no mostrar
 * información vieja o mandar peticiones fuera de línea que parezcan haber funcionado.
 */
const CACHE = 'skycel-app-v2';
const ARCHIVOS = [
  '/app', '/app/',
  '/app/index.html',
  '/app/css/app.css',
  '/app/js/db.js',
  '/app/js/api.js',
  '/app/js/net.js',
  '/app/js/app.js',
  '/app/icons/icon-192.png',
  '/app/icons/icon-512.png',
  '/manifest.webmanifest',
];

self.addEventListener('install', event => {
  event.waitUntil(caches.open(CACHE).then(c => c.addAll(ARCHIVOS)).then(() => self.skipWaiting()));
});

self.addEventListener('activate', event => {
  event.waitUntil(
    caches.keys()
      .then(nombres => Promise.all(nombres.filter(n => n !== CACHE).map(n => caches.delete(n))))
      .then(() => self.clients.claim())
  );
});

self.addEventListener('fetch', event => {
  const url = new URL(event.request.url);
  if (event.request.method !== 'GET') return;          // POST/PATCH/etc. van directo a la red
  if (url.pathname.startsWith('/api/')) return;         // nunca cachear datos del servidor

  event.respondWith(
    caches.match(event.request).then(cacheada => {
      const red = fetch(event.request).then(res => {
        if (res.ok) caches.open(CACHE).then(c => c.put(event.request, res.clone()));
        return res;
      }).catch(() => cacheada);
      return cacheada || red;
    })
  );
});
