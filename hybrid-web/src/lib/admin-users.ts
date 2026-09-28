import { apiFetch, apiGetJson } from "@/lib/api";
import type { AccountTier } from "@/lib/account";

export type AdminUser = {
  id: number;
  uuid: string;
  email: string | null;
  tier: AccountTier;
  admin: boolean;
  blocked: boolean;
  createdAt: string | null;
  updatedAt: string | null;
  lastActivitySource: string | null;
};

export type AdminUsersPage = { users: AdminUser[]; page: number; totalPages: number; totalElements: number };

export function fetchAdminUsers(search: string, page: number) {
  return apiGetJson<AdminUsersPage>(`/api/admin/users?${new URLSearchParams({ search, page: String(page) })}`);
}

export async function previewAdminUser(accountId: number) {
  const response = await apiFetch(`/api/admin/users/${accountId}/preview`, { method: "POST" });
  if (!response.ok) throw new Error(`Request failed with status ${response.status}`);
  return response.json() as Promise<AdminUser>;
}
