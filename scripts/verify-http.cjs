// Checks an empty, isolated verification app with OAuth and OpenAI disabled.
// scripts/verify-compose.ps1 provides SMOKE_* variables and creates the report directory.
const assert = require("node:assert/strict"),
  fs = require("fs");
const base = process.env.SMOKE_BASE_URL || "http://127.0.0.1:18080";
(async () => {
  const health = await fetch(base + "/actuator/health");
  assert.equal(health.status, 200);
  assert.equal((await health.json()).status, "UP");
  const overview = await fetch(base + "/api/portfolio/overview");
  assert.equal(overview.status, 200);
  assert.deepEqual((await overview.json()).projects, []);
  const auth = await fetch(base + "/api/auth");
  const session = await auth.json();
  assert.equal(session.oauthEnabled, false);
  const cookie = auth.headers
    .getSetCookie()
    .map((c) => c.split(";")[0])
    .join("; ");
  assert.ok(session.csrfToken);
  const protectedRoute = await fetch(
    base + "/api/github/repos?username=octocat",
  );
  assert.equal(protectedRoute.status, 401);
  const csrf = await fetch(base + "/api/interview/ask", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ question: "Spring?" }),
  });
  assert.equal(csrf.status, 403);
  const missingKey = await fetch(base + "/api/interview/ask", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Cookie: cookie,
      "X-XSRF-TOKEN": session.csrfToken,
    },
    body: JSON.stringify({ question: "Spring Security?" }),
  });
  assert.equal(missingKey.status, 503);
  assert.equal((await missingKey.json()).code, "AI_NOT_CONFIGURED");
  const invalid = await fetch(base + "/api/interview/ask", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Cookie: cookie,
      "X-XSRF-TOKEN": session.csrfToken,
    },
    body: JSON.stringify({ question: "x".repeat(1001) }),
  });
  assert.equal(invalid.status, 400);
  const oversized = await fetch(base + "/api/interview/ask", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Cookie: cookie,
      "X-XSRF-TOKEN": session.csrfToken,
    },
    body: JSON.stringify({ question: "x".repeat(9000) }),
  });
  assert.equal(oversized.status, 413);
  assert.equal((await oversized.json()).code, "REQUEST_TOO_LARGE");
  const streamed = await fetch(base + "/api/interview/ask", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Cookie: cookie,
      "X-XSRF-TOKEN": session.csrfToken,
    },
    body: require("node:stream").Readable.from([
      JSON.stringify({ question: "x".repeat(9000) }),
    ]),
    duplex: "half",
  });
  assert.equal(streamed.status, 413);
  const report = {
    health: 200,
    publicOverview: 200,
    adminRouteAnonymous: 401,
    missingCsrf: 403,
    missingOpenAiKey: 503,
    oversizeQuestion: 400,
    oversizeBodyWithLength: 413,
    oversizeBodyChunked: 413,
    oauthEnabled: false,
  };
  if (process.env.SMOKE_FRONTEND === "true") {
    const page = await fetch(base + "/projects/999");
    assert.equal(page.status, 200);
    assert.match(page.headers.get("content-type"), /text\/html/);
    assert.match(
      page.headers.get("content-security-policy"),
      /frame-ancestors 'none'/,
    );
    const html = await page.text();
    const asset = html.match(/src="([^"]+\.js)"/);
    assert.ok(asset);
    const script = await fetch(base + asset[1]);
    assert.equal(script.status, 200);
    report.frontendSpa = 200;
    report.frontendAsset = 200;
    report.securityHeaders = true;
  }
  fs.writeFileSync(
    process.env.SMOKE_REPORT_PATH || "work/http-smoke-result.json",
    JSON.stringify(report, null, 2),
  );
  console.log(report);
})().catch((e) => {
  console.error(e);
  process.exit(1);
});
