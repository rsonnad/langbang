-- Ephemeral browser/LLM control sessions for the Now Voicing Android screen.
-- The Durable Object owns hot ordered delivery; this table only authenticates
-- pairing, devices, and revocation. Never store plaintext pairing codes/tokens.
CREATE TABLE IF NOT EXISTS now_voicing_control_sessions (
  id TEXT PRIMARY KEY,
  user_id TEXT NOT NULL REFERENCES users(id),
  instance_id TEXT NOT NULL,
  code_digest TEXT NOT NULL UNIQUE,
  device_token_hash TEXT NOT NULL UNIQUE,
  controller_token_hash TEXT UNIQUE,
  created_at TEXT NOT NULL DEFAULT (datetime('now')),
  claimed_at TEXT,
  expires_at TEXT NOT NULL,
  revoked_at TEXT,
  last_device_ack INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_now_voicing_control_owner
ON now_voicing_control_sessions(user_id, revoked_at, expires_at);

CREATE INDEX IF NOT EXISTS idx_now_voicing_control_controller
ON now_voicing_control_sessions(controller_token_hash, revoked_at, expires_at);
