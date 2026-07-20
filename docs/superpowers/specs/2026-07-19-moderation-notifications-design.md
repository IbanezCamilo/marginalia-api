# Moderation Notifications — Settings & Email Preferences

**Date:** 2026-07-19
**Scope:** marginalia-api (core) + marginalia-web (Settings UI)
**Status:** Approved

## Context

Authors are never told when a moderator approves, rejects, or archives one of their posts — they only find out by logging into the dashboard. The dashboard's user dropdown (`TopBar.jsx`) has a disabled "Configuración" item with no page behind it. This feature builds the first real Settings section (email notification preferences) and wires moderation status changes to author emails through the existing `ResendEmailService`.

## Scope decisions

- **All** moderation-driven status changes email the author: approve, reject (incl. auto-archive on 3rd rejection), archive, toDraft, admin `updateStatus`, admin `resetPost`.
- Preference defaults **ON** (opt-out); these are service notifications about the user's own content, not marketing.
- **No admin digest, no `@EnableScheduling`** — explicitly deferred until there is more than one admin and request volume justifies it; the immediate per-request admin email (`sendAuthorRequestNotification`) already covers the need.
- Settings is a **new page** `/user/settings` (new `src/features/settings/` feature), not a section of ProfilePage.
- Preference storage is a **generic key-value table storing only deviations from defaults**, with a `VARCHAR` value column so future prefs (digest cadence, font size…) are just another row. Extensibility acid test: adding a preference = one registry enum entry + one UI row. No migration, no parallel system.

## 1. Preference storage (marginalia-api)

Flyway migration `V11__add_user_preferences.sql` in `src/main/resources/db/migration/`:

```sql
CREATE TABLE user_preferences (
  user_id    INTEGER     NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  pref_key   VARCHAR(50) NOT NULL,
  pref_value VARCHAR(50) NOT NULL,   -- deliberately VARCHAR, not BOOLEAN: type-agnostic for future
                                     -- prefs (cadence, font size…) and portable to the H2 test DB
  PRIMARY KEY (user_id, pref_key)
);
```

The column is named `pref_value` (not `value`) because `VALUE` is a reserved keyword in H2, and the test suite generates this schema on H2 via `ddl-auto=create-drop`.

Booleans are stored as the strings `"true"`/`"false"`; the `UserPreference` registry declares each key's expected type and is the only place values are parsed.

Registry enum `UserPreference` (single source of truth for known keys):

```java
public enum UserPreference {
    POST_MODERATION_EMAILS("notifications.post-moderation", ValueType.BOOLEAN, "true");

    private final String key;
    private final ValueType type;   // BOOLEAN today; more later
    private final String defaultValue;
    // static fromKey(String) -> Optional<UserPreference>
    // boolean isValidValue(String) per type
}
```

- Resolution rule (single shared conversion): stored row's `value` if present, else `defaultValue`. Nothing outside `UserPreferenceService` parses raw rows.
- Unknown keys / type-invalid values → 400 at the API edge.

New backend pieces:

- Entity `UserPreferenceEntry` — composite key (`user_id`, `pref_key`) via `@IdClass` or `@EmbeddedId`.
- `repositories/UserPreferenceRepository` — `findByUserId`, upsert via save.
- `services/users/UserPreferenceService`:
  - `getResolved(userId)` → `Map<String,String>` of ALL registry keys with defaults merged;
  - `update(userId, Map<String,String> partial)` → validate each key against registry + value against type, upsert rows, return resolved map;
  - `isEnabled(userId, UserPreference)` → boolean helper for in-transaction checks (BOOLEAN prefs).

## 2. Email trigger flow (marginalia-api)

Mirrors the existing `AuthorRequestSubmitted` → `AuthorRequestNotificationListener` pattern exactly.

New event `events/PostModerationStatusChanged.java` (record):
`postId, postTitle, authorName, authorEmail, previousStatus, newStatus` (typed as `PostStatus` — templates render `getDisplayName()`, subjects switch on the enum), `moderationNote` (nullable), `idempotencyKey` (`post-moderation/<postId>/<uuid>`, generated once at publish — stable across provider retry attempts). No event is published when the status did not actually change (e.g. an admin forcing a post to its current status).

Publish points (inside the transaction, via `ApplicationEventPublisher`):

- `ModeratorPostService.updateStatus` — covers approve, reject, auto-archive on 3rd rejection (event reports final status `ARCHIVED`), archive, toDraft.
- `AdminPostModerationService.updateStatus` and `resetPost`.

Skip publishing when (decided in-transaction so the listener never touches repositories — same constraint documented on `AuthorRequestSubmitted`):

- the author's `POST_MODERATION_EMAILS` resolves to false (`UserPreferenceService.isEnabled`), or
- moderator id == author id (no self-notification).

