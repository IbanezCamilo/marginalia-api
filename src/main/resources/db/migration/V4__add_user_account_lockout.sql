-- Temporary account locking after repeated failed logins.
-- failed_login_attempts defaults to 0 for existing rows; locked_until stays null (unlocked).
alter table users add column failed_login_attempts integer not null default 0;
alter table users add column locked_until timestamp(6);
