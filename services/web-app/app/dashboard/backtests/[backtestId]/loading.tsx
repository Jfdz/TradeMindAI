import { RouteLoading } from "@/components/route-loading";

export default function Loading() {
  return <RouteLoading eyebrow="Backtest Report" title="Loading backtest report" description="Fetching run status, metrics, and trade history." />;
}
