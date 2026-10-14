export class ApiError extends Error {
  constructor(
    message: string,
    public code: string,
    public status: number,
    public requestId?: string,
  ) {
    super(message);
  }
}
let csrf: { token: string; headerName: string } | null = null;
export async function refreshCsrf() {
  csrf = null;
  csrf = await request<{ token: string; headerName: string }>("/auth/csrf");
  return csrf;
}
function expireSession() {
  csrf = null;
  window.dispatchEvent(new Event("session-expired"));
}
export async function request<T>(
  path: string,
  options: RequestInit = {},
): Promise<T> {
  const mutating = !["GET", "HEAD", "OPTIONS"].includes(
    (options.method || "GET").toUpperCase(),
  );
  const headers = new Headers(options.headers);
  if (
    options.body &&
    !(options.body instanceof URLSearchParams) &&
    !headers.has("Content-Type")
  )
    headers.set("Content-Type", "application/json");
  if (mutating) {
    if (!csrf) await refreshCsrf();
    headers.set(csrf!.headerName, csrf!.token);
  }
  let response: Response;
  try {
    response = await fetch("/api" + path, {
      ...options,
      headers,
      credentials: "same-origin",
    });
  } catch {
    throw new ApiError(
      "网络连接中断，请检查服务连接后重试。",
      "NETWORK_ERROR",
      0,
    );
  }
  if (!response.ok) {
    const error = await response.json().catch(() => ({}));
    if (response.status === 403 && mutating) {
      // CSRF rejection precedes authentication, so check the session separately.
      const session = await fetch("/api/auth/me", {
        credentials: "same-origin",
      }).catch(() => null);
      if (session?.status === 401) {
        expireSession();
        throw new ApiError(
          "登录已过期，请重新登录。",
          "UNAUTHORIZED",
          401,
          error.requestId,
        );
      }
      // Keep the original failure and let the user retry; never replay a mutation.
      if (session?.ok) await refreshCsrf().catch(() => {});
    }
    if (response.status === 401) {
      csrf = null;
      if (path !== "/auth/me" && path !== "/auth/login") expireSession();
    }
    throw new ApiError(
      error.message || "请求失败，请稍后重试。",
      error.code || "REQUEST_ERROR",
      response.status,
      error.requestId,
    );
  }
  if (response.status === 204) return undefined as T;
  const text = await response.text();
  return (text ? JSON.parse(text) : undefined) as T;
}
export function errorMessage(error: unknown) {
  return error instanceof Error ? error.message : "操作失败，请重试。";
}
export function queryString(params: object) {
  return new URLSearchParams(
    Object.entries(params)
      .filter(([, v]) => v !== undefined && v !== null && v !== "")
      .map(([k, v]) => [k, String(v)]),
  ).toString();
}
