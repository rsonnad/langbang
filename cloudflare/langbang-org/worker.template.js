const LOGO_SQUARE = "__LOGO_SQUARE_DATA_URL__";
const LOGO_WORDMARK = "__LOGO_WORDMARK_DATA_URL__";
const INSTALL_QR = "__INSTALL_QR_DATA_URL__";
// Single-file LangBang web app (Vite build), base64-encoded and injected at deploy time
// by scripts/deploy-langbang-org-site.sh. Served at /app behind its own login gate.
const APP_HTML_BASE64 = "__APP_HTML_BASE64__";
const SITE_VERSION = "site-v2";

const PUBLIC_R2_BASE = "https://pub-5bfcb836ff7946b785556c2d8131cba5.r2.dev";
const API_BASE = "https://langbangml-api.langbangml.workers.dev";
const ADMIN_APP_URL = `${API_BASE}/admin/analytics`;
const PHRASE_BRIDGE_URL = "https://langbang-phrase-bridge.langbangml.workers.dev";
const CHANNELS = [
  {
    id: "en-pl",
    tab: "English to Polish",
    title: "English speakers learning Polish",
    instanceId: "langbangml-en-pl",
    description: "This channel boots into langbangml-en-pl and updates from its own manifest, so it stays separate from the other learning direction.",
    latestApk: `${PUBLIC_R2_BASE}/langbang/builds/en-pl/langbangml-en-pl-latest.apk`,
    manifest: `${PUBLIC_R2_BASE}/langbang/builds/en-pl/latest.json`,
  },
  {
    id: "pl-en",
    tab: "Polish to English",
    title: "Polish speakers learning English",
    instanceId: "langbangml-pl-en",
    description: "This channel boots into langbangml-pl-en and updates from its own manifest, so it stays separate from the other learning direction.",
    latestApk: `${PUBLIC_R2_BASE}/langbang/builds/pl-en/langbangml-pl-en-latest.apk`,
    manifest: `${PUBLIC_R2_BASE}/langbang/builds/pl-en/latest.json`,
  },
  {
    id: "g2trans",
    tab: "G2 Translate",
    title: "LangBangTrans G2 Translate",
    instanceId: "com.sponic.langbangtrans",
    description: "Native Android bridge for Even G2 glasses, Gemini 3.5 Live Translate, Android microphone input, and Bluetooth headphone output.",
    latestApk: `${PUBLIC_R2_BASE}/langbang/builds/g2trans/langbangtrans-latest.apk`,
    manifest: `${PUBLIC_R2_BASE}/langbang/builds/g2trans/latest.json`,
  },
];
const LATEST_APK = CHANNELS[0].latestApk;
const SOURCE_URL = "https://github.com/rsonnad/langbang";

