import { apiGetJson } from "@/lib/api";

type PowerLimitStat = {
  currentKw: number;
  name: string;
};

type ProductionSourceStat = {
  currentKw: number;
  name: string;
  peakKw: number;
};

export type AccountStats = {
  powerLimits: PowerLimitStat[];
  productionSources: ProductionSourceStat[];
};

export async function fetchAccountStats() {
  return apiGetJson<AccountStats>("/account/stats");
}

function sumValues(values: number[]) {
  return values.reduce((total, value) => total + value, 0);
}

export function getTotalConsumptionKw(stats: AccountStats) {
  return sumValues(stats.powerLimits.map((item) => item.currentKw));
}

export function getTotalProductionKw(stats: AccountStats) {
  return sumValues(stats.productionSources.map((item) => item.currentKw));
}

export function getTotalProductionPeakKw(stats: AccountStats) {
  return sumValues(stats.productionSources.map((item) => item.peakKw));
}

export function formatKw(value: number, signed = false) {
  const formatter = new Intl.NumberFormat(undefined, {
    minimumFractionDigits: 1,
    maximumFractionDigits: 1
  });

  const formatted = formatter.format(value);

  if (signed && value > 0) {
    return `+${formatted}`;
  }

  return formatted;
}
