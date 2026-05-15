import { useEffect, useState } from "react";
import { PhotoCapture } from "./components/PhotoCapture";
import { BeerSlider } from "./components/BeerSlider";
import { ResultView } from "./components/ResultView";
import { ApiKeyPanel } from "./components/ApiKeyPanel";
import { applyBeerGoggles, GeminiError } from "./lib/gemini";
import { BEER_STAGES, type BeerLevel } from "./lib/prompts";

const API_KEY_STORAGE = "goggle-gulp-ai:gemini-key";

const LOADING_LINES = [
  "On sert la pinte…",
  "On ajuste les lunettes de bière…",
  "L'IA fait tourner le verre…",
  "Mousse en cours de formation…",
  "On embrume légèrement la vision…",
  "Houblon numérique en infusion…",
];

export default function App() {
  const [apiKey, setApiKey] = useState<string>(() => localStorage.getItem(API_KEY_STORAGE) ?? "");
  const [photo, setPhoto] = useState<string | null>(null);
  const [level, setLevel] = useState<BeerLevel>(2);
  const [result, setResult] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [funnyLine, setFunnyLine] = useState<string>(LOADING_LINES[0]);

  useEffect(() => {
    if (!loading) return;
    const id = setInterval(() => {
      setFunnyLine(LOADING_LINES[Math.floor(Math.random() * LOADING_LINES.length)]);
    }, 1800);
    return () => clearInterval(id);
  }, [loading]);

  function saveKey(key: string) {
    setApiKey(key);
    localStorage.setItem(API_KEY_STORAGE, key);
  }

  function clearKey() {
    setApiKey("");
    localStorage.removeItem(API_KEY_STORAGE);
  }

  function reset() {
    setPhoto(null);
    setResult(null);
    setError(null);
    setLevel(2);
  }

  async function chug() {
    if (!photo) return;
    if (!apiKey) {
      setError("Renseigne ta clé Gemini d'abord (panneau en haut).");
      return;
    }
    setLoading(true);
    setError(null);
    setResult(null);
    setFunnyLine(LOADING_LINES[0]);
    try {
      const out = await applyBeerGoggles(apiKey, photo, level);
      setResult(out.imageDataUrl);
    } catch (e) {
      const message =
        e instanceof GeminiError ? e.message : e instanceof Error ? e.message : String(e);
      setError(message);
    } finally {
      setLoading(false);
    }
  }

  const stage = BEER_STAGES[level];

  return (
    <div className="app">
      <header className="hero">
        <div className="hero-title">
          <span className="hero-emoji" aria-hidden>🍺</span>
          <div>
            <h1>Goggle Gulp AI</h1>
            <p className="hero-sub">
              Voyez le monde à travers vos lunettes de bière, propulsé par l'IA.
            </p>
          </div>
        </div>
        <ApiKeyPanel apiKey={apiKey} onSave={saveKey} onClear={clearKey} />
      </header>

      <main className="main">
        {!result && (
          <>
            <section className="card">
              <h2 className="step">
                <span className="step-num">1</span> La réalité sobre
              </h2>
              {!photo ? (
                <PhotoCapture onPhoto={setPhoto} />
              ) : (
                <div className="preview">
                  <img src={photo} alt="Photo source" className="preview-img" />
                  <button className="btn btn-ghost" onClick={() => setPhoto(null)}>
                    Changer de photo
                  </button>
                </div>
              )}
            </section>

            <section className="card" aria-disabled={!photo}>
              <h2 className="step">
                <span className="step-num">2</span> Le choix des pintes
              </h2>
              <BeerSlider level={level} onChange={setLevel} disabled={!photo || loading} />
            </section>

            <section className="card chug-card">
              <h2 className="step">
                <span className="step-num">3</span> Cul sec !
              </h2>
              <button
                className="btn btn-chug"
                onClick={chug}
                disabled={!photo || loading || !apiKey}
              >
                {loading ? (
                  <>
                    <span className="spinner" aria-hidden /> {funnyLine}
                  </>
                ) : (
                  <>🍻 CHUG ! ({stage.emoji})</>
                )}
              </button>
              {!apiKey && (
                <p className="hint">
                  Ajoute ta clé Gemini dans le panneau en haut pour activer le bouton.
                </p>
              )}
              {error && <p className="error">{error}</p>}
            </section>
          </>
        )}

        {result && photo && (
          <ResultView
            original={photo}
            result={result}
            level={level}
            onReset={reset}
            onTryAgain={() => {
              setResult(null);
              setError(null);
            }}
          />
        )}
      </main>

      <footer className="footer">
        <p>
          Pour s'amuser uniquement. Boire avec modération. Aucune image n'est envoyée ailleurs
          que vers l'API Gemini de Google.
        </p>
      </footer>
    </div>
  );
}
