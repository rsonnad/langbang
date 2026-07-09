import { useEffect, useState } from "react";
import { clearAudioCache, countCachedAudio, primeAudio } from "../audio/audioCache";
import { signOut, useAuth } from "../auth/useAuth";
import { useStore } from "../lib/store";
import { settingsStore, updateSettings } from "../player/settings";
import { resolveOfflineUrls } from "./audioInventory";
import type { PageProps } from "./types";

type DlState = { phase: "idle" | "working" | "done"; done: number; total: number; cached: number; failed: number };

export function SettingsPage({ bootstrap, vp }: PageProps) {
  const s = useStore(settingsStore);
  const auth = useAuth();
  const cacheSupported = typeof caches !== "undefined";
  const [dl, setDl] = useState<DlState>({ phase: "idle", done: 0, total: 0, cached: 0, failed: 0 });
  const [stored, setStored] = useState<number | null>(null);
  useEffect(() => {
    void countCachedAudio().then(setStored);
  }, []);

  async function downloadOffline() {
    setDl({ phase: "working", done: 0, total: 0, cached: 0, failed: 0 });
    const urls = await resolveOfflineUrls(bootstrap, vp);
    if (urls.length === 0) {
      setDl({ phase: "done", done: 0, total: 0, cached: 0, failed: 0 });
      return;
    }
    const res = await primeAudio(urls, (done, total) =>
      setDl((d) => ({ ...d, phase: "working", done, total })),
    );
    setDl({ phase: "done", done: urls.length, total: urls.length, cached: res.cached, failed: res.failed });
    void countCachedAudio().then(setStored);
  }

  async function clearOffline() {
    await clearAudioCache();
    setDl({ phase: "idle", done: 0, total: 0, cached: 0, failed: 0 });
    setStored(0);
  }

  return (
    <div>
      <div className="section-head">
        <h1>Settings</h1>
        <p>Playback preferences and account.</p>
      </div>

      <div className="card">
        <h2>Playback</h2>
        <Toggle
          label={`Play ${vp.sourceLanguage} cue`}
          desc="Hear the source-language cue before each answer."
          checked={s.sourceCue}
          onChange={(v) => updateSettings({ sourceCue: v })}
        />
        <Toggle
          label={`Slow ${vp.targetLanguage}`}
          desc="Add a slowed re-articulation of the answer."
          checked={s.slowTarget}
          disabled={!vp.slowTargetVoice}
          onChange={(v) => updateSettings({ slowTarget: v })}
        />
        <Toggle
          label="Loop"
          desc="Repeat the queue until you stop."
          checked={s.loop}
          onChange={(v) => updateSettings({ loop: v })}
        />
      </div>

      <div className="card">
        <h2>Offline audio</h2>
        <p className="subtitle">
          Download every lesson clip to this device so playback works with no connection.
          Files are stored in your browser.
        </p>
        {!cacheSupported && <p className="subtitle">This browser can’t store offline audio.</p>}
        <div className="btn-row">
          <button
            className="btn primary"
            onClick={() => void downloadOffline()}
            disabled={dl.phase === "working" || !cacheSupported}
          >
            {dl.phase === "working"
              ? `Downloading… ${dl.total ? Math.round((dl.done / dl.total) * 100) : 0}%`
              : "Download for offline"}
          </button>
          <button
            className="btn"
            onClick={() => void clearOffline()}
            disabled={dl.phase === "working" || !cacheSupported}
          >
            Clear
          </button>
        </div>
        {dl.phase === "done" ? (
          <p className="subtitle">
            {dl.cached > 0
              ? `Saved ${dl.cached} clip${dl.cached === 1 ? "" : "s"} for offline${
                  dl.failed ? ` · ${dl.failed} unavailable` : ""
                }.`
              : "No audio was available to download."}
          </p>
        ) : stored && stored > 0 ? (
          <p className="subtitle">{stored} clip{stored === 1 ? "" : "s"} stored on this device.</p>
        ) : null}
      </div>

      <div className="card">
        <h2>Account</h2>
        <p className="subtitle">{auth.session?.user.email ?? "Signed in"}</p>
        <button className="btn" onClick={() => void signOut()}>
          Sign out
        </button>
      </div>

      <div className="card">
        <h2>About LangBang</h2>
        <p className="subtitle">
          Speak-first language practice. Lessons and audio stream from the LangBang cloud;
          your starred and custom phrases stay with your account.
        </p>
      </div>
    </div>
  );
}

function Toggle({
  label,
  desc,
  checked,
  disabled,
  onChange,
}: {
  label: string;
  desc: string;
  checked: boolean;
  disabled?: boolean;
  onChange: (v: boolean) => void;
}) {
  return (
    <div className="toggle">
      <div className="t-label">
        <b>{label}</b>
        <span>{desc}</span>
      </div>
      <button
        className="switch"
        role="switch"
        aria-checked={checked && !disabled}
        aria-label={label}
        disabled={disabled}
        onClick={() => onChange(!checked)}
      />
    </div>
  );
}
