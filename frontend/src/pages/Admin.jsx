import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import {
  ArrowUpRight,
  FileUp,
  Github,
  LoaderCircle,
  Lock,
  Play,
  RefreshCw,
  ShieldCheck,
} from "lucide-react";
import { useData } from "../context.jsx";
import { api, projectRepositories, safeUrl } from "../api.js";
import { RepositoryImporter } from "../RepositoryImporter.jsx";
import { ErrorState, PageTitle } from "../components.jsx";
const jobStatusLabels = {
  PENDING: "분석 대기",
  RUNNING: "분석 중",
  COMPLETED: "분석 완료",
  FAILED: "분석 실패",
};
export function Admin() {
  const {
      demo,
      session,
      toggleMode,
      refresh,
      error: connectionError,
    } = useData(),
    [projects, setProjects] = useState([]),
    [error, setError] = useState("");
  const load = () => api("/api/admin/projects").then(data => {
    setProjects(data);
    setError("");
  });
  useEffect(() => {
    if (!demo && session.admin) void load().catch((e) => setError(e.message));
  }, [demo, session.admin]);
  const reload = async () => {
    await load();
    await refresh();
  };
  if (!demo && connectionError)
    return (
      <ErrorState message={connectionError} retry={() => void refresh()} />
    );
  return (
    <>
      <PageTitle
        eyebrow="ADMIN WORKSPACE"
        title="경험을 정리하는 작업 공간"
        description="공개 저장소를 가져오고, 분석과 공개 범위를 관리하세요."
        action={
          session.admin ? (
            <button
              className="button"
              onClick={() =>
                void api("/api/logout", { method: "POST" }).then(() =>
                  window.location.reload(),
                )
              }
            >
              로그아웃
            </button>
          ) : undefined
        }
      />
      {demo ? (
        <section className="panel admin-gate">
          <div className="welcome-icon">
            <Lock size={28} />
          </div>
          <h2>관리자 기능은 실제 데이터 모드에서 사용할 수 있어요</h2>
          <p>
            데모에서는 샘플 포트폴리오를 둘러볼 수 있습니다.
            <br />
            GitHub OAuth를 설정하면 저장소 등록, 자료 업로드와 분석을 관리할 수
            있습니다.
          </p>
          <button className="button primary" onClick={toggleMode}>
            실제 데이터로 전환 <ArrowUpRight size={15} />
          </button>
        </section>
      ) : !session.admin ? (
        <section className="panel admin-gate">
          <Github size={32} />
          <h2>관리자 GitHub 로그인</h2>
          <p>환경변수에 허용된 GitHub 계정만 접근할 수 있습니다.</p>
          {new URLSearchParams(location.search).has("auth") && (
            <p className="inline-error">
              로그인하지 못했습니다. 허용된 관리자 계정을 확인해 주세요.
            </p>
          )}
          {session.oauthEnabled ? (
            <a className="button primary" href="/oauth2/authorization/github">
              <Github size={16} /> GitHub로 로그인
            </a>
          ) : (
            <div className="setup-guide">
              <ShieldCheck size={19} />
              <p>
                OAuth가 설정되지 않았습니다. README에 따라{" "}
                <code>GITHUB_CLIENT_ID</code>, <code>GITHUB_CLIENT_SECRET</code>
                , <code>ADMIN_GITHUB_LOGIN</code>을 설정하고 백엔드를 실행해
                주세요.
              </p>
            </div>
          )}
        </section>
      ) : (
        <>
          {error && <ErrorState message={error} />}{" "}
          <RepositoryImporter projects={projects} onUpdated={reload} />
          <div className="section-heading">
            <div>
              <h2>등록한 프로젝트</h2>
              <p>공개 승인된 자료는 AI 분석과 면접관에게 공개됩니다.</p>
            </div>
            <button
              className="icon-button"
              aria-label="프로젝트 새로고침"
              onClick={() => void load().catch(e => setError(e.message))}
            >
              <RefreshCw size={17} />
            </button>
          </div>
          {!session.aiConfigured && (
            <div className="notice warning">
              실제 분석과 Q&A에는 OPENAI_API_KEY 설정이 필요합니다.
            </div>
          )}
          {projects.map((p) => (
            <ProjectEditor key={p.id} project={p} onUpdated={reload} />
          ))}
          {projects.length === 0 && (
            <div className="empty-state">
              <Github />
              <p>위에서 저장소를 선택하고 첫 프로젝트를 등록해 주세요.</p>
            </div>
          )}
        </>
      )}
    </>
  );
}
function ProjectEditor({ project, onUpdated }) {
  const [title, setTitle] = useState(project.title),
    [description, setDescription] = useState(project.description ?? ""),
    [demoUrl, setDemoUrl] = useState(project.demoUrl ?? ""),
    [published, setPublished] = useState(project.published),
    [featured, setFeatured] = useState(project.featured),
    [busy, setBusy] = useState(false),
    [error, setError] = useState(""),
    [notice, setNotice] = useState(""),
    [job, setJob] = useState(null);
  useEffect(() => {
    if (!job || ["COMPLETED", "FAILED"].includes(job.status)) return;
    let active = true;
    const timer = setTimeout(() => {
      api(`/api/analysis/jobs/${job.id}`)
        .then(async (j) => {
          if (!active) return;
          setJob(j);
          if (j.status === "COMPLETED") await onUpdated();
        })
        .catch((e) => {
          if (active) {
            setError(e.message);
            setJob(null);
          }
        });
    }, 2500);
    return () => {
      active = false;
      clearTimeout(timer);
    };
  }, [job, onUpdated]);
  async function action(fn) {
    setBusy(true);
    setError("");
    setNotice("");
    try {
      await fn();
      await onUpdated();
    } catch (e) {
      setError(e.message);
    } finally {
      setBusy(false);
    }
  }
  async function save(e) {
    e.preventDefault();
    await action(async () => {
      await api(`/api/projects/${project.id}`, {
        method: "PATCH",
        body: JSON.stringify({
          title,
          description,
          demoUrl,
          published,
          featured,
        }),
      });
      setNotice("프로젝트 설정을 저장했습니다.");
    });
  }
  async function upload(files) {
    if (!files.length) return;
    await action(async () => {
      let completed = 0;
      const failures = [];
      for (const file of files) {
        try {
          const data = new FormData();
          data.append("file", file);
          await api(`/api/projects/${project.id}/documents`, { method: "POST", body: data });
          completed++;
        } catch (e) {
          failures.push(`${file.name}: ${e.message}`);
        }
      }
      setNotice(`${completed}개 파일을 비공개로 저장했습니다. 공개 승인 후 다음 분석에 포함됩니다.`);
      if (failures.length) setError(failures.join("\n"));
    });
  }
  return (
    <section className="panel project-editor">
      <div className="section-heading">
        <div>
          <h3>{project.title}</h3>
          <p>
            {project.published
              ? "공개 포트폴리오에 표시 중"
              : "비공개 · 관리자만 확인 가능"}
          </p>
        </div>
        <Link to={`/projects/${project.id}`} className="button small">
          상세 보기 <ArrowUpRight size={13} />
        </Link>
      </div>
      <div className="connected-repositories">
        <strong>연결된 GitHub 저장소 {projectRepositories(project).length}개</strong>
        {projectRepositories(project).map(repo => <a key={repo.fullName} href={safeUrl(repo.githubUrl)} target="_blank" rel="noreferrer">
          <Github size={14} /> {repo.fullName} <ArrowUpRight size={13} />
        </a>)}
        <small className="muted">위 등록 영역에서 ‘기존 프로젝트에 저장소 추가’를 선택해 더 연결할 수 있습니다.</small>
      </div>
      <form onSubmit={(e) => void save(e)}>
        <div className="editor-fields">
          <label>
            프로젝트 이름
            <input
              required
              maxLength={255}
              value={title}
              onChange={(e) => setTitle(e.target.value)}
            />
          </label>
          <label>
            시연 링크 (HTTPS)
            <input
              type="url"
              maxLength={1000}
              placeholder="https://…"
              value={demoUrl}
              onChange={(e) => setDemoUrl(e.target.value)}
            />
          </label>
          <label className="full-field">
            프로젝트 소개
            <textarea
              maxLength={3000}
              value={description}
              onChange={(e) => setDescription(e.target.value)}
            />
          </label>
        </div>
        <div className="editor-options">
          <label>
            <input
              type="checkbox"
              checked={published}
              onChange={(e) => setPublished(e.target.checked)}
            />{" "}
            면접관에게 프로젝트 공개
          </label>
          <label>
            <input
              type="checkbox"
              checked={featured}
              onChange={(e) => setFeatured(e.target.checked)}
            />{" "}
            대표 프로젝트
          </label>
          <button className="button small" disabled={busy}>
            설정 저장
          </button>
        </div>
      </form>
      <div className="analysis-controls">
        <div>
          <strong>
            {project.analysis ? "분석 결과 저장됨" : "분석 결과 없음"}
          </strong>
          <p>
            {project.analysis
              ? `${projectRepositories(project).length > 1 ? "분석 ID" : "커밋"} ${project.analysis.commitSha.slice(0, 7)} · ${project.analysis.model}`
              : "핵심 코드와 승인된 문서를 수집해 분석합니다."}
          </p>
        </div>
        <button
          className="button primary small"
          disabled={
            busy || job?.status === "RUNNING" || job?.status === "PENDING"
          }
          onClick={() =>
            void action(async () =>
              setJob(
                await api(`/api/projects/${project.id}/analyze`, {
                  method: "POST",
                }),
              ),
            )
          }
        >
          <Play size={14} /> 분석 실행
        </button>
        <button
          className="button small"
          disabled={
            busy || job?.status === "RUNNING" || job?.status === "PENDING"
          }
          onClick={() =>
            void action(async () =>
              setJob(
                await api(`/api/projects/${project.id}/analyze?force=true`, {
                  method: "POST",
                }),
              ),
            )
          }
        >
          강제 재분석
        </button>
      </div>
      {job && (
        <div className={`job-status ${job.status.toLowerCase()}`} role="status">
          {["PENDING", "RUNNING"].includes(job.status) && (
            <LoaderCircle size={15} className="spin" />
          )}
          <strong>{jobStatusLabels[job.status] ?? "상태 확인 중"}</strong> {job.message}
        </div>
      )}
      <div className="document-admin">
        <div className="section-heading">
          <div>
            <h4>프로젝트 자료</h4>
            <p>여러 파일 선택 가능 · 파일당 최대 10MB · 프로젝트당 30개 · PDF, DOCX, PPTX, TXT, MD, PNG, JPEG</p>
          </div>
          <label
            className={`button small upload-label ${busy ? "disabled" : ""}`}
          >
            <FileUp size={15} /> 자료 업로드
            <input
              type="file"
              multiple
              aria-label={`${project.title} 자료 업로드`}
              disabled={busy}
              accept=".pdf,.docx,.pptx,.txt,.md,.png,.jpg,.jpeg"
              onChange={(e) => {
                void upload(Array.from(e.target.files ?? []));
                e.target.value = "";
              }}
            />
          </label>
        </div>
        {project.documents.map((d) => (
          <div className="document-admin-row" key={d.id}>
            <a href={d.url} target="_blank" rel="noreferrer">
              {d.name}
            </a>
            <span>{(d.sizeBytes / 1024).toFixed(0)}KB</span>
            <label>
              <input
                type="checkbox"
                disabled={busy}
                checked={d.publicApproved}
                onChange={(e) => {
                  const approved = e.target.checked;
                  void action(async () => {
                    await api(`/api/documents/${d.id}/approval`, {
                      method: "PATCH",
                      body: JSON.stringify({ publicApproved: approved }),
                    });
                    setNotice(
                      "공개 범위를 변경했습니다. 최신 자료로 다시 분석해 주세요.",
                    );
                  });
                }}
              />
              공개 승인
            </label>
          </div>
        ))}
        <small className="muted">
          자료 공개 범위를 바꾸면 기존 공개 분석 연결이 해제됩니다. 재분석 후
          최신 결과를 공개합니다.
        </small>
      </div>
      {notice && (
        <p className="notice" role="status">
          {notice}
        </p>
      )}
      {error && (
        <p className="inline-error import-errors" role="alert">
          {error}
        </p>
      )}
    </section>
  );
}
