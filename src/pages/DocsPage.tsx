import { useEffect, useState } from "react";
import { useParams } from "react-router-dom";
import { MarkdownBody } from "../components/MarkdownBody";
import { type DocSlug, DOC_PAGES, readmeUrl } from "../config/docs";
import "./DocsPage.css";

function isDocSlug(s: string | undefined): s is DocSlug {
  return s !== undefined && DOC_PAGES.some((d) => d.slug === s);
}

export function DocsPage() {
  const { slug } = useParams();
  const [markdown, setMarkdown] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  const doc = isDocSlug(slug) ? DOC_PAGES.find((d) => d.slug === slug) : undefined;

  useEffect(() => {
    if (!isDocSlug(slug)) {
      setError("Unknown doc section.");
      setLoading(false);
      return;
    }

    const url = readmeUrl(slug);
    let cancelled = false;
    setLoading(true);
    setError(null);

    fetch(url)
      .then((res) => {
        if (!res.ok) throw new Error(`${res.status} ${res.statusText}`);
        return res.text();
      })
      .then((text) => {
        if (!cancelled) setMarkdown(text);
      })
      .catch((e: Error) => {
        if (!cancelled) setError(e.message);
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });

    return () => {
      cancelled = true;
    };
  }, [slug]);

  if (!doc) {
    return (
      <div className="docs-page">
        <p className="docs-error">Unknown documentation slug.</p>
      </div>
    );
  }

  return (
    <div className="docs-page">
      <header className="docs-header">
        <p className="docs-eyebrow">Documentation</p>
        <h1>{doc.label}</h1>
        <p className="docs-meta">
          Fetched from GitHub: <code>{doc.path}</code> on branch <code>master</code>
        </p>
      </header>

      {loading && <p className="docs-status">Loading README…</p>}
      {error && (
        <div className="docs-error">
          <strong>Could not load README.</strong> {error}
          <div className="docs-hint">
            Tip: if the default branch is <code>main</code>, update <code>DOCS_BRANCH</code> in{" "}
            <code>src/config/docs.ts</code>.
          </div>
        </div>
      )}
      {markdown && !loading && <MarkdownBody markdown={markdown} />}
    </div>
  );
}
