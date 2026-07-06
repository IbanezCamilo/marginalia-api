<h1>
  <img src="https://raw.githubusercontent.com/IbanezCamilo/marginalia-web/main/src/shared/components/Icons/marginalia.svg" alt="Marginalia logo" width="48" height="48" style="vertical-align:middle; margin-right:10px;">
  Marginalia — Literary Blog · Backend
</h1>

REST API for **Marginalia**, a literary blogging platform. Handles authentication, post lifecycle management, categories, author promotion requests, and full admin moderation.

## Stack

| Technology | Version | Role |
|---|---|---|
| Java | 21 | Runtime (virtual threads) |
| Spring Boot | 3.5.15 | Application framework |
| Spring Security | — | Authentication & authorization |
| Spring Data JPA / Hibernate | — | Data access layer (PostgreSQL dialect) |
| PostgreSQL | — | Relational database |
| Flyway | — | Schema migrations (`src/main/resources/db/migration`) |
| JJWT | 0.11.5 | JWT generation & validation |
| Bucket4j | 8.7.0 | Rate limiting (token bucket) |
| Lombok | — | Boilerplate reduction |
| Spring Actuator | — | Health & monitoring endpoints |

## Features

**Public**
- Paginated post feed with optional category filter
- Individual post by slug
- Author profile pages with their published posts
- Category listing and detail

**Authenticated users**
- Register / login / logout with JWT in HttpOnly cookie
- Edit profile: name, bio, and profile picture
- Submit and track author promotion requests
- Role system: `READER → AUTHOR → MODERATOR → ADMIN`

**Authors** (`AUTHOR+`)
- Full post CRUD with cover image upload
- Post status lifecycle: DRAFT ↔ PUBLISHED, REJECTED → DRAFT
- Rate-limited auth endpoints: 10 requests/min per IP

**Admin panel** (`ADMIN`)
- User management with pagination, search, and role filtering
- Unrestricted post status transitions and hard delete
- Category create / update / delete
- Author request approval and rejection (promotes `READER → AUTHOR`)

## Architecture

```
src/main/java/com/blog/blog_literario/
├── config/            # SecurityConfig, DataInitializer, @ConfigurationProperties records
├── controllers/
│   ├── admin/         # AdminUserController, AdminPostController, AdminCategoryController, AdminAuthorRequestController
│   ├── auth/          # AuthController
│   ├── posts/         # PublicPostController, MyPostController
│   ├── users/         # PublicAuthorController, MyProfileController
│   ├── authorrequest/ # ReaderAuthorRequestController
│   └── image/         # ImageController
├── services/          # Business logic, one package per feature
├── repositories/      # Spring Data JPA interfaces
├── model/             # JPA entities: User, Post, Category, Role, AuthorRequest
├── dto/               # Request/response DTOs per feature
├── security/          # JwtService, JwtAuthenticationFilter, RateLimitFilter, CookieUtil
├── exception/         # GlobalExceptionHandler (RFC 9457 ProblemDetail), custom exceptions
└── utils/             # SlugUtils, ImageValidator, FileNameGenerator, UserValidator
```

Controllers call services. Services call repositories. DTOs cross layer boundaries; entities do not leave the service layer.

## Getting Started

