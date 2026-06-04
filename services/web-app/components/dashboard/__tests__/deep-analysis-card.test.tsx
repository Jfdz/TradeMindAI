import React from "react";
import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it, vi } from "vitest";

import type { DeepAnalysisResponse, DeepAnalysisSection } from "@/lib/api-client";

vi.mock("@/components/ui/button", () => ({
  Button: ({ children, ...rest }: { children: React.ReactNode; [k: string]: unknown }) =>
    React.createElement("button", rest, children),
}));

vi.mock("@tanstack/react-query", () => ({
  useQuery: vi.fn(),
  useMutation: vi.fn(),
  useQueryClient: vi.fn(() => ({ setQueryData: vi.fn() })),
}));

import { useMutation, useQuery } from "@tanstack/react-query";
import { DeepAnalysisCard } from "../deep-analysis-card";

function makeSection(overrides: Partial<DeepAnalysisSection> = {}): DeepAnalysisSection {
  return {
    role: "BULL",
    text: "bull text",
    priceRefs: [],
    newsRefs: [],
    refused: false,
    refusalReason: null,
    validatorViolations: [],
    ...overrides,
  };
}

function makeAnalysis(overrides: Partial<DeepAnalysisResponse> = {}): DeepAnalysisResponse {
  return {
    schemaVersion: "v1.0",
    outcome: "GENERATED",
    ticker: "META",
    signalType: "BUY",
    generatedAt: "2026-05-13T12:00:00Z",
    verdictDirection: "BULLISH",
    conviction: "AGREES",
    verdict: makeSection({ role: "JUDGE", text: "verdict text" }),
    sections: [
      makeSection({ role: "BULL", text: "bull text" }),
      makeSection({ role: "BEAR", text: "bear text" }),
      makeSection({ role: "RISK", text: "risk text" }),
    ],
    provider: "minimax_oauth",
    modelVersion: "MiniMax-M2.5-highspeed",
    ...overrides,
  };
}

function mockQuery(data: DeepAnalysisResponse | null | undefined, isLoading = false) {
  (useQuery as unknown as ReturnType<typeof vi.fn>).mockReturnValue({ data, isLoading });
}

function mockMutation(overrides: Record<string, unknown> = {}) {
  (useMutation as unknown as ReturnType<typeof vi.fn>).mockReturnValue({
    mutate: vi.fn(),
    isPending: false,
    isError: false,
    ...overrides,
  });
}

function render(isPremium: boolean) {
  return renderToStaticMarkup(
    React.createElement(DeepAnalysisCard, { signalId: "sig-1", isPremium })
  );
}

describe("DeepAnalysisCard", () => {
  it("non-premium shows an upsell and no run button", () => {
    mockQuery(undefined);
    mockMutation();

    const html = render(false);

    expect(html).toContain("upgrade to unlock");
    expect(html).not.toContain("Run deep analysis");
  });

  it("premium with no analysis offers the run button", () => {
    mockQuery(null);
    mockMutation();

    const html = render(true);

    expect(html).toContain("Run deep analysis");
  });

  it("renders verdict direction, sections and a regenerate button", () => {
    mockQuery(makeAnalysis());
    mockMutation();

    const html = render(true);

    expect(html).toContain("BULLISH");
    expect(html).toContain("agrees with this signal");
    expect(html).toContain("bull text");
    expect(html).toContain("bear text");
    expect(html).toContain("risk text");
    expect(html).toContain("Regenerate");
  });

  it("flags a contradicting verdict as low conviction", () => {
    mockQuery(makeAnalysis({ verdictDirection: "BEARISH", conviction: "CONTRADICTS" }));
    mockMutation();

    const html = render(true);

    expect(html).toContain("BEARISH");
    expect(html).toContain("Low conviction");
  });

  it("withholds the text of a refused section", () => {
    mockQuery(
      makeAnalysis({
        sections: [
          makeSection({ role: "BULL", text: "bull text" }),
          makeSection({ role: "BEAR", text: "", refused: true, refusalReason: "failed_grounding_validation" }),
          makeSection({ role: "RISK", text: "risk text" }),
        ],
      })
    );
    mockMutation();

    const html = render(true);

    expect(html).toContain("Withheld");
  });

  it("shows a Partial badge for a partial outcome", () => {
    mockQuery(makeAnalysis({ outcome: "PARTIAL" }));
    mockMutation();

    const html = render(true);

    expect(html).toContain("Partial");
  });
});
