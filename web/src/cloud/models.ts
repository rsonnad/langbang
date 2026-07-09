// Ported from the Android app's data/model/Lesson.kt, Pronunciation.kt, and
// cloud/CloudModels.kt. The on-the-wire content fields are ROLE-named, not
// language-named: `en` is always the SOURCE cue, `pl` is always the TARGET
// answer (the thing being learned), in BOTH directions. Voices bind by role:
//   source cue  -> languagePair.sourceVoice / sourceLocale   (item.en)
//   target answer -> languagePair.targetVoice / targetLocale (item.pl) + slow

export interface TokenPair {
  pl: string;
  en: string;
  gender?: string | null;
  caseKey?: string | null;
  caseLabel?: string | null;
  numberLabel?: string | null;
  variableStart?: number | null;
  variableEnd?: number | null;
  variableKind?: string | null;
}

export interface SentenceExample {
  pl: string;
  en: string;
  literal?: string | null;
  words?: TokenPair[] | null;
}

export interface VerbEntry {
  lemma: string;
  en: string;
  forms: Record<string, string>;
  past_forms?: Record<string, string> | null;
}

export interface PronounEntry {
  lemma: string;
  en: string;
  case_forms: Record<string, string>;
}

export interface PhraseEntry {
  en: string;
  pl: string;
  focus?: string[];
}

export interface AdjectiveEntry {
  lemma: string;
  en: string;
  nom: Record<string, string>;
  acc: Record<string, string>;
}

export interface AdverbEntry {
  lemma: string;
  en: string;
}

export interface NounEntry {
  lemma: string;
  en: string;
  gender: string;
  nom: Record<string, string>;
  acc: Record<string, string>;
  gen: Record<string, string>;
}

export interface PhraseGroup {
  id: string;
  title: string;
  subtitle?: string;
  sentences: SentenceExample[];
}

export interface ExampleWord {
  pl: string;
  en: string;
}

export interface PhonemeEntry {
  letter: string;
  name: string;
  ipa: string;
  englishApproximation: string;
  description: string;
  examples: ExampleWord[];
}

// --- Lesson payloads (CloudLesson.payload, narrowed by CloudLesson.type) ---

export interface PronunciationPayload {
  id: string;
  title: string;
  summary: string;
  phonemes: PhonemeEntry[];
}
export interface VerbsPayload {
  id: string;
  title: string;
  summary: string;
  verbs: VerbEntry[];
  pronouns: PronounEntry[];
  phrases: PhraseEntry[];
}
export interface AdjectivesPayload {
  id: string;
  title: string;
  summary: string;
  adjectives: AdjectiveEntry[];
}
export interface AdverbsPayload {
  id: string;
  title: string;
  summary: string;
  adverbs: AdverbEntry[];
}
export interface PhrasesPayload {
  id: string;
  title: string;
  summary: string;
  groups: PhraseGroup[];
}
export interface NounsPayload {
  id: string;
  title: string;
  summary: string;
  nouns: NounEntry[];
}

// --- Cloud envelope (CloudModels.kt) ---

export interface CloudLanguagePairSummary {
  id: string;
  sourceLanguage: string;
  targetLanguage: string;
  sourceLocale: string;
  targetLocale: string;
}

export interface CloudInstanceSummary {
  id: string;
  displayName: string;
  uiLocale: string;
  contentVersionId: string;
  languagePair: CloudLanguagePairSummary;
}

export interface CloudInstance {
  id: string;
  displayName: string;
  uiLocale: string;
  settings?: Record<string, unknown>;
  updatedAt?: string;
}

export interface CloudLanguagePair {
  id: string;
  sourceLanguage: string;
  targetLanguage: string;
  sourceLocale: string;
  targetLocale: string;
  sourceVoice: string;
  targetVoice: string;
  targetSlowVoices?: string[];
  description?: string;
}

export interface CloudLesson {
  id: string;
  type: string;
  sortOrder: number;
  title: string;
  summary?: string;
  payload: Record<string, unknown>;
  updatedAt?: string;
}

export interface CloudContent {
  versionId?: string | null;
  lessons: CloudLesson[];
}

export interface CloudAudioConfig {
  manifestEndpoint: string;
  publicR2Base: string;
  audioPrefix: string;
}

export interface CloudBootstrap {
  instance: CloudInstance;
  languagePair: CloudLanguagePair;
  content: CloudContent;
  labels?: Record<string, string>;
  audio: CloudAudioConfig;
  syncedAt: string;
}

// --- Auth ---

export interface AuthUser {
  id: string;
  email: string;
  emailVerified?: boolean;
  displayName?: string;
  pictureUrl?: string;
}
export interface AuthSession {
  token: string;
  expiresAt: string;
}
export interface AuthResponse {
  user: AuthUser;
  session: AuthSession;
}
export interface EmailStartResponse {
  ok: boolean;
  email: string;
  sent: boolean;
  expiresInMinutes: number;
  devCode?: string;
}

// --- User-owned synced content ---

export interface CloudUserWords {
  verbs: VerbEntry[];
  nouns: NounEntry[];
  adjectives: AdjectiveEntry[];
  adverbs: AdverbEntry[];
}

export interface CloudUserContentResponse {
  instanceId: string;
  groups: PhraseGroup[];
  starredPhrases: string[];
  words: CloudUserWords;
  hasRemoteContent: boolean;
  hasRemotePhrases: boolean;
  hasRemoteWords: boolean;
  syncedAt: string;
}

// --- Audio manifest ---

export interface AudioPhraseReq {
  text: string;
  voice: string;
  locale: string;
}
export interface AudioManifestEntry extends AudioPhraseReq {
  sha1: string;
  url: string;
  uploaded: boolean;
  error?: string;
}
export interface AudioManifestResponse {
  summary: { requested: number; synthesized: number; cached: number; failed: number };
  manifest: AudioManifestEntry[];
}

// --- Derived: a resolved learning direction (role -> voice/locale) ---

export interface VoicingProfile {
  sourceLanguage: string;
  targetLanguage: string;
  sourceVoice: string;
  sourceLocale: string;
  targetVoice: string;
  targetLocale: string;
  /** Preferred slow target voice (stretch style), or null when none configured. */
  slowTargetVoice: string | null;
}

export function voicingFromBootstrap(b: CloudBootstrap): VoicingProfile {
  const lp = b.languagePair;
  return {
    sourceLanguage: lp.sourceLanguage,
    targetLanguage: lp.targetLanguage,
    sourceVoice: lp.sourceVoice,
    sourceLocale: lp.sourceLocale,
    targetVoice: lp.targetVoice,
    targetLocale: lp.targetLocale,
    slowTargetVoice: lp.targetSlowVoices?.[0] ?? null,
  };
}

/** Clean, "LangBang"-only label for a direction — never the API displayName (which leaks the internal name). */
export function directionLabel(pair: CloudLanguagePairSummary | CloudLanguagePair): string {
  return `${pair.sourceLanguage} → ${pair.targetLanguage}`;
}

export function findLesson<T>(b: CloudBootstrap, type: string): T | null {
  const lesson = b.content.lessons.find((l) => l.type === type);
  return lesson ? (lesson.payload as unknown as T) : null;
}
