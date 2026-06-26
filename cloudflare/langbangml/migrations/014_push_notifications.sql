-- FCM data-message subscriptions for push-triggered content refresh.
-- The token itself is required for FCM sends; token_hash is used for stable lookup
-- and audit joins without exposing the raw token in common query results.

CREATE TABLE IF NOT EXISTS push_device_tokens (
  token_hash TEXT PRIMARY KEY,
  token TEXT NOT NULL,
  platform TEXT NOT NULL DEFAULT 'android',
  user_id TEXT REFERENCES users(id),
  instance_id TEXT NOT NULL,
  installation_id TEXT NOT NULL DEFAULT '',
  app_package TEXT NOT NULL DEFAULT '',
  app_version_code INTEGER NOT NULL DEFAULT 0,
  app_version_name TEXT NOT NULL DEFAULT '',
  build_number INTEGER NOT NULL DEFAULT 0,
  locale TEXT NOT NULL DEFAULT '',
  enabled INTEGER NOT NULL DEFAULT 1,
  created_at TEXT NOT NULL DEFAULT (datetime('now')),
  last_registered_at TEXT NOT NULL DEFAULT (datetime('now')),
  last_seen_at TEXT NOT NULL DEFAULT (datetime('now')),
  last_sent_at TEXT,
  last_error TEXT
);

CREATE INDEX IF NOT EXISTS idx_push_device_tokens_instance
ON push_device_tokens(instance_id, enabled, last_seen_at);

CREATE INDEX IF NOT EXISTS idx_push_device_tokens_user
ON push_device_tokens(user_id, instance_id, enabled, last_seen_at);

CREATE INDEX IF NOT EXISTS idx_push_device_tokens_installation
ON push_device_tokens(installation_id, enabled);

CREATE TABLE IF NOT EXISTS push_send_events (
  id TEXT PRIMARY KEY,
  token_hash TEXT REFERENCES push_device_tokens(token_hash),
  user_id TEXT REFERENCES users(id),
  instance_id TEXT NOT NULL,
  push_type TEXT NOT NULL,
  reason TEXT NOT NULL DEFAULT '',
  status INTEGER NOT NULL DEFAULT 0,
  response_json TEXT NOT NULL DEFAULT '',
  error TEXT NOT NULL DEFAULT '',
  created_at TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE INDEX IF NOT EXISTS idx_push_send_events_created
ON push_send_events(created_at);

CREATE INDEX IF NOT EXISTS idx_push_send_events_instance
ON push_send_events(instance_id, created_at);
