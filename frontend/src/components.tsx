import type { ReactNode } from "react";
import { Link } from "react-router-dom";
import {
  ArrowUpRight,
  Check,
  ChevronRight,
  Code2,
  ExternalLink,
  FileCode2,
  GitBranch,
  Layers,
  LoaderCircle,
  ShieldCheck,
  TriangleAlert,
} from "lucide-react";
import type { Project, Status, Evidence } from "./types";
import { useData } from "./context";
import { safeUrl } from "./api";
export function PageTitle({
  eyebrow,
  title,
  description,
  action,
}: {
  eyebrow: string;
  title: string;
  description: string;
  action?: ReactNode;
}) {
  return (
    <div className="page-title">
      <div>
        <div className="eyebrow">{eyebrow}</div>
        <h1>{title}</h1>
        <p>{description}</p>
      </div>
      {action}
    </div>
  );
}
export function Loading() {
  return (
    <div className="empty-state">
      <LoaderCircle className="spin" />
      <p>포트폴리오를 불러오는 중입니다.</p>
    </div>
  );
}
export function ErrorState({
  message,
  retry,
}: {
  message: string;
  retry?: () => void;
}) {
  return (
    <div className="error-state" role="alert">
      <TriangleAlert size={22} />
      <div>
        <strong>정보를 불러오지 못했습니다</strong>
        <p>{message}</p>
        {retry && (
          <button className="button small" onClick={retry}>
            다시 시도
          </button>
        )}
      </div>
    </div>
  );
}
export function EmptyState() {
  return (
    <div className="empty-state">
      <Layers size={36} />
      <h2>첫 번째 프로젝트를 기다리고 있어요</h2>
      <p>관리자 워크스페이스에서 저장소를 가져오고 분석한 뒤 공개해 주세요.</p>
      <Link to="/admin" className="button primary">
        프로젝트 등록 <ArrowUpRight size={16} />
      </Link>
    </div>
  );
}
export function StatusLabel({ status }: { status: Status }) {
  return (
    <span
      className={`status-label ${status === "CONFIRMED" ? "confirmed" : "uncertain"}`}
    >
      {status === "CONFIRMED" ? (
        <ShieldCheck size={13} />
      ) : (
        <TriangleAlert size={13} />
      )}{" "}
      {status === "CONFIRMED"
        ? "근거 연결됨"
        : status === "NEEDS_VERIFICATION"
          ? "확인 필요"
          : "근거 부족"}
    </span>
  );
}
export function EvidenceLink({ source }: { source: Evidence }) {
  const { demo } = useData();
  return demo ? (
    <span className="evidence-link" title="데모용 출처 예시">
      <FileCode2 size={13} />
      {source.path}
      <span className="muted">샘플</span>
    </span>
  ) : (
    <a
      className="evidence-link"
      href={safeUrl(source.url)}
      target="_blank"
      rel="noreferrer"
    >
      <FileCode2 size={13} />
      {source.path}
      <ExternalLink size={12} />
    </a>
  );
}
export function ProjectArt({ project }: { project: Project }) {
  return (
    <div className={`project-art art-${project.id % 3}`} aria-hidden="true">
      <div className="art-grid" />
      {project.id % 3 === 1 ? (
        <div className="art-pipeline">
          <div className="art-node">
            <GitBranch size={24} />
            <span>repository</span>
          </div>
          <span className="pipeline-line" />
          <div className="art-node accent">
            <Code2 size={25} />
            <span>insight</span>
          </div>
          <span className="pipeline-line" />
          <div className="art-node">
            <ShieldCheck size={24} />
            <span>evidence</span>
          </div>
        </div>
      ) : project.id % 3 === 2 ? (
        <div className="art-board">
          {["BACKLOG", "IN PROGRESS", "DONE"].map((c, i) => (
            <div key={c}>
              <small>{c}</small>
              {[0, 1].map((n) => (
                <span key={n} className={`mini-card mini-${i}`}>
                  <i />
                  <b />
                  <em />
                  {i === 2 && <Check size={10} />}
                </span>
              ))}
            </div>
          ))}
        </div>
      ) : (
        <div className="art-terminal">
          <div>
            <i />
            <i />
            <i />
            <span>archive / terminal</span>
          </div>
          <p>
            <span>GET</span> /api/documents
          </p>
          <p className="terminal-json">{'{ "status": "ok",'}</p>
          <p className="terminal-json">{'  "knowledge": "connected" }'}</p>
          <b className="terminal-cursor" />
        </div>
      )}
      <span className="art-caption">
        {project.language ?? "PROJECT"} / {String(project.id).padStart(2, "0")}
      </span>
    </div>
  );
}
export function ProjectCard({ project }: { project: Project }) {
  const tech =
    project.analysis?.report.technologies ?? [project.language].filter(Boolean);
  return (
    <Link to={`/projects/${project.id}`} className="project-card">
      <ProjectArt project={project} />
      <div className="project-card-body">
        <div className="project-card-heading">
          <h3>{project.title}</h3>
          <ArrowUpRight size={18} />
        </div>
        <p>{project.analysis?.report.summary ?? project.description}</p>
        <div className="tags">
          {tech.slice(0, 4).map((t) => (
            <span key={t}>{t}</span>
          ))}
        </div>
        <div className="project-card-footer">
          <span>
            <span
              className={`language-dot ${project.language?.toLowerCase()}`}
            />
            {project.language || "언어 미확인"}
          </span>
          <span>
            <FileCode2 size={13} />
            {project.analysis?.evidence.length ?? 0}개 근거
            <ChevronRight size={13} />
          </span>
        </div>
      </div>
    </Link>
  );
}
export function SectionHeading({
  title,
  caption,
  link,
}: {
  title: string;
  caption?: string;
  link?: string;
}) {
  return (
    <div className="section-heading">
      <div>
        <h2>{title}</h2>
        {caption && <p>{caption}</p>}
      </div>
      {link && (
        <Link to={link}>
          전체 보기 <ChevronRight size={14} />
        </Link>
      )}
    </div>
  );
}
export function date(value: string) {
  return new Intl.DateTimeFormat("ko-KR", {
    year: "numeric",
    month: "short",
    day: "numeric",
  }).format(new Date(value));
}
