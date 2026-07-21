# Self-Service Email Change (verify-before-commit)

Approved design, 2026-07-20. Follow-up to the registration email-verification feature
(`2026-07-13-email-verification-design.md`), which listed "email-change re-verification"
as out of scope. Backend lives in `marginalia-api`; companion frontend in `marginalia-web`.

## Context

Marginalia users can edit their name, bio, and photo on the profile page, but the
account **email is read-only** — there is no way to correct an address that was
misspelled at registration. Email is also the login identity (JWT subject + every
`findByEmail` lookup key), so the change must be handled carefully.

A complete email-verification flow already exists (`EmailVerificationService`), used
today only at registration. This feature reuses it: a logged-in user requests a new email
from their profile; the change does **not** take effect until the new address is confirmed
via an emailed link. The old address is notified and can cancel. On confirmation the
session is invalidated so the user re-authenticates under the new email.

## Decisions (confirmed with the user)

1. **Old address gets notice + cancel link.** The new address receives the confirmation
   link; the old (current) address receives a security notice *and* a one-click cancel
   link, so the legitimate owner can abort a change they didn't initiate.
2. **UI lives on ProfilePage.** A "Cambiar correo" button sits next to "Cambiar
   contraseña", mirroring `ChangePasswordDialog`. Keeps both identity actions together
   where the email is already displayed; works for all authenticated roles as-is.
   (Not the AUTHOR-gated Settings page.)
3. **Force re-login on confirm.** On confirmation: swap the address, bump
   `tokenVersion`, and delete refresh tokens — exactly what the password-change flow
   already does. The user logs back in with the new email.
4. **Current-password gate to initiate**, mirroring `changePassword`.
5. **Explicit token-type discriminator** (see below) — never inferred from column nullness.
6. **Closure notification** to the old address on successful confirmation.
7. **The OWNER account cannot change its email** (see below) — rejected with **403**.

## The OWNER account is exempt (403)

`requestEmailChange` rejects the request up front when `user.getRole().isOwner()`, throwing
`OwnerEmailImmutableException` → **403** with detail *"El correo del propietario se gestiona
mediante una variable de entorno y no puede cambiarse desde la cuenta."*

**Why this is a hard rule, not a nicety:** `DataInitializer` reseeds on every startup by
looking up the OWNER by `OWNER_EMAIL` (env var) and creating one if absent. If the owner
self-changed their email, the next restart would not find a user at `OWNER_EMAIL` and would
**create a second OWNER** with the old address and the env password — and that duplicate can
never be removed, because `AdminUserService.deleteUser` refuses to delete any OWNER account.
So a self-service owner email change silently corrupts the account model on the next deploy.
The owner's address is therefore managed exclusively through the environment variable.

The frontend does not surface a path that would 403: the "Cambiar correo" button on
ProfilePage is **hidden for the OWNER**, replaced by a short muted note explaining that the
owner's email is environment-managed (never a disabled/inert control). The 403 remains the
server-side backstop for any direct API call.

## Integration approach — reuse, no duplicated logic

The existing token mechanism is reused wholesale. Only the *pending-address* concept and
the change-specific email templates are new.

- **Extend the existing token**, don't add a parallel one. `EmailVerificationToken`
  gains an **explicit discriminator** plus two nullable columns:
  - `token_type varchar(20) not null` — enum `VERIFICATION | EMAIL_CHANGE`. This is the
    single source of truth for which flow a row belongs to. Existing rows backfill to
    `VERIFICATION`. We do **not** infer type from `pending_email IS NOT NULL`; type is
    always read from this column.
  - `pending_email varchar(100) null` — the staged new address (set only on `EMAIL_CHANGE`).
  - `cancel_token varchar(64) null unique` — SHA-256 hash of the old-address cancel token
    (set only on `EMAIL_CHANGE`).
