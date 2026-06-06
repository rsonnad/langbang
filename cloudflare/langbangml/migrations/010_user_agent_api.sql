-- Per-user coding-agent tokens and user-owned custom word sync.
-- These tokens are shown inside the signed-in Android app. They must never grant
-- global content-admin privileges.

CREATE TABLE IF NOT EXISTS user_agent_tokens (
  id TEXT PRIMARY KEY,
  user_id TEXT NOT NULL REFERENCES users(id),
  token_hash TEXT NOT NULL UNIQUE,
  token_prefix TEXT NOT NULL,
  label TEXT NOT NULL DEFAULT 'Claude/Codex',
  default_instance_id TEXT NOT NULL DEFAULT 'langbangml-en-pl',
  created_at TEXT NOT NULL DEFAULT (datetime('now')),
  last_used_at TEXT,
  expires_at TEXT,
  revoked_at TEXT
);

CREATE INDEX IF NOT EXISTS idx_user_agent_tokens_user
ON user_agent_tokens(user_id, revoked_at, created_at);

CREATE TABLE IF NOT EXISTS user_agent_token_usage (
  token_id TEXT NOT NULL REFERENCES user_agent_tokens(id),
  day TEXT NOT NULL,
  call_count INTEGER NOT NULL DEFAULT 0,
  updated_at TEXT NOT NULL DEFAULT (datetime('now')),
  PRIMARY KEY (token_id, day)
);

CREATE TABLE IF NOT EXISTS user_agent_api_call_events (
  id TEXT PRIMARY KEY,
  token_id TEXT REFERENCES user_agent_tokens(id),
  user_id TEXT REFERENCES users(id),
  instance_id TEXT,
  method TEXT NOT NULL,
  route TEXT NOT NULL,
  operation TEXT,
  status INTEGER NOT NULL,
  created_at TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE INDEX IF NOT EXISTS idx_user_agent_api_call_events_created
ON user_agent_api_call_events(created_at);

CREATE INDEX IF NOT EXISTS idx_user_agent_api_call_events_user
ON user_agent_api_call_events(user_id, created_at);

CREATE TABLE IF NOT EXISTS user_custom_words (
  user_id TEXT NOT NULL REFERENCES users(id),
  instance_id TEXT NOT NULL,
  word_type TEXT NOT NULL,
  lemma TEXT NOT NULL,
  item_json TEXT NOT NULL,
  created_at TEXT NOT NULL DEFAULT (datetime('now')),
  updated_at TEXT NOT NULL DEFAULT (datetime('now')),
  deleted_at TEXT,
  PRIMARY KEY (user_id, instance_id, word_type, lemma)
);

CREATE INDEX IF NOT EXISTS idx_user_custom_words_instance
ON user_custom_words(user_id, instance_id, word_type, updated_at);
