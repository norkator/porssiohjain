# hybrid-web

Bundled WebView UI project for the Energy Controller Android app.

Available as web build at https://mobile.porssiohjain.fi

Admin user previews:

- Log in with an admin account, open **Users** from the main menu, search by email, account ID or UUID, and select **View as user**.
- The preview is read-only and uses the existing admin login. No target-user tokens are issued or placed in URLs.
- Each API request includes `X-View-As-Account`; the backend revalidates the admin and target account on every request and rejects writes, admin endpoints, credential operations and account exports.
- The preview is scoped to the current tab/WebView with `sessionStorage`. Starting or returning from preview clears application caches and reloads the UI. **Return to admin** opens the users list again.
- Deploy the backend and hybrid-web build together. The existing Vaadin impersonation flow remains separate.

Stack:

- Vite
- React
- TypeScript
- React Router with `HashRouter`
- Tailwind CSS
- PostCSS
- Autoprefixer

Why `HashRouter`:

- Android loads the bundle from `file:///android_asset/...`
- browser history routing is awkward there
- hash-based routes are simple and reliable inside WebView

Workflow:

1. Install dependencies with your package manager.
2. Run `npm run dev` during web development.
3. Run `npm run build` to produce `dist/`.
4. Run `npm run sync:android` to copy `dist/` into `../app/src/main/assets/hybrid/`.
5. Run `npm run build:android` if you want build + copy in one command.

Browser dev auth:

- copy `.env.example` to `.env.local`
- fill in `VITE_API_BASE_URL`
- fill in `VITE_DEV_TOKEN`
- or paste a token at runtime in DevTools with `window.devSession.setToken("your-token")`
- you can override the API URL with `window.devSession.setBaseUrl("https://api.example.com")`
- inspect the current browser dev session with `window.devSession.show()`
- clear local overrides with `window.devSession.clear()`
- browser development uses `.env.local` only when Android bridge is unavailable
- `localStorage` overrides take precedence over `.env.local` during browser development
- Android WebView production keeps using `getBootstrapData()` from native side

Nginx container:

- `.github/workflows/hybrid-web-container.yml` builds `ghcr.io/norkator/porssiohjain-hybrid-web:latest`
- local image builds should be run from the repository root with `docker build -f hybrid-web/Dockerfile .`
- the container serves the Vite build with nginx on port `80`
- the workflow passes `VITE_API_BASE_URL=https://app.porssiohjain.fi/` as a Docker build argument
- the intended hosted UI origin is `https://mobile.porssiohjain.fi/`

Current structure:

- `src/views/`: route-backed pages derived from your HTML mocks
- `src/components/`: reusable cards and progress headers
- `src/layouts/`: shared app shell
- `src/lib/android-bridge.ts`: typed Android bridge wrapper
- `src/lib/session.ts`: unified session source for Android bootstrap or browser env fallback
- `src/lib/api.ts`: shared helpers for base URL and bearer-authenticated fetches
- authenticated requests automatically exchange the stored rotating refresh token and retry once after a `401`
- `src/styles/`: theme tokens and local font loading
- `views/`: original HTML mock files kept as references

Fonts:

- `Inter` and `Manrope` are provided through `@fontsource-variable/*`
- no Google Fonts CDN dependency is needed
- no manual font file download is needed

Bridge contract:

- `getBootstrapData()`
- `showToast(message)`
- `openNativeScreen(screen)`
- `scanWifiAccessPoints(discoveryId)`
- `logout()`
