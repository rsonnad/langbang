import type { AuthResponse, AuthUser } from "./models";

// The Worker issues opaque `lb_…` session tokens validated server-side (D1 lookup),
// 90-day TTL. A SPA calling a cross-origin Worker can't use an httpOnly cookie set by
// that Worker, so we persist in localStorage and send it as a Bearer header. Standard
// SPA tradeoff; the token only grants this user's own content scope.
const KEY = "langbang.session.v1";

export interface StoredSession {
  user: AuthUser;
  token: string;
  expiresAt: string;
}

export function loadSession(): StoredSession | null {
  try {
    const raw = localStorage.getItem(KEY);
    if (!raw) return null;
    const parsed = JSON.parse(raw) as StoredSession;
    if (!parsed.token || !parsed.user?.id) return null;
    if (parsed.expiresAt) {
      const exp = Date.parse(parsed.expiresAt);
      if (Number.isFinite(exp) && exp <= Date.now()) {
        clearSession();
        return null;
      }
    }
    return parsed;
  } catch {
    return null;
  }
}

export function saveSession(resp: AuthResponse): StoredSession {
  const stored: StoredSession = {
    user: resp.user,
    token: resp.session.token,
    expiresAt: resp.session.expiresAt,
  };
  localStorage.setItem(KEY, JSON.stringify(stored));
  return stored;
}

export function clearSession(): void {
  localStorage.removeItem(KEY);
}
