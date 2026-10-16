# REST API

같은 출처의 `/api`로 요청합니다. 관리자 인증은 GitHub OAuth 세션입니다. 비공개 프로젝트/문서는 공개 사용자에게 404로 응답합니다. API는 JPA 엔티티를 직접 반환하지 않습니다.

| Method | Path | 권한 | 역할 |
| --- | --- | --- | --- |
| GET | `/actuator/health` | 공개 | DB를 포함한 상태 확인 |
| GET | `/api/auth` | 공개 | 관리자 여부, OAuth 활성 여부, CSRF 토큰 |
| POST | `/api/logout` | 세션 + CSRF | 로그아웃 |
| GET | `/api/github/repos?username=octocat&page=1` | 관리자 | GitHub 프로필과 공개 저장소 목록 |
| POST | `/api/projects/import` | 관리자 + CSRF | 저장소 등록, 기본 비공개 |
| POST | `/api/projects` | 관리자 + CSRF | 여러 저장소를 연결한 새 프로젝트 생성, 기본 비공개 |
| POST | `/api/projects/{id}/repositories` | 관리자 + CSRF | 기존 프로젝트에 공개 저장소 추가 |
| GET | `/api/projects` | 공개 | 공개 프로젝트 목록 |
| GET | `/api/admin/projects` | 관리자 | 비공개 포함 전체 목록 |
| GET | `/api/projects/{id}` | 공개/관리자 | 권한에 따른 프로젝트 상세 |
| PATCH | `/api/projects/{id}` | 관리자 + CSRF | 소개, 공개 여부, 대표 여부, 시연 링크 |
| POST | `/api/projects/{id}/analyze?force=false` | 관리자 + CSRF | 비동기 분석 접수, 202와 작업 ID |
| GET | `/api/analysis/jobs/{id}` | 관리자 | PENDING/RUNNING/COMPLETED/FAILED |
| POST | `/api/projects/{id}/documents` | 관리자 + CSRF | multipart `file`, 기본 비공개 |
| PATCH | `/api/documents/{id}/approval` | 관리자 + CSRF | 자료 공개 승인/철회 |
| GET | `/api/documents/{id}/content` | 승인된 공개 자료/관리자 | 원본 다운로드, 이미지는 미리보기 |
| GET | `/api/portfolio/overview` | 공개 | 프로필, 공개 프로젝트, 언어/근거 집계 |
| POST | `/api/interview/ask` | 공개 + CSRF + 사용량 제한 | 근거 기반 질문 응답 |

`GET /api/auth`의 `csrfToken` 값을 `X-XSRF-TOKEN` 헤더로 보내고, 응답에서 설정한 쿠키도 함께 유지합니다. OAuth 앱이 없으면 로그인 URL로 유도하지 않습니다.

## 요청 예시

```json
// POST /api/projects/import
{"owner":"octocat","name":"Hello-World"}

// POST /api/projects
{"title":"팀 협업 서비스","repositories":[{"owner":"my-account","name":"frontend"},{"owner":"my-team","name":"backend"}]}

// POST /api/projects/1/repositories
{"repositories":[{"owner":"my-team","name":"worker"}]}

// PATCH /api/projects/1
{"title":"My Project","description":"프로젝트 소개","published":true,"featured":true,"demoUrl":"https://example.com/demo"}

// PATCH /api/documents/1/approval
{"publicApproved":true}

// POST /api/interview/ask
{"question":"Spring Security로 구현한 기능은 무엇인가요?","projectId":1}
```

`projectId`는 선택 사항입니다. 특정 프로젝트가 없거나 비공개이면 자료가 없다는 답변을 반환합니다. 질문 최대 1,000자, 제목 최대 255자, 소개 최대 3,000자, 시연 링크 최대 1,000자이며 시연 링크는 HTTPS만 허용합니다.

프로젝트마다 저장소를 최대 10개 연결할 수 있으며 서로 다른 계정·조직의 공개 저장소도 지원합니다. 같은 저장소는 한 프로젝트에만 속합니다. 이미 다른 프로젝트에 연결된 저장소를 묶어 등록하면 `409 REPOSITORY_IN_USE`로 응답하고 해당 요청 전체를 취소합니다. 기존 프로젝트에 이미 연결된 저장소를 다시 추가해도 중복으로 저장하거나 분석을 무효화하지 않습니다. 기존 단일 가져오기 API는 연결된 프로젝트를 그대로 반환합니다.

프론트엔드의 ‘저장소마다 별도 프로젝트로 등록’은 단일 가져오기 API를 순서대로 호출합니다(한 번에 최대 30개). 개별 실패는 다른 등록을 취소하지 않으며 실패 항목은 선택 목록에 남습니다. 여러 자료 파일도 기존 multipart API를 파일마다 호출하므로 파일당 10MB 제한과 개별 성공·실패가 적용됩니다.

