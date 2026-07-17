-- Persisted word count of the post content (the fact; reading minutes are derived in
-- code via ReadingTime so the words-per-minute constant can change without a migration).
-- Nullable: existing rows are backfilled by WordCountBackfill on next startup.
alter table posts add column word_count int;