- **Type guards are explicit at every endpoint.** Each verify/confirm/cancel path asserts
  the looked-up row's `token_type` and rejects a wrong-type token as invalid (400), so a
  token issued for one flow can never be redeemed by another:
  - `verify()` (registration) accepts only `VERIFICATION`.
  - `confirmEmailChange()` accepts only `EMAIL_CHANGE` (and only via the `token` column).
  - `cancelEmailChange()` accepts only `EMAIL_CHANGE` (and only via the `cancel_token`
    column). Presenting a confirm token to the cancel endpoint (or vice-versa) is rejected.
- **Reused as-is:** token generation (`UUID` raw → SHA-256 hash at rest via `hashToken`),
  the 24h expiry, supersede-not-delete, the 60s cooldown + 5/24h daily cap
  (`findTopByUserOrderByCreatedAtDesc` / `countByUserAndCreatedAtAfter`), and the nightly
  `purgeExpiredTokens` scheduled job. Registration and change-email deliberately **share**
  the same per-user throttle budget (overall anti-spam).
- **Reused for the address itself:** `UserValidator.sanitizeEmail` (trim + lowercase) and
  `userRepository.existsByEmailExcludingId(sanitized, userId)` — the same normalization +
  uniqueness check `UserUpdateService.updateEmail` (admin path) already uses.
- **Reused for force-relogin:** `user.incrementTokenVersion()` +
  `RefreshTokenService.deleteAllByUser`, exactly as password change does.
- **New email sending** follows the established pattern (each email type has its own
  template regardless): new events + `@Async @TransactionalEventListener(AFTER_COMMIT)`
  listener, mirroring `PostModerationNotificationListener` / `VerificationEmailListener`,
  plus three Resend templates and their `LoggingEmailService` counterparts.

## What happens to the account while a change is pending

**Nothing changes.** `user.email` stays the old, verified address and `emailVerified`
stays `true`; the user keeps logging in with the old email normally. The pending change is
just a token row (`pending_email` set, 24h expiry). It resolves in exactly one of four ways:

- **Confirmed** (new-address link clicked) → address swapped, session invalidated.
- **Cancelled** (old-address link clicked) → token row deleted, no change.
- **Superseded** (user requests another change) → prior row expired via existing
  supersede logic; its confirm *and* cancel links stop working.
- **Expired** (24h) → nightly purge removes it; no change.

## Data flow

**Initiate** (authenticated, current-password gated)
`POST /api/me/profile/email` `{ newEmail, currentPassword }` → `UserProfileService`
verifies current password (reusing the `changePassword` check) → `EmailVerificationService
.requestEmailChange(user, newEmail)`:
reject if `user` is OWNER (403, `OwnerEmailImmutableException`) → sanitize → reject blank /
equal-to-current (400) → uniqueness check (409) → throttle →
issue token row (confirm hash in `token`, cancel hash in `cancel_token`, `pending_email`
set, `token_type = EMAIL_CHANGE`, `expiresAt = now + 24h`) → publish
`EmailChangeRequested(newEmail, oldEmail, userName, confirmRawToken, cancelRawToken,
idempotencyKey)`. Returns 200/202 with no session change.

**Two emails at request time** (sent by the AFTER_COMMIT listener):
- **To the NEW address** — subject e.g. *"Confirma tu nuevo correo en Marginalia"*, link
  `{frontend.url}/confirm-email-change?token={confirmRawToken}`.
- **To the OLD address** — subject e.g. *"Se solicitó un cambio de correo en tu cuenta"*,
  states the requested new address, link
  `{frontend.url}/cancel-email-change?token={cancelRawToken}`.

**One email at confirmation time** — closes the loop for the old address, which otherwise
would be left on the "a change is pending" message with no idea it went through:
- **To the OLD (former) address** — subject e.g. *"Tu correo de Marginalia fue cambiado"*,
  informational only, **no action link**: "tu correo se cambió a {newEmail}; si no fuiste
  tú, contacta con soporte." Captured before the swap (`oldEmail = user.getEmail()`) and
  sent by the confirm-path AFTER_COMMIT listener.

