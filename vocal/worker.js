/* Worker de transcription via transformers.js, 100 % dans le navigateur.
   Modèles : Whisper (tiny → large-v3 turbo) et Cohere Transcribe.
   Le modèle est téléchargé une fois depuis Hugging Face puis gardé en cache. */
import { pipeline, env } from "https://cdn.jsdelivr.net/npm/@huggingface/transformers@4.3.1";

env.allowLocalModels = false;

let asr = null;        // pipeline chargé
let loadedKey = "";    // modèle + device du pipeline chargé
let device = "wasm";
let forceWasm = false;  // passe à true si le GPU a échoué une fois

const isCohere = model => /cohere/i.test(model);
// Cohere attend un code de langue court ; il ne sait pas détecter la langue tout seul.
const COHERE_LANG = { french: "fr", english: "en", spanish: "es", portuguese: "pt", italian: "it",
                      german: "de", arabic: "ar", auto: "fr" };

async function gpuInfo() {
  if (forceWasm) return null;
  try {
    const adapter = navigator.gpu && await navigator.gpu.requestAdapter();
    return adapter ? { f16: adapter.features.has("shader-f16") } : null;
  } catch { return null; }
}

// Fichiers de ce modèle déjà enregistrés sur le téléphone (cache de transformers.js).
async function cachedFiles(model) {
  const prefix = `https://huggingface.co/${model}/resolve/main/`;
  try {
    const keys = await (await caches.open("transformers-cache")).keys();
    return new Set(keys.map(r => r.url).filter(u => u.startsWith(prefix)).map(u => decodeURIComponent(u.slice(prefix.length))));
  } catch { return new Set(); }
}

// Regroupe la progression des fichiers du modèle en un seul pourcentage.
// transformers.js signale de la même façon un vrai téléchargement et une simple lecture
// depuis le cache : on regarde nous-mêmes quels fichiers sont déjà sur le téléphone.
let loadedFiles = [];   // fichiers lus pendant le dernier chargement

function progressReporter(cached) {
  const files = new Map();
  loadedFiles = [];
  return p => {
    if (p.file && !loadedFiles.includes(p.file)) loadedFiles.push(p.file);
    if (p.status === "progress" && p.file) {
      files.set(p.file, { loaded: p.loaded || 0, total: p.total || 0, cached: cached.has(p.file) });
      let loaded = 0, total = 0, downloading = false;
      for (const f of files.values()) {
        loaded += f.loaded; total += f.total;
        if (!f.cached && f.loaded < f.total) downloading = true;
      }
      self.postMessage({ type: "load-progress", loaded, total, downloading });
    }
  };
}

async function load(model) {
  const gpu = await gpuInfo();
  const wantGPU = !!gpu;
  const key = model + (wantGPU ? "|webgpu" : "|wasm");
  if (asr && loadedKey === key) { loadedFiles = []; return; }
  if (asr) { try { await asr.dispose(); } catch {} asr = null; }

  const progress_callback = progressReporter(await cachedFiles(model));
  if (isCohere(model)) {
    // 2 milliards de paramètres : trop lourd pour le CPU d'un téléphone, GPU obligatoire.
    if (!wantGPU) throw new Error("Cohere Transcribe a besoin du GPU (WebGPU), indisponible ici. Choisis un modèle Whisper dans les réglages.");
    asr = await pipeline("automatic-speech-recognition", model, { device: "webgpu", dtype: "q4", progress_callback });
    device = "webgpu"; loadedKey = key;
    return;
  }
  if (wantGPU) {
    try {
      asr = await pipeline("automatic-speech-recognition", model, {
        device: "webgpu",
        dtype: { encoder_model: gpu.f16 ? "fp16" : "fp32", decoder_model_merged: "q4" },
        progress_callback,
      });
      device = "webgpu"; loadedKey = key;
      return;
    } catch (err) {
      forceWasm = true;
      self.postMessage({ type: "log", message: "WebGPU indisponible, passage en mode CPU : " + err.message });
    }
  }
  asr = await pipeline("automatic-speech-recognition", model, {
    device: "wasm",
    dtype: "q8",
    progress_callback,
  });
  device = "wasm"; loadedKey = model + "|wasm";
}

// Après un téléchargement, vérifie que les fichiers ont bien été enregistrés sur le téléphone.
// transformers.js ignore silencieusement un échec d'enregistrement (ex. : plus de place),
// ce qui oblige à tout re-télécharger à chaque fois.
async function checkSaved(model) {
  if (!loadedFiles.length) return;            // modèle déjà en mémoire : rien n'a été lu
  const saved = await cachedFiles(model);
  const missing = loadedFiles.filter(f => !saved.has(f));
  if (missing.length) self.postMessage({ type: "not-saved", model, missing });
}

async function run(audio, opts) {
  try {
    return await asr(audio, opts);
  } catch (err) {
    if (device !== "webgpu" || isCohere(loadedKey)) throw err;
    // Le GPU du téléphone a échoué en cours de route : on recommence sur le CPU.
    forceWasm = true;
    self.postMessage({ type: "log", message: "Échec GPU, nouvel essai sur CPU : " + err.message });
    await load(loadedKey.split("|")[0]);
    return await asr(audio, opts);
  }
}

async function handle(msg) {
  try {
    if (msg.type === "load") {
      self.postMessage({ type: "loading", model: msg.model });
      await load(msg.model);
      await checkSaved(msg.model);
      self.postMessage({ type: "ready", device });
    } else if (msg.type === "transcribe") {
      self.postMessage({ type: "loading", model: msg.model });
      await load(msg.model);
      await checkSaved(msg.model);
      self.postMessage({ type: "ready", device });
      const t0 = performance.now();
      let opts;
      if (isCohere(msg.model)) {
        // Cohere découpe lui-même l'audio long en morceaux.
        opts = { language: COHERE_LANG[msg.language] || "fr", max_new_tokens: 448 };
      } else {
        opts = { chunk_length_s: 30, stride_length_s: 5, task: "transcribe" };
        if (msg.language && msg.language !== "auto") opts.language = msg.language;
      }
      const out = await run(msg.audio, opts);
      const text = (Array.isArray(out) ? out.map(o => o.text).join(" ") : out.text || "").trim();
      self.postMessage({ type: "result", id: msg.id, text, device, seconds: (performance.now() - t0) / 1000 });
    }
  } catch (err) {
    self.postMessage({ type: "error", id: msg.id, message: err && err.message ? err.message : String(err) });
  }
}

// Les messages sont traités un par un (évite de charger deux fois le modèle en parallèle).
let chain = Promise.resolve();
self.onmessage = e => { chain = chain.then(() => handle(e.data)); };
