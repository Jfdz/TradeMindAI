"use client";

import { useEffect, useState } from "react";

import { apiClient, type SignalResponse } from "@/lib/api-client";

export function SignalsPreview() {
  const [signals, setSignals] = useState<SignalResponse[]>([]);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let active = true;

    async function load() {
      try {
        const page = await apiClient.getSignals();
        if (active) {
          setSignals(page.content.slice(0, 3));
        }
      } catch (err) {
        if (active) {
          setError(err instanceof Error ? err.message : "Unable to load signals");
        }
      }
    }

    void load();

    return () => {
      active = false;
    };
  }, []);

  return (
    <section className="border-t border-white/10 py-8">
      <div className="flex items-center justify-between">
        <div>
          <p className="text-xs uppercase tracking-[0.35em] text-gold-300/80">API client check</p>
          <h3 className="mt-2 text-2xl font-semibold text-white">Recent signals through `apiClient.getSignals()`</h3>
        </div>
        <span className="rounded-full border border-white/10 bg-white/5 px-4 py-2 text-xs uppercase tracking-[0.3em] text-slate-300">
          {signals.length} loaded
        </span>
      </div>

      <div className="mt-6 grid gap-4 md:grid-cols-3">
        {signals.length > 0 ? (
          signals.map((signal) => (
            <article key={signal.id} className="rounded-3xl border border-white/10 bg-white/5 p-5">
              <p className="text-xs uppercase tracking-[0.3em] text-slate-400">{signal.timeframe}</p>
              <h4 className="mt-3 text-xl font-semibold text-white">{signal.type}</h4>
              <p className="mt-2 text-sm text-slate-300">Confidence {(signal.confidence * 100).toFixed(1)}%</p>
              <p className="mt-1 text-sm text-slate-300">Generated at {new Date(signal.generatedAt).toLocaleString()}</p>
            </article>
          ))
        ) : (
          <div className="rounded-3xl border border-dashed border-white/15 bg-white/5 p-6 text-sm text-slate-300 md:col-span-3">
            {error
              ? `The API client is wired but the backend is unavailable: ${error}`
              : "The API client is wired and ready. Once trading-core is available, this panel will render live signals."}
          </div>
        )}
      </div>
    </section>
  );
}
