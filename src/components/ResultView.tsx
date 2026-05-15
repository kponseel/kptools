import { useEffect, useRef, useState } from "react";
import { BEER_STAGES, type BeerLevel } from "../lib/prompts";
import { downloadDataUrl } from "../lib/image";

interface Props {
  original: string;
  result: string;
  level: BeerLevel;
  onReset: () => void;
  onTryAgain: () => void;
}

export function ResultView({ original, result, level, onReset, onTryAgain }: Props) {
  const stage = BEER_STAGES[level];
  const [split, setSplit] = useState(50);
  const [zoomOpen, setZoomOpen] = useState(false);
  const containerRef = useRef<HTMLDivElement>(null);
  const draggingRef = useRef(false);

  useEffect(() => {
    function onUp() {
      draggingRef.current = false;
    }
    function onMove(e: PointerEvent) {
      if (!draggingRef.current || !containerRef.current) return;
      const rect = containerRef.current.getBoundingClientRect();
      const pct = ((e.clientX - rect.left) / rect.width) * 100;
      setSplit(Math.max(0, Math.min(100, pct)));
    }
    window.addEventListener("pointerup", onUp);
    window.addEventListener("pointermove", onMove);
    return () => {
      window.removeEventListener("pointerup", onUp);
      window.removeEventListener("pointermove", onMove);
    };
  }, []);

  function onHandleDown(e: React.PointerEvent) {
    draggingRef.current = true;
    e.preventDefault();
  }

  function download() {
    downloadDataUrl(result, `goggle-gulp-${stage.name.toLowerCase().replace(/\s+/g, "-")}-${level}beers.png`);
  }

  return (
    <div className="result">
      <div className="result-header">
        <div>
          <div className="result-title">
            {stage.emoji} {stage.name}
          </div>
          <div className="result-tagline">{stage.tagline}</div>
        </div>
      </div>

      <div className="compare" ref={containerRef}>
        <img src={result} alt="Vue après les bières" className="compare-img compare-after" />
        <div className="compare-before-wrap" style={{ width: `${split}%` }}>
          <img src={original} alt="Photo originale" className="compare-img compare-before" />
        </div>
        <div
          className="compare-handle"
          style={{ left: `${split}%` }}
          onPointerDown={onHandleDown}
          role="slider"
          aria-label="Comparer avant/après"
          aria-valuenow={split}
          aria-valuemin={0}
          aria-valuemax={100}
          tabIndex={0}
        >
          <div className="compare-handle-line" />
          <div className="compare-handle-knob">⇆</div>
        </div>
        <div className="compare-label compare-label-left">Sobre</div>
        <div className="compare-label compare-label-right">{stage.emoji}</div>
        <button
          type="button"
          className="zoom-btn"
          onClick={() => setZoomOpen(true)}
          aria-label="Agrandir"
        >
          🔍
        </button>
      </div>

      <div className="result-actions">
        <button className="btn btn-primary" onClick={download}>
          ⬇ Télécharger
        </button>
        <button className="btn btn-secondary" onClick={onTryAgain}>
          🍺 Reverser une tournée
        </button>
        <button className="btn btn-ghost" onClick={onReset}>
          Nouvelle photo
        </button>
      </div>

      {zoomOpen && (
        <div className="zoom-overlay" onClick={() => setZoomOpen(false)} role="dialog" aria-modal>
          <button className="zoom-close" aria-label="Fermer">
            ✕
          </button>
          <div className="zoom-grid" onClick={(e) => e.stopPropagation()}>
            <figure>
              <figcaption>Sobre</figcaption>
              <img src={original} alt="Original" />
            </figure>
            <figure>
              <figcaption>{stage.emoji} {stage.name}</figcaption>
              <img src={result} alt="Après les bières" />
            </figure>
          </div>
        </div>
      )}
    </div>
  );
}
