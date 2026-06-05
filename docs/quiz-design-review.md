# LangBang Quiz Design Review

Purpose: make the current quiz/practice modes explicit, then rationalize them into
one primary practice loop plus a smaller set of specialized drills.

## Current Ratings - 2026-06-05

Scoring uses 1-5 where 5 means the mode strongly helps that dimension. `N/A`
means the dimension is not the mode's job and should not count against it.

Deletion bar:

- Keep as a named quiz if it scores 4+ in at least one core learning dimension
  and is not redundant with a stronger mode.
- Merge or hide if it is mostly recognition, lacks context, or duplicates
  Practice/Reveal.
- Rename out of the quiz taxonomy if it is really pronunciation, listening, or
  passive exposure rather than memory practice.

| Surface | Option | Vocab | Recall | Grammar | Comp. | Feedback | Focus | Verdict |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| Quizzes tab | Practice | 4 | 5 | 4 | 3 | 5 | 4 | Keep as the primary quiz. This is the best learning loop because it has self-grade, miss bursts, success expansion, and auto/manual scope. |
| Quizzes tab | Helper + infinitive | 3 | 5 | 4 | 4 | 5 | 5 | Keep. This is narrow but very useful because it drills high-frequency helper patterns with real infinitives. |
| Quizzes tab | One verb | 2 | 3 | 4 | 1 | 3 | 4 | Keep only as a secondary recognition check. It is useful for contrasting six forms of one verb, but it is not production practice. |
| Quizzes tab | One person, all verbs | 3 | 3 | 4 | 1 | 3 | 4 | Keep as a secondary recognition check. Better than one-verb MC for pattern contrast across verbs. |
| Quizzes tab | Adjectives EN -> PL | 4 | 3 | 1 | 1 | 3 | 2 | Removed from the visible hub on 2026-06-05. Reformulate later as shared lemma/form Practice instead of a standalone MC card. |
| Quizzes tab | Adjectives PL -> EN | 3 | 2 | 1 | 1 | 3 | 2 | Removed from the visible hub on 2026-06-05. Passive recognition is not strong enough as a standalone quiz. |
| Quizzes tab | Adverbs EN -> PL | 3 | 3 | N/A | 1 | 3 | 2 | Removed from the visible hub on 2026-06-05. Sentence Practice is better for adverbs. |
| Quizzes tab | Adverbs PL -> EN | 3 | 2 | N/A | 1 | 3 | 2 | Removed from the visible hub on 2026-06-05. Passive recognition only. |
| Quizzes tab | Pronouns | 2 | 3 | 5 | 1 | 3 | 3 | Keep, but add case filters if it stays visible. It targets a real confusion point. |
| Verbs tab | Conj quiz | 3 | 4 | 4 | 2 | 1 | 4 | Keep as Reveal until Practice fully covers selected-verb form drills. It is hands-free but cannot remediate misses. |
| Verbs tab | Recall quiz | 3 | 4 | 4 | 2 | 1 | 4 | Removed on 2026-06-05. Current code ran the same path as Conj quiz, so the label was redundant. |
| Verbs tab | Sent. quiz | 4 | 4 | 4 | 4 | 1 | 4 | Keep as Reveal. Strong for sentence-level recall and comprehension, weak only on feedback. |
| Verbs/Phrases tab | Sent. quiz | 4 | 4 | 3 | 5 | 1 | 4 | Keep as comprehension Reveal, especially with related adj/adv/noun phrase toggles. |
| Adjectives tab | Recall quiz | 3 | 4 | 5 | 1 | 1 | 3 | Keep until Practice offers equivalent selected-adjective form scope. Strong grammar drill despite no remediation. |
| Adjectives tab | Sent. quiz | 3 | 4 | 3 | 4 | 1 | 3 | Keep only when sentence cache exists; merge into Practice/Reveal later. |
| Adverbs tab | Sent. quiz | 3 | 4 | N/A | 4 | 1 | 3 | Keep only when sentence cache exists; this is better than adverb lemma MC for actual use. |
| Nouns tab | Recall quiz | 3 | 4 | 5 | 1 | 1 | 4 | Keep. This is one of the better grammar Reveal drills because case and number filters exist. |
| Nouns tab | Sent. quiz | 3 | 4 | 3 | 4 | 1 | 4 | Keep when sentence cache exists; merge into shared Practice/Reveal later. |
| Phrases tab | Quiz | 4 | 4 | 2 | 5 | 1 | 4 | Keep as phrase-deck Reveal, especially starred-only. Rename to Reveal/Practice later to reduce taxonomy noise. |
| Pronunciation tab | Flashcard quiz | 2 | 1 | N/A | 2 | 1 | 3 | Keep, but rename to Pronunciation tour. It is not a memory quiz. |
| Phrase detail | Your turn | 1 | 2 | N/A | 2 | 4 | 5 | Keep as Pronunciation assessment, not as quiz. It measures speaking accuracy against a visible phrase. |

