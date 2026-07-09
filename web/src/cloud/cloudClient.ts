import type {
  AudioManifestResponse,
  AudioPhraseReq,
  AuthResponse,
  CloudBootstrap,
  CloudInstanceSummary,
  CloudUserContentResponse,
  EmailStartResponse,
  SentenceExample,
} from "./models";

// The app talks to the same-origin /v1 proxy on langbang.org (which forwards to the backend
// and aliases instance IDs), so the internal backend name never appears in the app's network
// calls. On langbang.org we use a relative base (same origin); elsewhere (local dev, preview)
// we target the live proxy, which is CORS-enabled.
export const DEFAULT_API_BASE =
  typeof location !== "undefined" && location.hostname.endsWith("langbang.org")
    ? ""
    : "https://langbang.org";

interface ReqOpts {
  method?: "GET" | "POST" | "PUT" | "DELETE";
  body?: unknown;
  token?: string;
}

/** Thin wrapper over the public Worker routes. CORS is `*`, so the SPA calls it cross-origin. */
export class CloudClient {
  constructor(private apiBase: string = DEFAULT_API_BASE) {}

  private async req<T>(path: string, opts: ReqOpts = {}): Promise<T> {
    const headers: Record<string, string> = { Accept: "application/json" };
    if (opts.body !== undefined) headers["Content-Type"] = "application/json";
    if (opts.token) headers["Authorization"] = `Bearer ${opts.token}`;
    const res = await fetch(`${this.apiBase.replace(/\/+$/, "")}${path}`, {
      method: opts.method ?? "GET",
      headers,
      body: opts.body !== undefined ? JSON.stringify(opts.body) : undefined,
    });
    const text = await res.text();
    if (!res.ok) {
      let message = `HTTP ${res.status}`;
      try {
        const parsed = JSON.parse(text) as { error?: string; message?: string };
        message = parsed.error || parsed.message || message;
      } catch {
        if (text) message = text.slice(0, 200);
      }
      throw new Error(message);
    }
    return (text ? JSON.parse(text) : {}) as T;
  }

  // --- Public content ---

  async fetchInstances(): Promise<CloudInstanceSummary[]> {
    const data = await this.req<{ instances?: CloudInstanceSummary[] }>("/v1/instances");
    return data.instances ?? [];
  }

  fetchBootstrap(instanceId: string): Promise<CloudBootstrap> {
    return this.req<CloudBootstrap>(`/v1/instances/${encodeURIComponent(instanceId)}/bootstrap`);
  }

  audioManifest(phrases: AudioPhraseReq[]): Promise<AudioManifestResponse> {
    return this.req<AudioManifestResponse>("/v1/audio/manifest", {
      method: "POST",
      body: { phrases },
    });
  }

  async completePhrase(input: {
    sourceText?: string;
    targetText?: string;
    literalText?: string;
    sourceLanguage: string;
    targetLanguage: string;
  }): Promise<SentenceExample> {
    const resp = await this.req<{
      consistent?: boolean;
      issue?: string;
      source?: string;
      target?: string;
      literal?: string | null;
      words?: SentenceExample["words"];
    }>("/v1/phrases/complete", { method: "POST", body: input });
    if (resp.consistent === false) {
      throw new Error(resp.issue || "The phrase fields are inconsistent.");
    }
    const source = (resp.source ?? "").trim();
    const target = (resp.target ?? "").trim();
    if (!source) throw new Error("The server returned an empty source cue.");
    if (!target) throw new Error("The server returned an empty target answer.");
    return {
      pl: target,
      en: source,
      literal: resp.literal?.trim() || null,
      words: resp.words && resp.words.length > 0 ? resp.words : null,
    };
  }

  // --- Auth ---

  googleAuth(idToken: string, nonce: string, instanceId: string): Promise<AuthResponse> {
    return this.req<AuthResponse>("/v1/auth/google", {
      method: "POST",
      body: { idToken, nonce, instanceId },
    });
  }

  emailStart(email: string): Promise<EmailStartResponse> {
    return this.req<EmailStartResponse>("/v1/auth/email/start", {
      method: "POST",
      body: { email: email.trim() },
    });
  }

  emailVerify(email: string, code: string, instanceId: string): Promise<AuthResponse> {
    return this.req<AuthResponse>("/v1/auth/email/verify", {
      method: "POST",
      body: { email: email.trim(), code: code.trim(), instanceId },
    });
  }

  /** Scoped username+password login for the test/review account (server-gated). */
  passwordLogin(email: string, password: string, instanceId: string): Promise<AuthResponse> {
    return this.req<AuthResponse>("/v1/auth/test-login", {
      method: "POST",
      body: { email: email.trim(), password, instanceId },
    });
  }

  async signOut(token: string): Promise<void> {
    try {
      await this.req("/v1/auth/sign-out", { method: "POST", body: {}, token });
    } catch {
      // Best-effort; clearing the local session is what matters.
    }
  }

  fetchUserContent(token: string, instanceId: string): Promise<CloudUserContentResponse> {
    const encoded = encodeURIComponent(instanceId);
    return this.req<CloudUserContentResponse>(`/v1/me/content?instanceId=${encoded}`, { token });
  }
}

export const cloudClient = new CloudClient();
