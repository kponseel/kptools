/* Service worker — coquille d'app en cache (stale-while-revalidate).
   Les appels TMDB et les images (autres origines) ne sont pas interceptés. */
const CACHE = "orq-shell-v1";
const ASSETS = ["index.html", "data.js", "manifest.json", "icon-192.png", "icon-512.png", "apple-touch-icon.png"];

self.addEventListener("install", e => {
  e.waitUntil(caches.open(CACHE).then(c => c.addAll(ASSETS)).then(() => self.skipWaiting()));
});

self.addEventListener("activate", e => {
  e.waitUntil(
    caches.keys()
      .then(keys => Promise.all(keys.filter(k => k !== CACHE).map(k => caches.delete(k))))
      .then(() => self.clients.claim())
  );
});

self.addEventListener("fetch", e => {
  const req = e.request;
  if (req.method !== "GET") return;
  if (new URL(req.url).origin !== location.origin) return;   // TMDB / affiches : réseau direct
  e.respondWith(
    caches.open(CACHE).then(async c => {
      const cached = await c.match(req);
      const network = fetch(req).then(res => { c.put(req, res.clone()).catch(() => {}); return res; }).catch(() => cached);
      return cached || network;
    })
  );
});
