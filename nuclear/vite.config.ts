import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import { fileURLToPath } from 'node:url';
import { pages } from './site/pages.mjs';
import { renderPage } from './site/render.mjs';

export default defineConfig({
  plugins: [
    react(),
    {
      name: 'static-search-content',
      transformIndexHtml: {
        order: 'pre',
        handler: (html, context) => renderPage(html, context.path),
      },
    },
  ],
  build: {
    rolldownOptions: {
      input: pages.map(page => fileURLToPath(new URL(`.${page.path}index.html`, import.meta.url))),
    },
  },
  server: {
    host: '0.0.0.0',
    port: 5174,
  },
});
