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

import { type PointerEvent, type SVGProps, useLayoutEffect, useRef, useState } from "react";

export function useChartViewport(isResponsive: boolean, defaultWidth: number, defaultHeight: number) {
  const ref = useRef<SVGSVGElement>(null);
  const [measuredWidth, setMeasuredWidth] = useState(defaultWidth);

  useLayoutEffect(() => {
    const svg = ref.current;
    if (!isResponsive || !svg) return;

    const measureWidth = () => setMeasuredWidth(Math.max(160, Math.round(svg.getBoundingClientRect().width)));
    measureWidth();
    const observer = new ResizeObserver(measureWidth);
    observer.observe(svg);
    return () => observer.disconnect();
  }, [isResponsive]);

  const width = isResponsive ? measuredWidth : defaultWidth;
  const height = isResponsive ? Math.max(190, Math.round(defaultHeight * width / defaultWidth)) : defaultHeight;
  return { ref, width, height };
}

export function chartPointSelectionProps(
  timestamps: number[],
  chartWidth: number,
  plotLeft: number,
  plotWidth: number,
  onSelect: (index: number | null) => void
): Pick<SVGProps<SVGSVGElement>, "onContextMenu" | "onPointerDown" | "onPointerLeave" | "onPointerMove"> {
  function selectPointAtPointer(event: PointerEvent<SVGSVGElement>) {
    if (!event.isPrimary) return;

    if (event.type === "pointerdown" && event.pointerType !== "mouse") {
      event.currentTarget.setPointerCapture(event.pointerId);
    }

    const bounds = event.currentTarget.getBoundingClientRect();
    const x = ((event.clientX - bounds.left) / bounds.width) * chartWidth;
    const progress = Math.min(Math.max((x - plotLeft) / plotWidth, 0), 1);
    const lastIndex = timestamps.length - 1;
    const timestamp = timestamps[0] + progress * (timestamps[lastIndex] - timestamps[0]);
    let index = timestamps.findIndex((candidate) => candidate >= timestamp);

    if (index === -1) index = lastIndex;
    if (index > 0 && timestamp - timestamps[index - 1] < timestamps[index] - timestamp) index--;
    onSelect(index);
  }

  return {
    onContextMenu: (event) => event.preventDefault(),
    onPointerDown: selectPointAtPointer,
    onPointerLeave: (event) => {
      if (event.pointerType === "mouse") onSelect(null);
    },
    onPointerMove: selectPointAtPointer
  };
}
