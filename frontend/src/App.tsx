import { useState } from "react";
import { NavLink, Link, Route, Routes, useLocation } from "react-router-dom";
import {
  Activity,
  ArrowUpRight,
  BookOpen,
  Boxes,
  ChevronRight,
  Code2,
  Github,
  LayoutDashboard,
  Menu,
  MessageSquare,
  Moon,
  PanelLeftClose,
  ShieldCheck,
  Sun,
  Terminal,
  X,
} from "lucide-react";
import { DataProvider, useData } from "./context";
import {
  Dashboard,
  Projects,
  ProjectDetail,
  ActivityPage,
} from "./pages/Portfolio";
import { Interview } from "./pages/Interview";
import { Admin } from "./pages/Admin";
export function App() {
  return (
    <DataProvider>
      <Shell />
    </DataProvider>
  );
}
function Shell() {
  const { demo, toggleMode, overview } = useData(),
    location = useLocation();
  const [open, setOpen] = useState(false),
    [light, setLight] = useState(
      () => localStorage.getItem("devinsight-theme") === "light",
    );
  const nav = [
    ["/", "개요", LayoutDashboard],
    ["/projects", "프로젝트", Boxes],
    ["/activity", "개발 활동", Activity],
    ["/interview", "AI 인터뷰", MessageSquare],
  ] as const;
  const theme = () =>
    setLight((v) => {
      localStorage.setItem("devinsight-theme", v ? "dark" : "light");
      return !v;
    });
  const title = location.pathname.startsWith("/projects/")
    ? "프로젝트 상세"
    : location.pathname === "/admin"
      ? "관리자 워크스페이스"
      : (nav.find((n) => n[0] === location.pathname)?.[1] ?? "포트폴리오");
  return (
    <div className={`app ${light ? "light" : ""}`}>
      {open && (
        <button
          className="sidebar-backdrop"
          aria-label="메뉴 닫기"
          onClick={() => setOpen(false)}
        />
      )}
      <aside className={`sidebar ${open ? "is-open" : ""}`}>
        <Link className="brand" to="/" onClick={() => setOpen(false)}>
          <span className="brand-icon">
            <Code2 size={24} />
          </span>
          <span>
            DevInsight<span className="ai-badge">AI</span>
          </span>
        </Link>
        <div className="workspace-label">DEVELOPER PORTFOLIO</div>
        <nav>
          {nav.map(([url, label, Icon]) => (
            <NavLink
              key={url}
              to={url}
              end={url === "/"}
              onClick={() => setOpen(false)}
              className={({ isActive }) =>
                isActive ? "nav-item active" : "nav-item"
              }
            >
              <Icon size={18} />
              <span>{label}</span>
              {url === "/interview" ? (
                <span className="tiny-badge">AI</span>
              ) : (
                url === "/projects" && (
                  <span className="nav-count">
                    {overview?.projects.length ?? 0}
                  </span>
                )
              )}
            </NavLink>
          ))}
        </nav>
        <div className="sidebar-note">
          <ShieldCheck size={19} />
          <h4>경험을 뒷받침하는 근거</h4>
          <p>
            점수 대신 코드와 프로젝트로
            <br />
            개발자의 역량을 확인하세요.
          </p>
          <Link to="/projects">
            프로젝트 살펴보기 <ArrowUpRight size={14} />
          </Link>
        </div>
        <div className="sidebar-bottom">
          <NavLink
            className="nav-item"
            to="/admin"
            onClick={() => setOpen(false)}
          >
            <Terminal size={18} />
            관리자 워크스페이스
          </NavLink>
          <div className="sidebar-footer">
            <span>
              <span className="dot" /> {demo ? "데모 환경" : "공개 포트폴리오"}
            </span>
            <PanelLeftClose size={15} />
          </div>
        </div>
      </aside>
      <div className="workspace">
        <header className="topbar">
          <div className="breadcrumb">
            <button
              className="icon-button mobile-menu"
              aria-label="메뉴 열기"
              onClick={() => setOpen(true)}
            >
              <Menu size={20} />
            </button>
            <span>포트폴리오</span>
            <ChevronRight size={14} />
            <strong>{title}</strong>
          </div>
          <div className="topbar-actions">
            <button
              onClick={toggleMode}
              className={`mode-pill ${demo ? "demo" : ""}`}
            >
              <span className="dot" />
              {demo ? "데모 모드" : "실제 데이터"}
              <span className="mode-switch">전환</span>
            </button>
            <button
              onClick={theme}
              className="icon-button"
              aria-label={light ? "다크 모드로 전환" : "라이트 모드로 전환"}
            >
              {light ? <Moon size={18} /> : <Sun size={18} />}
            </button>
            <a
              className="icon-button"
              href="https://github.com"
              target="_blank"
              rel="noreferrer"
              aria-label="GitHub 열기"
            >
              <Github size={18} />
            </a>
          </div>
        </header>
        {demo && (
          <div className="demo-banner">
            <BookOpen size={14} />
            <span>
              데모 미리보기 · 인물, 프로젝트, 활동 및 분석은 샘플 데이터입니다.
              실제 AI를 호출하지 않습니다.
            </span>
            <button onClick={toggleMode}>
              실제 데이터 연결 <ArrowUpRight size={13} />
            </button>
          </div>
        )}
        <main>
          <Routes>
            <Route path="/" element={<Dashboard />} />
            <Route path="/projects" element={<Projects />} />
            <Route path="/projects/:id" element={<ProjectDetail />} />
            <Route path="/activity" element={<ActivityPage />} />
            <Route path="/interview" element={<Interview />} />
            <Route path="/admin" element={<Admin />} />
            <Route
              path="*"
              element={
                <div className="empty-state">
                  <X />
                  <h2>페이지를 찾을 수 없습니다</h2>
                  <Link className="button" to="/">
                    대시보드로 돌아가기
                  </Link>
                </div>
              }
            />
          </Routes>
        </main>
        <footer className="page-footer">
          <span>
            <Code2 size={14} /> DevInsight AI
          </span>
          <span>코드에서 시작하는, 신뢰할 수 있는 이야기.</span>
          <span>{demo ? "SAMPLE PORTFOLIO" : "EVIDENCE-BASED PORTFOLIO"}</span>
        </footer>
      </div>
    </div>
  );
}