Removed in the 2026-06-05 cleanup pass:

1. `Verbs tab -> Recall quiz`.
2. `Quizzes tab -> Adjectives EN -> PL`.
3. `Quizzes tab -> Adjectives PL -> EN`.
4. `Quizzes tab -> Adverbs EN -> PL`.
5. `Quizzes tab -> Adverbs PL -> EN`.

Do not delete yet:

1. Lesson-tab Recall/Sent. Reveal drills for verbs, nouns, adjectives, and
   adverbs. They are not ideal, but they preserve selected-item and sentence-cache
   workflows that Practice does not fully expose yet.
2. Phrase Quiz. It is the strongest comprehension deck and has a useful starred
   personal-deck path.
3. Pronunciation Flashcard and Your turn. They should leave the quiz taxonomy,
   but they should stay in the product.

Process fixes found:

1. This document's older "Current Quiz And Practice Modes" section was stale: it
   did not include the current Quiz-tab Practice and Helper + infinitive cards.
2. The visible app taxonomy still mixes production Practice, recognition MC,
   timed Reveal, pronunciation tour, and speech assessment under quiz-ish labels.
3. Multiple choice modes have better answer audio now than the older notes imply,
   but their learning value is still recognition-heavy.

## Earlier Baseline Review

Bundled content baseline at this review pass: 20 phonemes / 200 example words,
38 verbs, 9 pronouns, 34 lesson-2 phrases, 63 adjectives, 10 adverbs, 3 phrase
groups / 35 sentences, and 34 nouns. Runtime repositories may add user content
on top of those bundled counts.

