# Google Play Listing Copy

Preferred marketing title:

```text
Speak Polish - LangBang AI Method
```

Character count: 33. Use this phrase in screenshots, app artwork, website copy,
and release messaging. Google Play's app-name field is capped at 30 characters,
so the Play Console app-name field needs the compliant version below.

## Positioning

LangBang should be positioned as a speaking-first Polish practice app. The
claim should not be "better than other apps" or "fast fluency." The claim should
be concrete:

- Speaking-first, not worksheet-first.
- Optimized for Polish sounds, spelling, syllables, and phrase rhythm.
- Built around hearing Polish and saying it back.
- Uses audio, pronunciation feedback, and AI-assisted phrase customization.
- Lets learners create personal phrase sets instead of staying inside a fixed
  flashcard deck.

## Store Field Drafts

### App Name

Google Play compliant app name:

```text
Speak Polish - LangBang AI
```

Character count: 26.

Preferred full display phrase outside the Play app-name field:

```text
Speak Polish - LangBang AI Method
```

Character count: 33.

### Short Description

Recommended:

```text
Speaking-first Polish with audio, syllables, and AI-customized content.
```

Character count: 71.

Alternate if Play review or user testing says "AI-customized" reads too vague:

```text
Speaking-first Polish with pronunciation, audio, syllables, and AI content.
```

Character count: 75.

### Full Description

Use this full description for Play only after the user-agent API path has passed
the release gate in the AI/API section below. Until then, remove the AI coding
agent sentence and keep the manual custom phrase wording.

```text
LangBang is a speaking-first Polish practice app built for hearing Polish,
saying it back, and repeating useful phrases until they stick.

Most language apps are built around reading, tapping, spelling, or passive
comprehension. LangBang focuses on the spoken side of Polish: audio, phrase
rhythm, pronunciation, syllables, and repeatable speaking practice.

Practice Polish with:
- Polish and English audio playback
- Slow Polish audio for difficult words and phrases
- Polish-specific pronunciation lessons
- Syllable cues that make long Polish words easier to say
- Tap-to-hear word practice inside phrase playback
- Pronunciation scoring and feedback for spoken practice
- Common phrases, verbs, numbers, adjectives, adverbs, and nouns
- Custom phrase groups you can build manually around your own life
- Support for custom content from your own AI coding agent
- AI-assisted phrase completion and validation

LangBang is built for learners who want to speak Polish out loud, not just
recognize words on a screen. Use it to practice pronunciation, listening,
speaking, phrase memory, and useful spoken Polish.

You can customize LangBang by adding your own phrase groups directly in the app.
If you use your favorite AI coding agent, you can also use LangBang's APIs to
help build and sync custom practice content for your account.

The app can help complete and validate custom phrases using AI. AI-generated or
agent-created language content may be imperfect, so review custom phrases before
relying on them in real conversations.
```

## Screenshot Captions

Use screenshots that prove the positioning. Do not spend early screenshots on
settings, accounts, or generic app chrome.

1. `Speak Polish out loud`
2. `Hear it slowly, then naturally`
3. `Polish syllable cues`
4. `Pronunciation feedback`
5. `Practice phrases, not worksheets`
6. `Build custom phrase sets`
7. `Customize with your AI agent`

## Feature Claim Audit

| Claim | Supported by current project | Store-safe wording |
| --- | --- | --- |
| Speaking-first Polish practice | Phrase playback, Now Voicing, pronunciation screens, spoken phrase detail | "speaking-first Polish practice" |
| Polish pronunciation help | `lesson-01.json` is a Polish pronunciation lesson; `AzurePronunciationClient` scores speech | "Polish-specific pronunciation lessons" and "pronunciation scoring and feedback" |
| Syllable help | `PolishSyllables.kt` provides a Polish syllabifier for visual pronunciation support | "syllable cues" |
| Audio-first repetition | Now Voicing shared playback and phrase queues support Polish/English and slow Polish playback | "Polish and English audio playback" and "slow Polish audio" |
| AI intelligence | `GeminiClient`, `/v1/gemini/generate`, and `/v1/phrases/complete` support AI-backed generation and phrase completion | "AI-assisted phrase completion and validation" |
| Manual customization | Phrase group and phrase creation flows exist; signed-in phrase sync stores user phrase groups | "custom phrase groups you can build manually" |
| Agent/API customization | Cloudflare APIs include user agent token and agent phrase/word routes in the current worktree; verify they ship in the Play-target release | "support for custom content from your own AI coding agent" after release verification |

## AI And API Copy Gate

Current safe Play copy:

```text
Custom phrase groups you can build manually around your own life
```

Current safe longer wording:

```text
You can customize LangBang by adding your own phrase groups directly in the app.
```

Use this as production Play copy only after the user-agent API path is included
in the Play-target release, documented, abuse-controlled, privacy-disclosed, and
reviewer-accessible:

```text
If you use your favorite AI coding agent, you can also use LangBang's APIs to
help build and sync custom practice content for your account.
```

That claim becomes acceptable only after all of these are true:

- The public or authenticated API path is included in the Play-target release.
- Documentation exists for what an agent can customize.
- Abuse controls, quotas, and auth are in place.
- The reviewer can exercise or verify the flow.
- Privacy policy, Data safety, and AI-generated content disclosures match the
  shipped behavior.

Short future wording after that gate is complete:

```text
Support for custom content from your own AI coding agent.
```

## Keyword Map

Primary keywords to use naturally:

- speak Polish
- Polish speaking practice
- Polish pronunciation
- Polish audio practice
- Polish syllables
- Polish phrases
- learn Polish by speaking
- AI Polish phrases
- AI coding agent language learning
- custom Polish practice content

Avoid keyword stuffing. Use each idea once or twice in natural sentences.

## Metadata Risks

Avoid these in title, short description, screenshot captions, and full
description:

- "best"
- "#1"
- "better than Duolingo"
- "works unlike other apps"
- "rapid fluency"
- "guaranteed"
- "advanced AI" without saying what the AI actually does
- "agent customizable" before the API/agent path ships in the Play release

Use concrete product claims instead: pronunciation feedback, syllable cues,
slow audio, phrase repetition, manual custom phrase groups, and agent-assisted
custom content.

## Listing Setup Notes

- Prefer one Play listing for Polish learning if the release app can switch
  direction in-app. Two separate listings split installs, ratings, reviews, and
  discovery signals.
- If `enPl` and `plEn` remain separate Play packages, this document applies to
  `com.sponic.langbangml.enpl` only. The `plEn` package needs separate copy
  focused on Polish speakers learning English.
- Category should be Education.
- First screenshots should show real speaking/audio/pronunciation value.
- The Play-safe release path still needs the policy cleanup tracked in
  `docs/prompts/03-play-store-readiness-and-legal-pages.md`.

## Official Policy References

- Google Play app field setup:
  https://support.google.com/googleplay/android-developer/answer/9859152
- Google Play metadata policy:
  https://support.google.com/googleplay/android-developer/answer/9898842
- Google Play AI-generated content policy:
  https://support.google.com/googleplay/android-developer/answer/14094294
