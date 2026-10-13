import { useEffect, useRef, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import {
  ArrowUp,
  ArrowUpRight,
  Bot,
  FileCode2,
  LoaderCircle,
  MessageSquare,
  ShieldCheck,
  Sparkles,
  User,
} from "lucide-react";
import { useData } from "../context";
import { api, safeUrl } from "../api";
import type { Answer } from "../types";
import { PageTitle, StatusLabel } from "../components";
type Exchange = { question: string; answer?: Answer; error?: string };
export function Interview() {
  const { demo, overview } = useData(),
    [params] = useSearchParams(),
    selected = overview?.projects.find(
      (p) => p.id === Number(params.get("project")),
    );
  const [question, setQuestion] = useState(""),
    [exchanges, setExchanges] = useState<Exchange[]>([]),
    [busy, setBusy] = useState(false),
    end = useRef<HTMLDivElement>(null);
  useEffect(() => {
    end.current?.scrollIntoView({ behavior: "smooth", block: "nearest" });
  }, [exchanges, busy]);
  const suggestions = selected
    ? [
        `${selected.title}의 주요 구현 기능은 무엇인가요?`,
        "이 프로젝트의 아키텍처를 설명해 주세요.",
        "개발자가 직접 기여한 부분은 무엇인가요?",
      ]
    : [
        "이 개발자의 대표적인 구현 경험은 무엇인가요?",
        "Spring Security로 구현한 기능이 있나요?",
        "Docker를 어디에 사용했나요?",
        "직접 구현한 것으로 확인되는 부분은 무엇인가요?",
      ];
  async function ask(value: string) {
    const q = value.trim();
    if (!q || q.length > 1000 || busy) return;
    setBusy(true);
    setQuestion("");
    setExchanges((prev) => [...prev, { question: q }]);
    try {
      let answer: Answer;
      if (demo) {
        const project =
          selected ??
          overview?.projects.find((p) =>
            p.analysis?.report.technologies.some((t) =>
              q.toLowerCase().includes(t.toLowerCase()),
            ),
          ) ??
          overview?.projects[0];
        const keywords = q
          .toLowerCase()
          .split(/\s+/)
          .map((t) => t.replace(/(를|을|는|은|로)$/, ""));
        const claim =
          project?.analysis?.report.claims.find((c) =>
            keywords.some(
              (k) =>
                k.length > 1 &&
                `${c.title} ${c.detail}`.toLowerCase().includes(k),
            ),
          ) ?? project?.analysis?.report.claims[0];
        answer = {
          answer: `[데모 응답 · 실제 AI 호출 없음]\n\n${project?.title ?? "샘플 프로젝트"}에서는 ${claim?.detail ?? "공개 자료의 구현 근거를 확인하는 과정을 보여줍니다."}\n\n이 답변은 UI 확인을 위해 미리 준비된 예시입니다. 질문별 실제 분석과 출처 검증은 API와 공개 프로젝트를 연결한 후 사용할 수 있습니다.`,
          status: "NEEDS_VERIFICATION",
          sources: project
            ? [
                {
                  id: "demo",
                  projectId: project.id,
                  projectTitle: project.title,
                  path:
                    project.analysis?.evidence.find((e) =>
                      claim?.evidenceIds.includes(e.id),
                    )?.path ?? "샘플 자료",
                  url: "#",
                },
              ]
            : [],
          demo: true,
        };
      } else
        answer = await api<Answer>("/api/interview/ask", {
          method: "POST",
          body: JSON.stringify({ question: q, projectId: selected?.id }),
        });
      setExchanges((prev) =>
        prev.map((e, i) => (i === prev.length - 1 ? { ...e, answer } : e)),
      );
    } catch (e) {
      setExchanges((prev) =>
        prev.map((item, i) =>
          i === prev.length - 1
            ? {
                ...item,
                error:
                  e instanceof Error
                    ? e.message
                    : "질문을 처리하지 못했습니다.",
              }
            : item,
        ),
      );
    } finally {
      setBusy(false);
    }
  }
  return (
    <>
      <PageTitle
        eyebrow="ASK THE PORTFOLIO"
        title="경험에 대해, 더 깊이 물어보세요."
        description="공개 프로젝트의 코드와 문서 근거를 바탕으로 답변합니다."
      />
      <div className="interview-layout">
        <section className="chat-panel">
          <div className="chat-header">
            <span className="chat-avatar">
              <Sparkles size={18} />
            </span>
            <div>
              <strong>DevInsight 어시스턴트</strong>
              <span>
                {demo
                  ? "샘플 대화 · GPT 미사용"
                  : "공개된 근거에 기반한 포트폴리오 Q&A"}
              </span>
            </div>
            <span className="subtle-badge">{demo ? "DEMO" : "AI"}</span>
          </div>
          <div className="chat-messages" aria-live="polite">
            {exchanges.length === 0 ? (
              <div className="chat-welcome">
                <div className="welcome-icon">
                  <MessageSquare size={30} />
                </div>
                <h2>
                  {selected
                    ? `${selected.title}, 무엇이 궁금한가요?`
                    : "어떤 경험이 궁금한가요?"}
                </h2>
                <p>
                  구현 방식부터 기술 선택까지,
                  <br />
                  확인 가능한 자료를 함께 살펴보세요.
                </p>
                <div className="suggestion-grid">
                  {suggestions.map((q) => (
                    <button key={q} onClick={() => void ask(q)}>
                      {q}
                      <ArrowUpRight size={15} />
                    </button>
                  ))}
                </div>
              </div>
            ) : (
              exchanges.map((e, i) => (
                <div className="exchange" key={i}>
                  <div className="chat-question">
                    <span>
                      <User size={16} />
                    </span>
                    <p>{e.question}</p>
                  </div>
                  {e.answer ? (
                    <div className="chat-answer">
                      <span className="bot-mark">
                        <Bot size={19} />
                      </span>
                      <div>
                        <strong>DevInsight AI</strong>
                        <p>{e.answer.answer}</p>
                        <StatusLabel status={e.answer.status} />
                        {e.answer.sources.length > 0 && (
                          <div className="answer-sources">
                            <small>관련 프로젝트와 근거</small>
                            {e.answer.sources.map((s) => (
                              <div key={s.id}>
                                <Link to={`/projects/${s.projectId}`}>
                                  <FileCode2 size={14} />
                                  {s.projectTitle}
                                </Link>
                                {e.answer?.demo ? (
                                  <span>샘플 출처</span>
                                ) : (
                                  <a
                                    href={safeUrl(s.url)}
                                    target="_blank"
                                    rel="noreferrer"
                                  >
                                    {s.path}
                                    <ArrowUpRight size={12} />
                                  </a>
                                )}
                              </div>
                            ))}
                          </div>
                        )}
                      </div>
                    </div>
                  ) : e.error ? (
                    <div className="inline-error" role="alert">
                      {e.error}
                      <button onClick={() => void ask(e.question)}>
                        다시 시도
                      </button>
                    </div>
                  ) : (
                    <div className="chat-thinking">
                      <LoaderCircle size={16} className="spin" /> 관련 근거를
                      살펴보고 있어요.
                    </div>
                  )}
                </div>
              ))
            )}
            <div ref={end} />
          </div>
          <form
            className="chat-composer"
            onSubmit={(e) => {
              e.preventDefault();
              void ask(question);
            }}
          >
            <div>
              <textarea
                aria-label="AI에게 질문"
                placeholder="이 개발자의 프로젝트에 대해 질문해 보세요…"
                value={question}
                maxLength={1000}
                onChange={(e) => setQuestion(e.target.value)}
                onKeyDown={(e) => {
                  if (
                    e.key === "Enter" &&
                    !e.shiftKey &&
                    !e.nativeEvent.isComposing
                  ) {
                    e.preventDefault();
                    void ask(question);
                  }
                }}
              />
              <button
                className="send-button"
                type="submit"
                disabled={busy || !question.trim()}
                aria-label="질문 보내기"
              >
                {busy ? (
                  <LoaderCircle size={20} className="spin" />
                ) : (
                  <ArrowUp size={20} />
                )}
              </button>
            </div>
            <small>
              <ShieldCheck size={12} /> 근거가 부족하면 추측하지 않습니다.
              <span>{question.length}/1,000</span>
            </small>
          </form>
        </section>
        <aside className="interview-aside">
          <section className="panel">
            <h3>무엇을 물어볼 수 있나요?</h3>
            <div className="topic-list">
              <div>
                <CodeBadge>01</CodeBadge>
                <h4>기술과 구현</h4>
                <p>사용한 기술, 인증 구조, 데이터 처리 방식</p>
              </div>
              <div>
                <CodeBadge>02</CodeBadge>
                <h4>설계와 선택</h4>
                <p>프로젝트 아키텍처와 근거가 있는 기술 선택</p>
              </div>
              <div>
                <CodeBadge>03</CodeBadge>
                <h4>개발 경험</h4>
                <p>코드에서 확인되는 문제 해결과 기여 범위</p>
              </div>
            </div>
          </section>
          <section className="panel limits-panel">
            <ShieldCheck size={21} />
            <h3>답변의 기준</h3>
            <p>
              공개 승인된 자료만 사용하며, 답변에 관련 프로젝트와 출처를
              연결합니다.
            </p>
            <p>
              AI의 해석은 실제 작성자의 설명과 다를 수 있습니다. 출처를 함께
              확인해 주세요.
            </p>
            <p>
              질문과 답변은 운영 목적으로 최대 30일 보관됩니다. 개인정보나
              비밀정보는 입력하지 마세요.
            </p>
          </section>
        </aside>
      </div>
    </>
  );
}
function CodeBadge({ children }: { children: string }) {
  return <span className="code-badge">{children}</span>;
}
