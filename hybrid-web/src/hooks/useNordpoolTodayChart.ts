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
import { getCurrentTimezone } from "@/lib/add-device-flow";
import { fetchCachedNordpoolTodayChart, fetchCachedNordpoolTomorrowChart, type NordpoolTodayChart } from "@/lib/nordpool";

type UseNordpoolTodayChartState = {
  chart: NordpoolTodayChart | null;
  tomorrowChart: NordpoolTodayChart | null;
  isLoading: boolean;
  error: string | null;
};

export function useNordpoolTodayChart() {
  const [state, setState] = useState<UseNordpoolTodayChartState>({
    chart: null,
    tomorrowChart: null,
    isLoading: true,
    error: null
  });

  useEffect(() => {
    let isActive = true;
    const timezone = getCurrentTimezone();

    async function loadChart() {
      try {
        const chart = await fetchCachedNordpoolTodayChart(timezone);
        const tomorrowChart = await fetchCachedNordpoolTomorrowChart(timezone).catch(() => null);

        if (!isActive) {
          return;
        }

        setState({
          chart,
          tomorrowChart,
          isLoading: false,
          error: null
        });
      } catch (error) {
        if (!isActive) {
          return;
        }

        setState({
          chart: null,
          tomorrowChart: null,
          isLoading: false,
          error: error instanceof Error ? error.message : "Failed to load Nord Pool pricing"
        });
      }
    }

    loadChart();

    return () => {
      isActive = false;
    };
  }, []);

  return state;
}
