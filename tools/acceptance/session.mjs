export class Session {
  constructor(base) {
    this.base = base;
    this.cookies = new Map();
    this.csrf = null;
  }
  async request(
    path,
    { method = "GET", body, headers = {}, csrf = true } = {},
  ) {
    const h = {
      ...headers,
      Cookie: [...this.cookies].map(([k, v]) => `${k}=${v}`).join("; "),
    };
    if (csrf && method !== "GET" && this.csrf)
      h[this.csrf.headerName] = this.csrf.token;
    if (body !== undefined && typeof body !== "string") {
      h["Content-Type"] = "application/json";
      body = JSON.stringify(body);
    }
    const res = await fetch(this.base + "/api" + path, {
      method,
      headers: h,
      body,
      signal: AbortSignal.timeout(15000),
    });
    for (const c of res.headers.getSetCookie()) {
      const pair = c.split(";")[0];
      const i = pair.indexOf("=");
      this.cookies.set(pair.slice(0, i), pair.slice(i + 1));
    }
    const raw = await res.text();
    let data;
    try {
      data = JSON.parse(raw);
    } catch {
      data = raw;
    }
    return { status: res.status, data };
  }
  async token() {
    const r = await this.request("/auth/csrf");
    if (r.status !== 200) throw Error(`CSRF ${r.status}`);
    this.csrf = r.data;
  }
  async login(username) {
    await this.token();
    const r = await this.request("/auth/login", {
      method: "POST",
      headers: { "Content-Type": "application/x-www-form-urlencoded" },
      body: new URLSearchParams({
        username,
        password: "Demo12345!",
      }).toString(),
    });
    if (r.status !== 200)
      throw Error(`login ${username}: ${JSON.stringify(r)}`);
    await this.token();
    return r;
  }
}
