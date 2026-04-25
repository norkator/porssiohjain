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
import HeaderLogo from "@/components/HeaderLogo";

type HeaderItem = {
  label: string;
  to?: string;
  active?: boolean;
};

export default function PageHeader({
  title,
  brand = "Energy Controller",
  items = [],
  rightSlot,
  compact = false,
  translucent = false
}: {
  title?: string;
  brand?: string;
  items?: HeaderItem[];
  rightSlot?: React.ReactNode;
  compact?: boolean;
  translucent?: boolean;
}) {
  const hasItems = items.length > 0;
  const hasRightSlot = rightSlot !== undefined && rightSlot !== null;

  return (
    <header className={`sticky top-0 z-40 w-full ${translucent ? "bg-background/85 backdrop-blur-md" : "bg-surface-container-low"}`}>
      <div className="mx-auto flex w-full max-w-7xl items-center justify-between px-4 py-4 sm:px-6">
        <div className="flex items-center gap-4">
          <HeaderLogo />
          <div className={compact ? "font-headline text-xl font-bold tracking-tight text-primary-container" : "font-headline text-xl font-black tracking-tight text-primary-container"}>
            {title ?? brand}
          </div>
        </div>

        {hasItems ? (
          <nav className="hidden items-center gap-6 md:flex">
            {items.map((item) =>
              item.to ? (
                <Link
                  key={item.label}
                  className={`rounded-lg px-3 py-1 font-label text-sm transition-colors ${
                    item.active
                      ? "font-bold text-primary-container"
                      : "text-on-surface-variant hover:bg-surface-container-highest"
                  }`}
                  to={item.to}
                >
                  {item.label}
                </Link>
              ) : (
                <span
                  key={item.label}
                  className={`rounded-lg px-3 py-1 font-label text-sm ${item.active ? "font-bold text-primary-container" : "text-on-surface-variant"}`}
                >
                  {item.label}
                </span>
              )
            )}
          </nav>
        ) : hasRightSlot ? (
          <div className="w-10" />
        ) : null}

        {hasRightSlot ? <div className="flex items-center gap-3">{rightSlot}</div> : null}
      </div>
    </header>
  );
}
