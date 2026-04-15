import { NavLink, Outlet, useLocation } from "react-router-dom";
import { DOC_PAGES, GITHUB_ORG_REPO, GITHUB_REPO_URL } from "../config/docs";
import "./Layout.css";

export function Layout() {
  const { pathname } = useLocation();
  const isDemo = pathname === "/demo";
  const isHome = pathname === "/";

  const shellClass = ["shell", isDemo && "shell--demo", isHome && "shell--home"].filter(Boolean).join(" ");

  return (
    <div className={shellClass}>
      <aside className="sidebar">
        <div className="sidebar-header">
          <div className="brand">
            <span className="brand-mark">KQ</span>
            <div>
              <div className="brand-title">Kahn Queue</div>
              <div className="brand-sub">Docs &amp; playground</div>
            </div>
          </div>

          <div className="sidebar-actions">
            <a
              className="sidebar-github"
              href={GITHUB_REPO_URL}
              target="_blank"
              rel="noopener noreferrer"
              title={GITHUB_ORG_REPO}
              aria-label={`${GITHUB_ORG_REPO} on GitHub`}
            >
              <svg className="sidebar-github-icon" viewBox="0 0 24 24" width={22} height={22} aria-hidden>
                <path
                  fill="currentColor"
                  d="M12 0c-6.626 0-12 5.373-12 12 0 5.302 3.438 9.8 8.207 11.387.599.111.793-.261.793-.577v-2.234c-3.338.726-4.033-1.416-4.033-1.416-.546-1.387-1.333-1.756-1.333-1.756-1.089-.745.083-.729.083-.729 1.205.084 1.839 1.237 1.839 1.237 1.07 1.834 2.807 1.304 3.492.997.107-.775.418-1.305.762-1.604-2.665-.305-5.467-1.334-5.467-5.931 0-1.311.469-2.381 1.236-3.221-.124-.303-.535-1.524.117-3.176 0 0 1.008-.322 3.301 1.23.957-.266 1.983-.399 3.003-.404 1.02.005 2.047.138 3.006.404 2.291-1.552 3.297-1.23 3.297-1.23.653 1.653.242 2.874.118 3.176.77.84 1.235 1.911 1.235 3.221 0 4.609-2.807 5.624-5.479 5.921.43.372.823 1.102.823 2.222v3.293c0 .319.192.694.801.576 4.765-1.589 8.199-6.086 8.199-11.386 0-6.627-5.373-12-12-12z"
                />
              </svg>
            </a>
            <a
              className="sidebar-sponsor-btn"
              href="https://github.com/sponsors/Flashlock"
              target="_blank"
              rel="noopener noreferrer"
              title="Sponsor Flashlock on GitHub"
            >
              <span className="sidebar-sponsor-heart" aria-hidden>
                <svg width="14" height="14" viewBox="0 0 16 16" fill="currentColor">
                  <path d="M4.25 2.5c-1.336 0-2.75 1.164-2.75 3 0 2.15 1.58 4.144 3.365 5.682A20.565 20.565 0 008 13.403a20.56 20.56 0 003.085-2.223C12.92 9.644 14.5 7.65 14.5 5.5c0-1.836-1.414-3-2.75-3-1.373 0-2.609.986-3.029 2.456a.75.75 0 01-1.442 0C6.859 3.486 5.623 2.5 4.25 2.5zM8 14.25l-.345.666-.002-.002-.006-.003-.018-.01a7.643 7.643 0 01-.31-.17 22.08 22.08 0 01-3.433-2.414C2.045 10.731 0 8.35 0 5.5 0 2.836 2.086 1 4.25 1 5.797 1 7.153 1.802 8 3.02 8.847 1.802 10.203 1 11.75 1 13.914 1 16 2.836 16 5.5c0 2.85-2.045 5.231-4.086 6.432a22.078 22.078 0 01-3.433 2.414 7.664 7.664 0 01-.318.165l-.007.003-.002.001L8 14.25z" />
                </svg>
              </span>
              Sponsor
            </a>
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

          <div className="nav-section">DOCS</div>
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
      </aside>

      <div className={isDemo ? "main main--demo" : isHome ? "main main--home" : "main"}>
        <Outlet />
      </div>
    </div>
  );
}
