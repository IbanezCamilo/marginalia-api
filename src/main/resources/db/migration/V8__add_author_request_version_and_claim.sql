-- Optimistic locking for concurrent approve/reject: Hibernate filters the UPDATE by
-- version, so the second of two racing resolutions fails instead of silently winning.
-- default 0 backfills existing rows (0 is a valid initial @Version value).
alter table author_requests add column version bigint not null default 0;

-- "Under review" claim: which admin currently has the resolution modal open and since
-- when. Both null while unclaimed; claims expire by TTL in code (author-request.claim-ttl-minutes),
-- so stale rows from closed tabs need no cleanup job.
alter table author_requests add column claimed_by_id integer;
alter table author_requests add column claimed_at timestamp(6);
alter table author_requests add constraint fk_author_requests_claimed_by foreign key (claimed_by_id) references users;
