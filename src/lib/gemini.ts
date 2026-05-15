import { GoogleGenAI, Modality } from "@google/genai";
import { buildPrompt, type BeerLevel } from "./prompts";

const IMAGE_MODEL = "gemini-2.5-flash-image-preview";

export class GeminiError extends Error {
  cause?: unknown;
  constructor(message: string, cause?: unknown) {
    super(message);
    this.name = "GeminiError";
    this.cause = cause;
  }
}

export interface GogglesResult {
  imageDataUrl: string;
  mimeType: string;
}

function dataUrlToInline(dataUrl: string): { mimeType: string; data: string } {
  const match = dataUrl.match(/^data:([^;]+);base64,(.+)$/);
  if (!match) {
    throw new GeminiError("Format d'image invalide (data URL attendue).");
  }
  return { mimeType: match[1], data: match[2] };
}

export async function applyBeerGoggles(
  apiKey: string,
  imageDataUrl: string,
  level: BeerLevel,
): Promise<GogglesResult> {
  if (!apiKey.trim()) {
    throw new GeminiError("Clé API Gemini manquante.");
  }

  const ai = new GoogleGenAI({ apiKey });
  const { mimeType, data } = dataUrlToInline(imageDataUrl);
  const prompt = buildPrompt(level);

  let response;
  try {
    response = await ai.models.generateContent({
      model: IMAGE_MODEL,
      contents: [
        {
          role: "user",
          parts: [
            { inlineData: { mimeType, data } },
            { text: prompt },
          ],
        },
      ],
      config: {
        responseModalities: [Modality.IMAGE],
      },
    });
  } catch (err) {
    const message = err instanceof Error ? err.message : String(err);
    throw new GeminiError(`Appel Gemini échoué : ${message}`, err);
  }

  const parts = response.candidates?.[0]?.content?.parts ?? [];
  for (const part of parts) {
    if (part.inlineData?.data) {
      const outMime = part.inlineData.mimeType ?? "image/png";
      return {
        mimeType: outMime,
        imageDataUrl: `data:${outMime};base64,${part.inlineData.data}`,
      };
    }
  }

  const textPart = parts.find((p) => p.text)?.text;
  throw new GeminiError(
    textPart
      ? `Gemini n'a pas renvoyé d'image. Réponse : ${textPart.slice(0, 200)}`
      : "Gemini n'a pas renvoyé d'image. Réessaie ou change le niveau.",
  );
}
