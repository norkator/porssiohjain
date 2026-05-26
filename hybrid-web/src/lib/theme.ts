import { setNativeTheme } from "@/lib/android-bridge";

const THEME_STORAGE_KEY = "porssiohjain-theme";

export type ThemePreference = "light" | "dark";

function isThemePreference(value: string | null): value is ThemePreference {
  return value === "light" || value === "dark";
}

export function getThemePreference(): ThemePreference {
  if (typeof window === "undefined") {
    return "light";
  }

  const storedTheme = window.localStorage.getItem(THEME_STORAGE_KEY);

  return isThemePreference(storedTheme) ? storedTheme : "light";
}

export function applyThemePreference(theme: ThemePreference) {
  if (typeof document === "undefined") {
    return;
  }

  document.documentElement.dataset.theme = theme;
}

export function initializeTheme() {
  applyThemePreference(getThemePreference());
}

export function setThemePreference(theme: ThemePreference) {
  if (typeof window !== "undefined") {
    window.localStorage.setItem(THEME_STORAGE_KEY, theme);
  }

  applyThemePreference(theme);
  setNativeTheme(theme);
}
