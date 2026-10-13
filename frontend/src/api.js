export class ApiError extends Error {
  constructor(message, code, status) {
    super(message);
    this.code = code;
    this.status = status;
  }
}
let csrfToken = "";
export async function session() {
  const s = await api("/api/auth");
  csrfToken = s.csrfToken;
  return s;
}
export async function api(path, options = {}) {
  const method = options.method ?? "GET";
  if (method !== "GET" && !csrfToken) await session();
  const headers = new Headers(options.headers);
  if (options.body && !(options.body instanceof FormData))
    headers.set("Content-Type", "application/json");
  if (method !== "GET") headers.set("X-XSRF-TOKEN", csrfToken);
  let res;
  try {
    res = await fetch(path, {
      ...options,
      headers,
      credentials: "same-origin",
    });
  } catch {
    throw new ApiError(
      "서버에 연결할 수 없습니다. 백엔드 실행 상태를 확인해 주세요.",
      "NETWORK_ERROR",
      0,
    );
  }
  if (!res.ok) {
    const body = await res.json().catch(() => ({}));
    if (res.status === 403) csrfToken = "";
    throw new ApiError(
      body.message ?? "요청을 완료하지 못했습니다.",
      body.code ?? "REQUEST_FAILED",
      res.status,
    );
  }
  if (res.status === 204) return undefined;
  return res.json();
}
export const safeUrl = (url) =>
  /^https:\/\//i.test(url) || /^\/api\/documents\/\d+\/content$/.test(url)
    ? url
    : "#";
