import { useMemo } from "react";
import { useQuery } from "@tanstack/react-query";

export function useStockLogos(symbols: string[]): Record<string, string | null> | undefined {
  const tickers = useMemo(() => [...new Set(symbols)], [symbols]);
  const { data } = useQuery<Record<string, string | null>>({
    queryKey: ["logos", tickers],
    queryFn: async () => {
      if (tickers.length === 0) return {};
      const res = await fetch(`/api/stocks/logos?tickers=${encodeURIComponent(tickers.join(","))}`);
      if (!res.ok) return {};
      return res.json() as Promise<Record<string, string | null>>;
    },
    enabled: tickers.length > 0,
    staleTime: 60 * 60 * 1000,
  });
  return data;
}
