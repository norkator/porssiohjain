import { apiFetch, apiGetJson } from "@/lib/api";

const WIND_FORECAST_CACHE_PREFIX = "porssiohjain.windForecast.";
const WIND_FORECAST_CACHE_TTL_MS = 15 * 60 * 1000;

export type WindRuleType = "TOMORROW_AVERAGE_ABOVE" | "TOMORROW_AVERAGE_BELOW" | "TOMORROW_DROP_PERCENT";
export type WindForecast = { timezone: string; todayAverage: number | null; tomorrowAverage: number | null; tomorrowDropPercent: number | null; points: { startTime: string; endTime: string; megawatts: number }[] };
export type WindNotification = { id: number; name: string; description: string | null; ruleType: WindRuleType; threshold: number; timezone: string; enabled: boolean; lastSentAt: string | null };
export type WindNotificationPayload = Omit<WindNotification, "id" | "lastSentAt">;
export const fetchWindForecast = (timezone: string) => apiGetJson<WindForecast>(`/fingrid/wind-forecast?timezone=${encodeURIComponent(timezone)}`);
export async function fetchCachedWindForecast(timezone: string) {
  const cacheKey = `${WIND_FORECAST_CACHE_PREFIX}${timezone}`;
  const now = Date.now();

  try {
    const cached = window.sessionStorage.getItem(cacheKey);
    if (cached) {
      const parsed = JSON.parse(cached) as { expiresAt: number; value: WindForecast };
      if (parsed.expiresAt > now) {
        return parsed.value;
      }
      window.sessionStorage.removeItem(cacheKey);
    }
  } catch {
    // Ignore storage failures; the API remains the source of truth.
  }

  const value = await fetchWindForecast(timezone);
  try {
    window.sessionStorage.setItem(cacheKey, JSON.stringify({
      expiresAt: now + WIND_FORECAST_CACHE_TTL_MS,
      value
    }));
  } catch {
    // Ignore quota and private-mode storage failures.
  }
  return value;
}
export const fetchWindNotifications = () => apiGetJson<WindNotification[]>("/api/wind-notifications");
async function send<T>(path: string, method: string, payload?: unknown) { const response = await apiFetch(path, { method, body: payload ? JSON.stringify(payload) : undefined, headers: { "Content-Type": "application/json" } }); if (!response.ok) throw new Error(`Request failed with status ${response.status}`); const text = await response.text(); return text ? JSON.parse(text) as T : undefined as T; }
export const createWindNotification = (payload: WindNotificationPayload) => send<WindNotification>("/api/wind-notifications", "POST", payload);
export const updateWindNotification = (id: number, payload: WindNotificationPayload) => send<WindNotification>(`/api/wind-notifications/${id}`, "PUT", payload);
export const deleteWindNotification = (id: number) => send<void>(`/api/wind-notifications/${id}`, "DELETE");
