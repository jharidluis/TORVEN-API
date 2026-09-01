// Sube este numero en cada deploy que toque index.html/app.js/styles.css:
// el navegador solo reinstala el service worker (y refresca el cache) si este
// archivo cambia de contenido, aunque los otros archivos si hayan cambiado.
const CACHE_NAME = 'torven-shell-v3';
const ARCHIVOS_SHELL = [
  '/',
  '/index.html',
  '/styles.css',
  '/app.js',
  '/manifest.json',
  '/icons/icon-192.png',
  '/icons/icon-512.png'
];

self.addEventListener('install', (evento) => {
  evento.waitUntil(
    caches.open(CACHE_NAME).then((cache) => cache.addAll(ARCHIVOS_SHELL))
  );
  self.skipWaiting();
});

self.addEventListener('activate', (evento) => {
  evento.waitUntil(
    caches.keys().then((nombres) =>
      Promise.all(nombres.filter((n) => n !== CACHE_NAME).map((n) => caches.delete(n)))
    )
  );
  self.clients.claim();
});

self.addEventListener('fetch', (evento) => {
  const url = new URL(evento.request.url);

  // Los datos (login, productos, clientes, ventas) siempre van a la red:
  // nunca deben servirse desde cache, porque el stock y los precios cambian.
  if (url.pathname.startsWith('/api/')) {
    return;
  }

  evento.respondWith(
    caches.match(evento.request).then((enCache) => enCache || fetch(evento.request))
  );
});
