import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useState,
} from "react";
import type { ReactNode } from "react";
import type { Overview, Session } from "./types";
import { api, session as getSession } from "./api";
import { demoOverview } from "./demo";
const noSession: Session = {
  admin: false,
  oauthEnabled: false,
  aiConfigured: false,
  login: null,
  csrfToken: "",
};
type State = {
  demo: boolean;
  overview: Overview | null;
  session: Session;
  loading: boolean;
  error: string;
  refresh: () => Promise<void>;
  toggleMode: () => void;
};
const Context = createContext<State | null>(null);
export function DataProvider({ children }: { children: ReactNode }) {
  const [demo] = useState(() =>
    localStorage.getItem("devinsight-mode")
      ? localStorage.getItem("devinsight-mode") === "demo"
      : import.meta.env.VITE_DEMO_MODE === "true",
  );
  const [overview, setOverview] = useState<Overview | null>(null),
    [session, setSession] = useState(noSession),
    [loading, setLoading] = useState(true),
    [error, setError] = useState("");
  const refresh = useCallback(async () => {
    setLoading(true);
    setError("");
    if (demo) {
      setOverview(demoOverview);
      setLoading(false);
      return;
    }
    try {
      const [o, s] = await Promise.all([
        api<Overview>("/api/portfolio/overview"),
        getSession(),
      ]);
      setOverview(o);
      setSession(s);
    } catch (e) {
      setError(e instanceof Error ? e.message : "정보를 불러오지 못했습니다.");
    } finally {
      setLoading(false);
    }
  }, [demo]);
  useEffect(() => {
    void refresh();
  }, [refresh]);
  const toggleMode = () => {
    localStorage.setItem("devinsight-mode", demo ? "live" : "demo");
    window.location.reload();
  };
  return (
    <Context.Provider
      value={{ demo, overview, session, loading, error, refresh, toggleMode }}
    >
      {children}
    </Context.Provider>
  );
}
export function useData() {
  const c = useContext(Context);
  if (!c) throw new Error("Missing provider");
  return c;
}
