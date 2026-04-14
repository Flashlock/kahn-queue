import { Link } from "react-router-dom";
import "./HomePage.css";

export function HomePage() {
  return (
    <div className="home">
      <p className="eyebrow">Dependency-ready scheduling</p>
      <h1 className="home-title">Kahn Queue</h1>
      <p className="home-lead">
        A small family of libraries for building directed acyclic graphs and driving them with a Kahn-style ready
        queue—same idea across Java, Go, TypeScript, and Python.
      </p>

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

      <section className="home-note">
        <h3>Rough cut</h3>
        <p>
          This site is a working sketch: routing, README embedding, and an interactive demo. Styling and copy are
          intentionally temporary—room to workshop a stronger visual story later.
        </p>
      </section>
    </div>
  );
}
