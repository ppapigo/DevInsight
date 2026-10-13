import type { Overview, Project, Claim } from "./types";
const stamp = "2026-10-10T09:00:00Z";
const definitions = [
  {
    id: 1,
    title: "DevInsight AI",
    description:
      "코드와 개발 이력을 근거로, 개발자의 경험을 읽는 AI 포트폴리오 플랫폼.",
    language: "Java",
    technologies: ["Spring Boot", "Spring AI", "React", "MySQL"],
    purpose:
      "면접관이 저장소를 일일이 탐색하지 않고도 개발자의 구현 경험과 코드 근거를 확인하는 서비스입니다.",
    claims: [
      [
        "FEATURE",
        "근거를 연결하는 프로젝트 분석",
        "설정 파일과 핵심 코드를 선별하고, 구조화된 분석 결과를 실제 코드 출처와 연결합니다.",
        "analysis/AnalysisWorker.java",
      ],
      [
        "ARCHITECTURE",
        "비동기 분석 파이프라인",
        "HTTP 요청에서 분석 작업을 분리하고 DB에 작업 상태와 분석 버전을 저장하는 구조입니다.",
        "analysis/AnalysisTransactions.java",
      ],
      [
        "FEATURE",
        "공개 승인 기반 자료 관리",
        "관리자가 공개한 프로젝트와 승인한 자료만 포트폴리오 및 질문 응답에 사용합니다.",
        "document/DocumentService.java",
      ],
    ],
  },
  {
    id: 2,
    title: "Flowboard",
    description: "팀의 작업 흐름과 실시간 협업을 위한 칸반 워크스페이스.",
    language: "TypeScript",
    technologies: ["React", "TypeScript", "WebSocket", "PostgreSQL"],
    purpose:
      "팀원이 같은 작업 보드를 보며 상태 변경을 실시간으로 공유하는 협업 도구를 가정한 샘플입니다.",
    claims: [
      [
        "FEATURE",
        "실시간 보드 동기화",
        "WebSocket 이벤트를 통해 카드 이동과 작업 상태 변경을 참여자에게 전달하는 예시입니다.",
        "src/board/BoardService.ts",
      ],
      [
        "ARCHITECTURE",
        "낙관적 UI와 상태 복구",
        "서버 응답 전 화면에 변경을 반영하고, 실패 시 이전 상태로 되돌리는 예시입니다.",
        "src/hooks/useBoard.ts",
      ],
    ],
  },
  {
    id: 3,
    title: "Archive API",
    description: "읽은 자료를 검색 가능한 개인 지식으로 모으는 백엔드 API.",
    language: "Python",
    technologies: ["Python", "FastAPI", "Redis", "Docker"],
    purpose: "자료 수집과 텍스트 검색을 제공하는 백엔드 프로젝트의 샘플입니다.",
    claims: [
      [
        "FEATURE",
        "자료 검색 API",
        "제목과 본문을 기준으로 저장된 문서를 검색하는 구현 예시입니다.",
        "app/services/search.py",
      ],
      [
        "ARCHITECTURE",
        "컨테이너 개발 환경",
        "API와 데이터 저장소를 Docker Compose로 함께 실행하는 예시입니다.",
        "docker-compose.yml",
      ],
    ],
  },
];
const projects: Project[] = definitions.map((d) => {
  const evidence = d.claims.map((c, i) => ({
    id: `file-${i + 1}`,
    kind: "CODE",
    path: c[3],
    url: "#demo-evidence",
    text: `// 데모용 코드 근거 예시\n// ${c[1]}\n// 실제 저장소에서 수집한 코드가 아닙니다.`,
    documentId: null,
  }));
  const claims: Claim[] = d.claims.map((c, i) => ({
    category: c[0],
    title: c[1],
    detail: c[2],
    status: "CONFIRMED",
    evidenceIds: [evidence[i].id],
  }));
  claims.push({
    category: "CONTRIBUTION",
    title: "개인 기여 범위",
    detail: "실제 커밋과 PR 검토 후 작성자 및 기여 범위를 확인해야 합니다.",
    status: "NEEDS_VERIFICATION",
    evidenceIds: [],
  });
  return {
    id: d.id,
    title: d.title,
    description: d.description,
    published: true,
    featured: d.id === 1,
    demoUrl: null,
    repository: `demo-developer/${d.title.toLowerCase().replaceAll(" ", "-")}`,
    githubUrl: "https://github.com",
    language: d.language,
    stars: 0,
    fork: false,
    repositoryCreatedAt: "2026-06-01T00:00:00Z",
    pushedAt: stamp,
    languages: {
      [d.language]: d.id === 1 ? 56000 : d.id === 2 ? 39000 : 21000,
      ...(d.id === 1 ? { TypeScript: 24000 } : {}),
    },
    documents: [],
    analysis: {
      id: d.id,
      commitSha: "demo000000000000000000000000000000000000",
      model: "샘플 데이터 · GPT 미사용",
      createdAt: stamp,
      inputBytes: 0,
      report: {
        summary: d.description,
        purpose: d.purpose,
        technologies: d.technologies,
        claims,
        interviewQuestions: [
          "이 구조를 선택한 이유와 대안을 설명해 주세요.",
          "실패 상황에서 데이터 일관성을 어떻게 유지하나요?",
          "직접 구현한 범위와 검증 방법은 무엇인가요?",
        ],
        limitations: [
          "이 페이지의 인물, 프로젝트 설명, 활동 및 분석 결과는 UI 확인을 위한 샘플 데이터입니다.",
          "실제 역량 평가나 AI 분석 결과가 아닙니다.",
        ],
      },
      evidence,
      activity: {
        commits: Array.from({ length: 7 }, (_, i) => ({
          sha: `demo${d.id}${i}`,
          message: [
            "문서와 코드 근거 연결",
            "예외 처리 개선",
            "핵심 서비스 테스트",
            "응답 DTO 분리",
            "초기 API 구성",
            "프로젝트 설정",
            "개발 환경 구성",
          ][i],
          date: `2026-10-${String(10 - i).padStart(2, "0")}T09:00:00Z`,
          author: "demo-developer",
          url: "#demo-commit",
        })),
        sampledCommits: 7,
        sampledPullRequests: 0,
        treeTruncated: false,
        warnings: ["실제 GitHub 활동이 아닌 샘플 이력입니다."],
      },
    },
  };
});
export const demoOverview: Overview = {
  profile: {
    login: "demo-developer",
    name: "김도윤",
    bio: "사용자의 문제를 이해하고, 안정적인 서비스로 해결하는 백엔드 개발자입니다.",
    avatarUrl: "",
    githubUrl: "https://github.com",
    publicRepos: 3,
  },
  projects,
  languages: { Java: 56000, TypeScript: 63000, Python: 21000 },
  evidenceCount: 7,
  analyzedProjects: 3,
};
