# 🍺 Goggle Gulp AI

> Voyez le monde à travers vos lunettes de bière, propulsé par l'IA générative.

Application web récréative qui simule l'effet « beer goggles » sur un portrait :
choisis ta photo, règle le curseur de 1 à 5 bières, et Gemini redessine la personne
telle que tu la verrais avec quelques verres dans le nez.

## Principe

1. **La réalité sobre** — prends une photo via la caméra ou uploade un portrait.
2. **Le choix des pintes** — un curseur 1 → 5 :
   - 1 🍺 *Golden Hour Glow* — un léger éclat naturel.
   - 2 🍺🍺 *Cozy Buzz* — le pub se réchauffe.
   - 3 🍺🍺🍺 *Tipsy Charm* — glow-up façon couverture de magazine.
   - 4 🍺🍺🍺🍺 *Hammered Halo* — beauté quasi irréelle, halo glamour.
   - 5 🍺🍺🍺🍺🍺 *Absolute Hallucination* — divinité de la beauté, vision céleste.
3. **CHUG !** — l'IA (Gemini 2.5 Flash Image, alias Nano Banana) redessine la photo.
4. **Compare & télécharge** — slider avant/après interactif, zoom plein écran,
   téléchargement du résultat.

## Stack

- [Vite](https://vite.dev/) + [React 19](https://react.dev/) + TypeScript
- [@google/genai](https://www.npmjs.com/package/@google/genai) — SDK Gemini officiel
- Tout tourne côté client : aucune image ne sort de ton navigateur sauf vers l'API
  Gemini de Google. Ta clé API est stockée uniquement dans `localStorage`.

## Configuration

1. Récupère une clé API Gemini sur https://aistudio.google.com/apikey
2. Lance le projet (voir ci-dessous).
3. Au premier lancement, colle ta clé dans le panneau en haut de l'app.

## Lancer en local

```bash
npm install
npm run dev
```

L'app est servie sur http://localhost:5173.

## Build de production

```bash
npm run build
npm run preview
```

## Disclaimer

Application strictement humoristique. Boire avec modération. Les transformations
sont appliquées via l'API Gemini de Google — soumises à ses conditions
d'utilisation et à ses filtres de sécurité.