## 주요 응답 구조

분석 결과의 `summary`, `purpose`, `claims[].title/detail`, `interviewQuestions[]`, `limitations[]`는 한국어로 생성하도록 요청합니다. 시스템 지시와 각 필드의 스키마 설명에 출력 언어를 명시하며, 기술명·코드 식별자·근거 ID·JSON 키와 열거형 값은 원문을 유지합니다. 이는 [OpenAI 공식 프롬프트 지침](https://developers.openai.com/api/docs/guides/prompt-engineering)의 명시적인 출력 지시 방식에 따릅니다.

저장 전 각 설명 항목에 한글이 포함되는지 최소 검증합니다. 한글이 없는 항목이 있으면 작업은 `FAILED`, `errorCode=AI_RESPONSE_LANGUAGE`가 되며 기존 분석을 보존합니다. 이전 분석이 이 검사를 통과하지 못하면 동일 커밋으로 `force=false` 분석을 실행해도 결과를 재사용하지 않습니다. 저장된 영어 결과를 바꾸려면 관리자가 분석을 다시 요청해야 하며, 조회 시 자동 번역이나 추가 AI 호출은 하지 않습니다.

프로젝트는 `analysis.report`의 요약·목적·기술·주장·예상 질문·한계와 `analysis.evidence`의 근거 목록을 제공합니다. 각 주장의 `evidenceIds`는 실제 수집된 근거 ID만 포함합니다. 각 근거에는 `path`, `url`, `kind`, 제한된 `text`가 있습니다. GitHub 코드 URL은 분석 대상 커밋 SHA로 고정합니다.

`repositories` 배열에는 연결된 모든 저장소의 `id/fullName/githubUrl/language/stars/fork`가 포함됩니다. 기존 `repository/githubUrl/language` 필드는 첫 저장소를 가리키도록 유지합니다. `languages/stars`는 연결된 저장소의 합계, `pushedAt`은 가장 최근 푸시입니다. 여러 저장소의 분석은 저장소별 근거 ID를 구분하고 공통 입력 예산 안에서 함께 선별합니다. 다중 저장소의 `analysis.commitSha`는 모든 저장소 ID와 HEAD SHA를 결합한 SHA-256 분석 식별자이며 특정 저장소의 커밋 SHA가 아닙니다. 단일 저장소에서는 기존 의미를 유지합니다.

저장소를 새로 연결하면 최신 분석 연결을 해제하고 재분석이 필요합니다. 분석 도중 저장소나 자료 공개 범위가 변경되면 오래된 결과를 공개하지 않고 해당 작업을 실패 처리합니다. 기존 분석 이력은 보존합니다. Flyway V2는 기존 프로젝트의 저장소 연결을 새 연결 테이블에 자동으로 이전합니다.

분석 상태 `CONFIRMED`는 **제공된 근거와 연결된 AI 해석**을 뜻하며 전문가의 사실 검증이나 개인 기여 인증이 아닙니다. 화면에는 “근거 연결됨”으로 표시합니다. 나머지는 `NEEDS_VERIFICATION`(확인 필요), `INSUFFICIENT_EVIDENCE`(근거 부족)입니다.

Q&A 응답은 `answer`, `status`, `sources`, `demo`로 구성됩니다. 출처 URL은 모델의 자유 생성 값을 사용하지 않고 서버가 검색한 원본 근거에서 선택합니다.

## 오류

```json
{"code":"AI_NOT_CONFIGURED","message":"실제 AI 분석에는 OPENAI_API_KEY 설정이 필요합니다.","requestId":"...","timestamp":"..."}
```

| 상태 | 예시 코드 |
| --- | --- |
| 400 | `INVALID_REQUEST` |
| 401 | `AUTH_REQUIRED` |
| 403 | `ACCESS_DENIED` (인가 또는 CSRF) |
| 404 | `NOT_FOUND`, `GITHUB_NOT_FOUND` |
| 409 | `REPOSITORY_IN_USE`, `DATA_CONFLICT` |
| 413 | `FILE_TOO_LARGE`, `REQUEST_TOO_LARGE` |
| 422 | `EMPTY_REPOSITORY`, `REPOSITORY_TOO_LARGE`, `INPUT_BUDGET_EXCEEDED` |
| 429 | `GITHUB_RATE_LIMIT`, `QUESTION_LIMIT`, `DAILY_AI_LIMIT` |
| 502 | `GITHUB_ERROR`, `GITHUB_UNAVAILABLE`, `AI_RESPONSE_FAILED` |
| 503 | `AI_NOT_CONFIGURED` |

인증 필터 오류는 `code/message`만 반환할 수 있습니다. 429에는 `Retry-After`가 포함됩니다. 비동기 작업의 실패 이유는 작업 조회의 `errorCode/message`에 표시되며 외부 응답 본문이나 예외 스택을 전달하지 않습니다.