`previousStatus` is captured BEFORE mutating the post. A small private helper shared by the publish points builds/publishes the event.

New listener `services/email/PostModerationNotificationListener.java`:
`@Async @TransactionalEventListener(phase = AFTER_COMMIT)`, try/catch log-never-rethrow, calls `EmailService.sendPostModerationNotification(...)` with CTA URL `frontendProperties.url() + "/user/posts"`.

`EmailService` interface gains `sendPostModerationNotification(String to, String authorName, String postTitle, PostStatus previousStatus, PostStatus newStatus, String moderationNote, String postsUrl, String idempotencyKey)`; implemented in BOTH `ResendEmailService` and `LoggingEmailService`.

Template (in `ResendEmailService`, same inline-styled single-column Spanish format as the existing two):

- Status-specific subject/headline by NEW status: PUBLISHED → "Tu post fue publicado en Marginalia"; REJECTED → "Tu post necesita cambios"; ARCHIVED → "Tu post fue archivado"; DRAFT → "Tu post volvió a borrador". One template method, a small switch for subject/headline copy.
- Body: post title, old→new status, moderation note in the existing quoted-block style when present, CTA button to `/user/posts`, HTML + plain-text variants.
- HTML-escape everything user/moderator-authored (`HtmlUtils.htmlEscape`), like existing templates.

## 3. Preferences API (marginalia-api)

`controllers/users/MyPreferencesController.java`, `@RequestMapping("/api/me/preferences")`, cookie auth, any authenticated role (style of `MyProfileController`):

- `GET` → resolved map `{ "notifications.post-moderation": "true" }`. The frontend never computes defaults.
- `PUT` → partial `Map<String,String>` body; validates, upserts, returns resolved map. 400 for unknown key or type-invalid value.

`SecurityConfig` should already require authentication on `/api/me/**`; verify, no change expected.

## 4. Settings UI (marginalia-web)

New feature `src/features/settings/` following the three-layer pattern:

- `services/preferencesService.js` — `getPreferences()` / `updatePreferences(partial)` via `apiClient`.
- `hooks/usePreferences.js` — loading/saving state, optimistic toggle with revert + Sonner toast on error (conventions of `useChangePassword`).
- `pages/SettingsPage.jsx` — sectioned layout; one "Notificaciones" card with the toggle: "Recibir un correo cuando el estado de uno de mis posts cambie por moderación". The card renders **only for AUTHOR and above**, gated with the existing `ROLE_LEVEL` map from `src/utils/roles.js` (same mechanism `RoleRoute.jsx` uses): `ROLE_LEVEL[user?.role] >= ROLE_LEVEL.AUTHOR`. Since this is currently the only section, a READER instead sees the shared `EmptyState` component ("Aún no hay ajustes disponibles para tu cuenta" or similar) — never a blank page, never a grayed-out toggle. Future sections = more cards, each with its own role gate if needed.
- Backend stays role-agnostic: `GET`/`PUT /api/me/preferences` accept any authenticated user (a READER storing the pref is harmless and takes effect if they later become an author); the gating is purely presentational.

Wiring:

- `src/routes/AppRouter.jsx` — add `{ path: "settings", element: <SettingsPage /> }` under `AdminLayout` children (all authenticated roles, next to `profile`).
- `src/panel/layout/TopBar.jsx` — the disabled "Configuración" item becomes `<DropdownMenuItem asChild><Link to="settings">…`, mirroring the "Perfil" item.
- Add shadcn `switch.jsx` to `src/components/ui/` (new dep `@radix-ui/react-switch`).

## 5. Error handling

- Email delivery failures never affect moderation: the listener swallows + logs; `ResendEmailService.sendWithRetry` already retries 429/5xx with the idempotency key riding on every attempt.
- Moderation transactions are unaffected by preference reads (in-transaction read of one row).

## 6. Testing

- Backend: `UserPreferenceService` (default resolution, unknown key → 400, bad value → 400, upsert), `MyPreferencesControllerTest` (GET/PUT, auth), moderation service tests asserting the event is published on each transition and is NOT published when the pref is off or moderator == author, listener test. Full existing suite re-run; any changed expectation is reported, never silently adjusted.
- Frontend (Vitest): `preferencesService` + `usePreferences` tests following profile-feature patterns, plus a `SettingsPage` render test for the role gate (AUTHOR+ sees the Notificaciones card; READER sees the EmptyState).
- E2E (dev, `email.provider=logging`): moderate another user's post → log line with author recipient and correct statuses; author toggles pref off in `/user/settings` → repeat → no log line; toggle survives reload. READER sees the Settings page without the moderation notifications section (EmptyState).
