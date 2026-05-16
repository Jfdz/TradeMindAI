"use client";

import { useQuery } from "@tanstack/react-query";
import { useState } from "react";

import { ReasoningAuditDetail } from "@/components/admin/reasoning-audit-detail";
import type {
  AdminSignalsPage,
  AdminSignalSummary,
  ReasoningAudit,
} from "@/lib/admin/reasoning-audit-types";
import { formatPredictedChange } from "@/lib/signal-utils";

const PAGE_SIZE = 25;

async function fetchTickers(): Promise<string[]> {
  const res = await fetch("/api/admin/signals/tickers", { cache: "no-store" });
  if (!res.ok) return [];
  return res.json() as Promise<string[]>;
}

async function fetchSignals(
  ticker: string,
  page: number,
): Promise<AdminSignalsPage | null> {
  const params = new URLSearchParams({ page: String(page), size: String(PAGE_SIZE) });
  if (ticker) params.set("ticker", ticker);
  const res = await fetch(`/api/admin/signals?${params}`, { cache: "no-store" });
  if (!res.ok) return null;
  return res.json() as Promise<AdminSignalsPage>;
}

async function fetchAudit(signalId: string): Promise<ReasoningAudit | null> {
  const res = await fetch(
    `/api/admin/signals/${encodeURIComponent(signalId)}/reasoning-audit`,
    { cache: "no-store" },
  );
  if (!res.ok) return null;
  return res.json() as Promise<ReasoningAudit>;
}

export function ReasoningAuditExplorer() {
  const [ticker, setTicker] = useState<string>("");
  const [page, setPage] = useState(0);
  const [selectedSignalId, setSelectedSignalId] = useState<string | null>(null);

  const tickersQuery = useQuery({
    queryKey: ["admin-tickers"],
    queryFn: fetchTickers,
    staleTime: 5 * 60 * 1000,
  });

  const signalsQuery = useQuery({
    queryKey: ["admin-signals", ticker, page],
    queryFn: () => fetchSignals(ticker, page),
  });

  const auditQuery = useQuery({
    queryKey: ["admin-audit", selectedSignalId],
    queryFn: () => (selectedSignalId ? fetchAudit(selectedSignalId) : null),
    enabled: !!selectedSignalId,
  });

  const tickers = tickersQuery.data ?? [];
  const signalsPage = signalsQuery.data ?? null;

  return (
    <div className="grid grid-cols-1 gap-6 lg:grid-cols-[1fr_minmax(0,1.2fr)]">
      <section className="space-y-4">
        <div className="flex items-end gap-3">
          <label className="block">
            <span className="text-xs uppercase tracking-wide text-text-2">
              Ticker filter
            </span>
            <select
              className="mt-1 block w-48 rounded border border-border bg-bg-2 px-3 py-2 text-text-1 focus:border-cyan focus:outline-none"
              value={ticker}
              onChange={(e) => {
                setTicker(e.target.value);
                setPage(0);
                setSelectedSignalId(null);
              }}
            >
              <option value="">All tickers</option>
              {tickers.map((t) => (
                <option key={t} value={t}>
                  {t}
                </option>
              ))}
            </select>
          </label>
          <div className="text-xs text-text-2">
            {signalsPage
              ? `${signalsPage.totalElements} signal${signalsPage.totalElements === 1 ? "" : "s"}`
              : ""}
          </div>
        </div>

        <div className="overflow-hidden rounded border border-border">
          <table className="min-w-full divide-y divide-border text-sm">
            <thead className="bg-bg-2 text-left text-xs uppercase tracking-wide text-text-2">
              <tr>
                <th className="px-3 py-2">Generated</th>
                <th className="px-3 py-2">Ticker</th>
                <th className="px-3 py-2">Type</th>
                <th className="px-3 py-2">Confidence</th>
                <th className="px-3 py-2">Expected move</th>
                <th className="px-3 py-2">Status</th>
                <th className="px-3 py-2">Outcome</th>
                <th className="px-3 py-2">Retry</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-border bg-bg-1">
              {signalsQuery.isLoading && (
                <tr>
                  <td className="px-3 py-4 text-text-2" colSpan={7}>
                    Loading…
                  </td>
                </tr>
              )}
              {signalsQuery.isError && (
                <tr>
                  <td className="px-3 py-4 text-red" colSpan={7}>
                    Failed to load signals
                  </td>
                </tr>
              )}
              {signalsPage?.empty && (
                <tr>
                  <td className="px-3 py-4 text-text-2" colSpan={7}>
                    No signals match the filter.
                  </td>
                </tr>
              )}
              {signalsPage?.content.map((row) => (
                <SignalRow
                  key={row.id}
                  row={row}
                  selected={row.id === selectedSignalId}
                  onSelect={() => setSelectedSignalId(row.id)}
                />
              ))}
            </tbody>
          </table>
        </div>

        {signalsPage && signalsPage.totalPages > 1 && (
          <div className="flex items-center justify-between text-sm text-text-2">
            <button
              type="button"
              className="rounded border border-border px-2 py-1 disabled:opacity-40"
              disabled={signalsPage.first}
              onClick={() => setPage((p) => Math.max(0, p - 1))}
            >
              ← Prev
            </button>
            <span>
              Page {signalsPage.number + 1} of {signalsPage.totalPages}
            </span>
            <button
              type="button"
              className="rounded border border-border px-2 py-1 disabled:opacity-40"
              disabled={signalsPage.last}
              onClick={() => setPage((p) => p + 1)}
            >
              Next →
            </button>
          </div>
        )}
      </section>

      <section
        aria-label="Reasoning audit detail"
        className="rounded border border-border bg-bg-1 p-4"
      >
        {!selectedSignalId && (
          <p className="text-text-2">Select a signal to inspect its reasoning artifact.</p>
        )}
        {selectedSignalId && auditQuery.isLoading && (
          <p className="text-text-2">Loading audit…</p>
        )}
        {selectedSignalId && auditQuery.isError && (
          <p className="text-red">Failed to load reasoning audit.</p>
        )}
        {selectedSignalId && auditQuery.data && (
          <ReasoningAuditDetail audit={auditQuery.data} />
        )}
      </section>
    </div>
  );
}

