import { useMemo } from "react";
import { getSessionData } from "@/lib/session";

export function useBootstrapData() {
  return useMemo(() => getSessionData(), []);
}