const commonHead = `
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <meta name="theme-color" content="#fff7f2">
  <link rel="icon" href="/assets/langbang-logo-square.png" type="image/png">
  <style>
    :root {
      --paper: #fff7f2;
      --ink: #1c1820;
      --muted: #6d606c;
      --line: rgba(45, 31, 48, 0.14);
      --soft: rgba(255, 255, 255, 0.68);
      --hot: #e74493;
      --coral: #ff735d;
      --violet: #6f4fe8;
      --blue: #3869d9;
      --radius: 8px;
      --shadow: 0 18px 60px rgba(83, 45, 77, 0.14);
    }
    * { box-sizing: border-box; }
    html { background: var(--paper); color: var(--ink); }
    body {
      margin: 0;
      font-family: Inter, ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
      line-height: 1.5;
      letter-spacing: 0;
      background:
        radial-gradient(circle at 12% 8%, rgba(255, 115, 93, 0.18), transparent 30%),
        radial-gradient(circle at 92% 10%, rgba(111, 79, 232, 0.14), transparent 30%),
        linear-gradient(180deg, #fffdfb 0%, #fff3ec 54%, #f8f7ff 100%);
      min-height: 100vh;
    }
    a { color: inherit; }
    .site {
      width: min(1120px, calc(100vw - 40px));
      margin: 0 auto;
    }
    header.nav {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 24px;
      padding: 24px 0;
    }
    .version-label {
      flex: 0 0 auto;
      color: #9a9099;
      font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
      font-size: 11px;
      line-height: 1;
      white-space: nowrap;
    }
    .brand-wrap {
      display: flex;
      align-items: center;
      gap: 14px;
      min-width: 0;
    }
    .brand {
      display: inline-flex;
      align-items: center;
      gap: 12px;
      min-width: 0;
      text-decoration: none;
    }
    .brand img.logo {
      width: 42px;
      height: 42px;
      object-fit: contain;
      flex: 0 0 auto;
    }
    .brand img.wordmark {
      width: 174px;
      max-width: 48vw;
      height: auto;
      display: block;
    }
    .navlinks {
      display: flex;
      align-items: center;
      gap: 18px;
      font-size: 14px;
      font-weight: 700;
      color: var(--muted);
    }
    .navlinks a { text-decoration: none; }
    .navlinks a:hover { color: var(--ink); }
    .hero {
      min-height: calc(100vh - 92px);
      display: grid;
      grid-template-columns: minmax(0, 1.05fr) minmax(300px, 0.95fr);
      gap: clamp(32px, 6vw, 76px);
      align-items: center;
      padding: 24px 0 56px;
    }
    .eyebrow {
      margin: 0 0 20px;
      color: var(--hot);
      font-size: 13px;
      font-weight: 800;
      text-transform: uppercase;
      letter-spacing: 0.12em;
    }
    h1 {
      margin: 0;
      max-width: 760px;
      font-size: clamp(48px, 8vw, 96px);
      line-height: 0.95;
      letter-spacing: 0;
      font-weight: 850;
    }
    .tagline {
      margin: 24px 0 0;
      max-width: 660px;
      font-size: clamp(22px, 3vw, 34px);
      line-height: 1.16;
      color: #342836;
      font-weight: 700;
    }
    .summary {
      margin: 18px 0 0;
      max-width: 600px;
      font-size: 18px;
      color: var(--muted);
    }
    .actions {
      display: flex;
      flex-wrap: wrap;
      gap: 12px;
      margin-top: 34px;
    }
    .button {
      display: inline-flex;
      min-height: 48px;
      align-items: center;
      justify-content: center;
      border: 1px solid transparent;
      border-radius: var(--radius);
      padding: 13px 18px;
      text-decoration: none;
      font-weight: 800;
      font-size: 15px;
      white-space: nowrap;
    }
    .button.primary {
      color: #fff;
      background: linear-gradient(135deg, var(--coral), var(--hot) 48%, var(--violet));
      box-shadow: 0 12px 28px rgba(231, 68, 147, 0.24);
    }
    .button.secondary {
      color: var(--ink);
      background: rgba(255, 255, 255, 0.72);
      border-color: var(--line);
    }
    .button:hover { transform: translateY(-1px); }
    .visual {
      position: relative;
      min-height: 540px;
      display: grid;
      place-items: center;
    }
    .app-panel {
      width: min(430px, 100%);
      border: 1px solid rgba(255,255,255,0.8);
      border-radius: 28px;
      padding: 32px;
      background: rgba(255, 255, 255, 0.58);
      box-shadow: var(--shadow);
      backdrop-filter: blur(18px);
    }
    .app-panel img.logo-big {
      width: min(260px, 76%);
      height: auto;
      display: block;
      margin: 10px auto 26px;
      filter: drop-shadow(0 20px 30px rgba(100, 48, 130, 0.18));
    }
    .module {
      border: 1px solid var(--line);
      border-radius: 14px;
      padding: 16px;
      background: rgba(255,255,255,0.72);
    }
    .module + .module { margin-top: 12px; }
    .module b {
      display: block;
      font-size: 14px;
      margin-bottom: 4px;
    }
    .module span {
      color: var(--muted);
      font-size: 14px;
    }
    .band {
      border-top: 1px solid var(--line);
      padding: 54px 0;
    }
    .grid {
      display: grid;
      grid-template-columns: repeat(3, 1fr);
      gap: 16px;
    }
    .card {
      background: rgba(255,255,255,0.66);
      border: 1px solid var(--line);
      border-radius: var(--radius);
      padding: 20px;
    }
    .card h2, .card h3 {
      margin: 0 0 8px;
      font-size: 18px;
      letter-spacing: 0;
    }
    .card p { margin: 0; color: var(--muted); }
    .build-page main {
      padding: 34px 0 72px;
    }
    .page-title {
      margin: 26px 0 10px;
      font-size: clamp(40px, 7vw, 72px);
      line-height: 1;
    }
    table {
      width: 100%;
      border-collapse: collapse;
      overflow: hidden;
      border: 1px solid var(--line);
      border-radius: var(--radius);
      background: rgba(255,255,255,0.72);
    }
    th, td {
      text-align: left;
      padding: 14px 16px;
      border-bottom: 1px solid var(--line);
      vertical-align: top;
    }
    th {
      font-size: 12px;
      text-transform: uppercase;
      letter-spacing: 0.08em;
      color: var(--muted);
    }
    td code {
      font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
      font-size: 13px;
    }
    .install-block {
      display: grid;
      grid-template-columns: 150px minmax(0, 1fr);
      gap: 20px;
      align-items: center;
      margin: 28px 0;
      padding: 20px;
      border: 1px solid var(--line);
      border-radius: var(--radius);
      background: rgba(255,255,255,0.72);
    }
    .install-block img {
      width: 150px;
      height: 150px;
      display: block;
    }
    .channel-tabs {
      display: flex;
      flex-wrap: wrap;
      gap: 10px;
      margin: 28px 0 18px;
    }
    .channel-tab {
      border: 1px solid var(--line);
      border-radius: var(--radius);
      background: rgba(255,255,255,0.72);
      color: var(--muted);
      cursor: pointer;
      font: inherit;
      font-weight: 800;
      min-height: 46px;
      padding: 10px 14px;
    }
    .channel-tab[aria-selected="true"] {
      color: #fff;
      background: linear-gradient(135deg, var(--coral), var(--hot) 48%, var(--violet));
      border-color: transparent;
    }
    .channel-panel {
      display: none;
      border: 1px solid var(--line);
      border-radius: var(--radius);
      background: rgba(255,255,255,0.72);
      padding: 22px;
      margin-bottom: 18px;
    }
    .channel-panel.active { display: block; }
    .channel-meta {
      display: grid;
      grid-template-columns: repeat(3, minmax(0, 1fr));
      gap: 12px;
      margin: 20px 0;
    }
    .stat {
      border: 1px solid var(--line);
      border-radius: var(--radius);
      padding: 14px;
      background: rgba(255,255,255,0.62);
    }
    .stat b {
      display: block;
      font-size: 12px;
      text-transform: uppercase;
      color: var(--muted);
      margin-bottom: 4px;
    }
    .stat span, .stat code { overflow-wrap: anywhere; }
    footer {
      border-top: 1px solid var(--line);
      padding: 26px 0 38px;
      color: var(--muted);
      font-size: 14px;
    }
    @media (max-width: 860px) {
      .site { width: min(100vw - 28px, 680px); }
      header.nav { align-items: flex-start; }
      .navlinks { gap: 12px; }
      .hero {
        min-height: auto;
        grid-template-columns: 1fr;
        padding: 18px 0 46px;
      }
      .visual { min-height: 360px; }
      .grid { grid-template-columns: 1fr; }
    }
    @media (max-width: 560px) {
      .brand img.wordmark { width: 140px; }
      .navlinks { font-size: 13px; }
      .actions { flex-direction: column; }
      .button { width: 100%; }
      .install-block { grid-template-columns: 1fr; }
      .install-block img { width: 132px; height: 132px; }
      .channel-meta { grid-template-columns: 1fr; }
      th, td { padding: 12px 10px; font-size: 14px; }
    }
  </style>`;