**Confirm** (permitAll — link may open in any browser, token is the proof)
`POST /api/auth/confirm-email-change` `{ token }` → `EmailVerificationService
.confirmEmailChange(rawToken)`: hash + lookup → 400 invalid **or wrong `token_type`** /
410 expired → **re-check uniqueness** (address may have been taken since request) → 409 if
taken → capture `oldEmail = user.getEmail()` → set `user.email = pending_email`,
`incrementTokenVersion()`, `deleteByUserAndTokenType(user, EMAIL_CHANGE)` (clears only this
flow's rows — never blindly all tokens, consistent with `token_type` being the source of
truth), `refreshTokenService.deleteAllByUser(user)` → publish `EmailChangeCompleted(oldEmail,
newEmail, userName, idempotencyKey)`. `emailVerified` stays true (new address is now
proven). Session is dead → user re-logs in with the new email.

**Cancel** (permitAll)
`POST /api/auth/cancel-email-change` `{ token }` → look up by `cancel_token` hash + expiry
+ `token_type = EMAIL_CHANGE` → delete the pending token row. Voids only the pending change
(does not log anyone out or lock the account — broader takeover response is out of scope).

## Backend — files

- **New** `V12__add_email_change.sql` (migrations run V1…V9, V11 — no V10): add
  `token_type varchar(20) not null default 'VERIFICATION'` (existing rows backfill to
  `VERIFICATION`), `pending_email varchar(100) null`, and `cancel_token varchar(64) null
  unique` to `email_verification_tokens`; index on `cancel_token`.
- `model/EmailVerificationToken.java` — add the `token_type` (enum) + two nullable fields.
- `repositories/EmailVerificationTokenRepository.java` — add `findByCancelToken(String)` and
  `deleteByUserAndTokenType(User, TokenType)`. The existing registration `verify()` keeps
  its current `deleteByUser` (an unverified account never holds `EMAIL_CHANGE` rows) — no
  behavior change there; only the new confirm path uses the type-scoped delete.
- `services/auth/EmailVerificationService.java` — add `requestEmailChange(User, String)`,
  `confirmEmailChange(String)`, `cancelEmailChange(String)`, reusing the existing private
  issue/hash/throttle helpers; inject `RefreshTokenService` for the confirm path.
- `services/users/UserProfileService.java` + `controllers/users/MyProfileController.java` —
  add `PUT /api/me/profile/email` (current-password gated), delegating to the service.
- `controllers/auth/AuthController.java` — add `POST /api/auth/confirm-email-change` and
  `POST /api/auth/cancel-email-change` (permitAll), mirroring `verify-email`.
- `events/EmailChangeRequested.java` + `events/EmailChangeCompleted.java` (new records) +
  `services/email/EmailChangeNotificationListener.java` (new, handles both events,
  mirrors `VerificationEmailListener`).
- `services/email/EmailService.java` (+ `ResendEmailService`, `LoggingEmailService`) —
  add **three** methods / templates + subjects:
  `sendEmailChangeConfirmation(...)` (new address, confirm link),
  `sendEmailChangeNotice(...)` (old address, cancel link, at request time),
  `sendEmailChangeCompletedNotice(...)` (old address, informational, no link, at confirm).
- **New DTOs** under `dto/`: `ChangeEmailRequest{ @Email newEmail, @NotBlank currentPassword }`,
  reusing the existing `{ token }` request shape for confirm/cancel.
- **New exception** `OwnerEmailImmutableException` → **403** (`forbidden` type) in
  `GlobalExceptionHandler`, thrown by the OWNER guard at the top of `requestEmailChange`.
- **Exceptions (reused):** invalid/wrong-type token → 400, expired → 410, email in use →
  `UserAlreadyExistsException` 409, wrong current password → same mapping as `changePassword`,
  same-as-current → 400. Wire new cases in `GlobalExceptionHandler` only if not covered.

