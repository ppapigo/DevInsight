import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { RepositoryImporter } from "./RepositoryImporter.jsx";
import { Admin } from "./pages/Admin.jsx";
import { ProjectDetail } from "./pages/Portfolio.jsx";
import { api } from "./api.js";

vi.mock("./api.js", async importOriginal => ({ ...await importOriginal(), api: vi.fn() }));
vi.mock("./context.jsx", () => ({ useData: () => ({
  demo: false, session: { admin: true, aiConfigured: true }, refresh: vi.fn(),
}) }));

const repo = (owner, name, githubId) => ({ owner, name, githubId, fullName: `${owner}/${name}`, language: "JavaScript" });
const existing = {
  id: 7, title: "Team service", description: "", published: false, featured: false,
  repository: "alice/web", githubUrl: "https://github.com/alice/web", language: "JavaScript", documents: [],
  repositories: [{ id: 1, fullName: "alice/web", githubUrl: "https://github.com/alice/web" }],
};
const listing = (owner, repositories, page = 1, hasMore = false) => ({
  profile: { login: owner, name: owner, publicRepos: repositories.length }, repositories, page, hasMore,
});
function addLinks(value) {
  fireEvent.change(screen.getByLabelText("저장소 주소로 추가"), { target: { value } });
  fireEvent.click(screen.getByRole("button", { name: "선택 목록에 추가" }));
}
async function search(owner) {
  fireEvent.change(screen.getByLabelText("GitHub 사용자명"), { target: { value: owner } });
  fireEvent.click(screen.getByRole("button", { name: "저장소 조회" }));
  await screen.findByText(`@${owner}`);
}
beforeEach(() => vi.clearAllMocks());
afterEach(cleanup);

