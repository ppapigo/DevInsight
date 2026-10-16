import { useState } from "react";
import { Check, ChevronLeft, ChevronRight, Github, LoaderCircle, Plus, Search, X } from "lucide-react";
import { api, projectRepositories } from "./api.js";

export function RepositoryImporter({ projects, onUpdated }) {
  const [username, setUsername] = useState("");
  const [listing, setListing] = useState(null);
  const [selected, setSelected] = useState([]);
  const [links, setLinks] = useState("");
  const [mode, setMode] = useState("group");
  const [title, setTitle] = useState("");
  const [targetId, setTargetId] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [progress, setProgress] = useState("");
  const registered = new Set(projects.flatMap(projectRepositories).map(r => r.fullName.toLowerCase()));
  const isRegistered = (repo) => registered.has(repo.fullName.toLowerCase());
  const isSelected = (repo) => selected.some(r => r.fullName.toLowerCase() === repo.fullName.toLowerCase());
  const available = listing?.repositories.filter(r => !isRegistered(r)) ?? [];
  const target = projects.find(p => String(p.id) === targetId);
  const tooMany = mode === "separate" ? selected.length > 30
    : selected.length + (mode === "existing" && target ? projectRepositories(target).length : 0) > 10;

  function addSelection(repositories) {
    setSelected(previous => {
      const entries = new Map(previous.map(r => [r.fullName.toLowerCase(), r]));
      repositories.filter(r => !isRegistered(r)).forEach(r => entries.set(r.fullName.toLowerCase(), r));
      return [...entries.values()];
    });
  }
  function removeSelection(repo) {
    setSelected(previous => previous.filter(r => r.fullName.toLowerCase() !== repo.fullName.toLowerCase()));
  }
  async function search(page = 1, login = username.trim()) {
    setBusy(true);
    setError("");
    try {
      setListing(await api(`/api/github/repos?username=${encodeURIComponent(login)}&page=${page}`));
    } catch (e) {
      setError(e.message);
    } finally {
      setBusy(false);
    }
  }
  function addLinks(e) {
    e.preventDefault();
    setError("");
    const repositories = [];
    for (const value of links.trim().split(/[\s,]+/)) {
      const match = value.match(/^(?:https:\/\/github\.com\/)?([A-Za-z0-9](?:[A-Za-z0-9-]{0,38}))\/([A-Za-z0-9_.-]{1,100})\/?$/i);
      if (!match) {
        setError(`저장소 주소를 확인해 주세요: ${value}`);
        return;
      }
      const name = match[2].replace(/\.git$/i, "");
      if (!name || name === "." || name === "..") {
        setError(`저장소 주소를 확인해 주세요: ${value}`);
        return;
      }
      repositories.push({ owner: match[1], name, fullName: `${match[1]}/${name}` });
    }
    addSelection(repositories);
    setNotice("주소를 선택 목록에 추가했습니다. 등록할 때 공개 저장소인지 확인합니다.");
    setLinks("");
  }
  async function importSelected(e) {
    e.preventDefault();
    if (busy || selected.length === 0 || tooMany) return;
    setBusy(true);
    setError("");
    setNotice("");
    const repositories = selected.map(({ owner, name }) => ({ owner, name }));
    try {
      if (mode === "separate") {
        let completed = 0;
        const failures = [];
        for (const [index, repo] of selected.entries()) {
          setProgress(`${index + 1}/${selected.length} · ${repo.fullName}`);
          try {
            await api("/api/projects/import", { method: "POST", body: JSON.stringify({ owner: repo.owner, name: repo.name }) });
            removeSelection(repo);
            completed++;
          } catch (e) {
            failures.push(`${repo.fullName}: ${e.message}`);
          }
        }
        setNotice(`${completed}개 저장소를 프로젝트로 등록했습니다. 실패한 항목은 선택 목록에 남아 있습니다.`);
        setError(failures.join("\n"));
      } else {
        setProgress(`${selected.length}개 저장소 연결 중`);
        await api(mode === "existing" ? `/api/projects/${targetId}/repositories` : "/api/projects", {
          method: "POST",
          body: JSON.stringify(mode === "existing" ? { repositories } : { title: title.trim(), repositories }),
        });
        setSelected([]);
        setTitle("");
        setNotice(mode === "existing" ? "저장소를 연결했습니다. 최신 자료로 다시 분석해 주세요."
          : "새 프로젝트를 비공개로 등록했습니다. 분석과 공개 설정을 확인해 주세요.");
      }
      await onUpdated();
    } catch (e) {
      setError(e.message);
    } finally {
      setBusy(false);
      setProgress("");
    }
  }
  return (
    <section className="panel repository-importer">
      <div className="section-heading">
        <div>
          <h2>프로젝트·GitHub 저장소 등록</h2>
          <p>여러 계정의 공개 저장소를 선택해 프로젝트로 등록하세요. 새 프로젝트는 비공개로 시작합니다.</p>
        </div>
        <Github size={24} />
      </div>
      <form className="import-form" onSubmit={e => { e.preventDefault(); void search(); }}>
        <label className="search-input">
          <Search size={17} />
          <input placeholder="GitHub 사용자명 또는 조직명" aria-label="GitHub 사용자명" required maxLength={39}
            disabled={busy} value={username} onChange={e => setUsername(e.target.value)} />
        </label>
        <button className="button" disabled={busy || !username.trim()}><Search size={16} /> 저장소 조회</button>
      </form>
      <form className="repository-links" onSubmit={addLinks}>
        <label htmlFor="repository-urls">저장소 주소로 추가</label>
        <textarea id="repository-urls" placeholder={"https://github.com/owner/frontend\nhttps://github.com/owner/backend"}
          maxLength={6000} value={links} onChange={e => setLinks(e.target.value)} disabled={busy} />
        <div><small>주소 또는 owner/repository를 줄바꿈으로 구분하세요.</small>
          <button className="button small" disabled={busy || !links.trim()}><Plus size={14} /> 선택 목록에 추가</button></div>
      </form>
      {listing && <>
        <div className="repo-heading"><span>{listing.profile.name} <small>@{listing.profile.login}</small></span>
          <span>{listing.profile.publicRepos}개 공개 저장소</span></div>
        {available.length > 0 && <label className="repo-select-all">
          <input type="checkbox" disabled={busy} checked={available.every(isSelected)}
            onChange={e => e.target.checked ? addSelection(available) : available.forEach(removeSelection)} />
          이 페이지 전체 선택
        </label>}
        <div className="repo-list">{listing.repositories.map(repo => <div className="repo-row" key={repo.githubId}>
          <input type="checkbox" aria-label={`${repo.fullName} 선택`} disabled={busy || isRegistered(repo)}
            checked={isSelected(repo)} onChange={e => e.target.checked ? addSelection([repo]) : removeSelection(repo)} />
          <Github size={17} /><div><strong>{repo.fullName}{repo.fork && <span className="subtle-badge">FORK</span>}</strong>
            <p>{repo.description || "설명이 없는 저장소"}</p><small>{repo.language || "언어 미확인"}</small></div>
          {isRegistered(repo) && <span className="subtle-badge"><Check size={14} /> 등록됨</span>}
        </div>)}</div>
        {listing.repositories.length === 0 && <p className="muted">공개 저장소가 없습니다.</p>}
        <div className="pagination">
          <button className="button small" disabled={busy || listing.page <= 1}
            onClick={() => void search(listing.page - 1, listing.profile.login)}><ChevronLeft size={14} /> 이전</button>
          <span>{listing.page} 페이지</span>
          <button className="button small" disabled={busy || !listing.hasMore}
            onClick={() => void search(listing.page + 1, listing.profile.login)}>다음 <ChevronRight size={14} /></button>
        </div>
      </>}
      <form className="repository-selection" onSubmit={e => void importSelected(e)}>
        <div className="section-heading"><h3>선택한 저장소 {selected.length}개</h3>
          <button type="button" className="button small" disabled={busy || !selected.length} onClick={() => setSelected([])}>선택 해제</button></div>
        <p className="muted">계정이나 페이지를 바꿔도 선택은 유지됩니다. 프로젝트당 최대 10개 저장소를 연결할 수 있습니다.</p>
        <div className="selected-repositories">{selected.map(repo => <span key={repo.fullName.toLowerCase()}>
          {repo.fullName}<button type="button" className="icon-button" aria-label={`${repo.fullName} 선택 해제`}
            disabled={busy} onClick={() => removeSelection(repo)}><X size={13} /></button>
        </span>)}</div>
        <div className="editor-fields">
          <label>등록 방식<select value={mode} disabled={busy} onChange={e => setMode(e.target.value)}>
            <option value="group">하나의 새 프로젝트로 묶기</option>
            <option value="separate">저장소마다 별도 프로젝트로 등록</option>
            <option value="existing" disabled={!projects.length}>기존 프로젝트에 저장소 추가</option>
          </select></label>
          {mode === "group" && <label>새 프로젝트 이름<input required maxLength={255} value={title}
            disabled={busy} onChange={e => setTitle(e.target.value)} placeholder="예: 팀 협업 서비스" /></label>}
          {mode === "existing" && <label>연결할 프로젝트<select required value={targetId} disabled={busy} onChange={e => setTargetId(e.target.value)}>
            <option value="">프로젝트 선택</option>{projects.map(p => <option key={p.id} value={p.id}>{p.title} · 저장소 {projectRepositories(p).length}개</option>)}
          </select></label>}
        </div>
        {tooMany && <p className="inline-error" role="alert">{mode === "separate" ? "한 번에 최대 30개를 등록할 수 있습니다." : "프로젝트당 저장소는 최대 10개입니다. 선택을 줄이거나 별도 프로젝트로 등록해 주세요."}</p>}
        <button className="button primary" disabled={busy || !selected.length || tooMany || (mode === "group" && !title.trim()) || (mode === "existing" && !target)}>
          {busy ? <LoaderCircle className="spin" size={16} /> : <Plus size={16} />} 선택한 저장소 등록
        </button>
      </form>
      {progress && <p role="status" className="notice">{progress}</p>}
      {notice && <p role="status" className="notice">{notice}</p>}
      {error && <p role="alert" className="inline-error import-errors">{error}</p>}
    </section>
  );
}
