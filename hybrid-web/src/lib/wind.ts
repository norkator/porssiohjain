import { apiFetch, apiGetJson } from "@/lib/api";

export type WindRuleType = "TOMORROW_AVERAGE_ABOVE" | "TOMORROW_AVERAGE_BELOW" | "TOMORROW_DROP_PERCENT";
export type WindForecast = { timezone: string; todayAverage: number | null; tomorrowAverage: number | null; tomorrowDropPercent: number | null; points: { startTime: string; endTime: string; megawatts: number }[] };
export type WindNotification = { id: number; name: string; description: string | null; ruleType: WindRuleType; threshold: number; timezone: string; enabled: boolean; lastSentAt: string | null };
export type WindNotificationPayload = Omit<WindNotification, "id" | "lastSentAt">;
export const fetchWindForecast = (timezone: string) => apiGetJson<WindForecast>(`/fingrid/wind-forecast?timezone=${encodeURIComponent(timezone)}`);
export const fetchWindNotifications = () => apiGetJson<WindNotification[]>("/api/wind-notifications");
async function send<T>(path: string, method: string, payload?: unknown) { const response = await apiFetch(path, { method, body: payload ? JSON.stringify(payload) : undefined, headers: { "Content-Type": "application/json" } }); if (!response.ok) throw new Error(`Request failed with status ${response.status}`); return response.status === 204 ? undefined as T : response.json() as Promise<T>; }
export const createWindNotification = (payload: WindNotificationPayload) => send<WindNotification>("/api/wind-notifications", "POST", payload);
export const updateWindNotification = (id: number, payload: WindNotificationPayload) => send<WindNotification>(`/api/wind-notifications/${id}`, "PUT", payload);
export const deleteWindNotification = (id: number) => send<void>(`/api/wind-notifications/${id}`, "DELETE");