describe("multiple projects and repository registration", () => {
  it("keeps selections across accounts and pages, then creates one private project", async () => {
    const updated = vi.fn();
    api.mockImplementation(async path => {
      if (path.includes("username=alice&page=1")) return listing("alice", [repo("alice", "web", 1)], 1, true);
      if (path.includes("username=alice&page=2")) return listing("alice", [repo("alice", "worker", 2)], 2);
      if (path.includes("username=team")) return listing("team", [repo("team", "api", 3)]);
      return {};
    });
    render(<RepositoryImporter projects={[]} onUpdated={updated} />);
    await search("alice");
    fireEvent.click(screen.getByLabelText("alice/web 선택"));
    fireEvent.change(screen.getByLabelText("GitHub 사용자명"), { target: { value: "unsent-user" } });
    fireEvent.click(screen.getByRole("button", { name: "다음" }));
    await screen.findByLabelText("alice/worker 선택");
    expect(api).toHaveBeenCalledWith("/api/github/repos?username=alice&page=2");
    fireEvent.click(screen.getByLabelText("alice/worker 선택"));
    await search("team");
    fireEvent.click(screen.getByLabelText("team/api 선택"));
    expect(screen.getByText("선택한 저장소 3개")).toBeInTheDocument();
    fireEvent.change(screen.getByLabelText("새 프로젝트 이름"), { target: { value: "Team project" } });
    fireEvent.click(screen.getByRole("button", { name: "선택한 저장소 등록" }));
    await waitFor(() => expect(updated).toHaveBeenCalledTimes(1));
    expect(api).toHaveBeenCalledWith("/api/projects", {
      method: "POST", body: JSON.stringify({ title: "Team project", repositories: [
        { owner: "alice", name: "web" }, { owner: "alice", name: "worker" }, { owner: "team", name: "api" },
      ] }),
    });
    expect(screen.getByText("선택한 저장소 0개")).toBeInTheDocument();
  });

  it("deduplicates URLs and connects them to the selected existing project", async () => {
    const updated = vi.fn();
    api.mockResolvedValue({});
    render(<RepositoryImporter projects={[existing, { ...existing, id: 8, title: "Other project" }]} onUpdated={updated} />);
    addLinks("https://github.com/team/api.git\nTEAM/api\nalice/web");
    expect(screen.getByText("선택한 저장소 1개")).toBeInTheDocument();
    fireEvent.change(screen.getByLabelText("등록 방식"), { target: { value: "existing" } });
    fireEvent.change(screen.getByLabelText("연결할 프로젝트"), { target: { value: "7" } });
    fireEvent.click(screen.getByRole("button", { name: "선택한 저장소 등록" }));
    await waitFor(() => expect(updated).toHaveBeenCalledTimes(1));
    expect(api).toHaveBeenCalledWith("/api/projects/7/repositories", {
      method: "POST", body: JSON.stringify({ repositories: [{ owner: "TEAM", name: "api" }] }),
    });
    expect(api).toHaveBeenCalledTimes(1);
  });

  it("preserves only failed items for retry when importing separate projects", async () => {
    api.mockImplementation(async (path, options) => {
      if (JSON.parse(options.body).name === "broken") throw new Error("저장소를 찾을 수 없습니다.");
      return {};
    });
    render(<RepositoryImporter projects={[]} onUpdated={vi.fn()} />);
    addLinks("alice/web\nteam/broken\nteam/api");
    fireEvent.change(screen.getByLabelText("등록 방식"), { target: { value: "separate" } });
    fireEvent.click(screen.getByRole("button", { name: "선택한 저장소 등록" }));
    await screen.findByText(/2개 저장소를 프로젝트로 등록했습니다/);
    expect(api).toHaveBeenCalledTimes(3);
    expect(screen.getByText("선택한 저장소 1개")).toBeInTheDocument();
    expect(screen.getByRole("alert")).toHaveTextContent("team/broken");
    expect(screen.getByLabelText("team/broken 선택 해제")).toBeInTheDocument();
    expect(screen.queryByLabelText("alice/web 선택 해제")).not.toBeInTheDocument();
  });

  it("retains the group on failure and validates URLs and capacity before submission", async () => {
    const updated = vi.fn();
    api.mockRejectedValue(new Error("이미 다른 프로젝트에 등록되어 있습니다."));
    render(<RepositoryImporter projects={[]} onUpdated={updated} />);
    addLinks("https://evil.example/team/api");
    expect(screen.getByRole("alert")).toHaveTextContent("저장소 주소를 확인");
    expect(screen.getByText("선택한 저장소 0개")).toBeInTheDocument();
    addLinks("team/web\nteam/api");
    fireEvent.change(screen.getByLabelText("새 프로젝트 이름"), { target: { value: "Project" } });
    fireEvent.click(screen.getByRole("button", { name: "선택한 저장소 등록" }));
    await screen.findByText("이미 다른 프로젝트에 등록되어 있습니다.");
    expect(screen.getByText("선택한 저장소 2개")).toBeInTheDocument();
    expect(updated).not.toHaveBeenCalled();
    addLinks(Array.from({ length: 9 }, (_, i) => `team/repo-${i}`).join("\n"));
    expect(screen.getByRole("button", { name: "선택한 저장소 등록" })).toBeDisabled();
    expect(api).toHaveBeenCalledTimes(1);
  });

  it("marks every connected repository as registered", async () => {
    api.mockResolvedValue(listing("alice", [repo("alice", "web", 1), repo("alice", "api", 2)]));
    render(<RepositoryImporter projects={[{ ...existing, repositories: [...existing.repositories, { fullName: "alice/api" }] }]} onUpdated={vi.fn()} />);
    await search("alice");
    expect(screen.getByLabelText("alice/web 선택")).toBeDisabled();
    expect(screen.getByLabelText("alice/api 선택")).toBeDisabled();
  });

  it("uploads multiple files to the correct project and reports individual failures", async () => {
    api.mockImplementation(async (path, options) => {
      if (path === "/api/admin/projects") return [existing];
      if (options.body.get("file").name === "broken.txt") throw new Error("파일 오류");
      return {};
    });
    render(<MemoryRouter><Admin /></MemoryRouter>);
    const input = await screen.findByLabelText("Team service 자료 업로드");
    expect(input).toHaveAttribute("multiple");
    fireEvent.change(input, { target: { files: [new File(["a"], "notes.txt"), new File(["b"], "broken.txt"), new File(["c"], "design.md")] } });
    await screen.findByText(/2개 파일을 비공개로 저장했습니다/);
    expect(screen.getByRole("alert")).toHaveTextContent("broken.txt: 파일 오류");
    const uploads = api.mock.calls.filter(([path]) => path.endsWith("/documents"));
    expect(uploads).toHaveLength(3);
    expect(uploads.every(([path]) => path === "/api/projects/7/documents")).toBe(true);
  });

  it("shows links to every repository on the public detail page", async () => {
    api.mockResolvedValue({ ...existing, repositories: [...existing.repositories,
      { fullName: "team/api", githubUrl: "https://github.com/team/api" }] });
    render(<MemoryRouter initialEntries={["/projects/7"]}><Routes>
      <Route path="/projects/:id" element={<ProjectDetail />} />
    </Routes></MemoryRouter>);
    expect(await screen.findByRole("link", { name: "alice/web" })).toHaveAttribute("href", "https://github.com/alice/web");
    expect(screen.getByRole("link", { name: "team/api" })).toHaveAttribute("href", "https://github.com/team/api");
  });

  it("displays analysis job status in Korean", async () => {
    api.mockImplementation(async path => {
      if (path === "/api/admin/projects") return [existing];
      if (path === "/api/projects/7/analyze") return { id: 1, status: "COMPLETED", message: "분석을 저장했습니다." };
      throw new Error(`Unexpected request: ${path}`);
    });
    render(<MemoryRouter><Admin /></MemoryRouter>);
    fireEvent.click(await screen.findByRole("button", { name: "분석 실행" }));
    expect(await screen.findByRole("status")).toHaveTextContent("분석 완료");
    expect(screen.queryByText("COMPLETED")).not.toBeInTheDocument();
  });
});
