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
import { type ReactNode, useEffect } from "react";

type Props = {
  children: ReactNode;
  description: string;
  isOpen: boolean;
  onClose: () => void;
  title: string;
  eyebrow?: string;
  maxWidthClassName?: string;
};

export default function AppDialog({
  children,
  description,
  eyebrow,
  isOpen,
  maxWidthClassName = "max-w-3xl",
  onClose,
  title
}: Props) {
  const common = useI18n("common").t;

  useEffect(() => {
    if (!isOpen) {
      return;
    }

    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        onClose();
      }
    };

    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [isOpen, onClose]);

  if (!isOpen) {
    return null;
  }

  return (
    <div
      className="fixed inset-0 z-50 flex items-end bg-on-surface/40 p-4 sm:items-center sm:justify-center"
      onClick={onClose}
    >
      <section
        aria-labelledby="app-dialog-title"
        aria-modal="true"
        className={`max-h-[92vh] w-full overflow-y-auto rounded-xl bg-surface-container-lowest p-5 shadow-2xl sm:p-6 ${maxWidthClassName}`}
        onClick={(event) => event.stopPropagation()}
        role="dialog"
      >
        <div className="mb-5 flex items-start justify-between gap-4">
          <div>
            {eyebrow ? <p className="metric-label mb-2">{eyebrow}</p> : null}
            <h2 className="font-headline text-2xl font-black text-on-surface" id="app-dialog-title">{title}</h2>
            <p className="mt-1 max-w-2xl text-sm text-on-surface-variant">{description}</p>
          </div>
          <button className="secondary-action px-3 py-2 text-sm" onClick={onClose} type="button">
            {common("close")}
          </button>
        </div>

        {children}
      </section>
    </div>
  );
}
