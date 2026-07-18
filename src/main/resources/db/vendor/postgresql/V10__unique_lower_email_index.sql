-- Case-insensitive email uniqueness at the database level, backstopping anything
-- that bypasses the app's lowercasing (manual inserts, future code paths).
-- Postgres-only: H2 (tests) has no expression indexes, hence db/vendor/{vendor}.
create unique index uq_users_email_lower on users (lower(email));
