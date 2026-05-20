import { Suspense } from "react";
import { notFound } from "next/navigation";
import { auth } from "@clerk/nextjs/server";
import { AISignalSection } from "@/components/stocks/ai-signal-section";
import { AnalystRecommendationsBar } from "@/components/stocks/analyst-recommendations-bar";
import { CompanyHeader } from "@/components/stocks/company-header";
import { EarningsBadge } from "@/components/stocks/earnings-badge";
import { FundamentalsPanel } from "@/components/stocks/fundamentals-panel";
import { NewsFeed } from "@/components/stocks/news-feed";
import { PeersList } from "@/components/stocks/peers-list";
import { TradingViewAdvancedChart } from "@/components/stocks/tradingview-advanced-chart";
import { TradingViewAttribution } from "@/components/stocks/tradingview-attribution";
import {
  fetchEarnings,
  fetchPeers,
  fetchProfile,
  fetchRecommendations,
} from "@/lib/enrichment-client";

type Props = {
  readonly params: Promise<{ readonly ticker: string }>;
};

export const revalidate = 600;

export default async function StockDetailPage({ params }: Props) {
  const { getToken } = await auth();
  const token = await getToken({ template: "backend" });
  if (!token) {
    notFound();
  }

  const { ticker } = await params;

  const [profileResult, earningsResult, recsResult, peersResult] =
    await Promise.allSettled([
      fetchProfile(ticker, token),
      fetchEarnings(ticker, token),
      fetchRecommendations(ticker, token),
      fetchPeers(ticker, token),
    ]);

  const profile = profileResult.status === "fulfilled" ? profileResult.value : null;
  const earnings = earningsResult.status === "fulfilled" ? earningsResult.value : [];
  const recommendations = recsResult.status === "fulfilled" ? recsResult.value : [];
  const peers = peersResult.status === "fulfilled" ? peersResult.value : [];

  return (
    <main className="mx-auto max-w-[1280px] space-y-10 px-6 pb-16 pt-8">
      <CompanyHeader profile={profile} ticker={ticker} />

      <div className="space-y-2">
        <div className="h-[360px] sm:h-[560px] rounded-xl overflow-hidden">
          <Suspense fallback={<div className="h-full animate-pulse rounded-xl bg-card" />}>
            <TradingViewAdvancedChart symbol={ticker} />
          </Suspense>
        </div>
        <TradingViewAttribution />
      </div>

      <div className="grid gap-6 lg:grid-cols-[1.5fr_1fr]">
        <section className="min-w-0 space-y-3">
          <h2 className="text-sm font-semibold uppercase tracking-wider text-muted-foreground">News</h2>
          <NewsFeed ticker={ticker} />
        </section>

        <aside className="space-y-4">
          <EarningsBadge earnings={earnings} />
          <FundamentalsPanel profile={profile} />
          <AnalystRecommendationsBar recommendations={recommendations} />
          <PeersList peers={peers} />
          <AISignalSection ticker={ticker} />
        </aside>
      </div>
    </main>
  );
}
