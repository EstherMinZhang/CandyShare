# Halloween CandyShare

**After Halloween, Give Your Extras. Take What You Need.**

Share your extra unopened Halloween candy with people and organizations in your community.

CandyShare replaces the BookFlow bookstore application in this repository. It is a simple neighborhood notes board: each note offers one whole bag of candy for a free offline handoff. Individuals and organizations use the same ordinary account.

## Implemented

- Registration, email/password sign-in, sign-out, and current-user lookup using Spring Security server-side sessions. Passwords use BCrypt; the browser receives an HttpOnly session cookie, not a password or bearer token.
- A single responsive English notes board with All notes, My notes, and My pickups tabs. Small dialogs handle posting, editing, note details, registration, and login.
- Search note text and filter by a case-insensitive exact pickup area.
- Create and edit available listings, withdraw available listings, reserve a whole bag, cancel an active reservation, and confirm handoff as the publisher.
- Posting and editing require only two fields: `notes` and `region`. Candy type, approximate amount, allergens, and timing can be described naturally in the note rather than separate questions.
- Only the publisher and current active claimant receive private contact and handoff details. Public list responses never include them. Completed and cancelled claims retain history but do not reveal a later claimant's contact information.
- Input validation, uniform JSON errors, CSRF protection, credentialed CORS with explicit allowed origins, backend ownership checks, and transactional row locking for state changes.
- Existing GitHub Actions build/deploy pipeline adapted to CandyShare. Existing Azure resource variables, deployment targets, and credentials remain in place.

The board intentionally uses text-only paper notes. There are no photo questions, image cards, upload controls, or separate brand/quantity/date fields. Existing image/metadata values remain stored for compatibility, but are not exposed as fields in the simplified API or UI.

Notes are public. The composer asks users to share unopened candy and keep phone numbers and exact addresses out of notes. Registration only asks for a name, email, and password; the email becomes the private pickup contact. The app does not certify wrapper condition, allergens, or the accuracy of user-written notes.

There are **no seeded accounts, sample listings, fake counters, or automatic demo-data inserts**. New databases start empty. Register two accounts in separate browsers (or a normal and private window) to try both sides of a pickup.

## Technology and structure

- Backend: Java 8, Spring Boot 2.7.18, Spring MVC, Spring Data JPA/Hibernate, Spring Security, Maven.
- Frontend: React, TypeScript, Vite, existing Lucide icons, CSS.
- Database: H2 for disposable local development/tests; PostgreSQL in CI and production.
- Hosting: existing Azure App Service, Azure Database for PostgreSQL, Azure Static Web Apps.

```text
backend/src/main/java/com/candyshare/
  Account, CandyListing, Claim         database entities
  *Repository                         Spring Data database access
  AccountService, SharingService      account and pickup business rules
  AuthController, CandyController    HTTP endpoints
  RegisterRequest, ListingRequest    validated input
  ApiViews                           explicit public/private response fields
  SecurityConfig                     session authentication, CSRF, CORS
  ApiError, ApiException,
  GlobalExceptionHandler             JSON error handling
frontend/src/
  App.tsx                            notes board, dialogs, and reusable UI components
  api.ts                             API types and credentialed requests
  styles.css                         responsive presentation
```

The layers stay deliberately small: Controller → Service → Repository. Request classes prevent clients from setting ownership or state; response classes avoid serializing password hashes and private entity relationships. Identity always comes from the authenticated session, never a supplied user ID.

## Run locally

Prerequisites: a Java 8 JDK, Maven, and Node.js 22 or a compatible version. The checkout directory may still be named `BookFlow`; renaming it or the GitHub repository is not required.

Terminal 1, from the repository root:

```powershell
cd backend
mvn spring-boot:run
```

Terminal 2:

```powershell
cd frontend
npm ci
npm run dev
```

Open the Vite URL, normally `http://127.0.0.1:5173`. Vite proxies `/api` to `http://localhost:8080`, so local session cookies and CSRF requests share the frontend origin. Leave `VITE_API_BASE_URL` unset/empty for this setup. If you have an old `.env`, check its API URL first.

Do not mix `localhost` and `127.0.0.1` between separate direct API/frontend URLs. Use the proxy, or consistently use the same hostname with an allowed origin. The default development backend uses H2 memory; restarting it removes local accounts/listings. Existing PostgreSQL data is unaffected by this default.

## Authentication and pickup flow

