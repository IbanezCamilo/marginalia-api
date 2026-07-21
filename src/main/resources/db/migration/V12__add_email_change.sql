-- Self-service email change reuses the verification-token table rather than a parallel
-- one, so the token issuance, hashing, cooldown, daily-cap and purge logic are shared.
--
-- token_type is the explicit discriminator between the two flows; it is never inferred
-- from pending_email being set. Existing rows are all registration-verification tokens,
-- so they backfill to VERIFICATION via the column default.
--
-- An EMAIL_CHANGE row also stages the new address (pending_email) and carries a second
-- token (cancel_token, SHA-256-hashed like `token`) emailed to the current address so the
-- owner can abort a change they didn't initiate.
alter table email_verification_tokens add column token_type varchar(20) not null default 'VERIFICATION';
alter table email_verification_tokens add column pending_email varchar(100);
alter table email_verification_tokens add column cancel_token varchar(64);

alter table email_verification_tokens add constraint uq_email_verification_tokens_cancel_token unique (cancel_token);
create index idx_email_verification_tokens_cancel_token on email_verification_tokens (cancel_token);
