import { useEffect, useState, type FormEvent } from "react";
import { useAuth } from "../auth/useAuth";
import { cloudClient } from "../cloud/cloudClient";
import { findLesson, type PhraseGroup, type PhrasesPayload, type SentenceExample } from "../cloud/models";
import { deleteCustomGroup, listCustomGroups, saveCustomGroup } from "../data/localStore";
import { itemFromSentence } from "../player/voicing";
import { startQueue } from "./lessonHelpers";
import type { PageProps } from "./types";

export function PhrasesPage({ bootstrap, vp }: PageProps) {
  const auth = useAuth();
  const payload = findLesson<PhrasesPayload>(bootstrap, "phrases");
  const instanceId = bootstrap.instance.id;
  const sessionToken = auth.session?.token || "";
  const [remote, setRemote] = useState<PhraseGroup[]>([]);
  const [custom, setCustom] = useState<PhraseGroup[]>([]);
  const [showForm, setShowForm] = useState(false);

  useEffect(() => {
    void refreshCustomGroups(instanceId, sessionToken, setRemote, setCustom);
  }, [instanceId, sessionToken]);

  const builtIn = payload?.groups ?? [];
  const groups = [...builtIn, ...remote, ...custom.filter((group) => !remote.some((r) => r.id === group.id))];
  const customIds = new Set(custom.map((g) => g.id));

  const playGroup = (g: PhraseGroup) => startQueue(g.sentences.map((s) => itemFromSentence(s)), vp);
  const playOne = (s: SentenceExample) => startQueue([itemFromSentence(s)], vp);
  const playAll = () => startQueue(groups.flatMap((g) => g.sentences.map((s) => itemFromSentence(s))), vp);

  async function refresh() {
    await refreshCustomGroups(instanceId, sessionToken, setRemote, setCustom);
  }

  return (
    <div>
      <div className="section-head">
        <h1>Phrases</h1>
        <p>{payload?.summary || "Real-world phrase groups — played as a monologue, cue then answer."}</p>
      </div>

      <div className="btn-row">
        <button className="btn primary" onClick={playAll} disabled={groups.length === 0}>
          ▶ Play all
        </button>
        <button className="btn" onClick={() => setShowForm((v) => !v)}>
          {showForm ? "Close" : "＋ Custom phrase"}
        </button>
      </div>

      {showForm && (
        <CustomPhraseForm
          instanceId={instanceId}
          onSaved={async () => {
            await refresh();
            setShowForm(false);
          }}
        />
      )}

      {groups.map((g) => (
        <div className="card" key={g.id}>
          <h2>{g.title}</h2>
          {g.subtitle && <p className="subtitle">{g.subtitle}</p>}
          <div className="btn-row">
            <button className="btn" onClick={() => playGroup(g)}>
              ▶ Play group
            </button>
            {customIds.has(g.id) && (
              <button
                className="btn"
                onClick={async () => {
                  await deleteCustomGroup(instanceId, g.id);
                  await refresh();
                }}
              >
                Delete
              </button>
            )}
          </div>
          {g.sentences.map((s, i) => (
            <div className="row" key={`${g.id}-${i}`}>
              <button className="btn play" onClick={() => playOne(s)} aria-label="Play phrase">
                ▶
              </button>
              <div className="grow">
                <div className="pl">{s.pl}</div>
                <div className="en">{s.en}</div>
                {s.literal && <div className="en" style={{ fontStyle: "italic" }}>{s.literal}</div>}
              </div>
            </div>
          ))}
        </div>
      ))}
    </div>
  );
}

async function refreshCustomGroups(
  instanceId: string,
  sessionToken: string,
  setRemote: (groups: PhraseGroup[]) => void,
  setCustom: (groups: PhraseGroup[]) => void,
): Promise<void> {
  const localPromise = listCustomGroups(instanceId);
  const remotePromise = sessionToken
    ? cloudClient.fetchUserContent(sessionToken, instanceId).then((content) => content.groups).catch(() => [])
    : Promise.resolve([]);
  const [local, remote] = await Promise.all([localPromise, remotePromise]);
  setCustom(local);
  setRemote(remote);
}

function CustomPhraseForm({
  instanceId,
  onSaved,
}: {
  instanceId: string;
  onSaved: () => void | Promise<void>;
}) {
  const [title, setTitle] = useState("");
  const [source, setSource] = useState("");
  const [target, setTarget] = useState("");
  const [literal, setLiteral] = useState("");

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    const group: PhraseGroup = {
      id: `custom-${crypto.randomUUID()}`,
      title: title.trim() || "Custom phrase",
      subtitle: "Your phrase",
      sentences: [
        { en: source.trim(), pl: target.trim(), literal: literal.trim() || null },
      ],
    };
    await saveCustomGroup(instanceId, group);
    await onSaved();
  }

  return (
    <form className="card" onSubmit={onSubmit}>
      <h2>New custom phrase</h2>
      <label>Group title</label>
      <input className="input" value={title} onChange={(e) => setTitle(e.target.value)} placeholder="My phrases" />
      <label style={{ marginTop: 10 }}>Source cue</label>
      <input className="input" value={source} onChange={(e) => setSource(e.target.value)} placeholder="What you'd say in your language" required />
      <label style={{ marginTop: 10 }}>Target answer</label>
      <input className="input" value={target} onChange={(e) => setTarget(e.target.value)} placeholder="The phrase you're learning" required />
      <label style={{ marginTop: 10 }}>Word-for-word (optional)</label>
      <input className="input" value={literal} onChange={(e) => setLiteral(e.target.value)} />
      <button className="btn primary" type="submit" disabled={!source.trim() || !target.trim()}>
        Save phrase
      </button>
    </form>
  );
}
