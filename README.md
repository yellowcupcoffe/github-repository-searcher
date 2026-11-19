# GitHub Repository Searcher

A Spring Boot REST API that searches GitHub repositories, stores the results in PostgreSQL
(inserting new ones and **updating existing ones**, never duplicating them) and lets you query the
stored data with filters, sorting and pagination.

## Features

- Search GitHub (`POST /api/github/search`) with optional language filter, sorting and paging
- Upsert into PostgreSQL by GitHub repository id (unique constraint + update-if-exists)
- Query stored repositories with `language`, `minStars`, sorting and pagination
- Get / delete a stored repository by id
- GitHub rate-limit handling (403/429 + `Retry-After`), timeouts, network and malformed-response handling
- Empty search results return `200` with an empty list
- Consistent JSON error responses, input validation, Swagger UI, health endpoint

## Tech stack

Java 21, Spring Boot 3.5, Spring Web, Spring Data JPA (Hibernate), PostgreSQL, `RestClient`,
Jakarta Bean Validation, Springdoc OpenAPI (Swagger UI), Actuator, JUnit 5, Mockito, Maven.

## Architecture

```
Client (Swagger UI / curl)
      |  JSON
      v
 GitHubController ---- validates input, returns DTOs
      |
      +--> GitHubService ------------> GitHubApiClient --> GitHub REST API
      |         |  (search workflow)    (only class that does HTTP)
      |         v
      +--> RepositoryService ---------> GitHubRepoRepository --> PostgreSQL
            (upsert, list, get, delete)   (Spring Data JPA)

 GlobalExceptionHandler: turns every exception into the same JSON error shape
```

| Package | Responsibility |
|---|---|
| `controller` | HTTP in/out only |
| `service` | Business logic (`GitHubService` = search flow, `RepositoryService` = stored data) |
| `client` | All communication with GitHub (+ `client.dto` = GitHub JSON shapes) |
| `repository` | Database access (Spring Data JPA, filter specifications) |
| `entity` | The `repositories` table as a Java class |
| `dto` | Request/response shapes of OUR API (entities are never exposed) |
| `mapper` | GitHub JSON <-> entity <-> response conversion |
| `exception` | Custom exceptions + `@RestControllerAdvice` |
| `config` | `RestClient`, typed GitHub settings, OpenAPI info |

## API

| Method | Path | Purpose |
|---|---|---|
| POST | `/api/github/search` | Search GitHub and save/update results |
| GET | `/api/github/repositories` | List stored repositories (filters, sort, paging) |
| GET | `/api/github/repositories/{id}` | One stored repository (404 if missing) |
| DELETE | `/api/github/repositories/{id}` | Delete (204, or 404) |
| GET | `/actuator/health` | Health check |
| GET | `/swagger-ui/index.html` | Swagger UI (Try it out) |

**Search body**

```json
{ "query": "spring boot", "language": "Java", "sort": "stars", "order": "desc", "page": 1, "perPage": 20 }
```

- `sort`: `stars`, `forks`, `updated` (omit for GitHub's best match). `order`: `asc`/`desc`.
- `page` is **1-based** (GitHub's convention), `perPage` 1-100.

**List query parameters**: `language`, `minStars`, `sortBy` (`stars|forks|name|updated|fetched`),
`direction` (`asc|desc`), `page` (**0-based**, Spring's convention), `size` (1-100).

**Error shape (all errors)**

```json
{ "timestamp": "2026-11-19T10:15:30Z", "status": 400, "error": "Bad Request",
  "message": "Validation failed", "path": "/api/github/search",
  "details": ["query: query must not be blank"] }
```

| Situation | Status |
|---|---|
| Invalid input | 400 |
| Repository id not found | 404 |
| Concurrent insert hit the unique constraint | 409 |
| GitHub query rejected (e.g. beyond first 1000 results) | 422 |
| GitHub rate limit (`Retry-After` header when known) | 429 |
| GitHub error / unreadable response / unreachable | 502 |
| GitHub timeout | 504 |

## Configuration (environment variables)

| Variable | Default | Meaning |
|---|---|---|
| `DB_HOST` | `localhost` | PostgreSQL host |
| `DB_PORT` | `5432` | PostgreSQL port |
| `DB_NAME` | `github_searcher` | Development database |
| `DB_USERNAME` | `postgres` | DB user |
| `DB_PASSWORD` | *(empty)* | DB password |
| `DB_SSLMODE` | `prefer` | Use `require` for hosted databases such as Neon |
| `GITHUB_TOKEN` | *(empty)* | Optional; raises GitHub's rate limit. Never logged. |
| `PORT` | `8080` | HTTP port (hosting platforms set this automatically) |
| `TEST_DB_*` | see LOCAL_SETUP.md | Separate test database settings |

See `.env.example`. No real credentials are committed.

## Run and test

Full Windows instructions are in **[LOCAL_SETUP.md](LOCAL_SETUP.md)**. In short:

```powershell
mvn clean test              # needs the PostgreSQL TEST database (see LOCAL_SETUP.md)
mvn spring-boot:run         # needs the development database
```

## Design decisions and assumptions

- **Upsert with Spring Data JPA** (look up by `github_id`, update or insert in one transaction) instead of
  PostgreSQL-specific `INSERT ... ON CONFLICT`. It is database-agnostic and easy to follow; the unique
  constraint on `github_id` is the safety net. If two requests insert the same repository at the same
  moment, PostgreSQL rejects one and the API answers `409` (no custom retry logic, on purpose).
- **`ddl-auto: update`**: Hibernate creates/extends the table on startup, so the project runs with zero
  manual SQL. This is fine for this assignment. In a production system I would use versioned migrations
  (Flyway or Liquibase) with `ddl-auto=validate`, because `update` cannot rename/drop columns safely and
  gives no history of schema changes. `create` / `create-drop` are never used.
- **Two services**: `GitHubService` is deliberately not transactional, so a slow GitHub call never holds a
  database connection; only `RepositoryService.upsertAll` runs in a transaction.
- **Tests use PostgreSQL only** (no H2, no Testcontainers). Repository tests need a local PostgreSQL *test*
  database; they run in a transaction that is rolled back, and a guard refuses to run unless the connected
  database name ends with `_test`. Unit tests for the service, client and controller need no database.
  Consequence: `mvn test` fails on a machine without that test database; to run only the DB-free tests use
  `mvn test "-Dtest=!GitHubRepoRepositoryTest"`.
- GitHub returns at most 1000 results per query, so `page * perPage > 1000` is rejected with 400.
- Searching always upserts every repository GitHub returns for that page and refreshes `last_fetched_at`.
- Language filters on stored data are case-insensitive exact matches.
- Delete is a hard delete.

## Deployment

The app is portable: it builds to one JAR and reads all settings from environment variables.

```
Build:  mvn clean package -DskipTests
Start:  java -jar target/github-repository-searcher-1.0.0.jar
```

Set `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD`, `DB_SSLMODE=require` (and optionally
`GITHUB_TOKEN`) on the hosting platform; it provides `PORT`. Free hosting may sleep when idle, so the first
request can be slow. Platform-specific steps will be added once the host is chosen.