1. Register a publisher account with a name, email, and password. The email is the private pickup contact; it is not shown on the public board.
2. Write a note describing the candy and enter a general pickup area. For example: "A bag of unopened chocolate and gummies, about 30 pieces. Some contain peanuts. Pickup this weekend." Keep exact addresses and contact details out of this public text.
3. In another browser session, register a recipient and reserve that bag.
4. The recipient finds the publisher's contact in My pickups; the publisher finds the claimant's contact in My notes. There is no built-in chat or automatic notification. Arrange the exact place and time privately.
5. The claimant can cancel before handoff; the bag becomes available and the cancelled record remains.
6. After the real-world exchange, the publisher confirms handoff; both records become completed.

| Action | Listing transition | Claim result |
| --- | --- | --- |
| Reserve | AVAILABLE → RESERVED | New RESERVED record |
| Cancel | RESERVED → AVAILABLE | Active record → CANCELLED |
| Confirm handoff | RESERVED → COMPLETED | Active record → COMPLETED |
| Withdraw | AVAILABLE → WITHDRAWN | No new claim |

All these writes lock the listing row inside a database transaction. Only one request can change that listing at a time. Failed claim insertion rolls back the listing status. Repeated cancellation/completion is rejected. Reserved, completed, and withdrawn listings cannot be edited or deleted; there is no DELETE endpoint. A best-before date already stored on an older listing still prevents a new reservation when past; new notes have no structured date field.

## API

| Method | Endpoint | Access |
| --- | --- | --- |
| GET | `/api/auth/csrf` | Public; issues session CSRF token |
| POST | `/api/auth/register` | Public with CSRF; JSON registration |
| POST | `/api/auth/login` | Public with CSRF; URL-encoded `email`, `password` |
| POST | `/api/auth/logout` | CSRF-protected logout |
| GET | `/api/auth/me` | Current account only |
| GET | `/api/listings?query=&region=` | Public summaries |
| GET | `/api/listings/{id}` | Public detail, with private fields only for authorized participants |
| POST | `/api/listings` | Signed-in publisher |
| PUT | `/api/listings/{id}` | Owner, AVAILABLE only |
| POST | `/api/listings/{id}/withdraw` | Owner, AVAILABLE only |
| POST | `/api/listings/{id}/claims` | Signed-in non-owner, AVAILABLE only |
| POST | `/api/claims/{id}/cancel` | Current claimant only |
| POST | `/api/listings/{id}/complete` | Owner, RESERVED only |
| GET | `/api/me/listings` | Current account's listings |
| GET | `/api/me/claims` | Current account's claim history |

Mutating browser requests first fetch `/api/auth/csrf`, then send the returned `headerName`/`token`, always with `credentials: include`. The frontend refreshes this token for each write, including after login/logout. The server changes the session ID on login and expires idle sessions after 30 minutes.

Both `POST /api/listings` and `PUT /api/listings/{id}` accept just:

```json
{
  "notes": "A bag of unopened chocolate and gummies. Some contain peanuts. Pickup this weekend.",
  "region": "Burnaby"
}
```

Notes are required (maximum 2,000 characters), as is the area (maximum 100 characters). Owners and states are assigned by the server. The low-level registration API still accepts `contactDetails`; the simplified registration UI supplies the submitted email automatically.

## Database and existing data

The project retains its existing `spring.jpa.hibernate.ddl-auto=update` strategy. CandyShare uses new tables: `candyshare_users`, `candyshare_listings`, and `candyshare_claims`. Removed BookFlow Java entities are no longer mapped, but the old `books`, `customers`, `customer_orders`, and `order_items` tables/data are **not dropped, renamed, or converted**. Old customer records are not automatically accounts and have no generated passwords.

The notes-only API reuses the existing `description` column. When reading an older listing, its title is included with its description in the note. Editing writes the full note back and clears the redundant title. Other old metadata columns remain mapped and stored, avoiding destructive schema changes or incompatible NOT NULL inserts. On new notes, legacy quantity `0` means unspecified, not zero candy, and is never displayed; wrapper/allergen metadata is not treated as a new user declaration. No automatic data-clearing migration runs.

No production database operation is executed by editing or testing this repository. Back up an existing production database before deploying; preview the additive schema changes in staging. Hibernate `update` is not a versioned migration/rollback system. A managed migration process and recovery plan remain launch work. Tests use their own disposable database and `create-drop`; **never point `TEST_DATABASE_URL` at a real business database**.

To use PostgreSQL locally, configure the backend:

