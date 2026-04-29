"use client";

import { useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { useRouter } from "next/navigation";

import { apiClient, type AddPositionPayload } from "@/lib/api-client";

export default function AddPositionPage() {
  const router = useRouter();
  const queryClient = useQueryClient();
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const { data: symbols = [] } = useQuery({
    queryKey: ["symbols"],
    queryFn: async () => (await apiClient.getSymbols()).content,
  });

  const [form, setForm] = useState<{
    ticker: string;
    quantity: string;
    entryPrice: string;
    purchaseDate: string;
    fees: string;
    notes: string;
  }>({
    ticker: "",
    quantity: "",
    entryPrice: "",
    purchaseDate: new Date().toISOString().slice(0, 10),
    fees: "",
    notes: "",
  });

  function set(field: string, value: string) {
    setForm((prev) => ({ ...prev, [field]: value }));
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);

    const quantity = parseFloat(form.quantity);
    const entryPrice = parseFloat(form.entryPrice);

    if (!form.ticker) { setError("Ticker is required"); return; }
    if (isNaN(quantity) || quantity <= 0) { setError("Quantity must be a positive number"); return; }
    if (isNaN(entryPrice) || entryPrice <= 0) { setError("Entry price must be a positive number"); return; }

    const payload: AddPositionPayload = {
      ticker: form.ticker,
      quantity,
      entryPrice,
      purchaseDate: form.purchaseDate || undefined,
      fees: form.fees ? parseFloat(form.fees) : undefined,
      notes: form.notes || undefined,
    };

    setIsSubmitting(true);
    try {
      await apiClient.addPosition(payload);
      await queryClient.invalidateQueries({ queryKey: ["portfolio"] });
      router.push("/dashboard/portfolio");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to add position");
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <div className="mx-auto max-w-xl space-y-6">
      <div>
        <div className="font-mono text-[11px] uppercase tracking-[0.22em] text-cyan">Portfolio</div>
        <h1 className="mt-2 font-display text-[clamp(24px,3vw,36px)] font-bold tracking-[-0.05em] text-white">
          Add Position
        </h1>
        <p className="mt-2 text-sm text-text-2">Manually log a position to track its P&amp;L against live prices.</p>
      </div>

      <form onSubmit={handleSubmit} className="space-y-5 rounded-[24px] border border-border bg-bg-1/80 p-6 shadow-glow">
        {error && (
          <div className="rounded-2xl border border-red/30 bg-red/10 px-4 py-3 text-sm text-red">{error}</div>
        )}

        <div className="space-y-1">
          <label className="font-mono text-[11px] uppercase tracking-[0.22em] text-text-3">Ticker</label>
          {symbols.length > 0 ? (
            <select
              value={form.ticker}
              onChange={(e) => set("ticker", e.target.value)}
              className="w-full rounded-xl border border-border bg-bg-2 px-3 py-2 text-sm text-white focus:border-cyan focus:outline-none"
            >
              <option value="">Select a symbol…</option>
              {symbols.filter((s) => s.active).map((s) => (
                <option key={s.ticker} value={s.ticker}>
                  {s.ticker} — {s.name}
                </option>
              ))}
            </select>
          ) : (
            <input
              type="text"
              value={form.ticker}
              onChange={(e) => set("ticker", e.target.value.toUpperCase())}
              placeholder="AAPL"
              maxLength={10}
              className="w-full rounded-xl border border-border bg-bg-2 px-3 py-2 text-sm text-white placeholder-text-3 focus:border-cyan focus:outline-none"
            />
          )}
        </div>

        <div className="grid grid-cols-2 gap-4">
          <div className="space-y-1">
            <label className="font-mono text-[11px] uppercase tracking-[0.22em] text-text-3">Quantity</label>
            <input
              type="number"
              value={form.quantity}
              onChange={(e) => set("quantity", e.target.value)}
              placeholder="10"
              min="0"
              step="any"
              className="w-full rounded-xl border border-border bg-bg-2 px-3 py-2 text-sm text-white placeholder-text-3 focus:border-cyan focus:outline-none"
            />
          </div>

          <div className="space-y-1">
            <label className="font-mono text-[11px] uppercase tracking-[0.22em] text-text-3">Entry Price (USD)</label>
            <input
              type="number"
              value={form.entryPrice}
              onChange={(e) => set("entryPrice", e.target.value)}
              placeholder="170.00"
              min="0"
              step="any"
              className="w-full rounded-xl border border-border bg-bg-2 px-3 py-2 text-sm text-white placeholder-text-3 focus:border-cyan focus:outline-none"
            />
          </div>
        </div>

        <div className="grid grid-cols-2 gap-4">
          <div className="space-y-1">
            <label className="font-mono text-[11px] uppercase tracking-[0.22em] text-text-3">Purchase Date</label>
            <input
              type="date"
              value={form.purchaseDate}
              onChange={(e) => set("purchaseDate", e.target.value)}
              className="w-full rounded-xl border border-border bg-bg-2 px-3 py-2 text-sm text-white focus:border-cyan focus:outline-none"
            />
          </div>

          <div className="space-y-1">
            <label className="font-mono text-[11px] uppercase tracking-[0.22em] text-text-3">Fees (optional)</label>
            <input
              type="number"
              value={form.fees}
              onChange={(e) => set("fees", e.target.value)}
              placeholder="0.00"
              min="0"
              step="any"
              className="w-full rounded-xl border border-border bg-bg-2 px-3 py-2 text-sm text-white placeholder-text-3 focus:border-cyan focus:outline-none"
            />
          </div>
        </div>

        <div className="space-y-1">
          <label className="font-mono text-[11px] uppercase tracking-[0.22em] text-text-3">Notes (optional)</label>
          <textarea
            value={form.notes}
            onChange={(e) => set("notes", e.target.value)}
            placeholder="e.g. Earnings play, long-term hold…"
            rows={2}
            className="w-full resize-none rounded-xl border border-border bg-bg-2 px-3 py-2 text-sm text-white placeholder-text-3 focus:border-cyan focus:outline-none"
          />
        </div>

        <div className="flex gap-3 pt-2">
          <button
            type="button"
            onClick={() => router.push("/dashboard/portfolio")}
            className="flex-1 rounded-full border border-border px-4 py-2 text-sm text-text-2 transition-colors hover:border-text-2 hover:text-white"
          >
            Cancel
          </button>
          <button
            type="submit"
            disabled={isSubmitting}
            className="flex-1 rounded-full bg-cyan px-4 py-2 text-sm font-semibold text-black transition-opacity disabled:opacity-50"
          >
            {isSubmitting ? "Adding…" : "Add Position"}
          </button>
        </div>
      </form>
    </div>
  );
}
