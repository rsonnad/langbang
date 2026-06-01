# LangBang Quiz Design Review

Purpose: make the current quiz/practice modes explicit, then rationalize them into
one primary practice loop plus a smaller set of specialized drills.

## Current Quiz And Practice Modes

Bundled content baseline: 20 phonemes / 200 example words, 38 verbs, 9
pronouns, 34 lesson-2 phrases, 56 adjectives, 10 adverbs, 2 phrase groups / 21
sentences, and 30 nouns. Runtime repositories may add user content on top of
those bundled counts.

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
