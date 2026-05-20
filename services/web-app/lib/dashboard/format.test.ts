import { describe, expect, it } from "vitest";

import {
  TONE_DEFAULT,
  TONE_NEGATIVE,
  TONE_NEUTRAL,
  TONE_POSITIVE,
  formatConfidencePct,
  formatMoney,
  formatMoneyOrDash,
  formatPercentOrDash,
  formatPriceUSD,
  formatSignedMoney,
  formatSignedMoneyOrDash,
  formatSignedPercent,
  signedTone,
} from "./format";

describe("formatMoney", () => {
  it("renders USD with two decimals", () => {
    expect(formatMoney(1234.5)).toBe("$1,234.50");
    expect(formatMoney(0)).toBe("$0.00");
    expect(formatMoney(-99.99)).toBe("-$99.99");
  });
});

describe("formatSignedMoney", () => {
  it("prefixes + for positive and - for negative", () => {
    expect(formatSignedMoney(42)).toBe("+$42.00");
    expect(formatSignedMoney(-42)).toBe("-$42.00");
  });

  it("renders zero without a sign", () => {
    expect(formatSignedMoney(0)).toBe("$0.00");
    expect(formatSignedMoney(-0)).toBe("$0.00");
  });
});

describe("formatSignedPercent", () => {
  it("prefixes + for positive only", () => {
    expect(formatSignedPercent(12.5)).toBe("+12.50%");
    expect(formatSignedPercent(-3)).toBe("-3.00%");
    expect(formatSignedPercent(0)).toBe("0.00%");
  });

  it("respects digits override", () => {
    expect(formatSignedPercent(1.2345, 1)).toBe("+1.2%");
  });
});

describe("formatPriceUSD", () => {
  it("ceilings to 2 dp and suffixes ' $'", () => {
    expect(formatPriceUSD(163.478225)).toBe("163.48 $");
    expect(formatPriceUSD(140)).toBe("140.00 $");
    expect(formatPriceUSD(0.001)).toBe("0.01 $");
  });

  it("returns em-dash for null/undefined", () => {
    expect(formatPriceUSD(null)).toBe("—");
    expect(formatPriceUSD(undefined)).toBe("—");
  });
});

describe("formatConfidencePct", () => {
  it("ceilings to an integer percent with ' %' suffix", () => {
    // ceil(43.21) = 44 — ceiling rule wins over the plan's 43 example.
    expect(formatConfidencePct(0.4321)).toBe("44 %");
    expect(formatConfidencePct(0.5)).toBe("50 %");
    expect(formatConfidencePct(0.62)).toBe("62 %");
  });

  it("returns em-dash for null/undefined", () => {
    expect(formatConfidencePct(null)).toBe("—");
    expect(formatConfidencePct(undefined)).toBe("—");
  });
});

describe("orDash wrappers", () => {
  it("returns em-dash for null and undefined", () => {
    expect(formatMoneyOrDash(null)).toBe("—");
    expect(formatMoneyOrDash(undefined)).toBe("—");
    expect(formatSignedMoneyOrDash(null)).toBe("—");
    expect(formatPercentOrDash(null)).toBe("—");
  });

  it("falls through to base formatter for present values", () => {
    expect(formatMoneyOrDash(5)).toBe("$5.00");
    expect(formatSignedMoneyOrDash(-2)).toBe("-$2.00");
    expect(formatPercentOrDash(3.456)).toBe("3.5%");
    expect(formatPercentOrDash(3.456, 2)).toBe("3.46%");
  });
});

describe("signedTone", () => {
  it("maps positive to green, negative to red", () => {
    expect(signedTone(1)).toBe(TONE_POSITIVE);
    expect(signedTone(-1)).toBe(TONE_NEGATIVE);
  });

  it("returns the configured neutral tone for exactly zero", () => {
    expect(signedTone(0)).toBe(TONE_DEFAULT);
    expect(signedTone(0, TONE_NEUTRAL)).toBe(TONE_NEUTRAL);
  });

  it("returns the neutral data-absent tone for null and undefined", () => {
    expect(signedTone(null)).toBe(TONE_NEUTRAL);
    expect(signedTone(undefined)).toBe(TONE_NEUTRAL);
  });
});
