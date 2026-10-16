import { useEffect, useMemo, useState, lazy, Suspense } from "react";
import { Link, useParams } from "react-router-dom";
import {
  ArrowLeft,
  ArrowRight,
  ArrowUpRight,
  Blocks,
  Check,
  ChevronRight,
  Clock,
  Code2,
  ExternalLink,
  FileCode2,
  GitBranch,
  GitCommitHorizontal,
  Github,
  MessageSquare,
  Search,
  ShieldCheck,
  Sparkles,
  Terminal,
} from "lucide-react";
import { useData } from "../context.jsx";
import { api, projectRepositories, safeUrl } from "../api.js";
import {
  EmptyState,
  ErrorState,
  EvidenceLink,
  Loading,
  PageTitle,
  ProjectCard,
  SectionHeading,
  StatusLabel,
  date,
} from "../components.jsx";
const ActivityChart = lazy(() => import("../ActivityChart.jsx"));
const colors = ["#9c8aff", "#5ccab0", "#f1c078", "#6baceb", "#e590b7"];
const sourceKindLabels = {
  CODE: "코드",
  TREE: "폴더 구조",
  DOCUMENT: "문서",
  COMMIT: "커밋",
  PULL_REQUEST: "변경 요청",
};
export function Dashboard() {
  const { overview, loading, error, refresh, demo } = useData();
  if (loading) return <Loading />;
  if (error) return <ErrorState message={error} retry={() => void refresh()} />;
  if (!overview?.profile) return <EmptyState />;
  const { profile, projects } = overview,
    technologies = [
      ...new Set(
        projects
          .flatMap((p) => p.analysis?.report.technologies ?? [p.language])
          .filter(Boolean),
      ),
    ];
  const claims = projects
    .flatMap((p) =>
      (p.analysis?.report.claims ?? [])
        .filter((c) => c.status === "CONFIRMED")
        .map((c) => ({ project: p, claim: c })),
    )
    .slice(0, 3);
  return (
    <>
      <PageTitle
        eyebrow="DEVELOPER OVERVIEW"
        title="좋은 코드는, 경험을 말합니다."
        description="프로젝트와 실제 구현 근거를 통해 개발자를 만나보세요."
        action={
          <Link to="/interview" className="button primary">
            <Sparkles size={16} /> AI에게 질문하기
          </Link>
        }
      />
      <section className="profile-card">
        <div className="profile-content">
          <div className="profile-identity">
            <div className="avatar">
              {profile.avatarUrl ? (
                <img src={safeUrl(profile.avatarUrl)} alt="개발자 프로필" />
              ) : (
                <span>{profile.name.slice(-2)}</span>
              )}
              <span className="avatar-status" />
            </div>
            <div>
              <div className="profile-kicker">
                {demo ? "SAMPLE DEVELOPER" : "GITHUB DEVELOPER"}
                <span className="subtle-badge">
                  {demo ? "가상 인물" : "공개 프로필"}
                </span>
              </div>
              <h2>
                {profile.name}
                <span>@{profile.login}</span>
              </h2>
              <p>{profile.bio || "프로젝트와 구현 경험을 통해 소개합니다."}</p>
            </div>
          </div>
          <div className="profile-tags">
            <span className="muted">TECH STACK</span>
            {technologies.slice(0, 6).map((t) => (
              <span className="tech-tag" key={t}>
                {t}
              </span>
            ))}
          </div>
          <div className="profile-links">
            <a
              href={safeUrl(profile.githubUrl)}
              target="_blank"
              rel="noreferrer"
            >
              <Github size={15} />
              {demo ? "GitHub (샘플)" : "GitHub 프로필"}
              <ArrowUpRight size={14} />
            </a>
            <Link to="/projects">
              프로젝트 둘러보기
              <ArrowRight size={14} />
            </Link>
          </div>
        </div>
        <div className="profile-insight">
          <span>
            <Sparkles size={15} /> 30초 인사이트
          </span>
          <h3>
            무엇을 만들었는지,
            <br />
            <em>어떻게 구현했는지.</em>
          </h3>
          <p>
            {claims[0]?.claim.detail ??
              "공개 프로젝트에 연결된 코드와 문서로 개발 경험을 확인하세요."}
          </p>
          <div>
            <ShieldCheck size={13} />
            {demo
              ? "아래 분석은 모두 샘플입니다"
              : "등록된 코드 근거로 확인하세요"}
          </div>
        </div>
      </section>
      <div className="stats-grid">
        {[
          [Blocks, "공개 프로젝트", projects.length, "개의 프로젝트 경험"],
          [
            FileCode2,
            "연결된 코드·문서",
            overview.evidenceCount,
            "개의 확인 가능한 출처",
          ],
          [Code2, "사용 기술", technologies.length, "개 기술과 구현 맥락"],
          [
            ShieldCheck,
            "분석된 프로젝트",
            overview.analyzedProjects,
            "개 프로젝트에 분석 연결",
          ],
        ].map(([Icon, label, value, sub]) => {
          const I = Icon;
          return (
            <div className="stat-card" key={String(label)}>
              <div>
                <span>{String(label)}</span>
                <I size={17} />
              </div>
              <strong>
                {String(value)}
                <span>
                  {label === "사용 기술"
                    ? "technologies"
                    : label === "공개 프로젝트"
                      ? "projects"
                      : label === "분석된 프로젝트"
                        ? "analyzed"
                        : "sources"}
                </span>
              </strong>
              <small>{String(sub)}</small>
            </div>
          );
        })}
      </div>
      <section>
        <SectionHeading
          title="대표 프로젝트"
          caption="아이디어부터 구현까지, 코드에 담긴 경험"
          link="/projects"
        />
        <div className="project-grid">
          {projects.slice(0, 3).map((p) => (
            <ProjectCard key={p.id} project={p} />
          ))}
        </div>
      </section>
      <div className="dashboard-bottom">
        <section className="panel">
          <SectionHeading
            title="구현으로 확인하는 역량"
            caption="각 역량에서 관련 프로젝트로 이어집니다"
          />
          {claims.map(({ project, claim }, i) => (
            <Link
              className="capability-row"
              to={`/projects/${project.id}`}
              key={`${project.id}-${claim.title}`}
            >
              <span className={`cap-icon cap-${i}`}>
                <Code2 size={19} />
              </span>
              <div>
                <h4>{claim.title}</h4>
                <p>
                  {project.title}{" "}
                  <span>· {claim.evidenceIds.length}개 근거</span>
                </p>
              </div>
              <ChevronRight size={16} />
            </Link>
          ))}
        </section>
        <section className="panel">
          <SectionHeading
            title="사용 언어"
            caption="공개 프로젝트의 GitHub 코드 바이트 기준"
          />
          <LanguageChart languages={overview.languages} />
          <p className="chart-note">
            언어 비율은 숙련도나 역량 점수가 아닙니다.
          </p>
        </section>
      </div>
      <Link className="interview-cta" to="/interview">
        <div className="cta-icon">
          <MessageSquare size={23} />
        </div>
        <div>
          <h3>이 개발자에 대해 더 궁금한가요?</h3>
          <p>기술 선택, 구현 방식, 프로젝트 경험을 근거와 함께 물어보세요.</p>
        </div>
        <span>
          AI 인터뷰 시작 <ArrowRight size={17} />
        </span>
      </Link>
    </>
  );
}
function LanguageChart({ languages }) {
  const entries = Object.entries(languages).sort((a, b) => b[1] - a[1]);
  const total = entries.reduce((a, b) => a + b[1], 0);
  const data = entries.map(([name, value]) => ({
    name,
    value,
    percent: Math.round((value / total) * 100),
  }));
  if (!total) return <p className="muted">아직 언어 통계가 없습니다.</p>;
  return (
    <>
      <div
        className="language-bar"
        role="img"
        aria-label={data.map((d) => `${d.name} ${d.percent}%`).join(", ")}
      >
        {data.map((d, i) => (
          <span
            key={d.name}
            style={{
              width: `${(d.value / total) * 100}%`,
              background: colors[i % colors.length],
            }}
          />
        ))}
      </div>
      <div className="language-legend">
        {data.slice(0, 5).map((d, i) => (
          <div key={d.name}>
            <span style={{ background: colors[i % colors.length] }} />
            <b>{d.name}</b>
            <strong>{d.percent}%</strong>
          </div>
        ))}
      </div>
    </>
  );
}
export function Projects() {
  const { overview, loading, error, refresh } = useData();
  const [search, setSearch] = useState(""),
    [technology, setTechnology] = useState("전체");
  if (loading) return <Loading />;
  if (error) return <ErrorState message={error} retry={() => void refresh()} />;
  const projects = overview?.projects ?? [],
    techs = [
      ...new Set(
        projects.flatMap((p) => p.analysis?.report.technologies ?? []),
      ),
    ];
  const filtered = projects.filter(
    (p) =>
      (
        p.title +
        " " +
        p.description +
        " " +
        projectRepositories(p).map(r => r.fullName).join(" ") +
        " " +
        p.analysis?.report.technologies.join(" ")
      )
        .toLowerCase()
        .includes(search.toLowerCase()) &&
      (technology === "전체" ||
        p.analysis?.report.technologies.includes(technology)),
  );
  return (
    <>
      <PageTitle
        eyebrow="PROJECT EXPLORER"
        title="코드로 이어지는 경험"
        description="관심 있는 프로젝트와 기술을 살펴보세요."
      />
      <div className="filter-toolbar">
        <label className="search-input">
          <Search size={17} />
          <input
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="프로젝트 또는 기술 검색"
            aria-label="프로젝트 검색"
          />
        </label>
        <select
          value={technology}
          onChange={(e) => setTechnology(e.target.value)}
          aria-label="기술 필터"
        >
          <option>전체</option>
          {techs.map((t) => (
            <option key={t}>{t}</option>
          ))}
        </select>
        <span>{filtered.length} projects</span>
      </div>
      {projects.length === 0 ? (
        <EmptyState />
      ) : filtered.length === 0 ? (
        <div className="empty-state">
          <Search />
          <h2>검색 결과가 없습니다</h2>
          <button
            className="button"
            onClick={() => {
              setSearch("");
              setTechnology("전체");
            }}
          >
            필터 초기화
          </button>
        </div>
      ) : (
        <div className="project-grid">
          {filtered.map((p) => (
            <ProjectCard key={p.id} project={p} />
          ))}
        </div>
      )}
    </>
  );
}
export function ProjectDetail() {
  const { id } = useParams(),
    { demo, overview } = useData(),
    [project, setProject] = useState(null),
    [error, setError] = useState(""),
    [tab, setTab] = useState("분석 개요");
  useEffect(() => {
    let alive = true;
    setProject(null);
    setError("");
    if (demo) {
      const p = overview?.projects.find((p) => p.id === Number(id));
      if (p) setProject(p);
      else if (overview) setError("프로젝트를 찾을 수 없습니다.");
    } else
      api(`/api/projects/${id}`)
        .then((p) => {
          if (alive) setProject(p);
        })
        .catch((e) => {
          if (alive) setError(e.message);
        });
    return () => {
      alive = false;
    };
  }, [id, demo, overview]);
  if (error) return <ErrorState message={error} />;
  if (!project) return <Loading />;
  const a = project.analysis,
    report = a?.report;
  return (
    <>
      <Link to="/projects" className="back-link">
        <ArrowLeft size={14} /> 프로젝트 목록
      </Link>
      <PageTitle
        eyebrow="프로젝트 상세 분석"
        title={project.title}
        description={project.description}
        action={
          <div className="title-actions">
            {!demo && projectRepositories(project).map(repo => (
              <a
                key={repo.fullName}
                className="button"
                href={safeUrl(repo.githubUrl)}
                target="_blank"
                rel="noreferrer"
              >
                <Github size={16} /> {repo.fullName} <ArrowUpRight size={14} />
              </a>
            ))}
            {project.demoUrl && (
              <a
                className="button primary"
                href={safeUrl(project.demoUrl)}
                target="_blank"
                rel="noreferrer"
              >
                시연 보기 <ExternalLink size={14} />
              </a>
            )}
          </div>
        }
      />
      <div className="detail-meta">
        <div className="tags">
          {(report?.technologies ?? [project.language]).map((t) => (
            <span key={t}>{t}</span>
          ))}
        </div>
        <span>
          <GitBranch size={14} />
          {a ? (demo ? "샘플 커밋" : `${projectRepositories(project).length > 1 ? "분석 ID " : ""}${a.commitSha.slice(0, 7)}`) : "분석 대기"}
        </span>
        {a && (
          <span>
            <Clock size={14} />
            {date(a.createdAt)}
          </span>
        )}
      </div>
      <div className="tabs" role="tablist" aria-label="프로젝트 상세 탭">
        {["분석 개요", "코드 근거", "개발 이력", "자료"].map((t) => (
          <button
            role="tab"
            aria-selected={tab === t}
            key={t}
            onClick={() => setTab(t)}
            className={tab === t ? "selected" : ""}
          >
            {t}
            {t === "코드 근거" && <span>{a?.evidence.length ?? 0}</span>}
          </button>
        ))}
      </div>
      {tab === "분석 개요" &&
        (!report ? (
          <div className="empty-state">
            <Terminal />
            <h2>아직 분석 결과가 없습니다</h2>
            <p>관리자 분석이 완료되면 코드 근거와 함께 표시됩니다.</p>
          </div>
        ) : (
          <div className="detail-layout">
            <div>
              <section className="panel overview-panel">
                <div className="eyebrow">
                  <Sparkles size={14} />{" "}
                  {demo ? "샘플 분석" : "AI 분석"}
                </div>
                <h2>{report.summary}</h2>
                <p>{report.purpose}</p>
              </section>
              <div className="claims-list">
                {report.claims.map((claim, i) => (
                  <section className="panel claim-panel" key={i}>
                    <div className="claim-heading">
                      <h3>{claim.title}</h3>
                      <StatusLabel status={claim.status} />
                    </div>
                    <p>{claim.detail}</p>
                    <div className="evidence-links">
                      {claim.evidenceIds
                        .map((id) => a.evidence.find((e) => e.id === id))
                        .filter((e) => !!e)
                        .map((e) => (
                          <EvidenceLink key={e.id} source={e} />
                        ))}
                    </div>
                  </section>
                ))}
              </div>
            </div>
            <aside className="detail-aside">
              <section className="panel">
                <h3>
                  <MessageSquare size={17} /> 면접에서 나눌 이야기
                </h3>
                <ol className="question-list">
                  {report.interviewQuestions.map((q) => (
                    <li key={q}>{q}</li>
                  ))}
                </ol>
                <Link
                  className="button wide"
                  to={`/interview?project=${project.id}`}
                >
                  AI에게 질문하기 <ArrowUpRight size={14} />
                </Link>
              </section>
              <section className="panel limits-panel">
                <h3>
                  <ShieldCheck size={17} /> 분석 범위와 한계
                </h3>
                {report.limitations.map((l, i) => (
                  <p key={i}>{l}</p>
                ))}
                <small>{a?.model}</small>
              </section>
            </aside>
          </div>
        ))}
      {tab === "코드 근거" && (
        <div className="evidence-panels">
          {a?.evidence.length ? (
            a.evidence.map((e) => (
              <details className="panel evidence-detail" key={e.id}>
                <summary>
                  <FileCode2 size={18} />
                  <span>{e.path}</span>
                  <span className="subtle-badge">{sourceKindLabels[e.kind] ?? "기타 근거"}</span>
                  <ChevronRight size={16} />
                </summary>
                <pre>
                  <code>{e.text}</code>
                </pre>
                <EvidenceLink source={e} />
              </details>
            ))
          ) : (
            <p className="muted">연결된 근거가 없습니다.</p>
          )}
        </div>
      )}
      {tab === "개발 이력" && (
        <section className="panel">
          <SectionHeading
            title="최근 커밋"
            caption="저장소별 최근 최대 30건의 표본입니다. 전체 활동이나 개인 기여도로 해석하지 않습니다."
          />
          {a?.activity.commits.map((c) => (
            <div className="commit-row" key={`${c.url}-${c.sha}`}>
              <GitCommitHorizontal size={20} />
              <div>
                <strong>{c.message.split("\n")[0]}</strong>
                <span>
                  {c.author} · {date(c.date)}
                </span>
              </div>
              {demo ? (
                <code>샘플</code>
              ) : (
                <a href={safeUrl(c.url)} target="_blank" rel="noreferrer">
                  <code>{c.sha.slice(0, 7)}</code>
                  <ArrowUpRight size={13} />
                </a>
              )}
            </div>
          ))}
          {a?.activity.warnings.map((w) => (
            <p key={w} className="chart-note">
              {w}
            </p>
          ))}
        </section>
      )}
      {tab === "자료" && (
        <section className="panel">
          <SectionHeading
            title="공개 프로젝트 자료"
            caption="명시적으로 공개 승인된 첨부 자료입니다."
          />
          {project.documents.length ? (
            project.documents.map((d) => (
              <div className="document-item" key={d.id}>
                {d.contentType.startsWith("image/") && (
                  <img className="document-preview" src={d.url} alt={d.name} />
                )}
                <FileCode2 size={18} />
                <a href={d.url} target="_blank" rel="noreferrer">
                  {d.name}
                </a>
                <span>{(d.sizeBytes / 1024).toFixed(1)} KB</span>
                <Check size={15} />
              </div>
            ))
          ) : (
            <div className="empty-state compact">
              <FileCode2 />
              <p>공개된 첨부 자료가 없습니다.</p>
            </div>
          )}
        </section>
      )}
    </>
  );
}
export function ActivityPage() {
  const { overview, loading, error, refresh, demo } = useData();
  const commits = useMemo(
    () =>
      (overview?.projects ?? [])
        .flatMap((p) =>
          (p.analysis?.activity.commits ?? []).map((c) => ({
            ...c,
            project: p.title,
            projectId: p.id,
          })),
        )
        .sort((a, b) => b.date.localeCompare(a.date)),
    [overview],
  );
  const data = useMemo(() => {
    const m = new Map();
    commits.forEach((c) => {
      const day = c.date.slice(0, 10);
      m.set(day, (m.get(day) ?? 0) + 1);
    });
    return [...m.entries()]
      .sort()
      .map(([day, count]) => ({ day: day.slice(5), count }));
  }, [commits]);
  if (loading) return <Loading />;
  if (error) return <ErrorState message={error} retry={() => void refresh()} />;
  return (
    <>
      <PageTitle
        eyebrow="DEVELOPMENT ACTIVITY"
        title="지속적으로 쌓아온 기록"
        description="공개 저장소에서 수집한 개발 이력을 프로젝트의 맥락과 함께 확인하세요."
      />
      <div className="activity-layout">
        <section className="panel">
          <SectionHeading
            title={demo ? "샘플 커밋 활동" : "수집한 커밋 활동"}
            caption="저장소별 최근 30건 이내 표본 · 전체 기여도 통계가 아닙니다"
          />
          <div className="activity-chart">
            {data.length ? (
              <Suspense fallback={<p>차트를 불러오는 중입니다.</p>}>
                <ActivityChart data={data} />
              </Suspense>
            ) : (
              <p>수집한 활동이 없습니다.</p>
            )}
          </div>
        </section>
        <section className="panel">
          <SectionHeading title="언어 분포" caption="코드 바이트 기준" />
          <LanguageChart languages={overview?.languages ?? {}} />
          <p className="chart-note">숙련도 점수가 아닌 저장소 구성입니다.</p>
        </section>
      </div>
      <section className="panel">
        <SectionHeading
          title="프로젝트 타임라인"
          caption={`${commits.length}개의 수집된 커밋`}
        />
        {commits.slice(0, 40).map((c) => (
          <div className="commit-row" key={`${c.projectId}-${c.url}-${c.sha}`}>
            <GitCommitHorizontal size={19} />
            <div>
              <Link to={`/projects/${c.projectId}`}>{c.project}</Link>
              <strong>{c.message.split("\n")[0]}</strong>
            </div>
            <span>{date(c.date)}</span>
            {!demo && (
              <a
                href={safeUrl(c.url)}
                target="_blank"
                rel="noreferrer"
                aria-label="GitHub 커밋"
              >
                <ArrowUpRight size={15} />
              </a>
            )}
          </div>
        ))}
      </section>
    </>
  );
}
