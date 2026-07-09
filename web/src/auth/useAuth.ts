import { cloudClient } from "../cloud/cloudClient";
import type { AuthResponse, EmailStartResponse } from "../cloud/models";
import { clearSession, loadSession, saveSession, type StoredSession } from "../cloud/session";
import { Store, useStore } from "../lib/store";

interface AuthState {
  session: StoredSession | null;
}

const authStore = new Store<AuthState>({ session: loadSession() });

export function useAuth(): AuthState {
  return useStore(authStore);
}

export function currentToken(): string | null {
  return authStore.get().session?.token ?? null;
}

function applyAuth(resp: AuthResponse): void {
  authStore.set({ session: saveSession(resp) });
}

export function emailStart(email: string): Promise<EmailStartResponse> {
  return cloudClient.emailStart(email);
}

export async function emailVerify(email: string, code: string, instanceId: string): Promise<void> {
  applyAuth(await cloudClient.emailVerify(email, code, instanceId));
}

export async function passwordVerify(email: string, password: string, instanceId: string): Promise<void> {
  applyAuth(await cloudClient.passwordLogin(email, password, instanceId));
}

export async function googleVerify(idToken: string, nonce: string, instanceId: string): Promise<void> {
  applyAuth(await cloudClient.googleAuth(idToken, nonce, instanceId));
}

export async function signOut(): Promise<void> {
  const token = authStore.get().session?.token;
  if (token) await cloudClient.signOut(token);
  clearSession();
  authStore.set({ session: null });
}
