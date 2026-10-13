# DevInsight AI 검증 결과

검증일: 2026-10-13 (Asia/Seoul). 작업 위치: `C:\Users\HP\portfolio`.

## 완료 범위

Java 21 / Spring Boot 4.1.1 / Spring AI 2.0.1 백엔드와 React / JavaScript / JSX 프론트엔드를 구현했습니다. 공개 GitHub 수집, 비동기 구조화 분석과 버전 보존, 문서 추출과 공개 승인, 관리자 OAuth 허용 계정 검사, 대시보드와 근거 기반 Q&A가 연결되어 있습니다. 외부 API를 모의 처리한 통합 테스트로 분석 저장·공개 조회·중복 방지·재사용·실패 시 이전 결과 보존을 검증했습니다.

후속 검증에서 일반 Gradle Wrapper 빌드, 실제 MySQL 8.0.33 및 MySQL 8.4.11 연결, 전체 Docker 스택의 기동과 HTTP 검증을 완료했습니다. 실제 OpenAI 호출과 OAuth 로그인 교환은 인증정보가 없어 미검증입니다. 사용자의 요청에 따라 이번 후속 작업은 외부 인증정보 없이 가능한 검증으로 한정합니다.

## JavaScript 전환 후 검증

프론트엔드 소스, 테스트, Vite 설정의 13개 모듈을 `.js` / `.jsx`로 전환했습니다. 타입 선언 파일, `tsconfig.json`, TypeScript 컴파일 단계 및 직접 설치한 `typescript`, `@types/react`, `@types/react-dom` 의존성을 제거했습니다. 저장소의 프론트엔드 소스와 설정에는 `.ts` / `.tsx` 파일이 없으며 `npm ls typescript --all`도 빈 결과입니다. 외부 의존성이 제공하는 타입 선언은 프론트엔드 작성 언어와 별개입니다.

2026-10-13 전환 완료 후 아래 항목을 다시 실행했습니다.

| 항목 | 결과 |
| --- | --- |
| `npm run build` | 실제 데이터 모드 빌드 성공 |
| `npm run build:demo -- --outDir ../work/javascript-demo-dist` | 데모 빌드 성공; 실제 데이터 모드 산출물과 별도 보관 |
| `npm test` | Vitest 4.1.11에서 기존 테스트 4개 통과 |
| `npm audit --json` | 개발 의존성 포함 취약점 0건 |
| `docker build --tag devinsight-frontend:javascript-refactor frontend` | Linux 컨테이너의 새 `npm ci` 설치와 프론트엔드 이미지 빌드 성공 |
| Vite 데모 브라우저 점검 | JavaScript 표기, 대시보드, 활동 차트 지연 로딩 및 페이지 이동 정상; 관찰한 콘솔 오류 0건 |