function shell(title, body, pageClass = "") {
  return `<!doctype html>
<html lang="en">
<head>
  <title>${title}</title>
  <meta name="description" content="Smart language learning - endlessly personalized.">
${commonHead}
</head>
<body class="${pageClass}">
  <div class="site">
    <header class="nav">
      <div class="brand-wrap">
        <span class="version-label">${SITE_VERSION}</span>
        <a class="brand" href="/">
          <img class="logo" src="/assets/langbang-logo-square.png" alt="">
          <img class="wordmark" src="/assets/langbang-logo-wordmark.png" alt="LangBang">
        </a>
      </div>
      <nav class="navlinks" aria-label="Primary">
        <a href="/builds.html">Builds</a>
        <a href="${SOURCE_URL}">Source</a>
      </nav>
    </header>
    ${body}
    <footer>LangBang is a personal-use Android tablet app for learning Polish.</footer>
  </div>
</body>
</html>`;
}

const homePage = shell("LangBang - Smart language learning", `
  <main class="hero">
    <section>
      <p class="eyebrow">Polish practice for Android tablets</p>
      <h1>Smart language learning</h1>
      <p class="tagline">Smart language learning - endlessly personalized.</p>
      <p class="summary">LangBang combines guided lessons, speech playback, generated examples, and pronunciation feedback into a native tablet practice loop.</p>
      <div class="actions">
        <a class="button primary" href="${LATEST_APK}">Install on Android</a>
        <a class="button secondary" href="/builds.html">APK builds page</a>
      </div>
    </section>
    <section class="visual" aria-label="LangBang app identity">
      <div class="app-panel">
        <img class="logo-big" src="/assets/langbang-logo-square.png" alt="LangBang logo">
        <div class="module"><b>Personalized examples</b><span>Generated practice sentences tuned to the words you are studying.</span></div>
        <div class="module"><b>Tap-to-hear playback</b><span>Polish and English audio ready inside the lesson flow.</span></div>
        <div class="module"><b>Pronunciation feedback</b><span>Speak, score, and repeat with precise practice targets.</span></div>
      </div>
    </section>
  </main>
  <section class="band">
    <div class="grid">
      <div class="card"><h3>Lessons</h3><p>Core Polish sounds, verbs, adjectives, adverbs, phrases, and numbers organized for repeat practice.</p></div>
      <div class="card"><h3>Adaptive practice</h3><p>Quiz and playback flows keep the next rep close to what you just missed.</p></div>
      <div class="card"><h3>Tablet first</h3><p>Landscape-friendly controls, large tap targets, and native Android install builds.</p></div>
    </div>
  </section>
`);

