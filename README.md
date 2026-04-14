# kahn-queue (GitHub Pages site)

Vite + React app for [kahn-queue](https://github.com/Flashlock/kahn-queue), deployed from the `gh-pages` branch via GitHub Actions.

## Scripts

- `npm install` — install dependencies
- `npm run dev` — local dev server
- `npm run build` — production build to `dist/`
- `npm run preview` — preview the production build (uses `base: /kahn-queue/`)

## Publishing

Repository **Settings → Pages**: source **GitHub Actions**. Pushes to `gh-pages` run `.github/workflows/deploy-pages.yml`.

The npm package [`@flashlock/kahn-queue`](https://www.npmjs.com/package/@flashlock/kahn-queue) is a normal dependency for demos and docs in the UI.
