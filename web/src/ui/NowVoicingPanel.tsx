import { useStore } from "../lib/store";
import { nowVoicing, type NowVoicing } from "../player/nowVoicing";
import {
  speechRatingArmed,
  speechRatingPartial,
  speechRatingPhase,
  speechRatingResult,
  speechRatingSupported,
  toggleSpeechRating,
} from "../player/speechRating";
import { studyQueue } from "../player/studyQueue";

const SEG_LABEL: Record<NonNullable<NowVoicing["lang"]>, string> = {
  en: "Source cue",
  pl: "Answer",
  "pl-slow": "Answer · slow",
  pause: "Paused",
};

export function NowVoicingPanel() {
  const nv = useStore(nowVoicing);
  const q = useStore(studyQueue.state);

  return (
    <div className="nv" aria-live="polite">
      <div className="label">
        Now voicing
        {nv?.position && <span className="position">{nv.position}</span>}
      </div>

      {nv ? (
        <>
          <div className="cue">{nv.en || " "}</div>
          <div className={"answer" + (nv.plHidden ? " dim" : "")}>{nv.pl || " "}</div>
          {nv.words && nv.words.length > 0 ? (
            <div className="gloss">
              {nv.words.map((w, i) => (
                <span className="tok" key={`${w.pl}-${i}`}>
                  <span className="t">{w.pl}</span>
                  <span className="g">{w.en}</span>
                </span>
              ))}
            </div>
          ) : nv.literal ? (
            <div className="gloss">
              <span className="g">{nv.literal}</span>
            </div>
          ) : null}
          {nv.lang && <span className="seg">{SEG_LABEL[nv.lang]}</span>}
        </>
      ) : (
        <div className="idle">
          Tap a phrase or press <b>Play</b> — the current line shows here with its
          word-for-word gloss.
        </div>
      )}

      {q.hasQueue && (
        <div className="transport">
          {q.controls.rewind && (
            <button className="btn" onClick={() => studyQueue.rewind()} aria-label="Previous">
              ⏮
            </button>
          )}
          <button className="btn primary" onClick={() => studyQueue.pauseResume()}>
            {q.isPaused ? "▶ Resume" : "⏸ Pause"}
          </button>
          {q.controls.next && (
            <button className="btn" onClick={() => studyQueue.next()} aria-label="Next">
              ⏭
            </button>
          )}
          <button className="btn" onClick={() => studyQueue.stop()} aria-label="Stop">
            ⏹
          </button>
        </div>
      )}

      <SpeechRatingControl />
    </div>
  );
}

/**
 * Fifth control: the sticky speech-rating mic, under the transport buttons. While on, the
 * study queue listens for the user to repeat each phrase after it's voiced and scores it
 * 0–100, then auto-advances. Latches until toggled off.
 */
function SpeechRatingControl() {
  const armed = useStore(speechRatingArmed);
  const phase = useStore(speechRatingPhase);
  const partial = useStore(speechRatingPartial);
  const result = useStore(speechRatingResult);

  return (
    <div className="speech-rating">
      <button
        className={"btn mic" + (armed ? " on" : "")}
        onClick={() => toggleSpeechRating()}
        disabled={!speechRatingSupported}
        aria-pressed={armed}
        title={
          speechRatingSupported
            ? "Rate my speech after each phrase"
            : "Speech rating needs a Chromium browser (Chrome / Edge)"
        }
      >
        🎤 {armed ? "Speech rating: on" : "Rate my speech"}
      </button>

      {(armed || result) && (
        <div className="sr-readout" aria-live="polite">
          {phase === "listening" && (
            <span className="sr-status">
              Listening…{partial && <i> {partial}</i>}
            </span>
          )}
          {phase === "scored" && result && result.error && (
            <span className="sr-error">{result.error}</span>
          )}
          {phase === "scored" && result && !result.error && (
            <>
              <span className={"sr-score " + scoreBand(result.score)}>{result.score}</span>
              <span className="sr-outof">/ 100</span>
              {result.transcribed && <span className="sr-heard">“{result.transcribed}”</span>}
            </>
          )}
          {phase === "idle" && armed && (
            <span className="sr-status">Repeat each phrase to be scored.</span>
          )}
        </div>
      )}
    </div>
  );
}

function scoreBand(score: number): string {
  return score >= 80 ? "good" : score >= 60 ? "ok" : "low";
}