function SignalRow({
  row,
  selected,
  onSelect,
}: {
  row: AdminSignalSummary;
  selected: boolean;
  onSelect: () => void;
}) {
  const confidence =
    row.confidence != null ? `${(row.confidence * 100).toFixed(0)}%` : "—";
  const generated = new Date(row.generatedAt).toLocaleString(undefined, {
    month: "short",
    day: "numeric",
    hour: "numeric",
    minute: "2-digit",
  });
  const typeClass = row.signalType === "BUY" ? "text-green" : row.signalType === "SELL" ? "text-red" : "text-gold";
  const expectedMove =
    row.predictedChangePct != null
      ? formatPredictedChange(row.predictedChangePct, row.signalType)
      : null;
  return (
    <tr
      onClick={onSelect}
      className={`cursor-pointer hover:bg-bg-2 ${selected ? "bg-bg-3" : ""}`}
    >
      <td className="px-3 py-2 text-text-2">{generated}</td>
      <td className="px-3 py-2 font-medium text-text-1">{row.ticker ?? "—"}</td>
      <td className={`px-3 py-2 font-medium ${typeClass}`}>{row.signalType}</td>
      <td className="px-3 py-2 text-text-1">{confidence}</td>
      <td className={`px-3 py-2 font-mono ${expectedMove?.colorClass ?? "text-text-2"}`}>
        {expectedMove?.label ?? "—"}
      </td>
      <td className="px-3 py-2 text-text-2">{row.reasoningStatus}</td>
      <td className="px-3 py-2 text-text-2">{row.reasoningOutcome ?? "—"}</td>
      <td className="px-3 py-2 text-text-2">
        {row.reasoningRetryCount != null ? row.reasoningRetryCount : "—"}
      </td>
    </tr>
  );
}
