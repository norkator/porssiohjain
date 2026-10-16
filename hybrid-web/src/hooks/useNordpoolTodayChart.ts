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
import { fetchCachedNordpoolTodayChart, fetchCachedNordpoolTomorrowChart, mergeNordpoolCharts, type NordpoolTodayChart } from "@/lib/nordpool";

type UseNordpoolTodayChartState = {
  chart: NordpoolTodayChart | null;
  isLoading: boolean;
  error: string | null;
};

export function useNordpoolTodayChart() {
  const [state, setState] = useState<UseNordpoolTodayChartState>({
    chart: null,
    isLoading: true,
    error: null
  });

  useEffect(() => {
    let isActive = true;
    const timezone = getCurrentTimezone();

    async function loadChart() {
      try {
        const results = await Promise.allSettled([
          fetchCachedNordpoolTodayChart(timezone),
          fetchCachedNordpoolTomorrowChart(timezone)
        ]);
        const charts = results.flatMap((result) => result.status === "fulfilled" ? [result.value] : []);
        if (!charts.length) {
          const failure = results.find((result) => result.status === "rejected");
          throw failure?.reason ?? new Error("Failed to load Nord Pool pricing");
        }
        const chart = mergeNordpoolCharts(charts);

        if (!isActive) {
          return;
        }

        setState({
          chart,
          isLoading: false,
          error: null
        });
      } catch (error) {
        if (!isActive) {
          return;
        }

        setState({
          chart: null,
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
