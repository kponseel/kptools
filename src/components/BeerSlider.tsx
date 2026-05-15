import { BEER_STAGES, type BeerLevel } from "../lib/prompts";

interface Props {
  level: BeerLevel;
  onChange: (level: BeerLevel) => void;
  disabled?: boolean;
}

export function BeerSlider({ level, onChange, disabled }: Props) {
  const stage = BEER_STAGES[level];
  return (
    <div className="slider">
      <div className="slider-header">
        <div className="slider-title">
          <span className="slider-emoji" aria-hidden>
            {stage.emoji}
          </span>
          <div>
            <div className="slider-name">{stage.name}</div>
            <div className="slider-tagline">{stage.tagline}</div>
          </div>
        </div>
        <div className="slider-level">{level}/5</div>
      </div>

      <input
        type="range"
        min={1}
        max={5}
        step={1}
        value={level}
        disabled={disabled}
        onChange={(e) => onChange(Number(e.target.value) as BeerLevel)}
        className="slider-input"
        aria-label="Niveau de bières"
      />

      <div className="slider-ticks">
        {([1, 2, 3, 4, 5] as BeerLevel[]).map((l) => (
          <button
            key={l}
            type="button"
            className={`tick ${l === level ? "active" : ""}`}
            onClick={() => onChange(l)}
            disabled={disabled}
            aria-label={`${l} pinte${l > 1 ? "s" : ""}`}
          >
            <span aria-hidden>🍺</span>
            <span className="tick-num">{l}</span>
          </button>
        ))}
      </div>
    </div>
  );
}
