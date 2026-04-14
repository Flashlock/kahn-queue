/** Raw GitHub content for READMEs on the default branch (swap branch name if the repo uses `main`). */
export const DOCS_BRANCH = "master";

export const GITHUB_ORG_REPO = "Flashlock/kahn-queue";

export const RAW_README_BASE = `https://raw.githubusercontent.com/${GITHUB_ORG_REPO}/${DOCS_BRANCH}`;

export type DocSlug = "overview" | "java" | "go" | "typescript" | "python";

export const DOC_PAGES: { slug: DocSlug; label: string; path: string; hint: string }[] = [
  { slug: "overview", label: "Overview", path: "README.md", hint: "Repository root" },
  { slug: "java", label: "Java", path: "java/README.md", hint: "JVM / Gradle" },
  { slug: "go", label: "Go", path: "go/README.md", hint: "Go modules" },
  { slug: "typescript", label: "TypeScript", path: "typescript/README.md", hint: "npm package" },
  { slug: "python", label: "Python", path: "python/README.md", hint: "PyPI-style layout" },
];

export function readmeUrl(slug: DocSlug): string {
  const row = DOC_PAGES.find((d) => d.slug === slug);
  if (!row) return `${RAW_README_BASE}/README.md`;
  return `${RAW_README_BASE}/${row.path}`;
}
