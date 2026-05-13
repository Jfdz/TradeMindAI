/**
 * Wire shapes returned by trading-core's admin signal endpoints.
 *
 * Keep aligned with:
 *   - AdminSignalSummary.java   (Page<AdminSignalSummary> rows)
 *   - ReasoningAuditResponse.java (full audit payload)
 *
 * Fields use camelCase to match Jackson default Java→JSON serialization.
 */

export type AdminSignalSummary = {
  id: string;
  ticker: string | null;
  signalType: "BUY" | "SELL" | "HOLD" | string;
  confidence: number | null;
  timeframe: string;
  generatedAt: string;
  entryPrice: number | null;
  predictedChangePct: number | null;
  reasoningStatus: "PENDING" | "READY" | "FALLBACK" | "FAILED" | string;
  reasoningGeneratedAt: string | null;
  reasoningOutcome: string | null;
  reasoningProvider: string | null;
  reasoningRetryCount: number | null;
  hasArtifact: boolean;
};

export type AdminSignalsPage = {
  content: AdminSignalSummary[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
  first: boolean;
  last: boolean;
  empty: boolean;
};

export type ReasoningAudit = {
  signalId: string;
  ticker: string | null;
  reasoningStatus: string | null;
  reasoning: string | null;
  reasoningGeneratedAt: string | null;
  outcome: string | null;
  provider: string | null;
  modelVersion: string | null;
  retryCount: number | null;
  refusalReason: string | null;
  factsSnapshot: Record<string, unknown> | null;
  priceRefs: string[] | null;
  newsRefs: string[] | null;
  validatorViolations: Array<Record<string, unknown>> | null;
  rawAudit: Record<string, unknown> | null;
};