| Surface | Current label | How it works | Prompt and answer direction | Audio | Text reveal | Scope and breadth | Order | Grammar coverage | Filters today | Self grade | Design notes |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Pronunciation tab | Flashcard | Guided phoneme tour, not really a quiz. Each card auto-plays up to 3 example words, then advances. | Polish phoneme and example words. | Polish example audio only. | Letter, IPA, examples, and hints are visible. | 20 phonemes, 200 bundled examples. | Phonemes shuffled; examples fixed per phoneme. | Pronunciation only. | None. | No. | Keep as "Pronunciation tour", not in the main quiz taxonomy. |
| Verbs tab | Conj | Audio reveal drill over verb forms. English cue is shown/spoken, Polish is hidden, then revealed and spoken. | EN subject+verb form -> PL pronoun+conjugated form. | EN cue, PL answer. | PL hidden during recall window. | Selected verb, or ticked verbs if any are checked. | Ticked verbs shuffle; within each verb, person order is fixed, present before past. | Present/past, person, conjugation. Past is collapsed masculine-sg / virile-pl defaults. | Ticked verbs; per-tense person checkboxes. | No. | Useful, but it is an auto-reveal loop, not a check/X practice quiz. |
| Verbs tab | Recall | Intended as "which form is this?" recall, but current code runs the same EN cue -> hidden PL -> reveal flow as Conj. | Currently EN subject+verb form -> PL pronoun+conjugated form. | EN cue, PL answer. | PL hidden during recall window. | Same as Conj. | Same as Conj. | Same as Conj. | Same as Conj. | No. | This is currently redundant with Conj; either make it true audio-first recognition or remove/rename. |
| Verbs tab | Sent. | Audio reveal quiz over generated verb example sentences. English is shown/spoken, Polish hidden, then revealed and spoken. | EN sentence -> PL sentence. | EN cue, PL answer. | PL hidden during recall window. | Selected verb's cached sentences, or checked verbs' cached/generated sentences. | Quiz queue shuffled, then sorted shortest Polish sentence first. | Person and tense are re-derived from verb forms in sentence text. | Ticked verbs; present/past/person checkboxes. | No. | Closest to useful practice, but lacks self-grade and miss remediation. |
| Adjectives tab | Recall | Audio reveal quiz over selected adjective paradigm. Cue is adjective meaning + gender + case. | EN adjective + gender + case -> PL adjective form. | EN cue, PL answer. | PL hidden during recall window. | Selected adjective only. | Shuffled. | Nominative/accusative across m/f/n/mp/other. | None beyond selected adjective. | No. | Strong grammar drill; should become a target type inside shared Practice. |
| Adjectives tab | Sent. | Audio reveal quiz over selected adjective's cached/generated sentences. | EN sentence -> PL sentence. | EN cue, PL answer. | PL hidden during recall window. | Selected adjective's sentence cache. | Shuffled. | Whatever grammar exists in generated sentence tokens. | Selected adjective only. | No. | No breadth control across adjectives except global Play Phrases All mode. |
| Adverbs tab | Sent. | Audio reveal quiz over selected adverb's cached/generated sentences. | EN sentence -> PL sentence. | EN cue, PL answer. | PL hidden during recall window. | Selected adverb's sentence cache. | Shuffled. | Adverbs are uninflected. | Selected adverb only. | No. | No recall quiz because adverbs have no paradigm; this is fine. |
| Nouns tab | Recall | Audio reveal quiz over selected noun forms. | EN noun cue -> PL noun form. | EN cue, PL answer. | PL hidden during recall window. | Selected noun only; current selected case/number filters decide forms. | Shuffled. | Nominative/accusative/genitive x singular/plural, with gender metadata. | Local case and number picker. | No. | Good grammar drill, but it should share the same filter UX as other practice modes. |
| Nouns tab | Sent. | Audio reveal quiz over selected noun's cached/generated sentences. | EN sentence -> PL sentence. | EN cue, PL answer. | PL hidden during recall window. | Selected noun's sentence cache. | Shuffled. | Whatever grammar exists in generated sentence tokens. | Selected noun only. | No. | Useful, but isolated from the main Play Phrases filters. |
| Phrases tab | Phrase | Audio reveal quiz over a phrase group, optionally limited to starred phrases across all groups. | EN sentence -> PL sentence. | EN cue, PL answer. | PL hidden briefly, then revealed and spoken. | Current group, or starred phrase deck. | Group order for current group; starred deck order follows source ordering. | Full sentence memory; no explicit conjugation/gender/case targeting. | Starred-only checkbox. | No. | Good personal-deck mechanism; should feed the shared Practice scope. |
| Quizzes tab | Verb forms - one verb | Multiple-choice recognition. Pick one verb and tense; each person is a question. | Person cue -> choose PL verb form. | Code currently speaks the answer and an extra sentence on miss, despite comments saying no audio. | Four options; correct option revealed after tap. | One selected verb x selected tense. | Questions shuffled; options shuffled. | Person and tense. | Local verb picker and tense chips only. | Automatic scoring by option choice. | Recognition, not production. Does not use shared practice filters. |
| Quizzes tab | Verb forms - one person, all verbs | Multiple-choice recognition. Pick one person and tense; each verb is a question. | EN verb + person -> choose PL form. | Same answer audio behavior as other multiple-choice modes. | Four options; correct option revealed after tap. | All lesson verbs for chosen person/tense. | Questions shuffled; options shuffled. | Person and tense. | Local person picker and tense chips only. | Automatic scoring by option choice. | Useful contrast drill, but separate from checked-verb and global pronoun filters. |
| Quizzes tab | Adjectives EN -> PL | Multiple-choice vocab recognition. | EN adjective -> choose PL lemma. | Speaks answer; extra practice sentence on miss if found. | Four options. | All adjectives. | Questions shuffled; options shuffled. | Dictionary form only, no gender/case forms. | None. | Automatic scoring. | Fast recognition; should not be the primary practice mode. |
| Quizzes tab | Adjectives PL -> EN | Multiple-choice vocab recognition. | PL adjective lemma -> choose EN meaning. | Speaks English answer; extra Polish practice sentence on miss if found. | Four options. | All adjectives. | Questions shuffled; options shuffled. | Dictionary form only. | None. | Automatic scoring. | Useful passive recognition, not production. |
| Quizzes tab | Adverbs EN <-> PL | Multiple-choice vocab recognition in both directions. | EN <-> PL lemma. | Speaks answer; extra sentence on miss if found. | Four options. | All adverbs. | Questions shuffled; options shuffled. | No inflection. | None. | Automatic scoring. | Fine as a light recognition mode. |
| Quizzes tab | Pronoun case forms | Multiple-choice recognition across pronoun case forms. | EN pronoun + case -> choose PL case form. | Speaks answer; extra sentence on miss is unlikely because bare pronoun forms may not token-match a sentence well. | Four options. | All pronouns x case forms. | Questions shuffled; options shuffled. | Pronoun cases. | None. | Automatic scoring. | Good confusion drill, but should expose case filters if it stays. |
| Lesson-2 phrase detail | Your turn | Speech assessment, not a quiz deck. User records the selected phrase and Azure scores pronunciation. | User says visible PL phrase. | Mic input; no quiz audio sequence. | Phrase remains visible. | Single selected phrase. | Single phrase. | Pronunciation scoring. | None. | Azure score, not green-check self grade. | Keep separate from memory practice. |

