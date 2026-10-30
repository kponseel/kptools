/* Service worker de « Vocaux ».
   1. Reçoit les fichiers audio partagés depuis WhatsApp (share target, POST multipart)
      et les met dans IndexedDB, puis ouvre l'app.
   2. Garde la coquille de l'app en cache pour l'usage hors ligne.
   3. Garde aussi les librairies du CDN (versions figées : transformers.js, ffmpeg, SDK Claude).
   Le modèle Whisper (Hugging Face) n'est pas intercepté : transformers.js gère son cache. */
importScripts("db.js");

const CACHE = "vocaux-shell-v6";
const CDN_CACHE = "vocaux-cdn-v1";
const ASSETS = ["./", "index.html", "app.js", "worker.js", "db.js", "manifest.json",
                "icon-192.png", "icon-512.png", "icon-maskable.png", "apple-touch-icon.png",
                "vendor/ffmpeg/index.js", "vendor/ffmpeg/classes.js", "vendor/ffmpeg/const.js",
                "vendor/ffmpeg/errors.js", "vendor/ffmpeg/types.js", "vendor/ffmpeg/utils.js",
                "vendor/ffmpeg/worker.js"];

self.addEventListener("install", e => {
  e.waitUntil(caches.open(CACHE).then(c => c.addAll(ASSETS)).then(() => self.skipWaiting()));
});

self.addEventListener("activate", e => {
  e.waitUntil(
    caches.keys()
      .then(keys => Promise.all(keys.filter(k => k.startsWith("vocaux-") && k !== CACHE && k !== CDN_CACHE).map(k => caches.delete(k))))
      .then(() => self.clients.claim())
  );
});

async function handleShare(request) {
  try {
    const form = await request.formData();
    const files = form.getAll("audio").filter(f => f && typeof f !== "string");
    for (const f of files) {
      await dbPut("inbox", { name: f.name || "vocal.opus", type: f.type, blob: f, receivedAt: Date.now() });
    }
  } catch (err) {
    // On ouvre l'app quand même ; elle affichera une liste vide.
  }
  return Response.redirect(new URL("./?shared=1", self.registration.scope).href, 303);
}

self.addEventListener("fetch", e => {
  const req = e.request;
  const url = new URL(req.url);
  if (req.method === "POST" && url.pathname.endsWith("/share-target")) {
    e.respondWith(handleShare(req));
    return;
  }
  if (req.method !== "GET") return;
  // Fichiers du CDN avec un numéro de version précis : ils ne changent jamais, donc cache d'abord.
  if (url.hostname === "cdn.jsdelivr.net" && /@\d+\.\d+\.\d+/.test(url.pathname)) {
    e.respondWith(
      caches.open(CDN_CACHE).then(async c => {
        const cached = await c.match(req);
        if (cached) return cached;
        const res = await fetch(req);
        if (res.ok && res.type !== "opaque") c.put(req, res.clone()).catch(() => {});
        return res;
      })
    );
    return;
  }
  if (url.origin !== location.origin) return;
  e.respondWith(
    caches.open(CACHE).then(async c => {
      const cached = await c.match(req, { ignoreSearch: req.mode === "navigate" });
      const network = fetch(req)
        .then(res => { if (res.ok) c.put(req, res.clone()).catch(() => {}); return res; })
        .catch(() => cached);
      return cached || network;
    })
  );
});
