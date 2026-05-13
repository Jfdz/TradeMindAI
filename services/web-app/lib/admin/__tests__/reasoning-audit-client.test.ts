import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import {
  fetchAdminSignals,
  fetchAdminTickers,
  fetchReasoningAudit,
  isError,
} from "@/lib/admin/reasoning-audit-client";

const mockFetch = vi.fn();
const ORIGINAL_FETCH = globalThis.fetch;

beforeEach(() => {
  vi.stubGlobal("fetch", mockFetch);
  mockFetch.mockReset();
});

afterEach(() => {
  if (ORIGINAL_FETCH) {
    globalThis.fetch = ORIGINAL_FETCH;
  }
});

function okJson(data: unknown) {
  return Promise.resolve({
    ok: true,
    status: 200,
    json: async () => data,
  });
}

function errorResp(status: number) {
  return Promise.resolve({
    ok: false,
    status,
    json: async () => ({}),
  });
}

describe("fetchAdminSignals", () => {
  it("forwards bearer token and pagination params", async () => {
    mockFetch.mockResolvedValue(
      okJson({
        content: [
          {
            id: "abc",
            ticker: "META",
            signalType: "BUY",
            confidence: 0.62,
            timeframe: "DAILY",
            generatedAt: "2026-05-13T12:00:00Z",
            entryPrice: 603,
            predictedChangePct: 4.5,
            reasoningStatus: "READY",
            reasoningGeneratedAt: "2026-05-13T12:00:30Z",
            reasoningOutcome: "GENERATED",
            reasoningProvider: "anthropic_oauth",
            reasoningRetryCount: 0,
            hasArtifact: true,
          },
        ],
        totalElements: 1,
        totalPages: 1,
        number: 0,
        size: 25,
        first: true,
        last: true,
        empty: false,
      }),
    );

    const result = await fetchAdminSignals("tok-1", {
      ticker: "META",
      page: 0,
      size: 25,
    });

    expect(isError(result)).toBe(false);
    if (!isError(result)) {
      expect(result.content).toHaveLength(1);
      expect(result.content[0].ticker).toBe("META");
    }
    expect(mockFetch).toHaveBeenCalledWith(
      expect.stringContaining("/api/v1/admin/signals?"),
      expect.objectContaining({
        headers: expect.objectContaining({
          Authorization: "Bearer tok-1",
        }),
      }),
    );
    const calledUrl = mockFetch.mock.calls[0][0] as string;
    expect(calledUrl).toContain("ticker=META");
    expect(calledUrl).toContain("page=0");
    expect(calledUrl).toContain("size=25");
  });

  it("omits ticker query param when not provided", async () => {
    mockFetch.mockResolvedValue(
      okJson({
        content: [],
        totalElements: 0,
        totalPages: 0,
        number: 0,
        size: 25,
        first: true,
        last: true,
        empty: true,
      }),
    );

    await fetchAdminSignals("tok-1");

    const calledUrl = mockFetch.mock.calls[0][0] as string;
    expect(calledUrl).not.toContain("ticker=");
    expect(calledUrl).toContain("page=0");
  });

  it("returns AdminApiError for 401 missing token", async () => {
    const result = await fetchAdminSignals(undefined);
    expect(isError(result)).toBe(true);
    if (isError(result)) {
      expect(result.status).toBe(401);
    }
    expect(mockFetch).not.toHaveBeenCalled();
  });

  it("returns AdminApiError on upstream 5xx", async () => {
    mockFetch.mockResolvedValue(errorResp(502));
    const result = await fetchAdminSignals("tok-1");
    expect(isError(result)).toBe(true);
    if (isError(result)) {
      expect(result.status).toBe(502);
    }
  });
});

describe("fetchAdminTickers", () => {
  it("returns the ticker list on 200", async () => {
    mockFetch.mockResolvedValue(okJson(["AAPL", "META", "NVDA"]));
    const result = await fetchAdminTickers("tok-1");
    expect(result).toEqual(["AAPL", "META", "NVDA"]);
    expect(mockFetch).toHaveBeenCalledWith(
      expect.stringContaining("/api/v1/admin/signals/tickers"),
      expect.anything(),
    );
  });
});

describe("fetchReasoningAudit", () => {
  it("encodes the signalId path segment", async () => {
    mockFetch.mockResolvedValue(
      okJson({
        signalId: "abc/with slash",
        ticker: "META",
        reasoningStatus: "READY",
        reasoning: "ok",
        reasoningGeneratedAt: "2026-05-13T12:00:30Z",
        outcome: "GENERATED",
        provider: "anthropic_oauth",
        modelVersion: "claude-haiku-4-5",
        retryCount: 0,
        refusalReason: null,
        factsSnapshot: { ticker: "META" },
        priceRefs: ["sma_200"],
        newsRefs: [],
        validatorViolations: [],
        rawAudit: { input_tokens: 100 },
      }),
    );

    await fetchReasoningAudit("tok-1", "abc/with slash");

    const calledUrl = mockFetch.mock.calls[0][0] as string;
    expect(calledUrl).toContain("/abc%2Fwith%20slash/reasoning-audit");
  });

  it("returns AdminApiError on 404", async () => {
    mockFetch.mockResolvedValue(errorResp(404));
    const result = await fetchReasoningAudit("tok-1", "unknown");
    expect(isError(result)).toBe(true);
    if (isError(result)) {
      expect(result.status).toBe(404);
    }
  });
});
