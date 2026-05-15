export type BeerLevel = 1 | 2 | 3 | 4 | 5;

export interface BeerStage {
  level: BeerLevel;
  name: string;
  tagline: string;
  emoji: string;
  prompt: string;
}

export const BEER_STAGES: Record<BeerLevel, BeerStage> = {
  1: {
    level: 1,
    name: "Golden Hour Glow",
    tagline: "Une petite mousse, juste pour se mettre en jambes.",
    emoji: "🍺",
    prompt: `Apply a very subtle, tasteful beauty retouch to the person in this photo, as if seen through a warm "golden hour" light after sipping a single beer. Smooth minor blemishes very lightly, add a gentle warm glow to the skin, brighten the eyes just a touch, and add a soft Rembrandt-style warm rim light. Keep the face, identity, hairstyle, expression, pose, clothing, and background completely identical. The change must be barely noticeable — like a flattering Instagram filter. Photorealistic, natural look.`,
  },
  2: {
    level: 2,
    name: "Cozy Buzz",
    tagline: "Le pub se réchauffe, tout le monde devient sympa.",
    emoji: "🍺🍺",
    prompt: `Apply a noticeable but still natural beauty enhancement to the person in this photo, as if seen after two beers. Smooth the skin more visibly, soften wrinkles and imperfections, brighten the eyes and teeth, add a healthy rosy flush on the cheeks, and slightly enhance the lips. Add cozy, warm "pub lighting" with golden bokeh in the background. Keep the same face shape, identity, hairstyle, pose, clothing and overall framing — just a flattering, friendly version. Photorealistic.`,
  },
  3: {
    level: 3,
    name: "Tipsy Charm",
    tagline: "Tout le monde au bar est soudainement canon.",
    emoji: "🍺🍺🍺",
    prompt: `Transform the person in this photo into an idealized, magazine-cover version of themselves, as if perceived through three-beer "beer goggles". Significantly smooth and even the skin, make the eyes larger, brighter and more sparkling, define and lift the cheekbones, slim the jawline subtly, plump the lips, perfect the hair with extra volume and shine. Add glamorous warm cinematic lighting with soft bokeh. The person should still be clearly recognizable as the same individual, just dramatically more attractive — like a flattering glamour portrait. Photorealistic, high-end editorial style.`,
  },
  4: {
    level: 4,
    name: "Hammered Halo",
    tagline: "Vision floue, beauté divine… ça commence à tanguer.",
    emoji: "🍺🍺🍺🍺",
    prompt: `Reimagine the person in this photo as a stunning, almost unreal beauty icon — the way they would appear after four beers, when judgment is severely impaired. Render them with flawless porcelain skin, perfectly symmetrical features, huge luminous eyes, sculpted cheekbones, an idealized nose, full glossy lips, and luxurious flowing hair. Add a soft glamorous halo of warm light around their head, dreamy lens flares, and a beautifully blurred bokeh background like a perfume commercial. The result should look like a celebrity beauty campaign — keep only a faint resemblance to the original face. Hyper-polished, photorealistic, slightly dreamlike.`,
  },
  5: {
    level: 5,
    name: "Absolute Hallucination",
    tagline: "Tu vois littéralement des dieux. Appelle un taxi.",
    emoji: "🍺🍺🍺🍺🍺",
    prompt: `Transform the person in this photo into an over-the-top, mythological deity of beauty — the way the world looks after five beers, in a state of full hallucinatory adoration. Render them as an impossibly perfect being: radiant glowing skin, ethereal symmetrical features, supernaturally large and shimmering eyes, perfect lips, divine flowing hair lifted by an invisible breeze. Surround them with a golden halo, floating sparkles, soft heavenly light beams, and a dreamlike celestial background of clouds and golden bokeh. Add a touch of fantasy: maybe subtle glowing freckles or a faint magical aura. The result must look like a fantasy goddess/god painting blended with hyperreal CGI — barely human anymore, pure idealized beauty. Cinematic, dramatic, glamorous.`,
  },
};

export function buildPrompt(level: BeerLevel): string {
  const stage = BEER_STAGES[level];
  return `${stage.prompt}\n\nIMPORTANT: This is a humorous "beer goggles" filter for an entertainment app. Output a single edited photo only, no text, no watermarks, no captions. Preserve the original framing and aspect ratio.`;
}
