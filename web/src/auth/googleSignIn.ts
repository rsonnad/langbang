// Google Identity Services (web). The Worker already trusts this client ID via its
// GOOGLE_WEB_CLIENT_ID var, so no server change is needed — but the deploy origin
// (langbang.org) and any localhost dev origin must be listed as Authorized JavaScript
// origins on this OAuth client in the Google Cloud console for the token to issue.
export const GOOGLE_WEB_CLIENT_ID =
  "385515827732-9r056blngf9vpv4r2ersiiv8lte5trvb.apps.googleusercontent.com";

const GIS_SRC = "https://accounts.google.com/gsi/client";

interface GoogleCredentialResponse {
  credential: string;
}

interface GoogleIdApi {
  initialize(config: {
    client_id: string;
    callback: (response: GoogleCredentialResponse) => void;
    nonce?: string;
    auto_select?: boolean;
    use_fedcm_for_prompt?: boolean;
  }): void;
  renderButton(
    parent: HTMLElement,
    options: {
      theme?: string;
      size?: string;
      width?: number;
      text?: string;
      shape?: string;
      logo_alignment?: string;
    },
  ): void;
}

function gis(): GoogleIdApi | null {
  const g = (window as unknown as { google?: { accounts?: { id?: GoogleIdApi } } }).google;
  return g?.accounts?.id ?? null;
}

let loader: Promise<void> | null = null;

function loadGis(): Promise<void> {
  if (gis()) return Promise.resolve();
  if (loader) return loader;
  loader = new Promise<void>((resolve, reject) => {
    const existing = document.querySelector(`script[src="${GIS_SRC}"]`);
    if (existing) {
      existing.addEventListener("load", () => resolve());
      existing.addEventListener("error", () => reject(new Error("Failed to load Google sign-in.")));
      return;
    }
    const script = document.createElement("script");
    script.src = GIS_SRC;
    script.async = true;
    script.defer = true;
    script.onload = () => resolve();
    script.onerror = () => reject(new Error("Failed to load Google sign-in."));
    document.head.appendChild(script);
  });
  return loader;
}

function randomNonce(): string {
  const bytes = new Uint8Array(24);
  crypto.getRandomValues(bytes);
  return btoa(String.fromCharCode(...bytes))
    .replace(/\+/g, "-")
    .replace(/\//g, "_")
    .replace(/=+$/, "");
}

/** Render the official Google button into `el`; invokes onCredential(idToken, nonce) on success. */
export async function renderGoogleButton(
  el: HTMLElement,
  onCredential: (idToken: string, nonce: string) => void,
): Promise<void> {
  await loadGis();
  const api = gis();
  if (!api) throw new Error("Google sign-in is unavailable.");
  const nonce = randomNonce();
  api.initialize({
    client_id: GOOGLE_WEB_CLIENT_ID,
    nonce,
    use_fedcm_for_prompt: true,
    callback: (response) => {
      if (response?.credential) onCredential(response.credential, nonce);
    },
  });
  api.renderButton(el, {
    theme: "outline",
    size: "large",
    width: 300,
    text: "continue_with",
    shape: "pill",
    logo_alignment: "left",
  });
}
