import { clearApplicationSessionStorage } from "@/lib/session-storage";

const STORAGE_KEY = "porssiohjain.user-preview";

type UserPreview = { accountId: number; email: string | null; adminAccountId: number };

export function getUserPreview(): UserPreview | null {
  try {
    const value = JSON.parse(window.sessionStorage.getItem(STORAGE_KEY) ?? "null") as UserPreview | null;
    return value && Number.isSafeInteger(value.accountId) && value.accountId > 0
      && Number.isSafeInteger(value.adminAccountId) && value.adminAccountId > 0 ? value : null;
  } catch {
    return null;
  }
}

export function startUserPreview(preview: UserPreview) {
  clearApplicationSessionStorage();
  window.sessionStorage.setItem(STORAGE_KEY, JSON.stringify(preview));
  window.location.hash = "#/menu";
  window.location.reload();
}

export function stopUserPreview() {
  clearApplicationSessionStorage();
  window.location.hash = "#/admin/users";
  window.location.reload();
}
