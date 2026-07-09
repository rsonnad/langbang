import { findLesson, type PhonemeEntry, type PronunciationPayload } from "../cloud/models";
import { playOneOff } from "../player/voicing";
import { startQueue } from "./lessonHelpers";
import type { PageProps } from "./types";

export function PronunciationPage({ bootstrap, vp }: PageProps) {
  const payload = findLesson<PronunciationPayload>(bootstrap, "pronunciation");
  const phonemes = payload?.phonemes ?? [];
  const cfg = bootstrap.audio;

  const playExamples = (ph: PhonemeEntry) =>
    startQueue(ph.examples.map((e) => ({ en: e.en, pl: e.pl })), vp, { speakSource: false });

  return (
    <div>
      <div className="section-head">
        <h1>Pronunciation</h1>
        <p>{payload?.summary || "The sounds, with example words you can play one at a time."}</p>
      </div>

      {phonemes.map((ph) => (
        <div className="card" key={ph.letter}>
          <div className="row" style={{ padding: "2px 4px" }}>
            <div className="grow">
              <span className="pl" style={{ fontSize: 24 }}>{ph.letter}</span>{" "}
              <span className="en">{ph.ipa} · {ph.name}</span>
              <div className="en">{ph.englishApproximation}</div>
            </div>
            <button className="btn" onClick={() => playExamples(ph)} disabled={ph.examples.length === 0}>
              ▶ Examples
            </button>
          </div>
          {ph.description && <p className="subtitle" style={{ marginTop: 8 }}>{ph.description}</p>}
          <div className="forms">
            {ph.examples.map((e, i) => (
              <button className="chip" key={`${e.pl}-${i}`} onClick={() => void playOneOff(cfg, vp, { en: e.en, pl: e.pl })}>
                {e.pl} <span className="en">· {e.en}</span>
              </button>
            ))}
          </div>
        </div>
      ))}
    </div>
  );
}
