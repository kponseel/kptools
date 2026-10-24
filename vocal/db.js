/* Petite couche IndexedDB partagée entre la page et le service worker.
   - "inbox"   : fichiers reçus via « Partager » en attente de transcription
   - "history" : transcriptions terminées (texte, résumé, audio) */
const DB_NAME = "vocaux";
const DB_VERSION = 1;

function openDB() {
  return new Promise((resolve, reject) => {
    const req = indexedDB.open(DB_NAME, DB_VERSION);
    req.onupgradeneeded = () => {
      const db = req.result;
      if (!db.objectStoreNames.contains("inbox")) db.createObjectStore("inbox", { keyPath: "id", autoIncrement: true });
      if (!db.objectStoreNames.contains("history")) db.createObjectStore("history", { keyPath: "id" });
    };
    req.onsuccess = () => resolve(req.result);
    req.onerror = () => reject(req.error);
  });
}

async function dbTx(store, mode, fn) {
  const db = await openDB();
  return new Promise((resolve, reject) => {
    const tx = db.transaction(store, mode);
    const out = fn(tx.objectStore(store));
    tx.oncomplete = () => { db.close(); resolve(out && "result" in out ? out.result : out); };
    tx.onerror = () => { db.close(); reject(tx.error); };
    tx.onabort = () => { db.close(); reject(tx.error); };
  });
}

const dbPut = (store, value) => dbTx(store, "readwrite", s => s.put(value));
const dbDelete = (store, key) => dbTx(store, "readwrite", s => s.delete(key));
const dbGetAll = store => dbTx(store, "readonly", s => s.getAll());
const dbClear = store => dbTx(store, "readwrite", s => s.clear());