## Main Problems

1. The app has too many quiz meanings: multiple-choice recognition, audio reveal,
   speech scoring, and phoneme tour all use quiz-ish labels.
2. The current "most useful" loop is missing: no mode shows a simple prompt, lets
   the learner self-mark with green check / orange X, and branches on a miss.
3. Conj and Recall on the Verbs tab are currently functionally redundant. The
   comments describe different drills, but both branches play EN first, hide PL,
   reveal PL, then speak PL.
4. Quizzes tab modes ignore the shared filter scope. They use local pickers and all
   repo content, rather than the same selection rules as Play Phrases / lesson tabs.
5. Filter UX is split across Settings practice pronouns, verb per-tense checkboxes,
   noun local case/number filters, Play Phrases sheet filters, Now Voicing compact
   toggles, starred phrase filters, and Quizzes tab pickers.
6. Miss handling is shallow. Multiple-choice plays one extra sentence on miss; audio
   reveal drills do not know whether the learner missed, so they cannot remediate.

## Recommended Taxonomy

| Final mode | Purpose | Keep / merge from current app | Feedback style |
| --- | --- | --- | --- |
| Practice | Primary learning loop. Production recall with adaptive repetition. | New engine fed by current verb/noun/adjective/adverb/phrase data. | Self grade: green check = next; orange X = repeat burst. |
| Listen | Passive study / exposure. | Current Play all and Play Phrases behavior. | No score. |
| Reveal | Timed recall without manual grading. | Current Sent. and form reveal drills where useful. | Auto reveal after delay. |
| Multiple choice | Lightweight recognition test. | Current Quizzes tab. | Automatic score. |
| Pronunciation | Mouth/ear work. | Current phoneme tour and Azure phrase assessment. | Audio tour or pronunciation score. |

## Rationalized Word-Type Model

Rationalize across word types by separating the thing being practiced from the
screen it came from. Every practice card should ask for one of four target shapes:

| Target shape | Word types | What learner produces | Core filters | Miss variations |
| --- | --- | --- | --- | --- |
| Lemma | Adverbs, basic adjective/noun/verb vocabulary | The dictionary word or meaning. | Word type, selected/all/starred, direction EN->PL or PL->EN. | Repeat same lemma in short phrases/sentences if examples exist. |
| Inflected form | Verbs, nouns, adjectives, pronouns | The exact Polish form for a grammar cue. | Person/tense for verbs; case/number/gender for nouns/adjectives; case for pronouns. | Same target form in nearby contexts, then adjacent grammar contexts. |
| Sentence | Verbs, nouns, adjectives, adverbs | A whole Polish sentence from an English cue. | Word type inclusion, target words, persons, tense, prepositions, must-contain words. | Same target word/form in four sentence variants. |
| Phrase deck | Fixed phrase groups and starred phrases | A full practical phrase/sentence. | Group, starred-only, selected/all. | Repeat exact phrase, then related phrases from the same group/deck. |

