-- Drafts may be created with no title, no slug, and no category;
-- those become mandatory only once a post is published (enforced in the service layer).
alter table posts alter column title drop not null;
alter table posts alter column slug drop not null;
alter table posts alter column category_id drop not null;
