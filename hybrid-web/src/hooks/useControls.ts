import { useEffect, useState } from "react";
import { fetchControls, getLatestControlUpdate, type ApiControl } from "@/lib/controls";

type UseControlsState = {
  controls: ApiControl[];
  isLoading: boolean;
  error: string | null;
};

export function useControls() {
  const [state, setState] = useState<UseControlsState>({
    controls: [],
    error: null,
    isLoading: true
  });

  useEffect(() => {
    let isActive = true;

    async function loadControls() {
      try {
        const controls = await fetchControls();

        if (!isActive) {
          return;
        }

        setState({
          controls,
          error: null,
          isLoading: false
        });
      } catch (error) {
        if (!isActive) {
          return;
        }

        setState({
          controls: [],
          error: error instanceof Error ? error.message : "Failed to load controls",
          isLoading: false
        });
      }
    }

    loadControls();

    return () => {
      isActive = false;
    };
  }, []);

  return {
    ...state,
    latestUpdate: getLatestControlUpdate(state.controls),
    manualCount: state.controls.filter((control) => control.mode === "MANUAL").length,
    sharedCount: state.controls.filter((control) => control.shared).length,
    totalCount: state.controls.length
  };
}
