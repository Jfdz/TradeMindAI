import { useEffect, useRef } from "react";
import { toast } from "sonner";

import type { FilteredSignal } from "@/lib/dashboard/dashboard-api";

/**
 * Fires a sonner toast when a user-selected signal ages out of the LIVE window
 * and falls back to the next live signal (or empty state).
 *
 * Only triggers if the user has actively picked something — default top-of-list
 * focus aging out is communicated via the page-level empty state instead.
 */
export function useAgeOutToast(
  liveSignals: readonly FilteredSignal[],
  selectedSignalId: string | null,
  topLiveSignal: FilteredSignal | null,
  clearSelection: () => void,
): void {
  const lastSelectedRef = useRef<FilteredSignal | null>(null);

  useEffect(() => {
    if (!selectedSignalId) {
      lastSelectedRef.current = null;
      return;
    }
    const stillLive = liveSignals.find((s) => s.id === selectedSignalId);
    if (stillLive) {
      lastSelectedRef.current = stillLive;
      return;
    }
    const aged = lastSelectedRef.current;
    if (!aged) return;
    toast.info(`${aged.symbol} ${aged.type} aged out of LIVE`, {
      description: topLiveSignal
        ? `Switched to ${topLiveSignal.symbol} ${topLiveSignal.type}`
        : "No live signals right now",
    });
    clearSelection();
    lastSelectedRef.current = null;
  }, [liveSignals, selectedSignalId, topLiveSignal, clearSelection]);
}
