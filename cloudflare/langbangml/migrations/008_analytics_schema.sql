-- Product analytics for LangBangML.
-- Public app clients write low-volume events; admin routes read aggregated usage.

CREATE TABLE IF NOT EXISTS analytics_profiles (
  profile_id TEXT PRIMARY KEY,
  provider TEXT NOT NULL DEFAULT 'anonymous',
  provider_subject TEXT,
  email TEXT,
  display_name TEXT,
  locale TEXT,
  signup_state TEXT NOT NULL DEFAULT 'anonymous',
  properties_json TEXT NOT NULL DEFAULT '{}',
  first_seen_at TEXT NOT NULL DEFAULT (datetime('now')),
  last_seen_at TEXT NOT NULL DEFAULT (datetime('now')),
  updated_at TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE INDEX IF NOT EXISTS idx_analytics_profiles_email
ON analytics_profiles(email);

CREATE UNIQUE INDEX IF NOT EXISTS idx_analytics_profiles_provider_subject
ON analytics_profiles(provider, provider_subject)
WHERE provider_subject IS NOT NULL;

CREATE TABLE IF NOT EXISTS analytics_installations (
  installation_id TEXT PRIMARY KEY,
  profile_id TEXT NOT NULL REFERENCES analytics_profiles(profile_id),
  platform TEXT NOT NULL DEFAULT 'android',
  app_package TEXT,
  app_version_code INTEGER,
  app_version_name TEXT,
  build_number INTEGER,
  flavor TEXT,
  instance_id TEXT,
  device_model TEXT,
  os_version TEXT,
  locale TEXT,
  properties_json TEXT NOT NULL DEFAULT '{}',
  first_seen_at TEXT NOT NULL DEFAULT (datetime('now')),
  last_seen_at TEXT NOT NULL DEFAULT (datetime('now')),
  updated_at TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE INDEX IF NOT EXISTS idx_analytics_installations_profile
ON analytics_installations(profile_id, last_seen_at);

CREATE INDEX IF NOT EXISTS idx_analytics_installations_instance
ON analytics_installations(instance_id, last_seen_at);

CREATE TABLE IF NOT EXISTS analytics_sessions (
  session_id TEXT PRIMARY KEY,
  installation_id TEXT NOT NULL REFERENCES analytics_installations(installation_id),
  profile_id TEXT NOT NULL REFERENCES analytics_profiles(profile_id),
  instance_id TEXT,
  platform TEXT NOT NULL DEFAULT 'android',
  app_package TEXT,
  app_version_code INTEGER,
  app_version_name TEXT,
  build_number INTEGER,
  started_at TEXT NOT NULL,
  last_seen_at TEXT NOT NULL,
  ended_at TEXT,
  event_count INTEGER NOT NULL DEFAULT 0,
  duration_ms INTEGER NOT NULL DEFAULT 0,
  properties_json TEXT NOT NULL DEFAULT '{}',
  updated_at TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE INDEX IF NOT EXISTS idx_analytics_sessions_profile_time
ON analytics_sessions(profile_id, started_at);

CREATE INDEX IF NOT EXISTS idx_analytics_sessions_instance_time
ON analytics_sessions(instance_id, started_at);

CREATE TABLE IF NOT EXISTS analytics_events (
  event_id TEXT PRIMARY KEY,
  session_id TEXT NOT NULL REFERENCES analytics_sessions(session_id),
  installation_id TEXT NOT NULL REFERENCES analytics_installations(installation_id),
  profile_id TEXT NOT NULL REFERENCES analytics_profiles(profile_id),
  instance_id TEXT,
  name TEXT NOT NULL,
  feature TEXT,
  action TEXT,
  screen TEXT,
  duration_ms INTEGER NOT NULL DEFAULT 0,
  app_version_code INTEGER,
  app_version_name TEXT,
  occurred_at TEXT NOT NULL,
  received_at TEXT NOT NULL DEFAULT (datetime('now')),
  properties_json TEXT NOT NULL DEFAULT '{}'
);

CREATE INDEX IF NOT EXISTS idx_analytics_events_time
ON analytics_events(occurred_at);

CREATE INDEX IF NOT EXISTS idx_analytics_events_profile_time
ON analytics_events(profile_id, occurred_at);

CREATE INDEX IF NOT EXISTS idx_analytics_events_feature_time
ON analytics_events(feature, occurred_at);

CREATE TABLE IF NOT EXISTS analytics_feature_daily (
  day TEXT NOT NULL,
  profile_id TEXT NOT NULL,
  installation_id TEXT NOT NULL,
  instance_id TEXT,
  feature TEXT NOT NULL,
  event_name TEXT NOT NULL,
  event_count INTEGER NOT NULL DEFAULT 0,
  duration_ms INTEGER NOT NULL DEFAULT 0,
  last_seen_at TEXT NOT NULL,
  PRIMARY KEY (day, profile_id, installation_id, instance_id, feature, event_name)
);

CREATE INDEX IF NOT EXISTS idx_analytics_feature_daily_day
ON analytics_feature_daily(day, feature, event_name);

CREATE TABLE IF NOT EXISTS analytics_admin_access_events (
  id TEXT PRIMARY KEY,
  email TEXT NOT NULL,
  route TEXT NOT NULL,
  authorized INTEGER NOT NULL,
  created_at TEXT NOT NULL DEFAULT (datetime('now'))
);