function buildChannelPanel(channel, manifest, index) {
  const versionCode = manifest?.versionCode ? `v${manifest.versionCode}` : "latest";
  const versionName = manifest?.versionName || "pending publish";
  const latestUrl = manifest?.url || channel.latestApk;
  const pinnedUrl = manifest?.pinnedUrl || latestUrl;
  const size = manifest?.sizeBytes ? `${Math.round(manifest.sizeBytes / 1024 / 1024)} MB` : "pending";
  const activeClass = index === 0 ? " active" : "";
  return `
    <section class="channel-panel${activeClass}" id="panel-${channel.id}" role="tabpanel" aria-labelledby="tab-${channel.id}">
      <h2>${channel.title}</h2>
      <p>${channel.description}</p>
      <div class="channel-meta">
        <div class="stat"><b>Current</b><span>${versionCode}</span></div>
        <div class="stat"><b>Manifest</b><code>${versionName}</code></div>
        <div class="stat"><b>APK size</b><span>${size}</span></div>
      </div>
      <div class="actions">
        <a class="button primary" href="${latestUrl}">Install latest</a>
        <a class="button secondary" href="${pinnedUrl}">Pinned APK</a>
        <a class="button secondary" href="${channel.manifest}">Manifest JSON</a>
      </div>
    </section>`;
}

function buildsPage(manifests) {
  const tabs = CHANNELS.map((channel, index) => `
    <button class="channel-tab" id="tab-${channel.id}" type="button" role="tab" aria-controls="panel-${channel.id}" aria-selected="${index === 0 ? "true" : "false"}">${channel.tab}</button>
  `).join("");
  const panels = CHANNELS.map((channel, index) => buildChannelPanel(channel, manifests[channel.id], index)).join("");
  return shell("LangBang - APK builds", `
  <main>
    <p class="eyebrow">Android APK builds</p>
    <h1 class="page-title">Install LangBang</h1>
    <p class="summary">Choose the APK channel. Each tab has its own app package, latest APK, pinned APK, and update manifest.</p>
    <div class="channel-tabs" role="tablist" aria-label="LangBang build channels">${tabs}</div>
    ${panels}
    <div class="install-block">
      <img src="/install-qr.svg" alt="QR code for the LangBang builds page">
      <div>
        <h2>Scan to choose a build</h2>
        <p>Point the tablet camera at this code to open the builds page, choose a tab, then approve Android's install prompt.</p>
      </div>
    </div>
    <script>
      const tabs = Array.from(document.querySelectorAll(".channel-tab"));
      const panels = Array.from(document.querySelectorAll(".channel-panel"));
      tabs.forEach((tab) => {
        tab.addEventListener("click", () => {
          tabs.forEach((item) => item.setAttribute("aria-selected", String(item === tab)));
          panels.forEach((panel) => panel.classList.toggle("active", panel.id === tab.getAttribute("aria-controls")));
        });
      });
    </script>
  </main>
`, "build-page");
}

function bridgeApprovalPage() {
  return shell("LangBang - Approve phrase bridge", `
  <main class="band">
    <section class="card">
      <p class="eyebrow">Phrase bridge approval</p>
      <h1>Allow phrase imports?</h1>
      <p class="summary">A client is trying to use the LangBang phrase bridge. Approving opens a one-hour write window for phrase imports into your LangBang library.</p>
      <div class="channel-meta">
        <div><span>Status</span><strong id="approval-status">Checking...</strong></div>
        <div><span>Signed in as</span><strong id="approval-user">Checking...</strong></div>
      </div>
      <div class="actions" style="margin-top: 18px;">
        <button class="button primary" id="approve-button" type="button">Allow for 1 hour</button>
        <button class="button secondary" id="revoke-button" type="button">Revoke</button>
        <a class="button secondary" href="/app">Open LangBang</a>
      </div>
      <p class="muted" id="approval-message" style="margin-top: 14px;"></p>
    </section>
  </main>
  <script>
    const bridgeUrl = ${JSON.stringify(PHRASE_BRIDGE_URL)};
    const sessionKey = "langbang.session.v1";
    const statusEl = document.getElementById("approval-status");
    const userEl = document.getElementById("approval-user");
    const messageEl = document.getElementById("approval-message");
    const approveButton = document.getElementById("approve-button");
    const revokeButton = document.getElementById("revoke-button");

    function setMessage(text, isError) {
      messageEl.textContent = text || "";
      messageEl.style.color = isError ? "#b42318" : "";
    }

    function loadSession() {
      try {
        const raw = localStorage.getItem(sessionKey);
        if (!raw) return null;
        const session = JSON.parse(raw);
        if (!session || !session.token || !session.user) return null;
        if (session.expiresAt && Date.parse(session.expiresAt) <= Date.now()) return null;
        return session;
      } catch (_error) {
        return null;
      }
    }

    async function refreshStatus() {
      const session = loadSession();
      userEl.textContent = session?.user?.email || "Not signed in";
      approveButton.disabled = !session;
      revokeButton.disabled = !session;
      if (!session) {
        setMessage("Open LangBang and sign in first, then come back to approve the bridge.", true);
      }
      try {
        const res = await fetch(bridgeUrl + "/approval-status", { cache: "no-store" });
        const body = await res.json();
        statusEl.textContent = body.approvalActive
          ? "Allowed until " + body.approvedUntil + " UTC"
          : "Approval required";
      } catch (_error) {
        statusEl.textContent = "Could not check bridge";
      }
    }

    async function submitApproval(path, body) {
      const session = loadSession();
      if (!session) {
        setMessage("Sign in at /app before approving bridge writes.", true);
        return;
      }
      setMessage("Sending approval...", false);
      const res = await fetch(bridgeUrl + path, {
        method: "POST",
        headers: {
          "Authorization": "Bearer " + session.token,
          "Content-Type": "application/json",
        },
        body: JSON.stringify(body || {}),
      });
      const payload = await res.json().catch(() => ({}));
      if (!res.ok || payload.ok === false) {
        setMessage(payload.message || payload.error || "Approval failed.", true);
        await refreshStatus();
        return;
      }
      setMessage(path === "/approve" ? "Phrase bridge writes are allowed for 1 hour." : "Phrase bridge writes are revoked.", false);
      await refreshStatus();
    }

    approveButton.addEventListener("click", () => submitApproval("/approve", { durationMinutes: 60 }));
    revokeButton.addEventListener("click", () => submitApproval("/revoke"));
    refreshStatus();
  </script>
`, "build-page");
}

