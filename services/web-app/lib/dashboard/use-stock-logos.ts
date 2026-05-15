import { useMemo } from "react";
import { useQuery } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";

/**
 * Resolves logo URLs for a set of tickers via the client-side apiClient
 * (browser → public trading-core API with the session Bearer token) —
 * the same auth mechanism signals/prices/portfolio use and that is
 * proven to work in production. The previous implementation went through
 * a Next server route whose getServerSession proxy never surfaced the
 * accessToken, so every logo resolved to null.
 */
export function useStockLogos(symbols: string[]): Record<string, string | null> | undefined {
  const tickers = useMemo(() => [...new Set(symbols)], [symbols]);
  const { data } = useQuery<Record<string, string | null>>({
    queryKey: ["logos", tickers],
    queryFn: async () => {
      if (tickers.length === 0) return {};
      const entries = await Promise.all(
        tickers.map(async (ticker) => {
          const logo = await apiClient.getCompanyLogo(ticker);
          return [ticker, logo] as const;
        }),
      );
      return Object.fromEntries(entries);
    },
    enabled: tickers.length > 0,
    staleTime: 60 * 60 * 1000,
  });
  return data;
}
