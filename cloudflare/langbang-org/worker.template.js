const LOGO_SQUARE = "__LOGO_SQUARE_DATA_URL__";
const LOGO_WORDMARK = "__LOGO_WORDMARK_DATA_URL__";
const INSTALL_QR = "__INSTALL_QR_DATA_URL__";
const SITE_VERSION = "site-v2";

const PUBLIC_R2_BASE = "https://pub-5bfcb836ff7946b785556c2d8131cba5.r2.dev";
const API_BASE = "https://langbangml-api.langbangml.workers.dev";
const ADMIN_APP_URL = `${API_BASE}/admin/analytics`;
const CHANNELS = [
  {
    id: "en-pl",
    tab: "English to Polish",
    title: "English speakers learning Polish",
    instanceId: "langbangml-en-pl",
    latestApk: `${PUBLIC_R2_BASE}/langbang/builds/en-pl/langbangml-en-pl-latest.apk`,
    manifest: `${PUBLIC_R2_BASE}/langbang/builds/en-pl/latest.json`,
  },
  {
    id: "pl-en",
    tab: "Polish to English",
    title: "Polish speakers learning English",
    instanceId: "langbangml-pl-en",
    latestApk: `${PUBLIC_R2_BASE}/langbang/builds/pl-en/langbangml-pl-en-latest.apk`,
    manifest: `${PUBLIC_R2_BASE}/langbang/builds/pl-en/latest.json`,
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
      <p>This channel boots into <code>${channel.instanceId}</code> and updates from its own manifest, so it stays separate from the other learning direction.</p>
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
    <p class="summary">Choose the learning direction. Each tab is a separate APK channel with its own app package, latest APK, pinned APK, and update manifest.</p>
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

addEventListener("fetch", (event) => {
  event.respondWith(handleRequest(event.request));
});

async function handleRequest(request) {
  const url = new URL(request.url);
  const path = url.pathname.replace(/\/+$/, "") || "/";
  if (path === "/health") {
    return json({ ok: true, service: "langbang-site" });
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
  if (path === "/admin" || path === "/admin/analytics") {
    return await adminPage();
  }
  if (path === "/v1/admin/analytics/summary" || path === "/v1/admin/analytics/events") {
    return await proxyAdminApi(request, path, url.search);
  }
  return html(homePage);
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