addEventListener("fetch", (event) => {
  event.respondWith(handleRequest(event.request));
});

async function handleRequest(request) {
  const url = new URL(request.url);
  const path = url.pathname.replace(/\/+$/, "") || "/";
  if (path === "/health") {
    return json({ ok: true, service: "langbang-site" });
  }
  if (path === "/app" || path.startsWith("/app/")) {
    return appHtmlResponse();
  }
  if (path === "/assets/langbang-logo-square.png") {
    return dataUrlResponse(LOGO_SQUARE, "image/png");
  }
  if (path === "/assets/langbang-logo-wordmark.png") {
    return dataUrlResponse(LOGO_WORDMARK, "image/png");
  }
  if (path === "/install-qr.svg") {
    return dataUrlResponse(INSTALL_QR, "image/svg+xml; charset=utf-8");
  }
  if (path === "/builds" || path === "/builds.html") {
    return html(buildsPage(await loadBuildManifests()));
  }
  if (path === "/bridge/approve" || path === "/bridge/approve.html") {
    return html(bridgeApprovalPage());
  }
  if (path === "/api" || path === "/api.html") {
    return await apiPage(request);
  }
  if (path === "/admin" || path === "/admin/analytics") {
    return await adminPage();
  }
  if (path === "/v1/admin/analytics/summary" || path === "/v1/admin/analytics/events") {
    return await proxyAdminApi(request, path, url.search);
  }
  if (path === "/privacy" || path === "/privacy.html") {
    return html(privacyPage());
  }
  if (path === "/terms" || path === "/terms.html") {
    return html(termsPage());
  }
  if (url.pathname.startsWith("/v1/")) {
    return await proxyV1(request, url);
  }
  return html(homePage);
}

// Same-origin API proxy for the /app web app: forwards /v1/* to the backend Worker and
// rewrites instance IDs (en-pl <-> langbangml-en-pl) + the "LangBangML" display name so
// the web app never surfaces the internal name. The Android app calls the backend directly
// and is unaffected.
const INSTANCE_ALIAS = [
  ["en-pl", "langbangml-en-pl"],
  ["pl-en", "langbangml-pl-en"],
];

function pathAliasToReal(pathname) {
  let out = pathname;
  for (const [alias, real] of INSTANCE_ALIAS) {
    out = out.split(`/instances/${alias}`).join(`/instances/${real}`);
  }
  return out;
}

function instanceAliasToReal(value) {
  for (const [alias, real] of INSTANCE_ALIAS) {
    if (value === alias) return real;
  }
  return value;
}

function searchAliasToReal(search) {
  if (!search) return "";
  const params = new URLSearchParams(search);
  for (const key of ["instanceId", "instance"]) {
    const value = params.get(key);
    if (value) params.set(key, instanceAliasToReal(value));
  }
  const next = params.toString();
  return next ? `?${next}` : "";
}

function bodyAliasToReal(text) {
  let out = text;
  for (const [alias, real] of INSTANCE_ALIAS) {
    out = out.split(`"instanceId":"${alias}"`).join(`"instanceId":"${real}"`);
    out = out.replace(new RegExp(`("instanceId"\\s*:\\s*)"${alias}"`, "g"), `$1"${real}"`);
    out = out.replace(new RegExp(`("instance"\\s*:\\s*)"${alias}"`, "g"), `$1"${real}"`);
  }
  return out;
}

function realToAlias(text) {
  let out = text;
  for (const [alias, real] of INSTANCE_ALIAS) {
    out = out.split(`"${real}"`).join(`"${alias}"`);
  }
  out = out.split("LangBangML").join("LangBang");
  // Cleanup any remaining internal-name markers (e.g. content "schema" tags). Runs after the
  // targeted instance-id aliasing above so real IDs are already friendly.
  return out.split("langbangml").join("langbang");
}