This gives one shared flow:

1. Pick a target shape.
2. Apply the shared scope filters.
3. Generate a queue of `PracticeItem`s.
4. Show cue -> learner self grades -> branch on miss.

Word-type differences become grammar tags on the item, not separate quiz engines:

| Word type | Stable unit | Grammar tags | Best default practice |
| --- | --- | --- | --- |
| Verb | Lemma + person/tense form | person, tense, conjugation class | Inflected form first, then sentence variants. |
| Noun | Lemma + case/number form | gender, case, number | Inflected form first, then sentence/object variants. |
| Adjective | Lemma + case/gender/number form | case, gender, number | Inflected form when learning grammar; lemma for vocabulary recognition. |
| Adverb | Lemma | none | Sentence practice; lemma recognition is secondary. |
| Pronoun | Case form | person/meaning, case | Inflected/case form drill. |
| Phrase | Sentence | group, starred, focus words | Sentence/phrase recall. |
| Phoneme | Sound + examples | letter, IPA | Pronunciation only; keep out of grammar Practice. |

So the clean rule is: if a word type inflects, Practice should default to the
inflected-form card; if it does not inflect, Practice should default to sentence
cards. Multiple choice can still exist for recognition, but it should be generated
from the same target shapes and filters.

## Primary Practice Loop

This should be the default "Practice" quiz.

1. Build a base queue from the selected filters.
2. Show one simple prompt, such as `I want`.
3. The learner answers mentally or aloud, then taps:
   - Green check: I got it right. Move to the next base item.
   - Orange X: I missed it. Enter a correction burst.
4. During a correction burst, keep the same target word or form active. Show the
   exact missed item once, then four variations:
   - Same exact prompt: `I want`
   - Near phrase: `I want to eat`
   - Same person with noun object: `I want a flower`
   - Different selected person: `he wants to eat`
   - Different selected person/object: `you want a fish`
5. After the burst, return to the base queue. Optionally reinsert the original item
   later in the queue so it comes back after spacing.

For the example target `chcieć / chcę`, the prompt cards should be generated from
one target lemma plus safe support words that also satisfy the current filters. If
`he` or `you` is not selected, do not show `he wants` or `you want`. If nouns are
filtered out, use verb-complement variations only.

## Shared Filter UX

Use one `PracticeScope` model and one visual filter component everywhere Practice,
Reveal, Listen, and Multiple Choice need a scope. Keep the established layout rule:
filters on the left, actions on the right; Play first, quiz/practice actions after.

| Filter group | Controls | Applies to | Notes |
| --- | --- | --- | --- |
| Mode | Verb forms / Sentences / Phrases | Practice, Listen, Reveal | This is currently split between Verb/Sent toggles and lesson tabs. |
| Word types | Verbs, Nouns, Adjectives, Adverbs, Phrase deck | Practice, Play Phrases, MC | Adjectives/adverbs should keep Off / Some / All, but label it as breadth. |
| Target words | Selected item, checked items, all in lesson, starred only, must contain | All quiz/practice modes | This replaces one-off selected-only behavior. |
| Persons | ja, ty, on/ona, my, wy, oni/one | Verbs and sentence modes | One source of truth; avoid separate global vs local meanings. |
| Tense | Present, Past | Verbs and sentence modes | Past should show the current limitation: masculine-sg / virile-pl defaults. |
| Noun grammar | Case: Nom / Acc / Gen; Number: sg / pl; Gender: m / f / n | Noun form practice and noun sentence practice | Reuse current noun picker, but mount it inside the shared scope component. |
| Adjective grammar | Case: Nom / Acc; Gender/number: m / f / n / mp / other | Adjective form practice | Current Recall uses all forms; shared filters should let the learner narrow. |
| Sentence features | Prepositions: none / w / na / do / z / o; Words field | Sentence practice and Play Phrases | "None" should be visually explicit: no tracked preposition. |
| Breadth/order | Progressive, Random, Shortest first, Round-robin all | All queue modes | Practice should default to Progressive; tests can use Random. |
| Scope mode | Manual / Auto | Practice first, later Reveal/Listen | Manual means exact filters. Auto starts broad by word type but low-difficulty inside each type, then widens forms and sentence complexity as performance improves. |

