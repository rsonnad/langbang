import { findLesson, type VerbEntry, type VerbsPayload } from "../cloud/models";
import { playOneOff, type VoiceItem } from "../player/voicing";
import { PERSON_KEYS, startQueue, targetSubject } from "./lessonHelpers";
import type { PageProps } from "./types";

export function VerbsPage({ bootstrap, vp }: PageProps) {
  const payload = findLesson<VerbsPayload>(bootstrap, "verbs");
  const verbs = payload?.verbs ?? [];
  const cfg = bootstrap.audio;

  function formItems(v: VerbEntry, forms: Record<string, string> | null | undefined): VoiceItem[] {
    if (!forms) return [];
    return PERSON_KEYS.filter((k) => forms[k]).map((k) => ({
      en: `${k} · ${v.en}`,
      pl: `${targetSubject(vp, k)} ${forms[k]}`.trim(),
    }));
  }

  function playVerb(v: VerbEntry) {
    startQueue([...formItems(v, v.forms), ...formItems(v, v.past_forms)], vp, { speakSource: false });
  }

  function playAll() {
    const items = verbs.flatMap((v) => formItems(v, v.forms));
    startQueue(items, vp, { speakSource: false });
  }

  return (
    <div>
      <div className="section-head">
        <h1>Verbs</h1>
        <p>{payload?.summary || "Core verbs across person and tense. Tap any form to hear it."}</p>
      </div>
      <div className="btn-row">
        <button className="btn primary" onClick={playAll} disabled={verbs.length === 0}>
          ▶ Play all present forms
        </button>
      </div>

      {verbs.map((v) => (
        <div className="card" key={v.lemma}>
          <div className="row" style={{ padding: "2px 4px" }}>
            <div className="grow">
              <span className="pl">{v.lemma}</span> <span className="en">— {v.en}</span>
            </div>
            <button className="btn" onClick={() => playVerb(v)}>
              ▶ Play
            </button>
          </div>
          <FormChips
            forms={v.forms}
            label="Present"
            onTap={(k, form) => void playOneOff(cfg, vp, { en: `${k} · ${v.en}`, pl: `${targetSubject(vp, k)} ${form}`.trim() })}
            subject={(k) => targetSubject(vp, k)}
          />
          {v.past_forms && (
            <FormChips
              forms={v.past_forms}
              label="Past"
              onTap={(k, form) => void playOneOff(cfg, vp, { en: `${k} · ${v.en}`, pl: `${targetSubject(vp, k)} ${form}`.trim() })}
              subject={(k) => targetSubject(vp, k)}
            />
          )}
        </div>
      ))}
    </div>
  );
}

function FormChips({
  forms,
  label,
  subject,
  onTap,
}: {
  forms: Record<string, string>;
  label: string;
  subject: (k: string) => string;
  onTap: (k: string, form: string) => void;
}) {
  return (
    <div style={{ marginTop: 8 }}>
      <div className="en" style={{ marginBottom: 4 }}>{label}</div>
      <div className="forms">
        {PERSON_KEYS.filter((k) => forms[k]).map((k) => (
          <button className="chip" key={k} onClick={() => onTap(k, forms[k])}>
            <b>{k}</b>
            {subject(k)} {forms[k]}
          </button>
        ))}
      </div>
    </div>
  );
}
