-- langbang: abuse controls — per-key fixed-window counters for rate limiting.
-- Backs enforceRateLimit() in src/index.js. Each row is one (bucket, window)
-- pair; the worker upserts a count per request and rejects past the limit.
-- NOTE: this is an application-layer backstop (one D1 write per guarded call).
-- Pair it with an edge Cloudflare WAF rate-limit rule (plan BE-0a) so a flood
-- is dropped at the edge before it reaches D1.
CREATE TABLE IF NOT EXISTS rate_limit_counters (
  bucket TEXT NOT NULL,
  window_start INTEGER NOT NULL,
  count INTEGER NOT NULL DEFAULT 0,
  updated_at TEXT NOT NULL DEFAULT (datetime('now')),
  PRIMARY KEY (bucket, window_start)
);

-- Lets a periodic cleanup job prune expired windows by age.
CREATE INDEX IF NOT EXISTS idx_rate_limit_window ON rate_limit_counters (window_start);
