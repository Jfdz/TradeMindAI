import type { ClosePositionPayload, PortfolioHoldingResponse } from "@/lib/api-client";

export type ClosePositionDraft = {
  exitPrice: string;
  fees: string;
  closedAt: string;
};

function parseNumber(value: string) {
  const trimmed = value.trim();
  if (!trimmed) {
    return null;
  }

  const parsed = Number.parseFloat(trimmed);
  return Number.isFinite(parsed) ? parsed : Number.NaN;
}

export function buildClosePositionPayload(draft: ClosePositionDraft): ClosePositionPayload {
  const exitPrice = parseNumber(draft.exitPrice);
  if (exitPrice == null || Number.isNaN(exitPrice) || exitPrice <= 0) {
    throw new Error("Exit price is required.");
  }

  const fees = parseNumber(draft.fees);
  if (fees != null && (Number.isNaN(fees) || fees < 0)) {
    throw new Error("Fees must be zero or greater.");
  }

  const payload: ClosePositionPayload = { exitPrice };
  if (fees != null) {
    payload.fees = fees;
  }
  if (draft.closedAt.trim()) {
    payload.closedAt = new Date(draft.closedAt).toISOString();
  }
  return payload;
}

export function calculateClosePositionPnl(holding: PortfolioHoldingResponse, payload: ClosePositionPayload) {
  return (payload.exitPrice - holding.averageCost) * holding.quantity - (payload.fees ?? 0);
}
