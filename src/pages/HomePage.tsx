import { Link } from "react-router-dom";
import { Seo } from "../components/Seo";
import { GITHUB_ORG_REPO, GITHUB_REPO_URL } from "../config/docs";
import { DEFAULT_DESCRIPTION, SITE_NAME } from "../config/site";
import { HomeHeroGraph } from "./HomeHeroGraph";
import "./HomePage.css";

export function HomePage() {
  return (
    <div className="home">
      <Seo title={SITE_NAME} description={DEFAULT_DESCRIPTION} />
      <HomeHeroGraph />
      <div className="home-inner">
        <div className="home-hero">
          <p className="eyebrow">Dependency-ready scheduling</p>
          <h1 className="home-title">Kahn Queue</h1>
          <p className="home-lead">
            A small family of libraries for building directed acyclic graphs and driving them with a Kahn-style ready
            queue—same idea across Java, Go, TypeScript, and Python.
          </p>
          <p className="home-repo">
            <a href={GITHUB_REPO_URL} target="_blank" rel="noopener noreferrer">
              {GITHUB_ORG_REPO}
            </a>{" "}
            on GitHub
          </p>
        </div>

        <div className="home-grid">
          <Link className="home-card" to="/docs/overview">
            <h2>Read the docs</h2>
            <p>READMEs are pulled live from the <code>master</code> branch on GitHub—pick a language in the sidebar.</p>
          </Link>
          <Link className="home-card home-card-accent" to="/demo">
            <h2>Play with a graph</h2>
            <p>Sketch a DAG on a canvas, hit run, and watch waves of “ready” nodes light up as the scheduler walks the graph.</p>
          </Link>
        </div>
      </div>
    </div>
  );
}
