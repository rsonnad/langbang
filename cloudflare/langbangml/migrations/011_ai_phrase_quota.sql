-- Account-level quota for in-app AI-generated custom phrases.

CREATE TABLE IF NOT EXISTS user_ai_phrase_quotas (
  user_id TEXT PRIMARY KEY REFERENCES users(id),
  quota INTEGER NOT NULL DEFAULT 50,
  generated_count INTEGER NOT NULL DEFAULT 0,
  created_at TEXT NOT NULL DEFAULT (datetime('now')),
  updated_at TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE TABLE IF NOT EXISTS user_ai_phrase_quota_requests (
  id TEXT PRIMARY KEY,
  user_id TEXT NOT NULL REFERENCES users(id),
  email TEXT NOT NULL,
  instance_id TEXT,
  current_quota INTEGER NOT NULL,
  generated_count INTEGER NOT NULL,
  message TEXT NOT NULL DEFAULT '',
  sent INTEGER NOT NULL DEFAULT 0,
  created_at TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE INDEX IF NOT EXISTS idx_user_ai_phrase_quota_requests_created
ON user_ai_phrase_quota_requests(created_at);
