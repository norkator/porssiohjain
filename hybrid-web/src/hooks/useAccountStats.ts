/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026  Martin Kankaanranta / Nitramite Tmi
 *
 * This source code is licensed under the Pörssiohjain Personal Use License v1.0.
 * Private self-hosting for personal household use is permitted.
 * Commercial use, resale, managed hosting, or offering the software as a
 * service to third parties requires separate written permission.
 * See LICENSE for details.
 */

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
