# Halloween CandyShare API

Java 8 / Spring Boot REST API. See the [project README](../README.md) for setup, full endpoints, database preservation, and Azure deployment requirements.

```powershell
mvn spring-boot:run
```

The local API listens on port 8080 and uses H2 memory unless database environment variables are supplied. `GET /api/listings` is public. No accounts or listings are automatically seeded.

## Reading order

1. `CandyShareApplication`: starts Spring Boot and scans `com.candyshare`.
2. `Account`, `CandyListing`, `Claim`: JPA entities in new, separate CandyShare tables.
3. `AccountRepository`, `ListingRepository`, `ClaimRepository`: database access; `findLocked` locks a listing row for writes.
4. `AccountService`: registration, hashing passwords, current-account lookup.
5. `SecurityConfig`, `AuthController`: Spring Security session login/logout, CSRF, CORS, current-user endpoint.
6. `SharingService`: transactions, ownership checks, availability, cancellation, completion, and response mapping.
7. `CandyController`: HTTP mappings and validated input. Posting/editing accept only `notes` and `region`.
8. `RegisterRequest`, `ListingRequest`, `ApiViews`: input fields and controlled public/private output. Entities are not serialized directly.
9. `ApiException`, `ApiError`, `GlobalExceptionHandler`: consistent HTTP errors.

Do not replace session identity with a client-supplied user ID, disable CSRF to fix a frontend error, or enable wildcard credentialed CORS. Browser requests must send cookies and fetch a fresh CSRF token before writes. Login accepts URL-encoded `email` and `password` rather than JSON; registration accepts JSON.

## Tests

```powershell
mvn --batch-mode --no-transfer-progress clean verify -DfailIfNoTests=true
```

Only use disposable test databases. Tests default to H2; GitHub Actions supplies PostgreSQL using `TEST_DATABASE_URL`, `TEST_DATABASE_USERNAME`, `TEST_DATABASE_PASSWORD`, and `TEST_HIBERNATE_DIALECT`. Production continues using the existing additive Hibernate `update` configuration; no old BookFlow data is automatically migrated or deleted.
