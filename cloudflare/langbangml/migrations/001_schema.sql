-- LangBangML Cloudflare-only backend.
-- D1 owns instance settings, language-pair configuration, UX labels, content metadata,
-- and audio metadata. R2 owns the binary audio/content assets.

CREATE TABLE IF NOT EXISTS language_pairs (
  id TEXT PRIMARY KEY,
  source_language TEXT NOT NULL,
  target_language TEXT NOT NULL,
  source_locale TEXT NOT NULL,
  target_locale TEXT NOT NULL,
  ui_locale TEXT NOT NULL DEFAULT 'en-US',
  source_voice TEXT NOT NULL,
  target_voice TEXT NOT NULL,
  target_slow_voices_json TEXT NOT NULL DEFAULT '[]',
  description TEXT NOT NULL DEFAULT '',
  active INTEGER NOT NULL DEFAULT 1,
  created_at TEXT NOT NULL DEFAULT (datetime('now')),
  updated_at TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE TABLE IF NOT EXISTS app_instances (
  id TEXT PRIMARY KEY,
  display_name TEXT NOT NULL,
  language_pair_id TEXT NOT NULL REFERENCES language_pairs(id),
  ui_locale TEXT NOT NULL DEFAULT 'en-US',
  content_version_id TEXT,
  settings_json TEXT NOT NULL DEFAULT '{}',
  active INTEGER NOT NULL DEFAULT 1,
  created_at TEXT NOT NULL DEFAULT (datetime('now')),
  updated_at TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE TABLE IF NOT EXISTS ui_labels (
  locale TEXT NOT NULL,
  label_key TEXT NOT NULL,
  label_value TEXT NOT NULL,
  updated_at TEXT NOT NULL DEFAULT (datetime('now')),
  PRIMARY KEY (locale, label_key)
);

CREATE TABLE IF NOT EXISTS content_versions (
  id TEXT PRIMARY KEY,
  language_pair_id TEXT NOT NULL REFERENCES language_pairs(id),
  version INTEGER NOT NULL,
  title TEXT NOT NULL,
  summary TEXT NOT NULL DEFAULT '',
  active INTEGER NOT NULL DEFAULT 1,
  created_at TEXT NOT NULL DEFAULT (datetime('now')),
  UNIQUE (language_pair_id, version)
);

CREATE TABLE IF NOT EXISTS content_lessons (
  content_version_id TEXT NOT NULL REFERENCES content_versions(id),
  lesson_id TEXT NOT NULL,
  lesson_type TEXT NOT NULL,
  sort_order INTEGER NOT NULL,
  title TEXT NOT NULL,
  summary TEXT NOT NULL DEFAULT '',
  payload_json TEXT NOT NULL,
  updated_at TEXT NOT NULL DEFAULT (datetime('now')),
  PRIMARY KEY (content_version_id, lesson_id)
);

CREATE INDEX IF NOT EXISTS idx_content_lessons_order
ON content_lessons(content_version_id, sort_order);

CREATE TABLE IF NOT EXISTS audio_assets (
  sha1 TEXT PRIMARY KEY,
  text TEXT NOT NULL,
  locale TEXT NOT NULL,
  voice TEXT NOT NULL,
  r2_key TEXT NOT NULL,
  public_url TEXT NOT NULL,
  bytes INTEGER,
  uploaded INTEGER NOT NULL DEFAULT 0,
  last_error TEXT,
  created_at TEXT NOT NULL DEFAULT (datetime('now')),
  updated_at TEXT NOT NULL DEFAULT (datetime('now')),
  UNIQUE (locale, voice, text)
);

CREATE INDEX IF NOT EXISTS idx_audio_assets_locale_voice
ON audio_assets(locale, voice);

CREATE TABLE IF NOT EXISTS content_audio_requirements (
  content_version_id TEXT NOT NULL REFERENCES content_versions(id),
  sha1 TEXT NOT NULL REFERENCES audio_assets(sha1),
  role TEXT NOT NULL,
  PRIMARY KEY (content_version_id, sha1, role)
);

CREATE TABLE IF NOT EXISTS sync_events (
  id TEXT PRIMARY KEY,
  instance_id TEXT NOT NULL,
  event_type TEXT NOT NULL,
  payload_json TEXT NOT NULL DEFAULT '{}',
  created_at TEXT NOT NULL DEFAULT (datetime('now'))
);
