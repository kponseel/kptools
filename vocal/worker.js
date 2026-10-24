/* Worker de transcription : Whisper via transformers.js, 100 % dans le navigateur.
   Le modèle est téléchargé une fois depuis Hugging Face puis gardé en cache. */
import { pipeline, env } from "https://cdn.jsdelivr.net/npm/@huggingface/transformers@4.3.1";

env.allowLocalModels = false;

let asr = null;        // pipeline chargé
let loadedKey = "";    // modèle + device du pipeline chargé
let device = "wasm";
let forceWasm = false;  // passe à true si le GPU a échoué une fois

async function gpuInfo() {
  if (forceWasm) return null;
  try {
    const adapter = navigator.gpu && await navigator.gpu.requestAdapter();
    return adapter ? { f16: adapter.features.has("shader-f16") } : null;
  } catch { return null; }
}

// Regroupe la progression des différents fichiers du modèle en un seul pourcentage.
function progressReporter() {
  const files = new Map();
  return p => {
    if (p.status === "progress" && p.file) {
      files.set(p.file, { loaded: p.loaded || 0, total: p.total || 0 });
      let loaded = 0, total = 0;
      for (const f of files.values()) { loaded += f.loaded; total += f.total; }
      self.postMessage({ type: "load-progress", loaded, total });
    }
  };
}

async function load(model) {
  const gpu = await gpuInfo();
  const wantGPU = !!gpu;
  const key = model + (wantGPU ? "|webgpu" : "|wasm");
  if (asr && loadedKey === key) return;
  if (asr) { try { await asr.dispose(); } catch {} asr = null; }

  const progress_callback = progressReporter();
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

async function run(audio, opts) {
  try {
    return await asr(audio, opts);
  } catch (err) {
    if (device !== "webgpu") throw err;
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
      self.postMessage({ type: "ready", device });
    } else if (msg.type === "transcribe") {
      self.postMessage({ type: "loading", model: msg.model });
      await load(msg.model);
      self.postMessage({ type: "ready", device });
      const t0 = performance.now();
      const opts = { chunk_length_s: 30, stride_length_s: 5, task: "transcribe" };
      if (msg.language && msg.language !== "auto") opts.language = msg.language;
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
