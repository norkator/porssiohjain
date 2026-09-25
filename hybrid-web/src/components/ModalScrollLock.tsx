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

import { useEffect } from "react";

let activeLocks = 0;
let restoreScroll: (() => void) | null = null;

export default function ModalScrollLock() {
  useEffect(() => {
    if (activeLocks === 0) {
      const body = document.body;
      const root = document.documentElement;
      const scrollY = window.scrollY;
      const previous = {
        bodyPosition: body.style.position,
        bodyTop: body.style.top,
        bodyLeft: body.style.left,
        bodyRight: body.style.right,
        bodyWidth: body.style.width,
        bodyOverflow: body.style.overflow,
        rootOverflow: root.style.overflow
      };

      body.style.position = "fixed";
      body.style.top = `-${scrollY}px`;
      body.style.left = "0";
      body.style.right = "0";
      body.style.width = "100%";
      body.style.overflow = "hidden";
      root.style.overflow = "hidden";

      restoreScroll = () => {
        body.style.position = previous.bodyPosition;
        body.style.top = previous.bodyTop;
        body.style.left = previous.bodyLeft;
        body.style.right = previous.bodyRight;
        body.style.width = previous.bodyWidth;
        body.style.overflow = previous.bodyOverflow;
        root.style.overflow = previous.rootOverflow;
        window.scrollTo(0, scrollY);
      };
    }

    activeLocks += 1;
    return () => {
      activeLocks -= 1;
      if (activeLocks === 0) {
        restoreScroll?.();
        restoreScroll = null;
      }
    };
  }, []);

  return null;
}
