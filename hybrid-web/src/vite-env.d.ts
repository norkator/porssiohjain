/// <reference types="vite/client" />

type DevSessionApi = {
  clear: () => unknown;
  setBaseUrl: (baseUrl: string) => unknown;
  setToken: (token: string) => unknown;
  show: () => unknown;
};

interface Window {
  devSession?: DevSessionApi;
}
