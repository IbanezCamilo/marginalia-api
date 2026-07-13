# Email Verification on Registration (Resend)

Approved design, 2026-07-13. Implemented alongside this commit; the companion frontend
changes live in `marginalia-web` (same-named branch).

## Context

Marginalia previously logged users in the moment they registered (`POST /api/auth/register`
set JWT + refresh cookies immediately), so nothing proved the email belonged to them. This
feature adds email verification using Resend, following `marginalia-web/resend-java-playbook-en.md`
and the project's existing conventions (Flyway, ProblemDetail, `@ConfigurationProperties`
records, Mockito BDD tests). It also introduces the project's first
`ApplicationEventPublisher` + `@TransactionalEventListener(AFTER_COMMIT)` usage.

## Decisions (confirmed with the user)

1. **Hard gating.** Registration returns 201 with NO cookies; login and refresh answer
   403 (`email-not-verified` ProblemDetail) until the emailed link is clicked.
2. **Backend + frontend.** API, email sending, and DB changes plus React screens
   (check-inbox, verify landing page, resend UI).
3. **Send-only v1.** No Resend webhooks; bounce/complaint handling is deferred. The
   `EmailService` interface and playbook §10 are the extension point.
4. **Token policy.** 24 h expiry, single active token, 60 s resend cooldown,
   5 sends per 24 h per account.
5. **Token mechanism.** DB-backed random token, SHA-256-hashed at rest, mirroring the
   `RefreshToken` pattern.

## Backend

- **`V5__add_email_verification.sql`** — `users.email_verified` (default false; existing
  rows backfilled to true) and `email_verification_tokens` (hash unique, FK to users,
  `created_at`, `expires_at`). Superseded tokens are expired, not deleted, so cooldown and
  daily cap derive from `created_at`; a 4 a.m. scheduled purge removes rows expired >24 h.
- **`EmailVerificationService`** (`services/auth`) — `requestVerificationEmail(User)`
  (throttled, publishes `VerificationEmailRequested`), `verify(rawToken)`
  (400 invalid / 410 expired / marks verified + deletes tokens), `resendVerification(email)`
  (silent no-op for unknown or already-verified accounts: anti-enumeration).
- **`VerificationEmailListener`** — `@Async @TransactionalEventListener(AFTER_COMMIT)`;
  builds `{frontend.url}/verify-email?token=...`; swallows and logs failures.
- **`EmailService`** — `ResendEmailService` (active when `email.provider=resend`; retries
  only 429/5xx/transport with 1s/2s/4s backoff; idempotency key `verify-email/<token-id>`;
  the 4.4.0 SDK exposes no status field, so the code is parsed from the exception message)
  and `LoggingEmailService` (default; logs the link, no API key needed).
- **Gating covers every token-issuing path**: register (no cookies), login (verified check
  AFTER successful authentication, outside the failed-attempt counter), refresh (check on
  rotation). Per-request JWT checks are unnecessary because unverified users can never
  obtain a token. Admin-created accounts and the OWNER seed pass `emailVerified=true`.
- **Endpoints** (`/api/auth`, permitAll): `POST /verify-email` (200/400/410),
  `POST /resend-verification` (always 200).
- **Config**: `email.provider=${EMAIL_PROVIDER:logging}`, `resend.api-key=${RESEND_API_KEY:}`,
  `resend.from=${RESEND_FROM:}`, `verification.token-expiration-hours|cooldown-seconds|daily-cap`.

## Frontend (`marginalia-web`)

- `emailVerificationService.js` (verify/resend), `useVerifyEmail` (on-mount verification,
  single-fire guard for the single-use token, distinguishes 410 expired from 400 invalid),
  `useResendVerification` (60 s client cooldown + Sonner toasts).
- `useRegister` navigates to `/auth/check-email` (no session). New pages `CheckEmailPage`
  (`/auth/check-email`) and `VerifyEmailPage` (`/verify-email`, matching the emailed link),
  both following the existing auth split-layout template.
- `useLogin` detects the `email-not-verified` ProblemDetail type and the login page offers
  a resend link.

## Out of scope

Resend webhooks (Svix), retroactive blocking of existing users, email-change
re-verification, password reset by email.

## Ops

Create a `sending_access` Resend API key; verify a sending subdomain (SPF + DKIM + DMARC);
set `EMAIL_PROVIDER=resend`, `RESEND_API_KEY`, `RESEND_FROM` in production. Free tier is
100 emails/day; the per-account cap protects it. Test against `delivered@resend.dev`,
never invented real-provider addresses.
