import { describe, expect, it } from "vitest";

import { hasOwnImage } from "../news-image-filter";

describe("hasOwnImage", () => {
  it("rejects null / undefined", () => {
    expect(hasOwnImage(null)).toBe(false);
    expect(hasOwnImage(undefined)).toBe(false);
  });

  it("rejects empty / whitespace", () => {
    expect(hasOwnImage("")).toBe(false);
    expect(hasOwnImage("   ")).toBe(false);
  });

  it("rejects non-http (data URIs, relative paths)", () => {
    expect(hasOwnImage("data:image/png;base64,AAAA")).toBe(false);
    expect(hasOwnImage("/static/placeholder.png")).toBe(false);
  });

  it("rejects the Yahoo Finance template logo", () => {
    expect(
      hasOwnImage(
        "https://s.yimg.com/rz/stage/p/yahoo_finance_en-US_h_p_finance_2.png",
      ),
    ).toBe(false);
  });

  it("accepts a real article image URL", () => {
    expect(
      hasOwnImage("https://media.example.com/articles/abc123/cover.jpg"),
    ).toBe(true);
    expect(hasOwnImage("  https://img.cdn.com/x.png  ")).toBe(true);
  });
});