## Auto Filter Progression

Add an `Auto` mode for filters so practice can stay simple without forcing the
learner to manually micromanage every grammar dimension.

Auto should not be "random everything". It should start with most word categories
eligible, but constrain the hard parts:

| Stage | Verbs | Nouns | Adjectives | Sentences | When to advance |
| --- | --- | --- | --- | --- | --- |
| 1. Core | 1sg/2sg/3sg, present | singular, accusative/default useful forms | lemma or simple masculine/feminine forms | short, 2-4 words, few/no prepositions | Rolling success is very high. |
| 2. Wider people/forms | add plural persons or past | add nominative/genitive | add nominative/accusative contrast | 4-6 words, one preposition | Stage 1 stays strong after mixed review. |
| 3. Full beginner grid | all selected persons/tenses | sg/pl x nom/acc/gen | m/f/n/mp/other x nom/acc | longer generated sentences | Stage 2 stays strong. |
| 4. Mixed challenge | interleave all enabled forms | interleave all enabled cases/numbers | interleave all enabled agreement targets | phrase deck + generated sentences | Used for review, not first exposure. |

Suggested advancement rule:

- Track success per target shape and word type over a rolling window, e.g. last 20
  self-graded cards.
- If success is around 85-90% or better, widen one dimension.
- If success drops below around 65-70%, narrow one dimension and schedule more
  correction bursts.
- Do not auto-enable a word type or person the user explicitly disabled in Manual
  filters. Auto can widen only inside the user's allowed universe.
- Show the current Auto stage as a small label, e.g. `Auto: Core`, `Auto: Wider`,
  `Auto: Mixed`.

This makes the default UX simple:

1. User chooses `Practice`.
2. Scope is `Auto` by default.
3. They can still open filters and pin hard limits like "only ja/ty/on" or "no
   past tense yet".
4. Auto manages the remaining breadth and difficulty.

## Practice Item Model

```kotlin
data class PracticeItem(
    val id: String,
    val targetKind: TargetKind,
    val prompt: String,
    val answerPl: String,
    val answerEn: String,
    val targetLemma: String?,
    val targetForm: String?,
    val supportTags: Set<String>,
    val grammar: GrammarTags,
    val audioCue: AudioCue,
)
```

Important fields:

- `targetLemma`: keeps the miss burst anchored to the thing that was missed.
- `grammar`: person, tense, case, gender, number, and sentence length.
- `supportTags`: marks whether support words are verbs/nouns/adjectives/adverbs so
  the burst can respect filters.
- `audioCue`: lets Practice decide whether to speak EN cue, PL answer after reveal,
  or no audio.

## Proposed Implementation Order

1. Rename UI concepts first: `Practice`, `Listen`, `Reveal`, `Multiple choice`,
   `Pronunciation`.
2. Extract a shared `PracticeScope` from `RandomConfig`, `PronounFilterStore`, noun
   case/number state, adjective form scope, checked verbs, and starred phrases.
3. Build the self-graded `PracticeQuiz` shell with green check and orange X.
4. Add verb practice generation first, because the `I want -> ja chcę` miss-burst
   flow proves the core model.
5. Add noun/adjective form practice next, then sentence and phrase practice.
6. Reduce redundant modes: either make Verb Recall true audio-first recognition or
   remove it once Practice covers the useful path.

## Decision Points

| Decision | Recommendation |
| --- | --- |
| Should Multiple Choice remain? | Yes, but demote it to recognition testing. It is not the main learning loop. |
| Should audio reveal remain? | Yes, as `Reveal`, especially useful hands-free. |
| Should Practice use automatic correctness? | No. For personal tablet use, self-grade is faster and avoids speech/typing friction. |
| Should missed items repeat exactly four times? | Use five cards after a miss: exact repeat, then four variations. That matches the `I want` example while keeping the burst bounded. |
| Should support words ignore filters? | No by default. Scope should be predictable. Add a later "allow helper words" option only if filters make bursts too sparse. |
| Should order be random? | Practice should start progressive and remedial. Multiple Choice can stay random. Listen can expose random/round-robin/shortest-first. |
