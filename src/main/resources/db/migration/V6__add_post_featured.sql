-- Editorial "featured" flag curated by moderators/admins; featured posts surface first in the public catalog.
-- Default false, no data backfill needed.
alter table posts add column featured boolean not null default false;
