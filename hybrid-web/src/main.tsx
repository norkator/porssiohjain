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

import React from "react";
import ReactDOM from "react-dom/client";
import { HashRouter } from "react-router-dom";
import App from "./App";
import { clearDevSessionOverride, getSessionData, setDevSessionOverride } from "@/lib/session";
import "./styles/index.css";

if (import.meta.env.DEV && typeof window !== "undefined") {
  window.devSession = {
    clear() {
      clearDevSessionOverride();
      return getSessionData();
    },
    setBaseUrl(baseUrl: string) {
      setDevSessionOverride({ baseUrl });
      return getSessionData();
    },
    setToken(token: string) {
      setDevSessionOverride({ token });
      return getSessionData();
    },
    show() {
      return getSessionData();
    }
  };
}

ReactDOM.createRoot(document.getElementById("root")!).render(
  <React.StrictMode>
    <HashRouter>
      <App />
    </HashRouter>
  </React.StrictMode>
);