### Requirements
- Java 21
- Maven 3.9+
- PostgreSQL 15+
- Frontend running — see [frontend repository](https://github.com/IbanezCamilo/marginalia-web)

### Steps

```bash
git clone https://github.com/IbanezCamilo/marginalia-api.git
cd marginalia-api
cp .env.sample .env
# Edit .env with your database credentials and secrets
# Create the database: CREATE DATABASE marginalia;
./mvnw spring-boot:run
```

The API server starts at `http://localhost:8080`. On first run, `DataInitializer` seeds the four roles and the default admin account from your `.env` values.

> **Migrating an existing local dev DB**: Flyway's `baseline-on-migrate` assumes an
> existing database already matches the entities and just marks it as version 1 without
> running `V1__baseline.sql`. If you already had a local dev DB from before Flyway existed,
> it may be missing tables added since you last ran the app (`ddl-auto=update` used to
> create them lazily). If `ddl-auto=validate` fails with a missing-table error, drop and
> recreate the local database so Flyway builds the schema from empty instead.

## Environment Variables

Copy `.env.sample` → `.env` and fill in your values:

| Variable | Description | Example |
|---|---|---|
| `DB_URL` | JDBC connection string | `jdbc:postgresql://localhost:5432/marginalia` |
| `DB_USERNAME` | Database user | `postgres` |
| `DB_PASSWORD` | Database password | `secret` |
| `ADMIN_EMAIL` | Seed admin account email | `admin@example.com` |
| `ADMIN_PASSWORD` | Seed admin account password | `changeme` |
| `FRONTEND_URL` | Allowed CORS origin | `http://localhost:5173` |
| `JWT_SECRET` | Base64-encoded HMAC key (min 64 chars) | *(generate with `openssl rand -base64 64`)* |
| `JWT_EXPIRATION` | Token TTL in milliseconds | `86400000` *(24 h)* |
| `APP_BASE_URL` | Base URL for constructing image URLs | `http://localhost:8080` |
| `STORAGE_ACTIVE` | Storage backend: `local` (default) or `r2` | `local` |
| `R2_ACCOUNT_ID` | Cloudflare account ID *(only when `STORAGE_ACTIVE=r2`)* | `a1b2c3d4...` |
| `R2_ACCESS_KEY_ID` | R2 bucket-scoped token Access Key ID | *(from Cloudflare)* |
| `R2_SECRET_ACCESS_KEY` | R2 bucket-scoped token Secret Access Key | *(from Cloudflare)* |
| `R2_BUCKET_NAME` | Target R2 bucket | `marginalia-media` |
| `R2_PUBLIC_BASE_URL` | Custom-domain base URL used to build public image URLs | `https://assets.example.com` |

> `.env` is in `.gitignore` and must never be committed.

### Image storage backend

Uploaded images are stored through the `StorageService` abstraction, which has two
implementations selected at runtime by `STORAGE_ACTIVE`:

- **`local`** (default) — files are written to `storage.local.upload-dir` (`uploads/`) and
  served by the backend at `/api/images/**`. No R2 variables are required.
- **`r2`** — files are uploaded to a [Cloudflare R2](https://developers.cloudflare.com/r2/)
  bucket over its S3-compatible API and served from `R2_PUBLIC_BASE_URL` (a custom domain).
  The `R2_*` variables must be set. Credentials come from a **bucket-scoped** R2 API token
  (never an Admin token) and must only ever live in the environment, never in the repo.

Switching back to local storage is a zero-code-change rollback: set `STORAGE_ACTIVE=local`
(or unset it) and restart.

`app.cookie.secure` and `app.cookie.domain` are set directly in `application.properties` — `false` / empty for local development, `true` / your domain for production.

## Deployment Notes

**Cookie domain topology**: auth cookies are issued with `SameSite=Lax`. For the
[frontend](https://github.com/IbanezCamilo/marginalia-web) to send them on cross-origin
`fetch()` calls with `credentials: 'include'`, `marginalia-web` and `marginalia-api` must
share a registrable domain in production (e.g. `app.example.com` + `api.example.com`) —
they cannot live on unrelated domains. Set `APP_COOKIE_DOMAIN` accordingly.

**Rate limiting is per-instance**: `RateLimitFilter` keeps its token buckets in an
in-process `ConcurrentHashMap`, not a shared store. This is fine at single-instance scale,
but each horizontally-scaled instance enforces its own independent limits — a client could
get up to `instances × limit` requests through. Move to a Redis-backed bucket store
(Bucket4j supports this) before scaling out horizontally.

## Scripts

```bash
./mvnw spring-boot:run         # Start dev server on :8080
./mvnw test                    # Run full test suite (H2 in-memory)
./mvnw package                 # Build executable JAR → target/
./mvnw package -DskipTests     # Build without running tests
```

## API Endpoints

| Method & Path | Access | Description |
|---|---|---|
| `POST /api/auth/register` | Public | Create a READER account, set JWT cookie |
| `POST /api/auth/login` | Public | Authenticate and set JWT cookie |
| `POST /api/auth/logout` | Public | Clear JWT cookie |
| `GET /api/public/posts` | Public | Paginated published posts (optional `categoryId`) |
| `GET /api/public/posts/{slug}` | Public | Single published post |
| `GET /api/public/categories` | Public | All categories |
| `GET /api/public/categories/{slug}` | Public | Category detail |
| `GET /api/public/authors/{id}` | Public | Author profile |
| `GET /api/public/authors/{id}/posts` | Public | Author's published posts (paginated) |
| `GET /api/images/{filename}` | Public | Serve stored image |
| `GET /api/me/profile` | Authenticated | Current user profile |
| `PUT /api/me/profile` | Authenticated | Update profile |
| `POST /api/me/profile/image` | Authenticated | Upload profile picture |
| `DELETE /api/me/profile/image` | Authenticated | Delete profile picture |
| `POST /api/me/author-request` | READER | Submit author promotion request |
| `GET /api/me/author-request/active` | READER | Current pending request |
| `GET /api/me/author-request/history` | Authenticated | Request history (paginated) |
| `GET /api/me/posts` | AUTHOR+ | List own posts (all statuses) |
| `POST /api/me/posts` | AUTHOR+ | Create draft post |
| `GET /api/me/posts/{id}` | AUTHOR+ | Get own post |
| `PUT /api/me/posts/{id}` | AUTHOR+ | Update post |
| `PATCH /api/me/posts/{id}/status` | AUTHOR+ | Change post status (restricted transitions) |
| `POST /api/me/posts/{id}/cover-image` | AUTHOR+ | Upload cover image |
| `DELETE /api/me/posts/{id}/cover-image` | AUTHOR+ | Delete cover image |
| `DELETE /api/me/posts/{id}` | AUTHOR+ | Delete post |
| `GET /api/admin/users` | ADMIN | List users (paginated) |
| `GET /api/admin/users/search` | ADMIN | Search users by name or email |
| `GET /api/admin/users/role/{roleName}` | ADMIN | Filter users by role |
| `POST /api/admin/users` | ADMIN | Create user with any role |
| `PUT /api/admin/users/{id}` | ADMIN | Update user |
| `DELETE /api/admin/users/{id}` | ADMIN | Delete user |
| `GET /api/admin/posts` | ADMIN | List all posts (optional `status` filter) |
| `PUT /api/admin/posts/{id}/status` | ADMIN | Change any post status (unrestricted) |
| `DELETE /api/admin/posts/{id}` | ADMIN | Hard-delete post |
| `POST /api/admin/categories` | ADMIN | Create category |
| `PUT /api/admin/categories/{id}` | ADMIN | Update category |
| `DELETE /api/admin/categories/{id}` | ADMIN | Delete category |
| `GET /api/admin/author-requests` | ADMIN | List requests (optional `status` filter) |
| `GET /api/admin/author-requests/pending-count` | ADMIN | Count pending requests |
| `PUT /api/admin/author-requests/{id}/approve` | ADMIN | Approve request, promote to AUTHOR |
| `PUT /api/admin/author-requests/{id}/reject` | ADMIN | Reject request |
| `GET /actuator/health` | Public | Health check |
| `GET /actuator/info` | Public | Application info |

---

## License

MIT License

Copyright (c) 2025 Camilo Ibañez

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