function apiCorsHeaders() {
  return {
    "Access-Control-Allow-Origin": "*",
    "Access-Control-Allow-Methods": "GET, POST, PUT, DELETE, OPTIONS",
    "Access-Control-Allow-Headers": "Content-Type, Authorization",
  };
}

async function proxyV1(request, url) {
  if (request.method === "OPTIONS") {
    return new Response(null, { status: 204, headers: { ...apiCorsHeaders(), "Cache-Control": "no-store" } });
  }
  const target = `${API_BASE}${pathAliasToReal(url.pathname)}${searchAliasToReal(url.search)}`;
  const headers = new Headers();
  const auth = request.headers.get("Authorization");
  if (auth) headers.set("Authorization", auth);
  const contentType = request.headers.get("Content-Type");
  if (contentType) headers.set("Content-Type", contentType);
  headers.set("Accept", "application/json");
  let body;
  if (request.method !== "GET" && request.method !== "HEAD") {
    body = bodyAliasToReal(await request.text());
  }
  const upstream = await fetch(target, { method: request.method, headers, body });
  const outText = realToAlias(await upstream.text());
  return new Response(outText, {
    status: upstream.status,
    headers: {
      ...apiCorsHeaders(),
      "Content-Type": upstream.headers.get("Content-Type") || "application/json; charset=utf-8",
      "Cache-Control": "no-store",
    },
  });
}

async function adminPage() {
  const response = await fetch(`${ADMIN_APP_URL}?site=${Date.now()}`, {
    headers: { "Cache-Control": "no-cache" },
  });
  const body = await response.text();
  return new Response(body, {
    status: response.status,
    headers: {
      "Content-Type": response.headers.get("Content-Type") || "text/html; charset=utf-8",
      "Cache-Control": "no-store",
    },
  });
}

async function apiPage(request) {
  const origin = new URL(request.url).origin;
  const response = await fetch(`${API_BASE}/agent?site=${Date.now()}`, {
    headers: { "Cache-Control": "no-cache" },
  });
  const body = (await response.text())
    .split(API_BASE).join(origin)
    .split("https://langbangml-api.langbangml.workers.dev").join(origin);
  return new Response(injectLimitedClientApiDocs(body), {
    status: response.status,
    headers: {
      "Content-Type": response.headers.get("Content-Type") || "text/html; charset=utf-8",
      "Cache-Control": "no-store",
    },
  });
}

function injectLimitedClientApiDocs(body) {
  const marker = "<h2>Limited Clients And Bridge Adapters</h2>";
  if (body.includes(marker)) return body;
  const section = `
    <section class="warn">
      ${marker}
      <p>The direct LangBang Agent API is the source of truth for capable clients that can safely send authenticated JSON requests with <code>Authorization: Bearer ...</code>.</p>
      <p>Some clients, including browser-only AI tools such as Grok, may be able to read this page but may not be able to securely store a LangBang token or make authenticated <code>POST</code> requests. Do not ask those clients to call <code>/v1/agent/phrases</code> directly.</p>
      <p>For clients that can make one ordinary JSON <code>POST</code>, use the bridge at <code>https://langbang-phrase-bridge.langbangml.workers.dev/add-phrases</code>. The bridge stores the LangBang agent token as a Worker secret, accepts a simple phrase list, then forwards each line to <code>/v1/agent/phrases</code> with <code>atomic:true</code>. This is an adapter around the Agent API, not a replacement for it.</p>
      <p>Bridge writes require a short approval window. Before using the bridge, open <code>https://langbang.org/bridge/approve</code> while signed in to LangBang and choose <b>Allow for 1 hour</b>. If no approval is active, the bridge returns <code>approval_required</code> plus the approval URL.</p>
      <p>If a client can only open or browse pages and cannot make any <code>POST</code> request, then it still cannot perform writes itself; use Codex, Claude, curl, or another tool that can submit the bridge request.</p>
      <p>LangBang bridge Workers that store LangBang tokens or write LangBang data belong in the <code>langbangapp@gmail.com</code> Cloudflare account, not the Wingsiebird DNS-only account.</p>
      <h3>Bridge Request Shape</h3>
      <pre>POST https://langbang-phrase-bridge.langbangml.workers.dev/add-phrases
Content-Type: application/json

{
  "groupTitle": "More Than I Thought",
  "phrases": [
    "Your laugh and that look in those eyes,",
    "rail trips, and blue sunny skies."
  ]
}</pre>
      <p class="muted">Long lyric imports can take a while because LangBang may generate missing translations and word alignment for each line. If a client has a short timeout, send smaller batches or include both <code>english</code> and <code>polish</code> fields for difficult lyric fragments.</p>
      <h3>Bridge Forwarding Rule</h3>
      <pre>{
  "groupTitle": "More Than I Thought",
  "phrases": ["one clean line"],
  "atomic": true
}</pre>
    </section>`;
  return body.replace("<section>\n      <h2>Examples</h2>", `${section}\n    <section>\n      <h2>Examples</h2>`);
}

