-- Seed "Calendar Basics" phrase group for all existing en-pl users.
-- Days of the week, months of the year, and common time expressions.
-- Uses INSERT OR IGNORE so users who already have this group_id are unaffected.
-- All phrases carry a `literal` (word-for-word gloss) for NowVoicing consistency.

INSERT OR IGNORE INTO user_phrase_groups
  (user_id, instance_id, group_id, sort_order, group_json, created_at, updated_at)
SELECT
  id,
  'langbangml-en-pl',
  'calendar-basics',
  0,
  '{"id":"calendar-basics","title":"Calendar Basics","subtitle":"Days, months, and time expressions","sentences":[{"pl":"poniedziałek","en":"Monday","literal":"Monday"},{"pl":"wtorek","en":"Tuesday","literal":"Tuesday"},{"pl":"środa","en":"Wednesday","literal":"Wednesday"},{"pl":"czwartek","en":"Thursday","literal":"Thursday"},{"pl":"piątek","en":"Friday","literal":"Friday"},{"pl":"sobota","en":"Saturday","literal":"Saturday"},{"pl":"niedziela","en":"Sunday","literal":"Sunday"},{"pl":"styczeń","en":"January","literal":"January"},{"pl":"luty","en":"February","literal":"February"},{"pl":"marzec","en":"March","literal":"March"},{"pl":"kwiecień","en":"April","literal":"April"},{"pl":"maj","en":"May","literal":"May"},{"pl":"czerwiec","en":"June","literal":"June"},{"pl":"lipiec","en":"July","literal":"July"},{"pl":"sierpień","en":"August","literal":"August"},{"pl":"wrzesień","en":"September","literal":"September"},{"pl":"październik","en":"October","literal":"October"},{"pl":"listopad","en":"November","literal":"November"},{"pl":"grudzień","en":"December","literal":"December"},{"pl":"dzisiaj","en":"today","literal":"today"},{"pl":"jutro","en":"tomorrow","literal":"tomorrow"},{"pl":"przedwczoraj","en":"day before yesterday","literal":"before-yesterday"},{"pl":"w przyszłym tygodniu","en":"next week","literal":"in next week"},{"pl":"w przyszłym miesiącu","en":"next month","literal":"in next month"},{"pl":"w przyszłym roku","en":"next year","literal":"in next year"},{"pl":"w tym roku","en":"this year","literal":"in this year"},{"pl":"w tym miesiącu","en":"this month","literal":"in this month"},{"pl":"w tym tygodniu","en":"this week","literal":"in this week"},{"pl":"za chwilę","en":"in a while","literal":"for a-moment"},{"pl":"drugiego lipca","en":"on the 2nd of July","literal":"second-of July"},{"pl":"piątego lipca","en":"on the 5th of July","literal":"fifth-of July"},{"pl":"Urodziny Soni są dwudziestego sierpnia.","en":"Sonia''s birthday is on the 20th of August.","literal":"Birthday-of Sonia are twentieth-of August."}]}',
  datetime('now'),
  datetime('now')
FROM users;
