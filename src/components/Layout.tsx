import { NavLink, Outlet, useLocation } from "react-router-dom";
import { DOC_PAGES, GITHUB_ORG_REPO } from "../config/docs";
import "./Layout.css";

export function Layout() {
  const { pathname } = useLocation();
  const isDemo = pathname === "/demo";

  return (
    <div className={isDemo ? "shell shell--demo" : "shell"}>
      <aside className="sidebar">
        <div className="brand">
          <span className="brand-mark">KQ</span>
          <div>
            <div className="brand-title">Kahn Queue</div>
            <div className="brand-sub">Docs &amp; playground</div>
          </div>
        </div>

        <nav className="nav">
          <div className="nav-section">Site</div>
          <NavLink className={({ isActive }) => (isActive ? "nav-link active" : "nav-link")} to="/" end>
            Home
          </NavLink>
          <NavLink className={({ isActive }) => (isActive ? "nav-link active" : "nav-link")} to="/demo">
            Live demo
          </NavLink>

          <div className="nav-section">Docs (GitHub READMEs)</div>
          {DOC_PAGES.map((d) => (
            <NavLink
              key={d.slug}
              className={({ isActive }) => (isActive ? "nav-link active" : "nav-link")}
              to={`/docs/${d.slug}`}
              title={d.hint}
            >
              {d.label}
            </NavLink>
          ))}
        </nav>

        <div className="sidebar-footer">
          <a href={`https://github.com/${GITHUB_ORG_REPO}`} target="_blank" rel="noreferrer">
            View on GitHub
          </a>
        </div>
      </aside>

      <div className={isDemo ? "main main--demo" : "main"}>
        <Outlet />
      </div>
    </div>
  );
}
