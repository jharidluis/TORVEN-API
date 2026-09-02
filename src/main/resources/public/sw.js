// Sube este numero en cada deploy que toque index.html/app.js/styles.css:
// el navegador solo reinstala el service worker (y refresca el cache) si este
// archivo cambia de contenido, aunque los otros archivos si hayan cambiado.
const CACHE_NAME = 'torven-shell-v7';
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

  // Los datos (login, productos, lugares de entrega, ventas) siempre van a la
  // red: nunca deben servirse desde cache, porque el stock y los precios cambian.
  if (url.pathname.startsWith('/api/')) {
    return;
  }

  // El resto (index.html, app.js, styles.css, iconos) va primero a la red
  // para que cada deploy se vea de inmediato, sin depender de que el
  // usuario borre datos del sitio. Si no hay conexion, se usa el cache
  // como respaldo para que la app siga abriendo.
  evento.respondWith(
    fetch(evento.request).then((respuesta) => {
      const copia = respuesta.clone();
      caches.open(CACHE_NAME).then((cache) => cache.put(evento.request, copia));
      return respuesta;
    }).catch(() => caches.match(evento.request))
  );
});