의존성 정리 과정에서 발견한 개발 도구 취약점을 해소하기 위해 Vitest를 4.1.11로 갱신했습니다. 관련 공지: [Vitest 보안 권고](https://github.com/advisories/GHSA-82fw-gwwq-j7x9).

이 변경은 프론트엔드와 문서에 한정됩니다. 아래 백엔드 25개 테스트, 실제 MySQL 연결 및 전체 Compose HTTP 결과는 전환 전 검증 기록이며, 이번 전환에서 재실행한 범위는 위 표와 같습니다. 로그는 Git에서 제외된 `work/javascript-build.log`, `work/javascript-demo-build.log`, `work/javascript-tests.log`, `work/javascript-docker-build.log`에 있습니다.

## 전체 구현의 빌드 및 자동 테스트 기록

| 항목 | 결과 | 범위 |
| --- | --- | --- |
| Gradle Wrapper `build --rerun-tasks --no-daemon` | **일반 설정으로 성공** | 별도 init script 없이 8개 작업 전체 실행, JAR/Boot JAR 생성 |
| 백엔드 테스트 | **25개 통과**, 실패/오류 0 | MySQL 8.0 및 8.4를 연결한 실행에서 각각 통과; 상세 연결 범위는 아래 참고 |
| 프론트엔드 `npm run build` | 성공 | Vite로 JavaScript / JSX 실제 데이터 모드 배포 빌드 |
| 프론트엔드 `npm run build:demo` | 성공 | 샘플 모드 배포 빌드 |
| 프론트엔드 `npm test` | **4개 통과** | 데모 표시/필터, 근거 패널, 네트워크 없는 데모 Q&A, 안전한 링크 |
| `npm audit --omit=dev` | 취약점 0 | 당시 설치된 production 의존성 기준 |
| Docker 이미지 및 스택 | **성공** | 원래 Backend/Frontend Dockerfile 빌드, MySQL·Backend·Frontend 기동 및 HTTP 통합 검증 |
| Flyway + JPA 검증 | H2, MySQL **8.0.33**, MySQL **8.4.11** 통과 | 별도 데이터 폴더/포트/테스트 DB 사용 |

백엔드 테스트 세부 결과:

| 클래스 | 수 | 주요 검증 |
| --- | ---: | --- |
| AiBudgetIntegrationTest | 1 | DB 일일 AI 호출 한도 |
| AnalysisSafetyTest | 5 | 파일 선택/비밀 패턴 마스킹/근거 검증/설정 누락/사용량 제한 |
| DocumentExtractorTest | 5 | PDF/DOCX/PPTX/텍스트 추출 및 위장 파일·위험 Office 구성 거부 |
| GitHubClientTest | 3 | API 제한, 일시적 장애 재시도, 경로 검증 |
| InterviewServiceTest | 2 | 근거 없는 경우 AI 미호출, 허위/철회된 출처 차단 |
| NoCredentialsApiTest | 2 | 인증정보 없이 공개 앱 시작, 과대 JSON 요청 차단 |
| OAuthConfigurationTest | 1 | OAuth 리다이렉트 및 state 구성; 실제 인증 교환 아님 |
| PortfolioIntegrationTest | 6 | API 인증/CSRF/공개 범위, 분석 저장·중복·재사용, 실패 보존, 문서 승인 철회, 입력 검증 |

기본 테스트에서는 GitHub/OpenAI를 모의 처리하며 실제 과금 호출을 하지 않습니다. MySQL 실행에서는 PortfolioIntegrationTest의 6개 테스트가 실제 MySQL에 연결됩니다. 나머지 19개는 단위 테스트 또는 격리된 H2 테스트입니다. H2는 테스트 의존성입니다. 운영 기본 DB를 H2로 변경하지 않았습니다.

## 실제 HTTP 검증

최초에는 H2 임시 DB로, 후속 작업에서는 실제 MySQL 8.0.33과 배포용 Boot JAR를 `127.0.0.1:18080`에서 실행하여 같은 HTTP 검증을 통과했습니다. 기존 서버/DB 데이터는 변경하지 않았습니다. MySQL의 Flyway V1 적용과 Hibernate 스키마 검증도 통과했습니다.

전체 Docker 스택에서는 MySQL 8.4.11을 사용하고 `127.0.0.1:15173`의 Nginx를 경유하여 아래 요청을 모두 다시 통과했습니다. SPA 상세 경로의 HTML 응답, JavaScript 자산의 200 응답, CSP 헤더도 확인했습니다.

| 요청 | 실제 결과 |
| --- | --- |
| GET `/actuator/health` | 200, UP |
| GET `/api/portfolio/overview` | 200, 빈 공개 프로젝트 목록 |
| GET `/api/auth` | OAuth 비활성, CSRF 토큰 발급 |
| 비로그인 GET `/api/github/repos` | 401 |
| CSRF 없는 POST `/api/interview/ask` | 403 |
| 유효한 CSRF, OpenAI 키 없는 질문 | 503, AI_NOT_CONFIGURED |
| 1,001자 질문 | 400 |
| Content-Length가 있는 8KB 초과 JSON | 413, REQUEST_TOO_LARGE |
| Content-Length 없는 chunked 8KB 초과 JSON | 413 |

## 재사용 Docker 검증 절차

`scripts/verify-compose.ps1`, `scripts/verify-http.cjs`, `docker-compose.verify.yml`을 추가했습니다. 원래 Compose/Dockerfile에 테스트 포트와 외부 API 비활성화 설정만 덧씌워 검증합니다.

- 임의의 별도 프로젝트 이름, 테스트 전용 비밀번호, 전용 포트 33071/18081/15173 사용
- 실제 사용자의 `.env` 생성/수정 없음; 실제 OpenAI/GitHub 자격증명 사용 안 함
- 별도 `devinsight_test` DB에서 테스트 실행; 응용 서버의 DB와 분리
- 최종 출력 `COMPOSE_VERIFICATION_PASSED`, 종료 코드 0 확인
- 검증 후 이번 실행이 만든 컨테이너·네트워크·테스트 볼륨 제거 확인; 이미지는 캐시로 유지

초기 보조 스크립트의 SQL 인용부호 문제는 SQL을 stdin으로 전달하도록 수정했습니다. 최종 제공 스크립트로 다시 실행하여 성공했습니다.

```powershell
cd C:\Users\HP\portfolio
powershell -NoProfile -File scripts\verify-compose.ps1
```

## 실제 GitHub 수집

별도 수동 점검에서 토큰 없이 `spring-projects/spring-petclinic` 공개 저장소에 실제 GitHub REST API로 접근했습니다. OpenAI는 호출하지 않았습니다.

- 대상 SHA: `500158f732419217507c7656904b8e6aa1bcc0d6`
- 수집 파일 수를 점검용으로 3개로 제한: `.devcontainer/Dockerfile`, `build.gradle`, `pom.xml`
- 커밋 30개, 닫힌 PR 5개 수집
- 실제 `GitHubClient`와 `SourceCollector`로 저장소 메타데이터·HEAD·트리·파일 내용·활동 수집 확인

이 점검은 실제 GitHub 통신 검증이며 로그인한 관리자 UI에서의 가져오기나 MySQL 저장 검증은 아닙니다.

## 브라우저 검증

빌드된 데모를 브라우저에서 직접 조작했습니다.

- 데스크톱 1440×1000 / 모바일 390×844, 가로 넘침 없음
- 다크/라이트 모드, 모바일 메뉴와 페이지 이동
- 프로젝트 검색으로 Flowboard 필터링, 상세 화면과 코드 근거 펼치기
- Q&A 질문/명시적인 샘플 답변/출처 링크 표시
- 활동 차트 지연 로딩 후 렌더링
- 확인한 브라우저 콘솔 오류 0건

후속 작업에서는 Vite 개발 서버도 15174 포트에서 정상 기동했습니다. 진입 HTML, 프론트엔드 진입 모듈, Vite 클라이언트가 모두 200으로 응답했고 브라우저에서 데모 React 화면을 렌더링했습니다. 소스 변경에 따른 HMR 갱신 자체는 별도로 시험하지 않았습니다.

Docker의 실제 데이터 모드에서는 빈 포트폴리오가 정상 렌더링되고, 관리자 화면에 OAuth 미설정 안내가 표시됐습니다. 이 시점까지 브라우저 콘솔 오류는 0건이었습니다. Q&A의 Docker 예시 질문을 눌러 503 AI_NOT_CONFIGURED에 대응하는 한국어 설정 안내와 재시도 버튼도 확인했습니다. 해당 503은 의도한 설정 누락 응답입니다.

실제 계정의 관리자 로그인, 실제 파일 업로드 UI와 외부 AI 결과를 이용한 브라우저 전체 흐름은 미검증입니다. 관련 서버 동작은 자동 테스트의 범위로 확인했습니다. 임시 HTTP 서버와 개발/미리보기 서버는 검증 후 종료했습니다.

## 실행 환경 제약과 우회

최초 검증에서는 Windows 에이전트 샌드박스의 JAR 경로 접근 제한으로 일반 Gradle 빌드가 실패했고, 당시 권한 상승 요청도 자동 승인 정책에서 거절됐습니다. 그래서 검증용 init script를 사용했습니다. 후속 작업에서는 범위를 프로젝트와 테스트 DB로 한정한 권한 검토가 승인되어, 아래 일반 Wrapper 빌드가 성공했습니다. **현재 빌드 성공 결과에는 그 init script가 필요하지 않습니다.**

후속 검증 명령의 구조:

```powershell
$env:GRADLE_USER_HOME='C:\Users\HP\portfolio\.gradle-user-home'
cd C:\Users\HP\portfolio\backend
# TEST_DB_*는 별도로 만든 MySQL 테스트 DB에 설정
.\gradlew.bat build --rerun-tasks --no-daemon
```

Vite는 Node.js 24와 `--configLoader native`를 사용합니다. 최초 샌드박스 안에서는 의존성 사전 번들링 경로 접근 오류가 있었지만, 후속 권한 검토 후 일반 `npm run dev`의 기동과 React 렌더링을 확인했습니다.

Docker Desktop의 named pipe 접근도 최초에는 거부됐으나 후속 권한 검토 후 Docker 29.0.1 / Compose 2.40.3 엔진에 접근할 수 있었습니다. `.github/workflows/ci.yml`의 원격 CI는 아직 실행하지 않았습니다.

로컬 상세 자료는 Git에서 제외된 `work/`의 `build-mysql-standard.log`, `mysql8-http-smoke-result.json`, `mysql80-test-results/`, `compose-verification-final.log`, `compose-http-smoke-result.json`, `github-live-result.log`와 `backend/build/test-results/test/`, `backend/build/reports/tests/test/`에 있습니다.

## 사용자 설정이 필요한 항목과 다음 순서

1. `.env.example`을 `.env`로 복사하고 직접 정한 DB_PASSWORD/MYSQL_ROOT_PASSWORD로 실제 사용할 MySQL 실행. 검증용 DB는 보존되는 운영 데이터 저장소가 아닙니다.
2. GITHUB_CLIENT_ID/GITHUB_CLIENT_SECRET/ADMIN_GITHUB_LOGIN 설정 후 실제 허용 계정 로그인. 콜백은 README의 주소와 정확히 일치시킴.
3. OPENAI_API_KEY 및 접근 가능한 Structured Outputs 모델 설정. 본인의 공개 저장소 하나를 가져와 분석 → 저장 → 공개 대시보드 → Q&A까지 검증.
4. 공개 승인 문서의 추가·철회와 재분석을 실제 환경에서 점검하고 AI 주장/기여/성과를 원본과 대조.

프로덕션 운영 확장 시에는 실제 토큰 비용 계측, 사실성 평가, 분산 작업 claim과 rate limiter, 검색 품질 개선을 우선합니다. 현재 작업 스케줄러는 단일 서버 전제입니다.
