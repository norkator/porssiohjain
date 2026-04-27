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

import enTranslations from "@/i18n/en.json";
import fiTranslations from "@/i18n/fi.json";
import { getSessionData, setDevSessionOverride } from "@/lib/session";

type TranslationValue = string | Record<string, string>;
type TranslationScope = Record<string, TranslationValue>;
type TranslationFile = Record<string, TranslationScope>;
type TranslationParams = Record<string, string | number>;
type EnglishTranslations = typeof enTranslations;

const translationsByLocale: Record<string, TranslationFile> = {
  en: enTranslations,
  fi: fiTranslations
};

export const supportedLocales = [
  { code: "en", label: "English" },
  { code: "fi", label: "Suomi" }
] as const;

export type SupportedLocale = typeof supportedLocales[number]["code"];

function normalizeLocale(locale?: string) {
  return locale?.split("-")[0]?.toLowerCase();
}

function getSupportedLocale(locale?: string): SupportedLocale | undefined {
  const normalizedLocale = normalizeLocale(locale);

  return supportedLocales.find((supportedLocale) => supportedLocale.code === normalizedLocale)?.code;
}

function getBrowserLocale() {
  if (typeof navigator === "undefined") {
    return undefined;
  }

  return normalizeLocale(navigator.language);
}

export function getCurrentLocale() {
  return getSupportedLocale(getSessionData().locale) ?? getSupportedLocale(getBrowserLocale()) ?? "en";
}

export function setCurrentLocale(locale: SupportedLocale) {
  setDevSessionOverride({ locale });
}

function interpolate(value: string, params?: TranslationParams) {
  if (!params) {
    return value;
  }

  return value.replace(/\{(\w+)}/g, (match, key) => String(params[key] ?? match));
}

export function useI18n<TScope extends Extract<keyof EnglishTranslations, string>>(scope: TScope) {
  const locale = getCurrentLocale();
  const localeMessages = translationsByLocale[locale]?.[scope] ?? {};
  const englishMessages = enTranslations[scope];

  function t<TKey extends Extract<keyof EnglishTranslations[TScope], string>>(key: TKey, params?: TranslationParams) {
    const translated = localeMessages[key];
    const fallback = englishMessages[key];
    const value = typeof translated === "string"
      ? translated
      : typeof fallback === "string"
        ? fallback
        : key;

    return interpolate(value, params);
  }

  function group<TKey extends Extract<keyof EnglishTranslations[TScope], string>>(
    key: TKey
  ): EnglishTranslations[TScope][TKey] extends Record<string, string> ? EnglishTranslations[TScope][TKey] : Record<string, never> {
    const fallback = englishMessages[key];
    const translated = localeMessages[key];

    if (typeof fallback !== "object") {
      return {} as EnglishTranslations[TScope][TKey] extends Record<string, string> ? EnglishTranslations[TScope][TKey] : Record<string, never>;
    }

    return {
      ...fallback,
      ...(typeof translated === "object" ? translated : {})
    } as EnglishTranslations[TScope][TKey] extends Record<string, string> ? EnglishTranslations[TScope][TKey] : Record<string, never>;
  }

  return { group, locale, t };
}
