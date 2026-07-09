import { useEffect, useMemo, useState } from "react";
import { LoginGate } from "../auth/LoginGate";
import { signOut, useAuth } from "../auth/useAuth";
import {
  directionLabel,
  voicingFromBootstrap,
  type CloudBootstrap,
  type CloudInstanceSummary,
} from "../cloud/models";
import { loadContent, loadInstances, type ContentSource } from "../data/contentRepository";
import { AdjectivesPage } from "../lessons/AdjectivesPage";
import { AdverbsPage } from "../lessons/AdverbsPage";
import { NounsPage } from "../lessons/NounsPage";
import { PhrasesPage } from "../lessons/PhrasesPage";
import { PronunciationPage } from "../lessons/PronunciationPage";
import { SettingsPage } from "../lessons/SettingsPage";
import { VerbsPage } from "../lessons/VerbsPage";
import type { PageProps } from "../lessons/types";
import { setSpeechRatingLocale } from "../player/speechRating";
import { studyQueue } from "../player/studyQueue";
import { NowVoicingPanel } from "../ui/NowVoicingPanel";

const DEFAULT_INSTANCE = "en-pl";
const INSTANCE_KEY = "langbang.instance.v2";

type TabId = "phrases" | "verbs" | "nouns" | "adjectives" | "adverbs" | "pronunciation" | "settings";

const TABS: { id: TabId; label: string; lessonType?: string }[] = [
  { id: "phrases", label: "Phrases", lessonType: "phrases" },
  { id: "verbs", label: "Verbs", lessonType: "verbs" },
  { id: "nouns", label: "Nouns", lessonType: "nouns" },
  { id: "adjectives", label: "Adjectives", lessonType: "adjectives" },
  { id: "adverbs", label: "Adverbs", lessonType: "adverbs" },
  { id: "pronunciation", label: "Pronunciation", lessonType: "pronunciation" },
  { id: "settings", label: "Settings" },
];

const SOURCE_LABEL: Record<ContentSource, string> = {
  network: "Live",
  cache: "Cached",
  bundled: "Offline copy",
};

export function App() {
  const auth = useAuth();
  const [instanceId, setInstanceId] = useState<string>(
    () => localStorage.getItem(INSTANCE_KEY) || DEFAULT_INSTANCE,
  );

  if (!auth.session) {
    return <LoginGate instanceId={instanceId} />;
  }
  return <StudyApp instanceId={instanceId} onInstanceChange={setInstanceId} />;
}

function StudyApp({
  instanceId,
  onInstanceChange,
}: {
  instanceId: string;
  onInstanceChange: (id: string) => void;
}) {
  const [instances, setInstances] = useState<CloudInstanceSummary[]>([]);
  const [bootstrap, setBootstrap] = useState<CloudBootstrap | null>(null);
  const [source, setSource] = useState<ContentSource>("network");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [tab, setTab] = useState<TabId>("phrases");

  useEffect(() => {
    void loadInstances().then(setInstances);
  }, []);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError(null);
    studyQueue.stop();
    loadContent(instanceId)
      .then((result) => {
        if (cancelled) return;
        setBootstrap(result.bootstrap);
        setSource(result.source);
        studyQueue.setAudioConfig(result.bootstrap.audio);
      })
      .catch((e: unknown) => {
        if (!cancelled) setError(e instanceof Error ? e.message : String(e));
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [instanceId]);

  const vp = useMemo(() => (bootstrap ? voicingFromBootstrap(bootstrap) : null), [bootstrap]);

  // Keep the speech-rating recognizer's language in sync with the active target locale.
  useEffect(() => {
    if (vp) setSpeechRatingLocale(vp.targetLocale);
  }, [vp]);

  function changeInstance(id: string) {
    localStorage.setItem(INSTANCE_KEY, id);
    onInstanceChange(id);
  }

  return (
    <div className="app">
      <header className="topbar">
        <a className="brand" href="/app">
          LangBang<span className="dot" />
        </a>
        <div className="spacer" />
        {instances.length > 1 && (
          <select
            className="select"
            value={instanceId}
            onChange={(e) => changeInstance(e.target.value)}
            aria-label="Learning direction"
          >
            {instances.map((inst) => (
              <option key={inst.id} value={inst.id}>
                {directionLabel(inst.languagePair)}
              </option>
            ))}
          </select>
        )}
        <span className={`status ${source}`} title={`Content: ${SOURCE_LABEL[source]}`}>
          <span className="led" />
          {SOURCE_LABEL[source]}
        </span>
        <button className="linklike" onClick={() => void signOut()}>
          Sign out
        </button>
      </header>

      <nav className="tabs" role="tablist" aria-label="Lessons">
        {TABS.map((t) => (
          <button
            key={t.id}
            className="tab"
            role="tab"
            aria-selected={tab === t.id}
            onClick={() => setTab(t.id)}
          >
            {t.label}
          </button>
        ))}
      </nav>

      <div className="main">
        <main className="content">
          {loading && (
            <div className="center-note">
              <div className="spinner" />
              Loading lessons…
            </div>
          )}
          {!loading && error && (
            <div className="center-note">
              <p>Couldn’t load content: {error}</p>
              <button className="btn" onClick={() => changeInstance(instanceId)}>
                Retry
              </button>
            </div>
          )}
          {!loading && !error && bootstrap && vp && (
            <ActivePage tab={tab} bootstrap={bootstrap} vp={vp} />
          )}
        </main>
        <aside className="side">
          <NowVoicingPanel />
        </aside>
      </div>
    </div>
  );
}

function ActivePage({ tab, bootstrap, vp }: { tab: TabId } & PageProps) {
  switch (tab) {
    case "phrases":
      return <PhrasesPage bootstrap={bootstrap} vp={vp} />;
    case "verbs":
      return <VerbsPage bootstrap={bootstrap} vp={vp} />;
    case "nouns":
      return <NounsPage bootstrap={bootstrap} vp={vp} />;
    case "adjectives":
      return <AdjectivesPage bootstrap={bootstrap} vp={vp} />;
    case "adverbs":
      return <AdverbsPage bootstrap={bootstrap} vp={vp} />;
    case "pronunciation":
      return <PronunciationPage bootstrap={bootstrap} vp={vp} />;
    case "settings":
      return <SettingsPage bootstrap={bootstrap} vp={vp} />;
    default:
      return null;
  }
}