async function proxyAdminApi(request, path, search) {
  if (request.method === "OPTIONS") {
    return new Response(null, { status: 204, headers: { "Cache-Control": "no-store" } });
  }
  if (request.method !== "GET") {
    return json({ error: "method not allowed" }, 405);
  }
  const headers = new Headers();
  const auth = request.headers.get("Authorization");
  if (auth) headers.set("Authorization", auth);
  headers.set("Accept", "application/json");
  const response = await fetch(`${API_BASE}${path}${search}`, {
    headers,
    cf: { cacheTtl: 0, cacheEverything: false },
  });
  return new Response(response.body, {
    status: response.status,
    headers: {
      "Content-Type": response.headers.get("Content-Type") || "application/json; charset=utf-8",
      "Cache-Control": "no-store",
    },
  });
}

async function loadBuildManifests() {
  const entries = await Promise.all(CHANNELS.map(async (channel) => {
    try {
      const response = await fetch(`${channel.manifest}?site=${Date.now()}`, {
        headers: { "Cache-Control": "no-cache" },
      });
      if (!response.ok) return [channel.id, null];
      return [channel.id, await response.json()];
    } catch (_error) {
      return [channel.id, null];
    }
  }));
  return Object.fromEntries(entries);
}

function legalShell(title, updated, bodyHtml) {
  return `<!doctype html>
<html lang="en"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>${title} — LangBang</title>
<style>
  :root { color-scheme: light dark; }
  body { margin:0; font:16px/1.65 -apple-system,BlinkMacSystemFont,"Segoe UI",Roboto,Helvetica,Arial,sans-serif; color:#1a1a1a; background:#fafafa; }
  .wrap { max-width:760px; margin:0 auto; padding:40px 22px 90px; }
  a { color:#2563eb; }
  h1 { font-size:1.9rem; margin:.2em 0 .1em; }
  h2 { font-size:1.18rem; margin:1.9em 0 .4em; }
  .updated { color:#666; font-size:.92rem; margin:0 0 1.6em; }
  .nav { font-size:.92rem; margin-bottom:2em; }
  ul { padding-left:1.2em; } li { margin:.25em 0; }
  code { background:rgba(0,0,0,.06); padding:.1em .35em; border-radius:4px; font-size:.9em; }
  .foot { margin-top:3em; color:#888; font-size:.85rem; }
  @media (prefers-color-scheme: dark){ body{background:#111;color:#e7e7e7} .updated,.nav,.foot{color:#9a9a9a} code{background:rgba(255,255,255,.1)} }
</style></head>
<body><div class="wrap">
<div class="nav"><a href="/">← LangBang</a></div>
<h1>${title}</h1>
<p class="updated">Last updated: ${updated}</p>
${bodyHtml}
<p class="foot">Questions about this policy or your data: <a href="mailto:langbangapp@gmail.com">langbangapp@gmail.com</a></p>
</div></body></html>`;
}

function privacyPage() {
  return legalShell("Privacy Policy", "June 29, 2026", `
<p>LangBang is a speaking-first Polish practice app, published as
<code>com.sponic.langbangml.enpl</code> (English speakers learning Polish) and
<code>com.sponic.langbangml.plen</code> (Polish speakers learning English). This
policy explains what data the app and its backend process, and why.</p>

<h2>Data we process</h2>
<ul>
  <li><b>Microphone audio</b> — only while you actively use a pronunciation
    practice feature. Audio is sent to <b>Microsoft Azure Speech</b> services for
    pronunciation scoring and is not retained by us after scoring.</li>
  <li><b>Text you enter</b> — custom phrases and generation prompts are processed
    by our backend and by <b>Google Gemini</b> to generate, complete, and validate
    language-learning content.</li>
  <li><b>Account data (only if you sign in)</b> — your email address, and for
    Google Sign-In your name and profile picture; login session tokens; and
    one-time email login codes. Used to authenticate you and sync your content.</li>
  <li><b>Your custom content</b> — phrase groups, starred phrases, and custom
    words you create, stored so they sync across your devices.</li>
  <li><b>Usage analytics</b> — a random installation identifier, session
    identifiers, app version, package/flavor, device model, OS version, locale,
    and feature/screen names and durations. If you are signed in, this may be
    associated with your account email.</li>
  <li><b>Audio delivery</b> — the app downloads and pre-fetches pronunciation
    audio and makes backend requests (including on launch).</li>
  <li><b>Optional backup</b> — if you configure the SFTP backup feature, app
    data, preferences, cached audio, and device metadata are sent to a server
    <i>you</i> specify. This is off unless you set it up.</li>
  <li><b>Server logs</b> — our infrastructure provider (Cloudflare) processes IP
    addresses and request metadata for security, abuse prevention, and
    diagnostics.</li>
</ul>

<h2>Service providers</h2>
<p>We share data with these processors only to provide the app:
<b>Cloudflare</b> (hosting, storage, logs), <b>Microsoft Azure</b> (speech
synthesis and pronunciation scoring), <b>Google</b> (Gemini content generation;
Google Sign-In identity), and <b>Resend</b> (delivering email login codes).</p>

<h2>How we use data</h2>
<p>To provide and operate app features, sync your content across devices,
generate and validate practice content, and prevent abuse of our services.</p>

<h2>Retention</h2>
<ul>
  <li>Account and synced content: kept until you delete your account.</li>
  <li>Login sessions: expire automatically (within ~90 days); email login codes:
    a few minutes.</li>
  <li>Analytics and server logs: retained for product analysis and operational
    security, then aged out.</li>
</ul>

<h2>Deleting your account and data</h2>
<p>You can delete your account and associated data at any time from the app's
account settings (where available), or by emailing
<a href="mailto:langbangapp@gmail.com">langbangapp@gmail.com</a> from your
account email. Deletion removes your account, sign-in identities, sessions, and
custom content, and anonymizes your analytics records (removing personal
identifiers).</p>

<h2>Advertising and sale of data</h2>
<p>LangBang shows no ads and does not sell your personal data.</p>

<h2>Children</h2>
<p>LangBang is not directed to children under 13, and we do not knowingly
collect personal data from children.</p>

<h2>Changes</h2>
<p>If this policy changes we will update the date above.</p>
`);
}

