import { findLesson, type NounEntry, type NounsPayload } from "../cloud/models";
import { playOneOff, type VoiceItem } from "../player/voicing";
import { startQueue } from "./lessonHelpers";
import type { PageProps } from "./types";

const CASES: { key: "nom" | "acc" | "gen"; label: string }[] = [
  { key: "nom", label: "Nominative" },
  { key: "acc", label: "Accusative" },
  { key: "gen", label: "Genitive" },
];
const NUMS = ["sg", "pl"];

export function NounsPage({ bootstrap, vp }: PageProps) {
  const payload = findLesson<NounsPayload>(bootstrap, "nouns");
  const nouns = payload?.nouns ?? [];
  const cfg = bootstrap.audio;

  function nounItems(n: NounEntry): VoiceItem[] {
    const items: VoiceItem[] = [];
    for (const c of CASES) {
      const map = n[c.key];
      for (const num of NUMS) {
        const form = map?.[num];
        if (form) items.push({ en: n.en, pl: form });
      }
    }
    return items;
  }

  return (
    <div>
      <div className="section-head">
        <h1>Nouns</h1>
        <p>{payload?.summary || "Core nouns across nominative, accusative, and genitive — singular and plural."}</p>
      </div>
      <div className="btn-row">
        <button className="btn primary" onClick={() => startQueue(nouns.flatMap(nounItems), vp, { speakSource: false })} disabled={nouns.length === 0}>
          ▶ Play all forms
        </button>
      </div>

      {nouns.map((n) => (
        <div className="card" key={n.lemma}>
          <div className="row" style={{ padding: "2px 4px" }}>
            <div className="grow">
              <span className="pl">{n.lemma}</span> <span className="en">— {n.en}</span>{" "}
              <span className="chip"><b>{n.gender}</b></span>
            </div>
            <button className="btn" onClick={() => startQueue(nounItems(n), vp, { speakSource: false })}>
              ▶ Play
            </button>
          </div>
          {CASES.map((c) => (
            <div style={{ marginTop: 8 }} key={c.key}>
              <div className="en" style={{ marginBottom: 4 }}>{c.label}</div>
              <div className="forms">
                {NUMS.filter((num) => n[c.key]?.[num]).map((num) => (
                  <button
                    className="chip"
                    key={num}
                    onClick={() => void playOneOff(cfg, vp, { en: n.en, pl: n[c.key][num] })}
                  >
                    <b>{num}</b>
                    {n[c.key][num]}
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
