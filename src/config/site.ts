/** Public site origin (GitHub Pages project site). Must match `base` in vite.config.ts. */
export const SITE_URL = "https://flashlock.github.io/kahn-queue";

export const SITE_NAME = "Kahn Queue";

export const DEFAULT_DESCRIPTION =
  "Build directed acyclic graphs (DAGs) and run them with a Kahn-style ready queue. Libraries for Java, Go, TypeScript, and Python—with docs and a live graph demo.";

/** Open Graph / Twitter image (served from `public/`). */
export function ogImageUrl(): string {
  return `${SITE_URL.replace(/\/$/, "")}/og.svg`;
}