## Frontend (`marginalia-web`) — files

- `features/profile/pages/ProfilePage.jsx` — add a "Cambiar correo" button next to
  "Cambiar contraseña", **hidden for the OWNER** (replaced by a short muted note that the
  owner's email is environment-managed).
- `features/profile/components/ProfileEditDialog.jsx` — **remove the read-only Email field**
  and its "no se puede modificar" helper text. Now that email has its own dedicated action
  and is shown on the profile page, the field is redundant and its message is inaccurate;
  the edit modal is limited to name, bio, and photo. (Email is not part of the modal's
  submit payload, so this is a display-only removal.)
- `features/profile/components/ChangeEmailDialog.jsx` (new, mirrors
  `ChangePasswordDialog.jsx`) — fields `newEmail` + `currentPassword`; success toast:
  "Te enviamos un enlace a {newEmail}. Tu correo actual sigue activo hasta que lo confirmes."
- `features/profile/hooks/useChangeEmail.js` + `services/userService.js` method
  `changeEmail({ newEmail, currentPassword })` → `PUT /me/profile/email`.
- **New pages** (mirror `VerifyEmailPage.jsx`, single-fire guard for the one-time token):
  - `ConfirmEmailChangePage` at route `/confirm-email-change` — calls confirm on mount;
    on success shows "Correo actualizado, vuelve a iniciar sesión" and redirects to login
    (session is already invalidated; `apiClient` 401 handling also covers it).
  - `CancelEmailChangePage` at route `/cancel-email-change` — calls cancel; shows
    "Cancelaste el cambio de correo".
- New hooks `useConfirmEmailChange` / `useCancelEmailChange` + service methods; routes
  added to `AppRouter.jsx` (public, alongside `/verify-email`).

## Out of scope

- Merging into / claiming an email already tied to another account (rejected as in-use).
- Full account-takeover response beyond cancelling the pending change.
- A separate rate-limit budget (reuses the shared verification throttle).
- Resend webhooks / bounce handling (already deferred).

## Ops

Three new Resend templates + subjects (new-address confirmation, old-address cancel notice,
old-address completed notice); reuses existing `email.provider`, `resend.*`, and
`verification.*` (expiry / cooldown / daily-cap) config — no new config keys. New `V12`
Flyway migration. Test against `delivered@resend.dev` under `email.provider=resend`, or read
the link from logs under the default `logging` provider.

## Verification (how to test end-to-end)

1. **Migration:** boot the API; confirm `V12` applies and `email_verification_tokens` has
   `token_type`, `pending_email`, `cancel_token`.
2. **Happy path (logging provider):** log in, open ProfilePage → "Cambiar correo", submit a
   new address + current password. Confirm two links appear in the API logs (confirm → new
   address, cancel → old address). Hit the confirm link; assert `users.email` is updated,
   the session is dead (next authed call 401 → redirected to login), login now works with the
   new email and fails with the old, and a third (informational, no-link) email is logged to
   the OLD address confirming the change completed.
3. **Cancel path:** request a change, then hit the cancel link; assert the token row is gone
   and `users.email` is unchanged and still logs in.
4. **Guards:** OWNER account → **403** (env-managed email); wrong current password →
   rejected; new email equal to current → 400; new email
   already used by another account → 409; reusing a confirmed/expired/superseded link → 400/410;
   wrong token type — a registration `VERIFICATION` token to `/confirm-email-change`, a change
   token to `/verify-email`, or a confirm token to `/cancel-email-change` → 400.
5. **Automated:** service tests for `requestEmailChange` / `confirmEmailChange` /
   `cancelEmailChange` (Mockito BDD, matching existing verification tests), a `@WebMvcTest`
   for the three endpoints, and Vitest tests for `useChangeEmail` + the confirm/cancel pages
   (mirroring `useVerifyEmail`). Run the full existing suite afterward and report any test
   whose expectations shift.
