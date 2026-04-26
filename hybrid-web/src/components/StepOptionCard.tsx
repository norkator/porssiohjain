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

import { useI18n } from "@/lib/i18n";

type StepOptionCardProps = {
  title: string;
  description: string;
  icon: string;
  featured?: boolean;
};

export default function StepOptionCard({
  title,
  description,
  icon,
  featured = false
}: StepOptionCardProps) {
  const { t } = useI18n("common");

  return (
    <button
      className={`group relative flex h-full w-full flex-col rounded-xl2 p-8 text-left transition-all duration-300 active:scale-95 ${
        featured
          ? "bg-primary-container text-primary-fixed shadow-xl"
          : "app-card bg-surface-container-lowest hover:bg-surface-container-low"
      }`}
      type="button"
    >
      <div
        className={`mb-6 flex h-12 w-12 items-center justify-center rounded-lg ${
          featured
            ? "bg-white/10 text-primary-fixed"
            : "bg-surface-container-high text-primary group-hover:bg-primary group-hover:text-on-primary"
        }`}
      >
        <span className="font-headline text-xl font-black">{icon}</span>
      </div>

      <h3 className={`font-headline text-2xl font-bold ${featured ? "text-white" : "text-on-surface"}`}>
        {title}
      </h3>
      <p className={`mt-2 text-sm leading-relaxed ${featured ? "text-primary-fixed" : "text-on-surface-variant"}`}>
        {description}
      </p>

      {featured ? (
        <span className="absolute right-4 top-4 rounded bg-primary-fixed px-2 py-1 text-[10px] font-bold uppercase tracking-tight text-primary">
          {t("recommended")}
        </span>
      ) : null}
    </button>
  );
}
