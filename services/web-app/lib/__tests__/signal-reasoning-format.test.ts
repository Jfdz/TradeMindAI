import { describe, expect, it } from "vitest";

import type { SignalResponse } from "@/lib/api-client";
import { scrubReasoningText } from "@/lib/signal-reasoning-format";

const signal: SignalResponse = {
  id: "sig-1",
  symbol: "PLTR",
  type: "BUY",
  confidence: 0.4321,
  generatedAt: "2026-05-16T00:00:00Z",
  timeframe: "DAILY",
  reasoning: null,
};

describe("scrubReasoningText", () => {
  // ceil(43.21) = 44 — ceiling rule wins; see format.ts formatConfidencePct.
  it.each([
    ["replaces snake_case field names with friendly labels", "sits below sma_200 at 163.478225", "sits below 200-day average at 163.48 $"],
    ["renders confidence as ceiling integer percent", "with confidence 0.4321", "with confidence 44 %"],
    ["leaves RSI raw — 0–100 indicator, not a percent", "RSI at 43.1629 suggests momentum", "RSI at 43.1629 suggests momentum"],
    ["keeps ungoverned numbers verbatim (grounded facts)", "the move spans 12 sessions", "the move spans 12 sessions"],
    ["maps price-context field and ceilings its value", "target_price 627.121", "target 627.13 $"],
  ] as const)("%s", (_label, input, expected) => {
    expect(scrubReasoningText(input, signal)).toBe(expected);
  });

  it("returns empty string for null/undefined input", () => {
    expect(scrubReasoningText(null, signal)).toBe("");
    expect(scrubReasoningText(undefined, signal)).toBe("");
  });
});
