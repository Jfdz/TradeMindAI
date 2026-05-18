"use client";

import type { ReasoningAudit } from "@/lib/admin/reasoning-audit-types";
import { calculateExpectedMovePct } from "@/lib/signal-utils";

function formatTimestamp(value: string | null | undefined): string {
  if (!value) return "—";
  try {
    return new Date(value).toLocaleString();
  } catch {
    return value;
  }
}

function outcomeClass(outcome: string | null): string {
  if (!outcome) return "text-text-2";
  if (outcome === "GENERATED") return "text-green";
  if (outcome.startsWith("REFUSED_")) return "text-gold";
  return "text-red";
}

function badge(audit: ReasoningAudit): { text: string; className: string } {
  if (audit.outcome) {
    return { text: audit.outcome, className: outcomeClass(audit.outcome) };
  }
  if (audit.reasoningStatus === "PENDING") {
    return { text: "awaiting reasoning", className: "text-text-2" };
  }
  if (audit.reasoningStatus) {
    return { text: audit.reasoningStatus.toLowerCase(), className: "text-text-2" };
  }
  return { text: "no artifact", className: "text-text-2" };
}

function formatPrice(value: number | null): string {
  return value != null ? `$${value.toFixed(2)}` : "—";
}

function formatPct(value: number | null): string {
  return value != null ? `${value.toFixed(2)}%` : "—";
}

export function ReasoningAuditDetail({ audit }: { audit: ReasoningAudit }) {
  const b = badge(audit);
  const hasPricing =
    audit.entryPrice != null
    || audit.targetPrice != null
    || audit.stopLoss != null
    || audit.expectedMovePct != null;
  return (
    <article className="space-y-5 text-sm">
      <header className="space-y-1">
        <div className="flex items-baseline gap-3">
          <h2 className="text-lg font-semibold text-text-1">
            {audit.ticker ?? "?"}{" "}
            <span className="text-text-2 text-xs font-normal">
              signal {audit.signalId.slice(0, 8)}…
            </span>
          </h2>
          <span className={`text-xs font-medium uppercase ${b.className}`}>{b.text}</span>
        </div>
        <div className="grid grid-cols-2 gap-x-4 gap-y-1 text-xs text-text-2 sm:grid-cols-4">
          <MetaCell label="Status" value={audit.reasoningStatus} />
          <MetaCell label="Provider" value={audit.provider} />
          <MetaCell label="Model" value={audit.modelVersion} />
          <MetaCell
            label="Retry"
            value={audit.retryCount != null ? String(audit.retryCount) : null}
          />
          <MetaCell label="Signal generated" value={formatTimestamp(audit.signalGeneratedAt)} />
          <MetaCell label="Reasoning generated" value={formatTimestamp(audit.reasoningGeneratedAt)} />
        </div>
      </header>

      {hasPricing && (
        <Section title="Pricing">
          <div className="grid grid-cols-2 gap-2 text-xs sm:grid-cols-4">
            <PriceCell label="Entry" value={formatPrice(audit.entryPrice)} />
            <PriceCell label="Target" value={formatPrice(audit.targetPrice)} />
            <PriceCell label="Stop" value={formatPrice(audit.stopLoss)} />
            <PriceCell label="Expected move" value={formatPct(calculateExpectedMovePct(audit.entryPrice, audit.targetPrice) ?? audit.expectedMovePct)} />
          </div>
        </Section>
      )}

      <Section title="Reasoning text">
        {audit.reasoning ? (
          <p className="whitespace-pre-wrap text-text-1">{audit.reasoning}</p>
        ) : (
          <p className="text-text-3 italic">(none)</p>
        )}
      </Section>

      {audit.refusalReason && (
        <Section title="Refusal reason">
          <pre className="whitespace-pre-wrap rounded bg-bg-0 p-2 text-xs text-gold">
            {audit.refusalReason}
          </pre>
        </Section>
      )}

      <Section title="Citations">
        <div className="space-y-2">
          <ChipsRow label="price_refs" items={audit.priceRefs ?? []} />
          <ChipsRow
            label="news_refs"
            items={audit.newsRefs ?? []}
            renderItem={(url) => (
              <a
                key={url}
                href={url}
                target="_blank"
                rel="noopener noreferrer"
                className="rounded bg-bg-3 px-2 py-0.5 text-xs text-cyan hover:text-cyan-bright"
              >
                {shortUrl(url)}
              </a>
            )}
          />
        </div>
      </Section>

      {audit.validatorViolations && audit.validatorViolations.length > 0 && (
        <Section title={`Validator violations (${audit.validatorViolations.length})`}>
          <ul className="space-y-2">
            {audit.validatorViolations.map((v, i) => (
              <li
                key={i}
                className="rounded border border-red/40 bg-red/10 p-2 text-xs"
              >
                <span className="font-semibold text-red">
                  {String(v.type ?? "violation")}
                </span>
                <span className="ml-2 text-text-1">{String(v.detail ?? "")}</span>
              </li>
            ))}
          </ul>
        </Section>
      )}

      <Section title="Facts snapshot">
        <JsonBlob value={audit.factsSnapshot} />
      </Section>

      <Section title="Raw provider audit">
        <JsonBlob value={audit.rawAudit} />
      </Section>
    </article>
  );
}

function MetaCell({ label, value }: { label: string; value: string | null | undefined }) {
  return (
    <div>
      <div className="uppercase tracking-wide">{label}</div>
      <div className="text-text-1">{value || "—"}</div>
    </div>
  );
}

function PriceCell({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <div className="uppercase tracking-wide text-text-2">{label}</div>
      <div className="text-text-1">{value}</div>
    </div>
  );
}

function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <section>
      <h3 className="mb-1 text-xs font-semibold uppercase tracking-wide text-text-2">
        {title}
      </h3>
      {children}
    </section>
  );
}

function ChipsRow({
  label,
  items,
  renderItem,
}: {
  label: string;
  items: string[];
  renderItem?: (item: string) => React.ReactNode;
}) {
  if (items.length === 0) {
    return (
      <div className="text-xs">
        <span className="mr-2 text-text-2">{label}:</span>
        <span className="text-text-3 italic">(none)</span>
      </div>
    );
  }
  return (
    <div className="flex flex-wrap items-center gap-1 text-xs">
      <span className="mr-1 text-text-2">{label}:</span>
      {items.map((item) =>
        renderItem ? (
          renderItem(item)
        ) : (
          <span key={item} className="rounded bg-bg-3 px-2 py-0.5 text-text-1">
            {item}
          </span>
        ),
      )}
    </div>
  );
}

function JsonBlob({ value }: { value: Record<string, unknown> | null | undefined }) {
  if (!value || Object.keys(value).length === 0) {
    return <p className="text-text-3 italic">(none)</p>;
  }
  return (
    <details className="rounded border border-border bg-bg-0">
      <summary className="cursor-pointer px-2 py-1 text-xs text-text-2 hover:text-text-1">
        Show {Object.keys(value).length} field
        {Object.keys(value).length === 1 ? "" : "s"}
      </summary>
      <pre className="overflow-auto px-3 py-2 text-xs text-text-1">
        {JSON.stringify(value, null, 2)}
      </pre>
    </details>
  );
}

function shortUrl(url: string): string {
  try {
    const parsed = new URL(url);
    return `${parsed.hostname}${parsed.pathname.length > 1 ? parsed.pathname.slice(0, 40) : ""}`;
  } catch {
    return url.slice(0, 60);
  }
}
