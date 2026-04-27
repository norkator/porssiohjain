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

import { Link } from "react-router-dom";
import { useI18n } from "@/lib/i18n";

type DeviceCardProps = {
  type: string;
  status: string;
  statusTone?: "online" | "offline";
  title: string;
  subtitle: string;
  detailLabel: string;
  detailValue: string;
  accent: string;
  manageTo?: string;
  actions?: Array<{
    label: string;
    onClick?: () => void;
    to?: string;
  }>;
};

export default function DeviceCard({
  type,
  status,
  statusTone = "offline",
  title,
  subtitle,
  detailLabel,
  detailValue,
  accent,
  manageTo,
  actions = []
}: DeviceCardProps) {
  const { t } = useI18n("common");
  const cardActions = [
    ...actions,
    manageTo
      ? { label: t("manage"), to: manageTo }
      : { label: t("manage") }
  ];

  return (
    <article className={`group app-card border-l-4 ${accent} p-6 transition-all duration-300 hover:-translate-y-1 hover:bg-surface-container-high hover:shadow-soft`}>
      <div className="mb-6 flex items-start justify-between gap-4">
        <span className="chip bg-surface-container-highest text-primary-container transition-colors duration-300 group-hover:bg-white">{type}</span>
        <span
          className={`inline-flex items-center gap-1 rounded px-2 py-1 text-[10px] font-bold ${
            statusTone === "online"
              ? "bg-primary-fixed text-primary"
              : "bg-error-container text-on-error-container"
          }`}
        >
          <span
            className={`h-1.5 w-1.5 rounded-full ${
              statusTone === "online" ? "bg-primary" : "bg-red-500"
            }`}
          />
          {status}
        </span>
      </div>

      <h3 className="font-headline text-2xl font-bold text-on-surface">{title}</h3>
      <p className="mb-8 mt-1 font-mono text-xs tracking-tight text-outline">{subtitle}</p>

      <div className="flex items-center justify-between border-t border-surface-container-low pt-4">
        <div className="flex flex-col">
          <span className="metric-label">{detailLabel}</span>
          <span className="text-sm font-semibold text-on-surface">{detailValue}</span>
        </div>
        <div className="flex items-center gap-2">
          {cardActions.map((action) => action.to ? (
            <Link
              className="secondary-action rounded-lg px-3 py-2 text-sm transition-all duration-300 group-hover:-translate-y-0.5"
              key={action.label}
              to={action.to}
            >
              {action.label}
            </Link>
          ) : (
            <button
              className="secondary-action rounded-lg px-3 py-2 text-sm transition-all duration-300 group-hover:-translate-y-0.5"
              key={action.label}
              onClick={action.onClick}
              type="button"
            >
              {action.label}
            </button>
          ))}
        </div>
      </div>
    </article>
  );
}
