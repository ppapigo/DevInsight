# DevInsight AI

개발자의 공개 GitHub 코드, 커밋, PR, 승인된 프로젝트 문서를 근거로 분석하는 풀스택 포트폴리오 플랫폼입니다. 면접관은 대시보드와 프로젝트 상세 페이지에서 구현 근거를 열어 보고, 공개 자료에 관해 질문할 수 있습니다. 임의의 역량 점수를 만들지 않습니다.

## 기술 및 버전

| 영역 | 구성 |
| --- | --- |
| Backend | Java 21, Spring Boot **4.1.1**, Spring AI **2.0.1**, Spring MVC, JPA, Security OAuth2 Client, Validation, Actuator |
| Build | Gradle **8.14.3** Wrapper, Groovy DSL, 배포 ZIP SHA-256 검증 |
| Database | MySQL **8.4**, Flyway, 운영 `ddl-auto=validate` |
| Documents | PDFBox **3.0.8**, Apache POI **5.5.1** |
| Frontend | React 19, Vite 7, TypeScript 5.9, Tailwind CSS 4, React Router 7, Lucide, Recharts |
| Tests | JUnit 5, Mockito, MockMvc, H2 MySQL 모드, Vitest, Testing Library |

프론트엔드의 정확한 버전은 `frontend/package-lock.json`에 고정됩니다. Node.js **24**를 사용합니다. Spring AI 2.0.1은 정식 Maven Central 배포본이며 Snapshot/Milestone 저장소를 사용하지 않습니다. [Spring AI 2.0 정식 릴리스](https://spring.io/blog/2026/06/12/spring-ai-2-0-0-GA-available-now/)는 Spring Boot 4.0/4.1 호환성을 명시합니다. [Spring Boot 요구사항](https://docs.spring.io/spring-boot/system-requirements.html)도 참고하세요.

## 가장 빠른 데모 실행

DB, OAuth, OpenAI 키 없이 프론트엔드 전체를 확인할 수 있습니다.

```powershell
cd C:\Users\HP\portfolio\frontend
npm ci
npm run demo
```

브라우저에서 `http://localhost:5173`을 엽니다. 화면 상단에 **데모**가 표시되며, 인물·프로젝트·활동·분석·채팅 답변은 명시적인 샘플입니다. 데모는 GPT를 호출하거나 백엔드 데이터를 변경하지 않습니다. 상단 모드 전환은 브라우저에 저장됩니다. 이전에 실제 데이터 모드를 선택했다면 다시 데모로 전환하세요.

개발 중 HMR이 필요하면 `npm run dev:demo`를 사용합니다. `npm run demo`는 데모 빌드와 미리보기 서버를 실행합니다. 두 명령 모두 포트 5173을 사용하므로 동시에 실행하지 않습니다.

## 실제 서비스 실행 — 로컬 개발

### 1. 환경변수와 MySQL

```powershell
cd C:\Users\HP\portfolio
Copy-Item .env.example .env
# .env의 DB_PASSWORD, MYSQL_ROOT_PASSWORD를 직접 설정합니다.
docker compose up -d mysql
docker compose ps
```

Docker Desktop의 Linux 컨테이너 엔진이 실행 중이어야 합니다. DB는 `localhost:3306`에 바인딩됩니다. 기존 3306 포트를 사용 중이면 Compose 포트와 `DB_URL`을 함께 바꾸세요. MySQL 볼륨은 재시작 후에도 유지됩니다.

### 2. 백엔드

같은 PowerShell 창에서 실행합니다. Spring Boot와 Gradle은 루트 `.env`를 자동으로 읽지 않으므로 제공된 로더를 사용합니다.

```powershell
cd C:\Users\HP\portfolio
. .\scripts\load-env.ps1
cd backend
.\gradlew.bat bootRun
```

백엔드: `http://localhost:8080`, 상태 확인: `http://localhost:8080/actuator/health`.

GitHub OAuth와 OpenAI 키가 비어 있어도 DB가 준비되어 있으면 공개 조회 API가 시작됩니다. 프로젝트를 아직 공개하지 않았다면 빈 상태가 표시됩니다. 실제 분석/Q&A 요청에 키가 없으면 `503 AI_NOT_CONFIGURED`를 반환합니다.

### 3. 프론트엔드

새 터미널에서 실행합니다.

```powershell
cd C:\Users\HP\portfolio\frontend
npm ci
npm run dev
```

`http://localhost:5173`을 사용합니다. Vite가 `/api`, `/oauth2`, `/login`, `/actuator`를 8080으로 프록시하므로 브라우저는 동일 출처로 요청합니다. 데모 모드가 남아 있으면 상단에서 실제 데이터로 전환하세요. 로그인 시 `localhost`와 `127.0.0.1`을 혼용하지 않습니다.

### 전체 Docker 실행

```powershell
cd C:\Users\HP\portfolio
docker compose --profile app up -d --build
```

Frontend 5173 / Backend 8080 / MySQL 3306. Nginx는 SPA 라우팅과 동일 출처 API/OAuth 프록시를 제공합니다. 업로드 파일과 MySQL 데이터는 각각 전용 볼륨에 보관됩니다. 중지는 `docker compose --profile app stop`입니다. 데이터를 보존하려면 볼륨 삭제 옵션을 사용하지 마세요.

## 환경변수

| 변수 | 의미 |
| --- | --- |
| `DB_URL` | 로컬 JDBC URL. Compose 전체 실행에서는 내부 MySQL 서비스 주소 사용 |
| `DB_USERNAME`, `DB_PASSWORD` | 애플리케이션 DB 계정 |
| `MYSQL_ROOT_PASSWORD` | Docker MySQL 초기화용 root 비밀번호 |
| `OPENAI_API_KEY` | 실제 분석 및 Q&A에 필요. 서버에서만 사용 |
| `OPENAI_MODEL` | 기본 `gpt-4o-mini`. Structured Outputs를 지원하는 접근 가능한 모델 사용 |
| `GITHUB_CLIENT_ID`, `GITHUB_CLIENT_SECRET` | 관리자용 GitHub OAuth 앱 |
| `ADMIN_GITHUB_LOGIN` | 로그인 허용 GitHub 사용자명. 대소문자를 구분하지 않고 정확히 비교 |
| `GITHUB_TOKEN` | 선택 사항. 공개 저장소 REST API 호출 한도 향상용. OAuth 토큰과 별도 |
| `FRONTEND_URL` | 기본 `http://localhost:5173`. 로그인 후 이동 및 OAuth 콜백의 고정 기준 URL |
| `COOKIE_SECURE` | 로컬 HTTP는 false, HTTPS 운영 환경은 true |
| `UPLOAD_DIR` | 서버 파일 저장 디렉터리. 정적 웹 루트 밖 사용 |
| `INTERVIEW_PER_HOUR` | 요청 IP별 시간당 질문 수. 기본 12 |
| `AI_CALLS_PER_DAY` | DB에서 원자적으로 제한하는 서비스 전체 일일 AI 호출 수. 기본 150, UTC 기준 |

`.env`와 업로드·빌드·캐시 디렉터리는 `.gitignore`에서 제외합니다. 키를 React의 `VITE_*` 변수에 넣지 않습니다. 환경변수 로더는 값을 출력하지 않습니다.

## GitHub 관리자 OAuth

GitHub Settings → Developer settings → OAuth Apps에서 앱을 만듭니다.

- Homepage URL: `http://localhost:5173`
- Authorization callback URL: `http://localhost:5173/login/oauth2/code/github`
- 위 Client ID/Secret과 본인의 GitHub 로그인명을 `.env`에 저장하고 백엔드를 다시 실행합니다.
- 관리자 워크스페이스에서 GitHub로 로그인합니다. `read:user`만 요청하며 저장소 쓰기 권한을 요구하지 않습니다.
- 허용 목록과 다른 계정은 로그인 실패로 처리합니다. OAuth 설정이 불완전하면 관리자 로그인은 비활성화됩니다.

로그인 세션과 CSRF를 사용합니다. 프론트엔드는 `GET /api/auth`에서 CSRF 토큰을 받고 변경 요청에 `X-XSRF-TOKEN` 헤더를 전송합니다. AI 질문을 포함한 모든 POST/PATCH 요청에 CSRF 검증이 적용됩니다. 운영 환경에서는 HTTPS와 올바른 `FRONTEND_URL`을 설정하세요.

## 사용 흐름

1. 관리자 워크스페이스에서 GitHub 사용자명을 조회하고 공개 저장소를 가져옵니다. 목록은 30개 단위로 탐색합니다.
2. 프로젝트 이름·소개·시연 HTTPS 링크·대표 여부를 설정합니다. 가져온 프로젝트는 **기본 비공개**입니다.
3. 선택적으로 문서나 PNG/JPEG 이미지를 업로드합니다. 업로드 자료도 **기본 비공개**이며, 공개 승인한 문서만 AI 입력에 포함됩니다. 승인 시 원본 다운로드도 허용되므로 자료 내용을 확인하세요.
4. 분석을 실행합니다. DB의 PENDING → RUNNING → COMPLETED/FAILED 상태를 화면에서 확인합니다.
5. 분석 결과와 근거를 검토하고 프로젝트 공개를 선택합니다. 면접관용 대시보드·상세·Q&A에 반영됩니다.

문서 공개 범위가 변경되면 이전 분석을 삭제하지 않고 최신 공개 분석 연결만 해제합니다. 재분석해야 최신 자료에 맞는 결과가 보입니다. 분석 중 자료 승인이 변경되어도 이전 범위로 생성한 결과를 공개하지 않습니다.

## 수집·분석 범위

- GitHub 사용자/저장소/언어, 커밋 SHA, 디렉터리 트리, 빌드 설정, 핵심 코드, README, 최근 커밋 최대 30건, 닫힌 PR 최대 5건을 수집합니다.
- 빌드 설정 → 인증/서비스/엔티티 → 진입점/컨트롤러 → README 등의 순서로 우선순위를 적용합니다. 최대 24개 파일, 파일당 48KB입니다. 파일 내용은 입력 예산에 맞춰 일부만 분석할 수 있습니다.
- 빌드 산출물, node_modules, vendor, 바이너리, 잠금 파일, 환경 파일, 자격증명 경로 등을 제외합니다. 본문의 흔한 키·비밀번호 패턴도 마스킹합니다. 이 마스킹은 모든 비밀을 탐지하는 DLP를 대신하지 않습니다.
- 승인된 문서는 입력 예산의 일부만 사용하도록 제한합니다. 최종 입력은 시스템 메시지·스키마·메타데이터를 포함한 UTF-8 바이트 상한으로 보수적으로 제한합니다. 기본 입력 상한 18,000 / 출력 토큰 상한 3,500입니다. DB의 `input_tokens`에는 실제 청구 토큰이 아닌 수집 JSON의 바이트 기반 상한 지표가 저장되며 API에서는 `inputBytes`로 노출합니다.
- 커밋 SHA·문서 리비전·모델이 같으면 이전 결과를 재사용합니다. 강제 재분석은 이를 건너뜁니다.
- GitHub 일시적 장애는 최대 3회 시도합니다. rate limit은 Retry-After와 명시적인 오류로 반환합니다. OpenAI는 자동 재시도하지 않으며 실패 후 관리자가 다시 요청할 수 있습니다.
- Spring AI의 OpenAI Chat Model로 [Structured Outputs](https://developers.openai.com/api/docs/guides/structured-outputs?api-mode=chat)를 요청하고 DTO·길이·출처 ID를 다시 검증합니다. 임의의 출처 ID를 제거하며 근거 없는 확정 주장은 `근거 부족`으로 내려 표시합니다.
- 외부 내용은 고정 시스템 지침과 분리된 데이터입니다. AI에 서버 도구나 파일 접근 권한을 부여하지 않습니다. 모델의 의미적 사실성은 완벽하게 보장할 수 없으므로 실제 취업 자료는 출처와 대조해 검토해야 합니다.

## 문서 및 Q&A

문서: PDF, DOCX, PPTX, UTF-8 TXT/MD. 이미지: PNG/JPEG 미리보기. 최대 10MB, 프로젝트당 30개, 추출 텍스트 최대 40,000자. PDF는 최대 200페이지이며 암호화 파일을 지원하지 않습니다. Office ZIP의 확장 크기·항목 수·매크로/임베딩을 검사합니다. 이미지 픽셀 크기도 제한합니다. OCR, 동영상 업로드, 이미지 AI 분석은 포함하지 않습니다. 시연은 외부 HTTPS 링크로 등록합니다.

Q&A는 현재 **공개된 최신 분석**과 그 근거 텍스트를 DB에서 검색합니다. 프로젝트별 질문도 지원합니다. 출처가 없거나 공개 승인이 취소되면 근거 부족으로 답하며 추측을 보완하지 않습니다. `EvidenceSearch` 인터페이스를 교체해 향후 벡터 검색을 추가할 수 있습니다. 키워드 검색은 어휘 차이에 따른 누락 가능성이 있습니다.

질문은 최대 1,000자이며 JSON 요청 본문은 Content-Length 유무와 관계없이 8KB로 제한합니다. IP 한도는 단일 서버 메모리에, 전역 일일 AI 호출 한도는 DB에 저장합니다. 성공한 질문/답변은 최대 30일 보관 후 정리합니다. 사용자 IP 원문은 DB에 저장하지 않습니다. 프록시 뒤에서는 IP가 합쳐질 수 있으므로 운영 시 신뢰할 수 있는 프록시 구성과 전용 rate limiter를 도입해야 합니다. 임의의 `X-Forwarded-For`는 신뢰하지 않습니다.

## 프로젝트 구조

```text
portfolio/
├─ backend/
│  ├─ build.gradle, settings.gradle, gradlew, gradlew.bat
│  └─ src/
│     ├─ main/java/com/portfolio/devinsight/
│     │  ├─ config, security, common
│     │  ├─ github       # 고정 GitHub API, 수집과 우선순위
│     │  ├─ project      # 등록/공개 설정/DTO
│     │  ├─ analysis     # 구조화된 분석, 예산, 작업 큐, 버전 보존
│     │  ├─ document     # 안전한 추출, 파일 저장, 공개 승인
│     │  ├─ interview    # 검색 인터페이스, 근거 기반 응답
│     │  └─ portfolio    # 대시보드 집계
│     ├─ main/resources/db/migration/V1__initial_schema.sql
│     └─ test/           # 외부 API 모의, API/DB/보안/문서 테스트
├─ frontend/src/
│  ├─ pages/Portfolio.tsx, Interview.tsx, Admin.tsx
│  ├─ api.ts, context.tsx, types.ts, demo.ts
│  ├─ components.tsx, ActivityChart.tsx, styles.css
│  └─ portfolio.test.tsx
├─ scripts/load-env.ps1, verify-compose.ps1, verify-http.cjs
├─ docker-compose.yml, docker-compose.verify.yml, .env.example
├─ .github/workflows/ci.yml
└─ docs/API.md, docs/VALIDATION.md
```

엔티티를 Controller 응답에 직접 반환하지 않습니다. DB 외래키·인덱스·고유 제약과 서비스 트랜잭션을 사용하며, 외부 통신 중에는 분석 결과 저장 트랜잭션을 잡지 않습니다. 예외 응답과 로그에는 외부 응답 본문·키·문서·프롬프트를 남기지 않습니다.

분석 작업 스케줄러는 **단일 애플리케이션 인스턴스**를 전제로 합니다. 활성 프로젝트의 DB UNIQUE 키와 잠금으로 중복 요청을 막습니다. 서버 재시작 시 RUNNING 작업을 FAILED로 바꾸고 재시도를 허용합니다. 다중 인스턴스에서는 DB 작업 claim 잠금/lease와 분산 rate limiter로 확장한 후 운영하세요.

## 테스트와 빌드

```powershell
cd C:\Users\HP\portfolio\backend
.\gradlew.bat build

cd ..\frontend
npm ci
npm run build
npm test
```

백엔드 테스트는 기본적으로 외부 인증정보 없이 H2 MySQL 모드에 Flyway를 실행합니다. GitHub와 AI는 Mock 처리합니다. **H2 통과가 MySQL 검증을 대체하지는 않습니다.** 실제 MySQL 전용 테스트 DB를 만들어 다음 변수를 설정한 뒤 `gradlew.bat test --rerun-tasks`로 검증할 수 있습니다. 업무 데이터가 있는 DB를 테스트 대상으로 사용하지 마세요.

```powershell
$env:TEST_DB_URL='jdbc:mysql://localhost:3306/devinsight_test?serverTimezone=UTC'
$env:TEST_DB_USERNAME='테스트_DB_계정'
$env:TEST_DB_PASSWORD='직접_설정한_테스트_DB_비밀번호'
$env:TEST_DB_DRIVER='com.mysql.cj.jdbc.Driver'
.\gradlew.bat test --rerun-tasks
```

CI에는 H2와 MySQL 8.4 서비스 검증을 모두 구성했습니다. 아직 원격 CI 실행을 완료한 것은 아닙니다. 실제 이번 작업의 검증 범위와 환경 제약은 `docs/VALIDATION.md`에 기록합니다.

### 인증정보 없는 Docker 통합 검증

Java 21, Node.js 24, Docker Desktop과 `!override`를 지원하는 Docker Compose V2가 필요합니다. 이번 검증에는 Compose 2.40.3을 사용했습니다.

```powershell
cd C:\Users\HP\portfolio
powershell -NoProfile -File scripts\verify-compose.ps1
```

이 스크립트는 외부 API 키 없이 다음 절차를 수행합니다.

1. 임의의 별도 Compose 프로젝트 이름과 테스트 전용 DB 비밀번호를 생성합니다. `.env`를 만들거나 수정하지 않으며 실제 OpenAI/GitHub 인증정보는 사용하지 않습니다.
2. MySQL 8.4, 백엔드, 프론트엔드를 빌드·기동합니다. 기존 기본 포트 대신 33071 / 18081 / 15173을 사용하며 포트 충돌 시 중단합니다.
3. Nginx를 통한 상태 조회·SPA 경로·정적 자산·보안 헤더·인증·CSRF·질문 크기 제한·키 누락 오류를 검사합니다.
4. 별도의 `devinsight_test` DB에 핵심 통합 테스트를 실행합니다. 나머지 단위/H2 테스트도 함께 실행합니다. 외부 GitHub/AI는 Mock 처리합니다.
5. 성공·실패와 관계없이 이번 실행이 만든 테스트 컨테이너와 볼륨만 정리합니다. 빌드 이미지/캐시는 남겨 다음 실행에서 재사용합니다.

HTTP 결과는 `work/compose-http-smoke-result.json`, 테스트 보고서는 `backend/build/reports/tests/test/index.html`에 저장합니다. 일반 MySQL 테스트를 직접 실행할 때도 반드시 비어 있는 전용 테스트 DB를 사용하세요.

운영 빌드 산출물: `backend/build/libs/devinsight-0.1.0.jar`, `frontend/dist/`. 프론트엔드 활동 차트는 지연 로딩합니다. `npm run build`는 실제 데이터 모드의 정적 배포본이며 `npm run build:demo`는 샘플 모드 배포본입니다.

## 다음 우선순위

1. 실제 MySQL·GitHub OAuth·OpenAI 키를 연결하고 본인의 공개 저장소 1개로 전체 흐름 검증.
2. 본인 기여와 기술 선택에 관한 문서를 추가하고, 공개 전에 AI 주장과 출처 검토.
3. Spring AI 응답의 사실성 평가 세트와 토큰 실사용/비용 계측 추가.
4. 필요에 따라 분산 작업 큐·벡터 검색·더 넓은 커밋 수집·파일 검사/OCR을 확장.
