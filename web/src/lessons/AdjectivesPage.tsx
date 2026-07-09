import { findLesson, type AdjectiveEntry, type AdjectivesPayload } from "../cloud/models";
import { playOneOff, type VoiceItem } from "../player/voicing";
import { startQueue } from "./lessonHelpers";
import type { PageProps } from "./types";

const CASES: { key: "nom" | "acc"; label: string }[] = [
  { key: "nom", label: "Nominative" },
  { key: "acc", label: "Accusative" },
];
const GENDERS = ["m", "f", "n", "mp", "other"];

export function AdjectivesPage({ bootstrap, vp }: PageProps) {
  const payload = findLesson<AdjectivesPayload>(bootstrap, "adjectives");
  const adjectives = payload?.adjectives ?? [];
  const cfg = bootstrap.audio;

  function items(a: AdjectiveEntry): VoiceItem[] {
    const out: VoiceItem[] = [];
    for (const c of CASES) {
      const map = a[c.key];
      for (const g of GENDERS) {
        const form = map?.[g];
        if (form) out.push({ en: a.en, pl: form });
      }
    }
    return out;
  }

  return (
    <div>
      <div className="section-head">
        <h1>Adjectives</h1>
        <p>{payload?.summary || "Adjectives across gender and case. Tap a form to hear it."}</p>
      </div>
      <div className="btn-row">
        <button className="btn primary" onClick={() => startQueue(adjectives.flatMap(items), vp, { speakSource: false })} disabled={adjectives.length === 0}>
          ▶ Play all forms
        </button>
      </div>

      {adjectives.map((a) => (
        <div className="card" key={a.lemma}>
          <div className="row" style={{ padding: "2px 4px" }}>
            <div className="grow">
              <span className="pl">{a.lemma}</span> <span className="en">— {a.en}</span>
            </div>
            <button className="btn" onClick={() => startQueue(items(a), vp, { speakSource: false })}>
              ▶ Play
            </button>
          </div>
          {CASES.map((c) => (
            <div style={{ marginTop: 8 }} key={c.key}>
              <div className="en" style={{ marginBottom: 4 }}>{c.label}</div>
              <div className="forms">
                {GENDERS.filter((g) => a[c.key]?.[g]).map((g) => (
                  <button
                    className="chip"
                    key={g}
                    onClick={() => void playOneOff(cfg, vp, { en: a.en, pl: a[c.key][g] })}
                  >
                    <b>{g}</b>
                    {a[c.key][g]}
                  </button>
                ))}
              </div>
            </div>
          ))}
        </div>
      ))}
    </div>
  );
}
