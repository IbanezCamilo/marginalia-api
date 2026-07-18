-- Every application write path lowercases emails before storing; fix any legacy
-- mixed-case rows, which lookups (they lowercase the typed email) could never
-- reach. Fails loudly if lowering collides with an existing row — that conflict
-- needs a human decision, not a silent merge.
update users set email = lower(trim(email)) where email <> lower(trim(email));
