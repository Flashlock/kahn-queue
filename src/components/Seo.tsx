import { useEffect } from "react";
import { useLocation } from "react-router-dom";
import { DEFAULT_DESCRIPTION, SITE_NAME, SITE_URL, ogImageUrl } from "../config/site";

export type SeoProps = {
  title: string;
  description?: string;
  /** Extra or page-specific JSON-LD (merged into head on mount). */
  jsonLd?: object | object[];
  robots?: "index, follow" | "noindex, nofollow";
};

function setMeta(attr: "name" | "property", key: string, content: string) {
  const sel = attr === "name" ? `meta[name="${key}"]` : `meta[property="${key}"]`;
  let el = document.head.querySelector(sel) as HTMLMetaElement | null;
  if (!el) {
    el = document.createElement("meta");
    el.setAttribute(attr, key);
    document.head.appendChild(el);
  }
  el.setAttribute("content", content);
}

function setCanonical(href: string) {
  let el = document.head.querySelector('link[rel="canonical"]') as HTMLLinkElement | null;
  if (!el) {
    el = document.createElement("link");
    el.rel = "canonical";
    document.head.appendChild(el);
  }
  el.href = href;
}

export function Seo({ title, description = DEFAULT_DESCRIPTION, jsonLd, robots = "index, follow" }: SeoProps) {
  const { pathname } = useLocation();
  const pageTitle = title === SITE_NAME ? title : `${title} · ${SITE_NAME}`;
  const base = SITE_URL.replace(/\/$/, "");
  const canonical =
    pathname === "/" ? `${base}/` : `${base}/#${pathname}`;

  useEffect(() => {
    document.title = pageTitle;
    setMeta("name", "description", description);
    setMeta("name", "robots", robots);
    setMeta("property", "og:title", pageTitle);
    setMeta("property", "og:description", description);
    setMeta("property", "og:url", canonical);
    setMeta("property", "og:image", ogImageUrl());
    setMeta("property", "og:image:alt", `${SITE_NAME} — DAG scheduling across multiple languages`);
    setMeta("property", "og:type", "website");
    setMeta("property", "og:site_name", SITE_NAME);
    setMeta("property", "og:locale", "en_US");
    setMeta("name", "twitter:card", "summary_large_image");
    setMeta("name", "twitter:title", pageTitle);
    setMeta("name", "twitter:description", description);
    setMeta("name", "twitter:image", ogImageUrl());
    setCanonical(canonical);
  }, [pageTitle, description, canonical, robots]);

  useEffect(() => {
    if (!jsonLd) return;
    const id = "page-jsonld";
    const nodes: HTMLElement[] = [];
    const list = Array.isArray(jsonLd) ? jsonLd : [jsonLd];
    list.forEach((data, i) => {
      const s = document.createElement("script");
      s.type = "application/ld+json";
      s.id = i === 0 ? id : `${id}-${i}`;
      s.textContent = JSON.stringify(data);
      document.head.appendChild(s);
      nodes.push(s);
    });
    return () => {
      nodes.forEach((n) => n.remove());
    };
  }, [jsonLd]);

  return null;
}
