export type Evidence = {
  id: string;
  kind: string;
  path: string;
  url: string;
  text: string;
  documentId: number | null;
};
export type Status =
  | "CONFIRMED"
  | "NEEDS_VERIFICATION"
  | "INSUFFICIENT_EVIDENCE";
export type Claim = {
  category: string;
  title: string;
  detail: string;
  status: Status;
  evidenceIds: string[];
};
export type Report = {
  summary: string;
  purpose: string;
  technologies: string[];
  claims: Claim[];
  interviewQuestions: string[];
  limitations: string[];
};
export type Commit = {
  sha: string;
  message: string;
  date: string;
  author: string;
  url: string;
};
export type Analysis = {
  id: number;
  commitSha: string;
  model: string;
  createdAt: string;
  inputBytes: number;
  report: Report;
  evidence: Evidence[];
  activity: {
    commits: Commit[];
    sampledCommits: number;
    sampledPullRequests: number;
    treeTruncated: boolean;
    warnings: string[];
  };
};
export type Document = {
  id: number;
  name: string;
  contentType: string;
  sizeBytes: number;
  publicApproved: boolean;
  createdAt: string;
  url: string;
};
export type Project = {
  id: number;
  title: string;
  description: string;
  published: boolean;
  featured: boolean;
  demoUrl: string | null;
  repository: string;
  githubUrl: string;
  language: string;
  stars: number;
  fork: boolean;
  repositoryCreatedAt: string;
  pushedAt: string;
  languages: Record<string, number>;
  analysis: Analysis | null;
  documents: Document[];
};
export type Overview = {
  profile: {
    login: string;
    name: string;
    bio: string;
    avatarUrl: string;
    githubUrl: string;
    publicRepos: number;
  } | null;
  projects: Project[];
  languages: Record<string, number>;
  evidenceCount: number;
  analyzedProjects: number;
};
export type Session = {
  admin: boolean;
  oauthEnabled: boolean;
  aiConfigured: boolean;
  login: string | null;
  csrfToken: string;
};
export type Answer = {
  answer: string;
  status: Status;
  sources: {
    id: string;
    projectId: number;
    projectTitle: string;
    path: string;
    url: string;
  }[];
  demo: boolean;
};
export type Job = {
  id: number;
  projectId: number;
  status: "PENDING" | "RUNNING" | "COMPLETED" | "FAILED";
  message: string;
  errorCode: string | null;
  commitSha: string | null;
};
export type Repo = {
  githubId: number;
  owner: string;
  name: string;
  fullName: string;
  description: string;
  language: string;
  stars: number;
  fork: boolean;
  url: string;
  defaultBranch: string;
  pushedAt: string;
};
export type Listing = {
  profile: {
    login: string;
    name: string;
    bio: string;
    avatarUrl: string;
    url: string;
    publicRepos: number;
  };
  repositories: Repo[];
  page: number;
  hasMore: boolean;
};
