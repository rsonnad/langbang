-- The expanded lesson payloads are delivered through the authenticated Content API.
-- Fresh databases receive the same source-of-truth data from regenerated seed files
-- 003 and 005; this marker keeps already-provisioned D1 databases migration-safe.
SELECT 1;
