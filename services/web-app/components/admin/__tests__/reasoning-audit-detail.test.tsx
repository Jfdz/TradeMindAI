import React from "react";
import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it } from "vitest";
import type { ReasoningAudit } from "@/lib/admin/reasoning-audit-types";
import { ReasoningAuditDetail } from "../reasoning-audit-detail";

function makeAudit(overrides: Partial<ReasoningAudit> = {}): ReasoningAudit {
  return {
    signalId: "ae75a376-3e64-4e3f-b87b-66fb63107923",
    ticker: "TSLA",
    reasoningStatus: "PENDING",
    reasoning: null,
    reasoningGeneratedAt: null,
    signalGeneratedAt: "2026-05-14T12:00:00Z",
    entryPrice: null,
    targetPrice: null,
    stopLoss: null,
    expectedMovePct: null,
    outcome: null,
    provider: null,
    modelVersion: null,
    retryCount: null,
    refusalReason: null,
    factsSnapshot: null,
    priceRefs: null,
    newsRefs: null,
    validatorViolations: null,
    rawAudit: null,
    ...overrides,
  };
}

function html(audit: ReasoningAudit): string {
  return renderToStaticMarkup(
    React.createElement(ReasoningAuditDetail, { audit }),
  );
}

describe("ReasoningAuditDetail", () => {
  it("renders 'awaiting reasoning' when status is PENDING and outcome is null", () => {
    const out = html(makeAudit({ reasoningStatus: "PENDING", outcome: null }));
    expect(out).toContain("awaiting reasoning");
    expect(out).not.toContain("no artifact");
  });

  it("renders status verbatim when status is READY and outcome is null", () => {
    const out = html(
      makeAudit({
        reasoningStatus: "READY",
        outcome: null,
        reasoning: "The balanced BUY setup...",
      }),
    );
    expect(out).toContain("ready");
    expect(out).not.toContain("no artifact");
    expect(out).toContain("The balanced BUY setup...");
  });

  it("renders outcome verbatim when artifact has outcome", () => {
    const out = html(makeAudit({ outcome: "GENERATED", reasoningStatus: "READY" }));
    expect(out).toContain("GENERATED");
  });

  it("renders 'Signal generated' from signalGeneratedAt even when reasoningGeneratedAt is null", () => {
    const out = html(
      makeAudit({
        signalGeneratedAt: "2026-05-14T12:00:00Z",
        reasoningGeneratedAt: null,
      }),
    );
    expect(out).toContain("Signal generated");
    // Reasoning generated row still present but with em-dash placeholder.
    expect(out).toContain("Reasoning generated");
  });

  it("renders Pricing section when entryPrice/targetPrice/stopLoss/expectedMovePct present", () => {
    const out = html(
      makeAudit({
        entryPrice: 603.0,
        targetPrice: 650.0,
        stopLoss: 580.0,
        expectedMovePct: 7.79,
      }),
    );
    expect(out).toContain("Pricing");
    expect(out).toContain("$603.00");
    expect(out).toContain("$650.00");
    expect(out).toContain("$580.00");
    expect(out).toContain("7.79%");
  });

  it("hides the Pricing section when all four price fields are null", () => {
    const out = html(makeAudit());
    expect(out).not.toContain(">Pricing<");
  });
});
