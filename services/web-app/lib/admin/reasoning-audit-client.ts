/**
 * Server-side HTTP helpers that proxy the admin reasoning-audit endpoints
 * on trading-core. Forward the user's session bearer token; trading-core
 * SecurityConfig enforces ROLE_ADMIN.
 *
 * These helpers are imported by the `/app/api/admin/signals/**` route
 * handlers so the proxy logic lives in one place and stays testable.
 */

import type {
  AdminSignalsPage,
  ReasoningAudit,
} from "@/lib/admin/reasoning-audit-types";

const API_BASE_URL =
  process.env.API_BASE_URL ?? process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8082";

export type AdminApiError = {
  status: number;
  message: string;
};

async function adminFetch<T>(
  path: string,
  token: string | undefined,
): Promise<T | AdminApiError> {
  if (!token) {
    return { status: 401, message: "Missing access token" };
  }
  const response = await fetch(`${API_BASE_URL}${path}`, {
    headers: {
      Accept: "application/json",
      Authorization: `Bearer ${token}`,
    },
    cache: "no-store",
  });
  if (!response.ok) {
    return {
      status: response.status,
      message: `Upstream returned ${response.status}`,
    };
  }
  return (await response.json()) as T;
}

export async function fetchAdminSignals(
  token: string | undefined,
  options: { ticker?: string; page?: number; size?: number } = {},
): Promise<AdminSignalsPage | AdminApiError> {
  const params = new URLSearchParams();
  if (options.ticker && options.ticker.trim()) {
    params.set("ticker", options.ticker.trim());
  }
  params.set("page", String(options.page ?? 0));
  params.set("size", String(options.size ?? 25));
  return adminFetch<AdminSignalsPage>(`/api/v1/admin/signals?${params}`, token);
}

export async function fetchAdminTickers(
  token: string | undefined,
): Promise<string[] | AdminApiError> {
  return adminFetch<string[]>("/api/v1/admin/signals/tickers", token);
}

export async function fetchReasoningAudit(
  token: string | undefined,
  signalId: string,
): Promise<ReasoningAudit | AdminApiError> {
  return adminFetch<ReasoningAudit>(
    `/api/v1/admin/signals/${encodeURIComponent(signalId)}/reasoning-audit`,
    token,
  );
}

export function isError(value: unknown): value is AdminApiError {
  return (
    typeof value === "object" &&
    value !== null &&
    "status" in value &&
    "message" in value
  );
}
