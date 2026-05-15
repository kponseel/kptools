import { useState } from "react";

interface Props {
  apiKey: string;
  onSave: (key: string) => void;
  onClear: () => void;
}

export function ApiKeyPanel({ apiKey, onSave, onClear }: Props) {
  const [draft, setDraft] = useState(apiKey);
  const [revealed, setRevealed] = useState(false);
  const hasKey = Boolean(apiKey);

  function save() {
    const trimmed = draft.trim();
    if (trimmed) onSave(trimmed);
  }

  return (
    <details className="api-panel" open={!hasKey}>
      <summary>
        <span className="api-status" data-ok={hasKey}>
          {hasKey ? "🔑 Clé Gemini configurée" : "🔐 Clé Gemini requise"}
        </span>
        <span className="api-hint">{hasKey ? "modifier" : "configurer"}</span>
      </summary>

      <div className="api-body">
        <p className="api-help">
          Cette app appelle directement l'API Gemini depuis ton navigateur. Ta clé est
          stockée uniquement dans le <code>localStorage</code> de ce site. Récupère-la sur{" "}
          <a href="https://aistudio.google.com/apikey" target="_blank" rel="noreferrer">
            aistudio.google.com/apikey
          </a>
          .
        </p>
        <div className="api-row">
          <input
            type={revealed ? "text" : "password"}
            value={draft}
            onChange={(e) => setDraft(e.target.value)}
            placeholder="AIza…"
            spellCheck={false}
            autoComplete="off"
            className="api-input"
          />
          <button
            type="button"
            className="btn btn-ghost btn-sm"
            onClick={() => setRevealed((r) => !r)}
          >
            {revealed ? "Masquer" : "Voir"}
          </button>
        </div>
        <div className="api-actions">
          <button className="btn btn-primary btn-sm" onClick={save} disabled={!draft.trim()}>
            Enregistrer
          </button>
          {hasKey && (
            <button
              className="btn btn-ghost btn-sm"
              onClick={() => {
                setDraft("");
                onClear();
              }}
            >
              Oublier la clé
            </button>
          )}
        </div>
      </div>
    </details>
  );
}
