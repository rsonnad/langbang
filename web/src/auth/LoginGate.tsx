import { useEffect, useRef, useState, type FormEvent } from "react";
import { renderGoogleButton } from "./googleSignIn";
import { emailStart, emailVerify, googleVerify, passwordVerify } from "./useAuth";

function errMessage(e: unknown): string {
  return e instanceof Error ? e.message : String(e);
}

/**
 * The login gate. Email-code sign-in is the guaranteed path (Resend delivery is live);
 * Google sign-in renders the official GIS button. On success the auth store flips and the
 * app mounts — no navigation needed.
 */
export function LoginGate({ instanceId }: { instanceId: string }) {
  const [email, setEmail] = useState("");
  const [code, setCode] = useState("");
  const [password, setPassword] = useState("");
  const [pwMode, setPwMode] = useState(false);
  const [stage, setStage] = useState<"email" | "code">("email");
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState<string | null>(null);
  const [info, setInfo] = useState<string | null>(null);
  const [googleErr, setGoogleErr] = useState<string | null>(null);
  const googleSlot = useRef<HTMLDivElement>(null);

  useEffect(() => {
    let cancelled = false;
    const slot = googleSlot.current;
    if (!slot) return;
    renderGoogleButton(slot, (idToken, nonce) => {
      setBusy(true);
      setErr(null);
      googleVerify(idToken, nonce, instanceId)
        .catch((e) => !cancelled && setErr(errMessage(e)))
        .finally(() => !cancelled && setBusy(false));
    }).catch((e) => {
      if (!cancelled) setGoogleErr(errMessage(e));
    });
    return () => {
      cancelled = true;
    };
  }, [instanceId]);

  async function onSendCode(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setErr(null);
    setInfo(null);
    try {
      const resp = await emailStart(email);
      setStage("code");
      if (resp.devCode) {
        setCode(resp.devCode);
        setInfo(`Dev code prefilled: ${resp.devCode}`);
      } else {
        setInfo(resp.sent ? `We sent a 6-digit code to ${resp.email}.` : "Could not send the email — try again.");
      }
    } catch (e) {
      setErr(errMessage(e));
    } finally {
      setBusy(false);
    }
  }

  async function onVerify(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setErr(null);
    try {
      await emailVerify(email, code, instanceId);
      // success → auth store updates → gate unmounts
    } catch (e) {
      setErr(errMessage(e));
      setBusy(false);
    }
  }

  async function onPasswordLogin(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setErr(null);
    setInfo(null);
    try {
      await passwordVerify(email, password, instanceId);
      // success → auth store updates → gate unmounts
    } catch (e) {
      setErr(errMessage(e));
      setBusy(false);
    }
  }

  return (
    <div className="gate">
      <div className="gate-card">
        <h1>
          LangBang<span className="dot" style={{ display: "inline-block", width: 10, height: 10, borderRadius: "50%", background: "var(--grad)", marginLeft: 6 }} />
        </h1>
        <p className="sub">Speak-first language practice. Sign in to start.</p>

        <div className="gbtn-wrap" ref={googleSlot} />
        {googleErr && (
          <p className="msg" style={{ color: "var(--muted)", fontSize: 12 }}>
            Google sign-in unavailable here — use email below.
          </p>
        )}

        <div className="divider">or use email</div>

        {stage === "email" ? (
          pwMode ? (
            <form onSubmit={onPasswordLogin}>
              <label htmlFor="email">Email address</label>
              <input
                id="email"
                className="input"
                type="email"
                autoComplete="email"
                required
                value={email}
                placeholder="you@example.com"
                onChange={(e) => setEmail(e.target.value)}
              />
              <label htmlFor="password">Password</label>
              <input
                id="password"
                className="input"
                type="password"
                autoComplete="current-password"
                required
                value={password}
                placeholder="••••••••"
                onChange={(e) => setPassword(e.target.value)}
              />
              <button className="btn primary" type="submit" disabled={busy || !email || !password}>
                {busy ? "Signing in…" : "Sign in"}
              </button>
              <button
                type="button"
                className="linklike"
                style={{ marginTop: 10, width: "100%" }}
                onClick={() => {
                  setPwMode(false);
                  setPassword("");
                  setErr(null);
                  setInfo(null);
                }}
              >
                Use a sign-in code instead
              </button>
            </form>
          ) : (
            <form onSubmit={onSendCode}>
              <label htmlFor="email">Email address</label>
              <input
                id="email"
                className="input"
                type="email"
                autoComplete="email"
                required
                value={email}
                placeholder="you@example.com"
                onChange={(e) => setEmail(e.target.value)}
              />
              <button className="btn primary" type="submit" disabled={busy || !email}>
                {busy ? "Sending…" : "Send sign-in code"}
              </button>
              <button
                type="button"
                className="linklike"
                style={{ marginTop: 10, width: "100%" }}
                onClick={() => {
                  setPwMode(true);
                  setErr(null);
                  setInfo(null);
                }}
              >
                Sign in with a password
              </button>
            </form>
          )
        ) : (
          <form onSubmit={onVerify}>
            <label htmlFor="code">6-digit code</label>
            <input
              id="code"
              className="input"
              inputMode="numeric"
              autoComplete="one-time-code"
              required
              value={code}
              placeholder="123456"
              onChange={(e) => setCode(e.target.value.replace(/\D/g, "").slice(0, 6))}
            />
            <button className="btn primary" type="submit" disabled={busy || code.length < 6}>
              {busy ? "Verifying…" : "Verify & continue"}
            </button>
            <button
              type="button"
              className="linklike"
              style={{ marginTop: 10, width: "100%" }}
              onClick={() => {
                setStage("email");
                setCode("");
                setInfo(null);
                setErr(null);
              }}
            >
              Use a different email
            </button>
          </form>
        )}

        {info && <p className="msg ok">{info}</p>}
        {err && <p className="msg err">{err}</p>}

        <p className="fineprint">
          Your sign-in keeps your starred and custom phrases with you across devices.
        </p>
      </div>
    </div>
  );
}
