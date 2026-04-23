import { useEffect, useState } from "react";
import {
  fetchAccountStats,
  getTotalConsumptionKw,
  getTotalProductionKw,
  getTotalProductionPeakKw,
  type AccountStats
} from "@/lib/account-stats";

type UseAccountStatsState = {
  stats: AccountStats | null;
  isLoading: boolean;
  error: string | null;
};

export function useAccountStats() {
  const [state, setState] = useState<UseAccountStatsState>({
    stats: null,
    isLoading: true,
    error: null
  });

  useEffect(() => {
    let isActive = true;

    async function loadStats() {
      try {
        const stats = await fetchAccountStats();

        if (!isActive) {
          return;
        }

        setState({
          stats,
          isLoading: false,
          error: null
        });
      } catch (error) {
        if (!isActive) {
          return;
        }

        setState({
          stats: null,
          isLoading: false,
          error: error instanceof Error ? error.message : "Failed to load account stats"
        });
      }
    }

    loadStats();

    return () => {
      isActive = false;
    };
  }, []);

  return {
    ...state,
    totalConsumptionKw: state.stats ? getTotalConsumptionKw(state.stats) : 0,
    totalProductionKw: state.stats ? getTotalProductionKw(state.stats) : 0,
    totalProductionPeakKw: state.stats ? getTotalProductionPeakKw(state.stats) : 0
  };
}