```powershell
$env:DATABASE_URL = "jdbc:postgresql://localhost:5432/your_existing_database"
$env:DATABASE_USERNAME = "your_username"
$env:DATABASE_PASSWORD = "your_password"
$env:HIBERNATE_DIALECT = "org.hibernate.dialect.PostgreSQLDialect"
mvn spring-boot:run
```

## Verification

```powershell
cd backend
mvn --batch-mode --no-transfer-progress clean verify -DfailIfNoTests=true
```

```powershell
cd frontend
npm ci
npm run build
```

Integration tests cover real registration/login/logout and CSRF session flow, password hashing, whole-bag pickup completion, private-field filtering, user isolation, validation, forbidden state changes, concurrent reservation/cancellation, rollback after failed claim creation, and preservation of an existing unmapped book table under schema update.

Local tests default to H2. CI supplies a temporary PostgreSQL 16 database via `TEST_DATABASE_*` and `TEST_HIBERNATE_DIALECT`; the legacy `bookflow_test` CI database/user names are retained for compatibility. A local H2 pass does not establish PostgreSQL or Azure verification. No separate browser/unit frontend test suite is currently configured; TypeScript/build checks do not replace browser testing.

## Existing Azure deployment and CI/CD

The workflow remains `.github/workflows/ci-cd.yml`. Pushes and PRs targeting `main` run backend tests and frontend builds. Only non-PR runs on `main` with `AZURE_DEPLOY_ENABLED=true` can deploy, after both checks pass. The backend artifact is now `candyshare-backend-*.jar`; the deployment smoke check is `/api/listings`.

Existing Azure resource names and database names do not need to change. Preserve repository variables `AZURE_WEBAPP_NAME`, `AZURE_CLIENT_ID`, `AZURE_TENANT_ID`, `AZURE_SUBSCRIPTION_ID`, and `VITE_API_BASE_URL`, plus secret `AZURE_STATIC_WEB_APPS_API_TOKEN`. Existing OIDC configuration uses the GitHub `production` environment. The application does not create or rename cloud resources.

Before an approved deployment:

1. Back up the database and verify the new application in staging against PostgreSQL. Retain current resource credentials; do not copy secrets into the repository.
2. In App Service set `SPRING_PROFILES_ACTIVE=production`, `DATABASE_URL` (JDBC PostgreSQL URL with TLS, e.g. `?sslmode=require`), `DATABASE_USERNAME`, `DATABASE_PASSWORD`, and `CORS_ALLOWED_ORIGINS` (exact frontend HTTPS origin, no trailing slash).
3. Build the frontend with `VITE_API_BASE_URL` set to the existing backend's HTTPS origin, without a trailing slash. This is a build-time value, not a secret.
4. Production session cookies are Secure, HttpOnly, and default to `SameSite=None` for separate Azure origins. `SESSION_COOKIE_SAME_SITE` can override this setting. Configure HTTPS and forwarded headers correctly. App Service should run the new executable JAR on the expected platform port; review any custom startup command that still references `bookflow-backend`.
5. **Validate real browser cookies before enabling production deployment.** Unrelated `azurestaticapps.net` and `azurewebsites.net` domains can trigger third-party-cookie restrictions. `SameSite=None` alone does not bypass them. Prefer same-site custom domains (for example `share.example.com` and `api.example.com`) on the existing resources, or an explicitly configured same-origin proxy. Exact CORS origins must match the chosen setup.
6. Sessions are stored in application memory. A restart/deployment signs users out. Use one App Service instance for this version; multi-instance deployment needs a shared session store and additional testing.
7. Enable `AZURE_DEPLOY_ENABLED` only after resource configuration and approval. Then review GitHub Actions, API responses, sign-in, reservation, cancellation, handoff, and privacy behavior in the deployed browsers.

## Remaining public-launch work

This is a functional first implementation, **not a claim that a public launch or Azure deployment has been validated**. Pending work includes production browser/cookie verification, PostgreSQL CI execution for this change, a supported framework/runtime security review (the existing Java 8/Spring Boot 2.7 versions were retained), login/registration abuse throttling, password recovery/account lifecycle, backups and migration recovery, operational monitoring, and a privacy/retention policy.

Email verification, password reset, organization certification, image uploads, chat, payments, deliveries, maps, AI recommendations, points, and moderation workflows are not implemented. Users must avoid posting exact addresses or contact details in public notes; the application cannot reliably detect such free text. Structured allergen/quantity/date collection was intentionally removed to keep the notes flow simple.
