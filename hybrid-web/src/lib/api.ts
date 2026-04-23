import { getSessionData } from "@/lib/session";

export function getApiUrl(path: string) {
  const { baseUrl } = getSessionData();

  return new URL(path, baseUrl).toString();
}

export function getAuthHeaders(headers?: HeadersInit) {
  const { token } = getSessionData();
  const nextHeaders = new Headers(headers);

  if (token) {
    nextHeaders.set("Authorization", token);
  }

  return nextHeaders;
}

export async function apiFetch(path: string, init?: RequestInit) {
  return fetch(getApiUrl(path), {
    ...init,
    headers: getAuthHeaders(init?.headers)
  });
}

export async function apiGetJson<T>(path: string, init?: RequestInit) {
  const response = await apiFetch(path, init);

  if (!response.ok) {
    throw new Error(`Request failed with status ${response.status}`);
  }

  return response.json() as Promise<T>;
}
