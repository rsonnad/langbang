-- Follow-up copy polish for the already published EN→JA starter pack.
-- Keep 014 immutable: this migration updates the live content version in place.

UPDATE content_lessons
SET payload_json = REPLACE(
  payload_json,
  'I am so busy I could borrow a cats paws.',
  'I am so busy I could borrow a cat''s paws.'
)
WHERE content_version_id = 'en-ja-v1' AND lesson_id = 'lesson-05';

UPDATE content_lessons
SET payload_json = REPLACE(
  payload_json,
  '"lemma":"元気な"',
  '"lemma":"元気"'
)
WHERE content_version_id = 'en-ja-v1' AND lesson_id = 'lesson-03';
