// Predicate for "this news item carries its own article image".
//
// A news card is only worth showing when it has an article-specific image.
// Two failure modes are filtered out:
//   1. No image at all (source returned null / "" ) — the empty dark cards.
//   2. A generic placeholder/template logo (a real URL, but not the article's
//      own image) — listed in BAD_IMAGE_SUBSTRINGS.

export const BAD_IMAGE_SUBSTRINGS: readonly string[] = [
  // Yahoo Finance template logo:
  // https://s.yimg.com/rz/stage/p/yahoo_finance_en-US_h_p_finance_2.png
  "yahoo_finance_en-US_h_p_finance",
];

export function hasOwnImage(image: string | null | undefined): boolean {
  if (!image) return false;
  const url = image.trim();
  if (!url.startsWith("http")) return false;
  return !BAD_IMAGE_SUBSTRINGS.some((bad) => url.includes(bad));
}
