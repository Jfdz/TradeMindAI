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
  it("replaces snake_case field names with friendly labels", () => {
    expect(scrubReasoningText("sits below sma_200 at 163.478225", signal)).toBe(
      "sits below 200-day average at 163.48 $",
    );
  });

  it("renders confidence as a ceiling integer percent", () => {
    // ceil(43.21) = 44 — ceiling rule wins over the plan's 43 example.
    expect(scrubReasoningText("with confidence 0.4321", signal)).toBe(
      "with confidence 44 %",
    );
  });

  it("leaves RSI raw — it is a 0–100 indicator, not a percent", () => {
    expect(scrubReasoningText("RSI at 43.1629 suggests momentum", signal)).toBe(
      "RSI at 43.1629 suggests momentum",
    );
  });

  it("keeps ungoverned numbers verbatim (grounded facts)", () => {
    expect(scrubReasoningText("the move spans 12 sessions", signal)).toBe(
      "the move spans 12 sessions",
    );
  });

  it("maps a price-context field and ceilings its value", () => {
    expect(scrubReasoningText("target_price 627.121", signal)).toBe(
      "target 627.13 $",
    );
  });

  it("returns an empty string for null/undefined input", () => {
    expect(scrubReasoningText(null, signal)).toBe("");
    expect(scrubReasoningText(undefined, signal)).toBe("");
  });
});
