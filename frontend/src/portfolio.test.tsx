import { describe, it, expect, beforeEach, vi } from "vitest";
import {
  render,
  screen,
  fireEvent,
  waitFor,
  cleanup,
} from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { App } from "./App";
import { safeUrl } from "./api";
beforeEach(() => {
  cleanup();
  localStorage.clear();
  localStorage.setItem("devinsight-mode", "demo");
  Element.prototype.scrollIntoView = vi.fn();
});
describe("portfolio visitor journeys", () => {
  it("labels demo data and filters projects", async () => {
    render(
      <MemoryRouter initialEntries={["/projects"]}>
        <App />
      </MemoryRouter>,
    );
    await screen.findByText("DevInsight AI", { selector: "h3" });
    expect(
      screen.getByText(/인물, 프로젝트, 활동 및 분석은 샘플/),
    ).toBeInTheDocument();
    fireEvent.change(screen.getByLabelText("프로젝트 검색"), {
      target: { value: "Flowboard" },
    });
    expect(
      screen.getByText("Flowboard", { selector: "h3" }),
    ).toBeInTheDocument();
    expect(
      screen.queryByText("DevInsight AI", { selector: "h3" }),
    ).not.toBeInTheDocument();
  });
  it("shows source previews and interview sample disclaimer", async () => {
    render(
      <MemoryRouter initialEntries={["/projects/1"]}>
        <App />
      </MemoryRouter>,
    );
    await screen.findByRole("tab", { name: /코드 근거/ });
    fireEvent.click(screen.getByRole("tab", { name: /코드 근거/ }));
    expect(
      await screen.findByText("analysis/AnalysisWorker.java", {
        selector: "summary span",
      }),
    ).toBeInTheDocument();
  });
  it("answers in demo without invoking GPT", async () => {
    const fetchSpy = vi.spyOn(globalThis, "fetch");
    render(
      <MemoryRouter initialEntries={["/interview"]}>
        <App />
      </MemoryRouter>,
    );
    fireEvent.change(screen.getByLabelText("AI에게 질문"), {
      target: { value: "Docker를 어디에 사용했나요?" },
    });
    fireEvent.click(screen.getByLabelText("질문 보내기"));
    await waitFor(() =>
      expect(
        screen.getByText(/데모 응답 · 실제 AI 호출 없음/),
      ).toBeInTheDocument(),
    );
    expect(fetchSpy).not.toHaveBeenCalled();
    fetchSpy.mockRestore();
  });
  it("blocks unsafe links", () => {
    expect(safeUrl("javascript:alert(1)")).toBe("#");
    expect(safeUrl("/api/documents/1/content")).toBe(
      "/api/documents/1/content",
    );
    expect(safeUrl("//evil.example")).toBe("#");
  });
});
