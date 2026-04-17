"use client";

import Link from "next/link";
import { useEffect, useState } from "react";

import {
  formatConfidence,
  formatPrice,
  formatSignalDate,
  signalRecords,
  type SignalRecord,
  type SignalType,
} from "@/lib/dashboard/signals";

const signalTypes: Array<SignalType | "ALL"> = ["ALL", "BUY", "SELL", "HOLD"];
const pageSize = 4;

function sortSignals(signals: SignalRecord[], sortBy: string) {
  const items = [...signals];

  if (sortBy === "confidence") {
    return items.sort((left, right) => right.confidence - left.confidence);
  }

  if (sortBy === "price") {
    return items.sort((left, right) => right.price - left.price);
  }

  return items.sort((left, right) => Number(new Date(right.date)) - Number(new Date(left.date)));
}

export default function SignalsPage() {
  const [search, setSearch] = useState("");
  const [selectedType, setSelectedType] = useState<(typeof signalTypes)[number]>("ALL");
  const [sortBy, setSortBy] = useState("recent");
  const [page, setPage] = useState(1);

  const filteredSignals = sortSignals(
    signalRecords.filter((signal) => {
      const matchesSearch = [signal.symbol, signal.type, signal.timeframe].some((value) =>
        value.toLowerCase().includes(search.trim().toLowerCase())
      );
      const matchesType = selectedType === "ALL" || signal.type === selectedType;

      return matchesSearch && matchesType;
    }),
    sortBy
  );

  const pageCount = Math.max(1, Math.ceil(filteredSignals.length / pageSize));
  const currentPage = Math.min(page, pageCount);
  const pagedSignals = filteredSignals.slice((currentPage - 1) * pageSize, currentPage * pageSize);

  useEffect(() => {
    setPage(1);
  }, [search, selectedType, sortBy]);

  return (
    <div className="space-y-8">
      <section className="rounded-[2rem] border border-white/10 bg-white/5 p-6 shadow-glow">
        <div className="flex flex-col gap-4 lg:flex-row lg:items-end lg:justify-between">
          <div>
            <p className="text-xs uppercase tracking-[0.35em] text-gold-300/80">Signals</p>
            <h1 className="mt-3 text-3xl font-semibold text-white">Filter and review trading signals</h1>
            <p className="mt-3 max-w-2xl text-sm leading-7 text-slate-300">
              Review the latest model output, narrow the list with symbol and type filters, and sort by recency,
              confidence, or price before opening the full signal detail page.
            </p>
          </div>

          <div className="rounded-3xl border border-white/10 bg-ink-800/70 px-5 py-4 text-sm text-slate-200">
            <p className="text-xs uppercase tracking-[0.35em] text-gold-300/80">Visible</p>
            <p className="mt-2 text-2xl font-semibold text-white">{filteredSignals.length}</p>
            <p className="mt-1 text-xs uppercase tracking-[0.3em] text-slate-400">of {signalRecords.length} signals</p>
          </div>
        </div>

        <div className="mt-6 grid gap-4 lg:grid-cols-[1fr_auto_auto]">
          <label className="block">
            <span className="mb-2 block text-xs uppercase tracking-[0.3em] text-slate-400">Search</span>
            <input
              className="w-full rounded-2xl border border-white/10 bg-ink-800/80 px-4 py-3 text-sm text-white outline-none ring-0 placeholder:text-slate-500 focus:border-gold-300/40"
              onChange={(event) => setSearch(event.target.value)}
              placeholder="Symbol, type, or timeframe"
              value={search}
            />
          </label>

          <label className="block min-w-40">
            <span className="mb-2 block text-xs uppercase tracking-[0.3em] text-slate-400">Type</span>
            <select
              className="w-full rounded-2xl border border-white/10 bg-ink-800/80 px-4 py-3 text-sm text-white outline-none focus:border-gold-300/40"
              onChange={(event) => setSelectedType(event.target.value as (typeof signalTypes)[number])}
              value={selectedType}
            >
              {signalTypes.map((type) => (
                <option key={type} value={type}>
                  {type === "ALL" ? "All signals" : type}
                </option>
              ))}
            </select>
          </label>

          <label className="block min-w-44">
            <span className="mb-2 block text-xs uppercase tracking-[0.3em] text-slate-400">Sort by</span>
            <select
              className="w-full rounded-2xl border border-white/10 bg-ink-800/80 px-4 py-3 text-sm text-white outline-none focus:border-gold-300/40"
              onChange={(event) => setSortBy(event.target.value)}
              value={sortBy}
            >
              <option value="recent">Recent</option>
              <option value="confidence">Confidence</option>
              <option value="price">Price</option>
            </select>
          </label>
        </div>
      </section>

      <section className="rounded-[2rem] border border-white/10 bg-white/5 p-6 shadow-glow">
        <div className="overflow-hidden rounded-3xl border border-white/10">
          <table className="min-w-full divide-y divide-white/10">
            <thead className="bg-white/5">
              <tr className="text-left text-xs uppercase tracking-[0.3em] text-slate-400">
                <th className="px-5 py-4">Symbol</th>
                <th className="px-5 py-4">Type</th>
                <th className="px-5 py-4">Confidence</th>
                <th className="px-5 py-4">Price</th>
                <th className="px-5 py-4">Date</th>
                <th className="px-5 py-4">Timeframe</th>
                <th className="px-5 py-4">Action</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-white/10 bg-ink-800/50">
              {pagedSignals.map((signal) => (
                <tr key={signal.id} className="text-sm text-slate-200 transition hover:bg-white/5">
                  <td className="px-5 py-4 font-semibold text-white">{signal.symbol}</td>
                  <td className="px-5 py-4">{signal.type}</td>
                  <td className="px-5 py-4">{formatConfidence(signal.confidence)}</td>
                  <td className="px-5 py-4">{formatPrice(signal.price)}</td>
                  <td className="px-5 py-4">{formatSignalDate(signal.date)}</td>
                  <td className="px-5 py-4">{signal.timeframe}</td>
                  <td className="px-5 py-4">
                    <Link
                      className="rounded-full border border-gold-300/20 bg-gold-300/10 px-4 py-2 text-xs uppercase tracking-[0.3em] text-gold-300 transition hover:border-gold-300/40 hover:bg-gold-300/20"
                      href={`/dashboard/signals/${signal.id}`}
                    >
                      Open
                    </Link>
                  </td>
                </tr>
              ))}
              {pagedSignals.length === 0 ? (
                <tr>
                  <td className="px-5 py-10 text-center text-sm text-slate-400" colSpan={7}>
                    No signals match the current filters.
                  </td>
                </tr>
              ) : null}
            </tbody>
          </table>
        </div>

        <div className="mt-6 flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
          <p className="text-sm text-slate-300">
            Showing {pagedSignals.length} of {filteredSignals.length} matching signals.
          </p>

          <div className="flex items-center gap-3">
            <button
              className="rounded-full border border-white/10 bg-white/5 px-4 py-2 text-xs uppercase tracking-[0.3em] text-slate-200 disabled:opacity-40"
              disabled={currentPage === 1}
              onClick={() => setPage((value) => Math.max(1, value - 1))}
              type="button"
            >
              Previous
            </button>
            <span className="text-sm text-slate-300">
              Page {currentPage} of {pageCount}
            </span>
            <button
              className="rounded-full border border-white/10 bg-white/5 px-4 py-2 text-xs uppercase tracking-[0.3em] text-slate-200 disabled:opacity-40"
              disabled={currentPage === pageCount}
              onClick={() => setPage((value) => Math.min(pageCount, value + 1))}
              type="button"
            >
              Next
            </button>
          </div>
        </div>
      </section>
    </div>
  );
}
