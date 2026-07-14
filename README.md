<h1>
  <img src="https://raw.githubusercontent.com/IbanezCamilo/marginalia-web/main/src/shared/components/Icons/marginalia.svg" alt="Marginalia logo" width="48" height="48" style="vertical-align:middle; margin-right:10px;">
  Marginalia — Literary Blog · Backend
</h1>

REST API for **Marginalia**, a literary blogging platform: authentication, post lifecycle, categories, author promotion requests, moderation, and full admin management.

[![CI](https://github.com/IbanezCamilo/marginalia-api/actions/workflows/ci.yml/badge.svg)](https://github.com/IbanezCamilo/marginalia-api/actions/workflows/ci.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

Built with Java 21 and Spring Boot 3.5 on PostgreSQL, it serves the [Marginalia frontend](https://github.com/IbanezCamilo/marginalia-web) with stateless JWT auth in HttpOnly cookies, refresh-token rotation, per-IP rate limiting, and email verification.

```bash
git clone https://github.com/IbanezCamilo/marginalia-api.git && cd marginalia-api
cp .env.example .env   # fill in DB credentials, owner account, and JWT secret
# In psql: CREATE DATABASE marginalia;
./mvnw spring-boot:run
```

## Table of Contents

- [Features](#features)
- [Stack](#stack)
- [Getting Started](#getting-started)
- [Environment Variables](#environment-variables)
- [Image Storage Backend](#image-storage-backend)
- [Scripts](#scripts)
- [API Endpoints](#api-endpoints)
- [API Documentation](#api-documentation)
- [Architecture](#architecture)
- [Deployment Notes](#deployment-notes)
- [Testing](#testing)
- [License](#license)

## Features

**Public**
- Paginated post feed with optional category filter
- Individual post by slug
- Author profile pages with their published posts
- Category listing and detail

**Authentication & accounts**
- Register / login / logout with a short-lived JWT access token (15 min) in an HttpOnly cookie
- Refresh-token rotation (7-day refresh cookie, `POST /api/auth/refresh`)
- Email verification: 24 h token, resend with a 60 s cooldown and a daily cap of 5
- Account lockout after 5 consecutive failed logins (15 min)
- Editable profile: name, bio, profile picture, and password change
- Author promotion requests with history tracking
- Role hierarchy: `READER → AUTHOR → MODERATOR → ADMIN → OWNER`

**Authors** (`AUTHOR+`)
- Full post CRUD with cover image upload (local disk or Cloudflare R2)
- Post status lifecycle: DRAFT ↔ PUBLISHED, REJECTED → DRAFT
- Post content sanitized server-side (OWASP Java HTML Sanitizer)

**Moderators** (`MODERATOR+`)
- Moderation queue and post status decisions

**Admins** (`ADMIN+`)
- User management with pagination, search, role filtering, and password reset
- Unrestricted post status transitions, post reset, and hard delete
- Category create / update / delete
- Author request approval and rejection (promotes `READER → AUTHOR`)

**Hardening**
- Per-IP token-bucket rate limiting (Bucket4j): 10/min on auth, 60/min public reads, 30/min images, 10/h uploads, 5/h password changes, 120/min authenticated
- Security headers (HSTS, CSP, frame deny), CORS locked to the frontend origin
- RFC 7807 Problem Details error responses; correlation-ID request logging

## Stack

| Technology | Version | Role |
|---|---|---|
| Java | 21 | Runtime (virtual threads enabled) |
| Spring Boot | 3.5.15 | Application framework |
| Spring Security | 6 | Authentication & authorization |
| Spring Data JPA / Hibernate | — | Data access layer (PostgreSQL dialect) |
| PostgreSQL | — | Relational database |
| Flyway | — | Schema migrations (`src/main/resources/db/migration`) |
| JJWT | 0.12.6 | JWT generation & validation |
| Bucket4j | 8.7.0 | Rate limiting (token bucket) |
| OWASP Java HTML Sanitizer | — | Server-side post content sanitization |
| Resend | — | Transactional email (verification links) |
| AWS SDK v2 (S3) | — | Cloudflare R2 object storage client |
| springdoc-openapi | 2.8.9 | OpenAPI docs & Swagger UI (dev profile) |
| Lombok | — | Boilerplate reduction |
| Spring Actuator | — | Health & monitoring endpoints |
| H2 | — | In-memory database for tests |

## Getting Started

### Requirements

- Java 21
- Maven 3.9+ (or use the bundled `./mvnw` wrapper)
- PostgreSQL 15+
- Frontend running — see the [frontend repository](https://github.com/IbanezCamilo/marginalia-web)

### Steps

```bash
git clone https://github.com/IbanezCamilo/marginalia-api.git
cd marginalia-api
cp .env.example .env
# Edit .env with your database credentials and secrets
# Create the database: CREATE DATABASE marginalia;
./mvnw spring-boot:run
```

The API starts at `http://localhost:8080`. On first run, `DataInitializer` seeds the roles and the **owner** account from your `OWNER_EMAIL` / `OWNER_PASSWORD` values. Flyway builds the schema from the migrations in `src/main/resources/db/migration`.

> [!WARNING]
> **Migrating an existing local dev DB**: Flyway's `baseline-on-migrate` assumes an existing database already matches the entities and just marks it as version 1 without running `V1__baseline.sql`. If you had a local dev DB from before Flyway existed, it may be missing tables that were previously created lazily by `ddl-auto=update`. If `ddl-auto=validate` fails with a missing-table error, drop and recreate the local database so Flyway builds the schema from empty.

## Environment Variables

Copy `.env.example` → `.env` and fill in your values:

| Variable | Required | Description | Example / default |
|---|---|---|---|
| `DB_URL` | Yes | JDBC connection string | `jdbc:postgresql://localhost:5432/marginalia` |
| `DB_USERNAME` | Yes | Database user | `postgres` |
| `DB_PASSWORD` | Yes | Database password | `secret` |
| `OWNER_EMAIL` | Yes | Seed owner account email | `owner@example.com` |
| `OWNER_PASSWORD` | Yes | Seed owner account password | `changeme` |
| `FRONTEND_URL` | Yes | Allowed CORS origin; also used to build emailed verification links | `http://localhost:5173` |
| `JWT_SECRET` | Yes | Base64-encoded HMAC key (min 64 chars) | *(generate with `openssl rand -base64 64`)* |
| `JWT_EXPIRATION` | No | Access token TTL in ms | `900000` *(15 min)* |
| `JWT_REFRESH_EXPIRATION` | No | Refresh token TTL in ms | `604800000` *(7 days)* |
| `EMAIL_PROVIDER` | No | `logging` (default — writes the verification link to the log) or `resend` (real sends) | `logging` |
| `RESEND_API_KEY` | With `resend` | Resend API key | *(from Resend)* |
| `RESEND_FROM` | With `resend` | Verified sender address | `noreply@example.com` |
| `APP_BASE_URL` | No | Base URL for constructing image URLs (local storage) | `http://localhost:8080` |
| `STORAGE_ACTIVE` | No | Storage backend: `local` (default) or `r2` | `local` |
| `R2_ACCOUNT_ID` | With `r2` | Cloudflare account ID | `a1b2c3d4...` |
| `R2_ACCESS_KEY_ID` | With `r2` | R2 bucket-scoped token Access Key ID | *(from Cloudflare)* |
| `R2_SECRET_ACCESS_KEY` | With `r2` | R2 bucket-scoped token Secret Access Key | *(from Cloudflare)* |
| `R2_BUCKET_NAME` | With `r2` | Target R2 bucket | `marginalia-media` |
| `R2_PUBLIC_BASE_URL` | With `r2` | Custom-domain base URL used to build public image URLs | `https://assets.example.com` |
| `APP_COOKIE_SECURE` | No | `Secure` flag on auth cookies: `false` in dev (HTTP), `true` in prod (HTTPS) | `false` |

> [!NOTE]
> `.env` is in `.gitignore` and must never be committed. `app.cookie.domain` is set directly in `application.properties` — empty for local development, your domain for production.

## Image Storage Backend

Uploaded images go through the `StorageService` abstraction, with two implementations selected at runtime by `STORAGE_ACTIVE`:

- **`local`** (default) — files are written to `storage.local.upload-dir` (`uploads/`) and served by the backend at `/api/images/**`. No R2 variables are required.
- **`r2`** — files are uploaded to a [Cloudflare R2](https://developers.cloudflare.com/r2/) bucket over its S3-compatible API and served from `R2_PUBLIC_BASE_URL` (a custom domain). The `R2_*` variables must be set. Credentials come from a **bucket-scoped** R2 API token (never an Admin token) and must only ever live in the environment, never in the repo.

Switching back to local storage is a zero-code-change rollback: set `STORAGE_ACTIVE=local` (or unset it) and restart.

## Scripts

```bash
./mvnw spring-boot:run         # Start dev server on :8080
./mvnw test                    # Run full test suite (H2 in-memory)
./mvnw package                 # Build executable JAR → target/
./mvnw package -DskipTests     # Build without running tests
```

## API Endpoints

All application endpoints live under `/api`. Role requirements are hierarchical (`ADMIN` implies everything below it, `OWNER` above all).

### Auth — public

| Method & Path | Description |
|---|---|
| `POST /api/auth/register` | Create a READER account and send a verification email |
| `POST /api/auth/login` | Authenticate; set access + refresh JWT cookies |
| `POST /api/auth/refresh` | Rotate the refresh token; issue a new access token |
| `POST /api/auth/logout` | Clear auth cookies |
| `POST /api/auth/verify-email` | Verify an account from the emailed token |
| `POST /api/auth/resend-verification` | Resend the verification email (60 s cooldown, 5/day) |
| `GET /api/auth/verification-status` | Check whether an email is verified |

### Public content

| Method & Path | Description |
|---|---|
| `GET /api/public/posts` | Paginated published posts (optional `categoryId`) |
| `GET /api/public/posts/{slug}` | Single published post |
| `GET /api/public/categories` | All categories |
| `GET /api/public/categories/{slug}` | Category detail |
| `GET /api/public/authors/{id}` | Author profile |
| `GET /api/public/authors/{id}/posts` | Author's published posts (paginated) |
| `GET /api/images/{filename}` | Serve stored image (local storage mode) |

### Me — authenticated

| Method & Path | Access | Description |
|---|---|---|
| `GET /api/me/profile` | Authenticated | Current user profile |
| `PUT /api/me/profile` | Authenticated | Update profile |
| `PUT /api/me/profile/password` | Authenticated | Change own password |
| `POST /api/me/profile/image` | Authenticated | Upload profile picture |
| `DELETE /api/me/profile/image` | Authenticated | Delete profile picture |
| `POST /api/me/author-request` | READER | Submit author promotion request |
| `GET /api/me/author-request/active` | Authenticated | Current pending request |
| `GET /api/me/author-request/history` | Authenticated | Request history (paginated) |
| `GET /api/me/posts` | AUTHOR+ | List own posts (all statuses) |
| `GET /api/me/posts/{id}` | AUTHOR+ | Get own post |
| `POST /api/me/posts` | AUTHOR+ | Create draft post |
| `PUT /api/me/posts/{id}` | AUTHOR+ | Update post |
| `PATCH /api/me/posts/{id}/status` | AUTHOR+ | Change post status (restricted transitions) |
| `POST /api/me/posts/{id}/cover-image` | AUTHOR+ | Upload cover image |
| `DELETE /api/me/posts/{id}/cover-image` | AUTHOR+ | Delete cover image |
| `DELETE /api/me/posts/{id}` | AUTHOR+ | Delete post |

### Moderator — `MODERATOR+`

| Method & Path | Description |
|---|---|
| `GET /api/moderator/posts` | Moderation queue (paginated, optional `status` filter) |
| `PUT /api/moderator/posts/{id}/status` | Approve / reject a post (3rd rejection auto-archives) |

### Admin — `ADMIN+`

| Method & Path | Description |
|---|---|
| `GET /api/admin/users` | List users (paginated) |
| `GET /api/admin/users/search` | Search users by name or email |
| `GET /api/admin/users/role/{roleName}` | Filter users by role |
| `GET /api/admin/users/{id}` | Get user |
| `POST /api/admin/users` | Create user with any role |
| `PUT /api/admin/users/{id}` | Update user |
| `PUT /api/admin/users/{id}/password` | Reset a user's password |
| `DELETE /api/admin/users/{id}` | Delete user |
| `GET /api/admin/posts` | List all posts (optional `status` filter) |
| `PUT /api/admin/posts/{id}/status` | Change any post status (unrestricted) |
| `PUT /api/admin/posts/{id}/reset` | Reset a post to draft state |
| `DELETE /api/admin/posts/{id}` | Hard-delete post |
| `POST /api/admin/categories` | Create category |
| `PUT /api/admin/categories/{id}` | Update category |
| `DELETE /api/admin/categories/{id}` | Delete category |
| `GET /api/admin/author-requests` | List requests (optional `status` filter) |
| `GET /api/admin/author-requests/pending-count` | Count pending requests |
| `PUT /api/admin/author-requests/{id}/approve` | Approve request, promote to AUTHOR |
| `PUT /api/admin/author-requests/{id}/reject` | Reject request |

### Ops

| Method & Path | Access | Description |
|---|---|---|
| `GET /actuator/health` | Public | Health check (includes storage free-space indicator) |
| `GET /actuator/info` | Public | Application info |
| `GET /actuator/metrics` | ADMIN+ | Micrometer metrics |

## API Documentation

Interactive OpenAPI docs are generated by springdoc and **enabled only with the `dev` profile** (disabled by default in every other environment):

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

- Swagger UI: `http://localhost:8080/api/swagger-ui`
- OpenAPI JSON: `http://localhost:8080/api/docs`

## Architecture

```text
src/main/java/com/blog/blog_literario/
├── config/            # SecurityConfig, OpenApiConfig, R2Config, DataInitializer
│   └── properties/    # @ConfigurationProperties records (Jwt, Cookie, RateLimit, Storage…)
├── controllers/
│   ├── auth/          # AuthController
│   ├── posts/         # PublicPostController, MyPostController
│   ├── users/         # PublicAuthorController, MyProfileController
│   ├── categories/    # PublicCategoryController
│   ├── authorrequest/ # ReaderAuthorRequestController
│   ├── moderator/     # ModeratorPostController
│   ├── admin/         # AdminUserController, AdminPostController, AdminCategoryController, AdminAuthorRequestController
│   └── image/         # ImageController
├── services/          # Business logic, one package per feature (incl. email/, images/)
├── repositories/      # Spring Data JPA interfaces
├── model/             # JPA entities: User, Post, Category, Role, RefreshToken, AuthorRequest…
├── dto/               # Request/response DTOs per feature
├── security/          # JwtService, JwtAuthenticationFilter, RateLimitFilter, CorrelationIdFilter, CookieUtil
├── exception/         # GlobalExceptionHandler (RFC 7807 ProblemDetail), custom exceptions
└── utils/             # SlugUtils, ImageValidator, PostContentSanitizer, UserValidator
```

Controllers call services. Services call repositories. DTOs cross layer boundaries; entities do not leave the service layer. The database schema is owned by Flyway (`ddl-auto=validate`).

## Deployment Notes

> [!IMPORTANT]
> **Cookie domain topology**: auth cookies are issued with `SameSite=Lax`. For the [frontend](https://github.com/IbanezCamilo/marginalia-web) to send them on cross-origin `fetch()` calls with `credentials: 'include'`, `marginalia-web` and `marginalia-api` must share a registrable domain in production (e.g. `app.example.com` + `api.example.com`) — they cannot live on unrelated domains. Set the cookie domain accordingly.

> [!WARNING]
> **Rate limiting is per-instance**: `RateLimitFilter` keeps its token buckets in an in-process `ConcurrentHashMap`, not a shared store. This is fine at single-instance scale, but each horizontally-scaled instance enforces its own independent limits — a client could get up to `instances × limit` requests through. Move to a Redis-backed bucket store (Bucket4j supports this) before scaling out horizontally.

Only set `app.rate-limit.trust-forwarded-for=true` when deployed behind a reverse proxy that overwrites `X-Forwarded-For`; otherwise clients can spoof the header to bypass the auth rate limit.

## Testing

```bash
./mvnw test
```

59 test classes cover controllers (MockMvc slices), services, repositories (H2), security (JWT filter, rate-limit filter incl. encoded-path bypass, cookies), utilities, and a Flyway/entity consistency check. CI runs the full suite on every push and pull request to `master` ([workflow](.github/workflows/ci.yml)).

## License

[MIT](LICENSE) © Camilo Ibañez