function termsPage() {
  return legalShell("Terms of Service", "June 29, 2026", `
<p>By using LangBang you agree to these terms.</p>

<h2>What LangBang is</h2>
<p>LangBang is an educational language-learning app for practicing spoken Polish
(and English). It is a study aid, not a certified instruction or translation
service.</p>

<h2>AI-generated content</h2>
<p>LangBang uses AI to generate, complete, and validate practice content.
AI-generated or agent-created content may be inaccurate or incomplete. Review
custom phrases before relying on them in real conversations.</p>

<h2>Pronunciation feedback</h2>
<p>Pronunciation scores are automated educational feedback, not a measurement or
guarantee of fluency or correctness.</p>

<h2>Your content and conduct</h2>
<ul>
  <li>You are responsible for the custom phrases and content you create or submit,
    and for ensuring you have the right to use it.</li>
  <li>Do not submit unlawful, infringing, or abusive content.</li>
  <li>Do not abuse the content-generation, audio, or API services; do not perform
    automated bulk abuse, attempt to exceed or circumvent rate limits or quotas,
    scrape the service, reverse engineer it, or extract credentials.</li>
</ul>

<h2>Accounts</h2>
<p>Keep your sign-in credentials secure. We may suspend or terminate access for
abuse or violations of these terms.</p>

<h2>Third-party services</h2>
<p>The app relies on third-party services (see our
<a href="/privacy">Privacy Policy</a>); your use of the app is also subject to
their terms.</p>

<h2>Availability and updates</h2>
<p>The app and backend are provided on an "as is" and "as available" basis.
Updates are delivered through Google Play. We may change, suspend, or discontinue
features.</p>

<h2>Disclaimers and liability</h2>
<p>To the maximum extent permitted by law, LangBang is provided without
warranties of any kind, and our liability for any claim relating to the app is
limited to the extent permitted by applicable law.</p>

<h2>Contact</h2>
<p>Support, privacy, account deletion, and content questions:
<a href="mailto:langbangapp@gmail.com">langbangapp@gmail.com</a>.</p>
`);
}

function html(content) {
  return new Response(content, {
    headers: {
      "Content-Type": "text/html; charset=utf-8",
      "Cache-Control": "public, max-age=60",
    },
  });
}

function json(value) {
  return new Response(JSON.stringify(value), {
    headers: {
      "Content-Type": "application/json",
      "Cache-Control": "no-store",
    },
  });
}

function appHtmlResponse() {
  if (!APP_HTML_BASE64 || APP_HTML_BASE64.indexOf("__APP_HTML") === 0) {
    return new Response("LangBang app is not built into this deploy yet.", {
      status: 503,
      headers: { "Content-Type": "text/plain; charset=utf-8", "Cache-Control": "no-store" },
    });
  }
  const bytes = Uint8Array.from(atob(APP_HTML_BASE64), (c) => c.charCodeAt(0));
  return new Response(bytes, {
    headers: {
      "Content-Type": "text/html; charset=utf-8",
      "Cache-Control": "no-store",
    },
  });
}

function dataUrlResponse(dataUrl, contentType) {
  const base64 = dataUrl.replace(/^data:[^;]+;base64,/, "");
  const bytes = Uint8Array.from(atob(base64), (c) => c.charCodeAt(0));
  return new Response(bytes, {
    headers: {
      "Content-Type": contentType,
      "Cache-Control": "public, max-age=31536000, immutable",
    },
  });
}
