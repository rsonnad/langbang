import { findLesson, type AdverbsPayload } from "../cloud/models";
import { playOneOff } from "../player/voicing";
import { startQueue } from "./lessonHelpers";
import type { PageProps } from "./types";

export function AdverbsPage({ bootstrap, vp }: PageProps) {
  const payload = findLesson<AdverbsPayload>(bootstrap, "adverbs");
  const adverbs = payload?.adverbs ?? [];
  const cfg = bootstrap.audio;
  const items = adverbs.map((a) => ({ en: a.en, pl: a.lemma }));

  return (
    <div>
      <div className="section-head">
        <h1>Adverbs</h1>
        <p>{payload?.summary || "Common adverbs. Tap one to hear it, or play them all."}</p>
      </div>
      <div className="btn-row">
        <button className="btn primary" onClick={() => startQueue(items, vp, { speakSource: false })} disabled={items.length === 0}>
          ▶ Play all
        </button>
      </div>

      <div className="card">
        {adverbs.map((a) => (
          <div className="row" key={a.lemma}>
            <button className="btn play" onClick={() => void playOneOff(cfg, vp, { en: a.en, pl: a.lemma })} aria-label="Play adverb">
              ▶
            </button>
            <div className="grow">
              <div className="pl">{a.lemma}</div>
              <div className="en">{a.en}</div>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
