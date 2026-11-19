# Local setup (Windows)

All commands are for **PowerShell**.

## 1. Required software

| Software | Version | Check |
|---|---|---|
| JDK | 21 | `java -version` |
| Apache Maven | 3.9+ | `mvn -version` |
| PostgreSQL | 14+ (any recent) | `psql --version` |
| Git | any | `git --version` |

**JDK 21**: install Eclipse Temurin 21 from https://adoptium.net (the installer can set `JAVA_HOME` and PATH
for you). Open a NEW PowerShell and run `java -version`.

**Maven**: download the binary zip from https://maven.apache.org/download.cgi, extract it (for example to
`C:\tools\apache-maven`), add its `bin` folder to PATH, open a new PowerShell and run `mvn -version`.
(IntelliJ also bundles a Maven you can use from its Maven tool window.)

**Maven wrapper (optional, recommended before you push to GitHub)**: once Maven works, run
`mvn wrapper:wrapper` in the project folder. It creates `mvnw.cmd`, `mvnw` and `.mvn/`; commit them. After that
you can use `.\mvnw.cmd` instead of `mvn` in every command below.

**PostgreSQL**: install from https://www.postgresql.org/download/windows/ . Remember the password you set for
the `postgres` user. Keep the default port 5432. Make sure `psql` works (the installer's `bin` folder, e.g.
`C:\Program Files\PostgreSQL\17\bin`, must be on PATH).

## 2. Create the databases

You need TWO databases: one for running the app, one for automated tests (so tests can never touch your
development data). The test database name **must end with `_test`**.

```powershell
psql -U postgres
```

Then, inside `psql` (replace the password):

```sql
CREATE ROLE github_app LOGIN PASSWORD 'choose_a_password';
CREATE DATABASE github_searcher      OWNER github_app;
CREATE DATABASE github_searcher_test OWNER github_app;
\l
\q
```

(Making `github_app` the owner lets Hibernate create tables on PostgreSQL 15+. If you prefer, you can use the
`postgres` superuser for both databases instead and skip the `CREATE ROLE` line.)

The tables themselves are created automatically by Hibernate the first time the app/tests run.

## 3. Configure environment variables

Environment variables keep passwords out of the code. They are read by `application.yml`
(`${DB_PASSWORD}`) and `application-test.yml` (`${TEST_DB_PASSWORD}`).

For the current PowerShell window only:

```powershell
# development database (used by the running app)
$env:DB_HOST="localhost"
$env:DB_PORT="5432"
$env:DB_NAME="github_searcher"
$env:DB_USERNAME="github_app"
$env:DB_PASSWORD="choose_a_password"

# test database (used only by automated tests)
$env:TEST_DB_NAME="github_searcher_test"
$env:TEST_DB_USERNAME="github_app"
$env:TEST_DB_PASSWORD="choose_a_password"

# optional
$env:GITHUB_TOKEN=""
```

Defaults if you skip a variable: host `localhost`, port `5432`, dev DB `github_searcher`, test DB
`github_searcher_test`, user `postgres`, empty password. To keep variables permanently use
`setx DB_PASSWORD "choose_a_password"` (then open a new PowerShell), or set them in your IDE run configuration.

### Optional GitHub token

Without a token the app works but GitHub allows only about 10 search requests per minute. To raise the limit:
GitHub -> Settings -> Developer settings -> Personal access tokens -> generate a token with no special scopes
(public data only), then `$env:GITHUB_TOKEN="ghp_..."`. Never commit it; it is never logged.

## 4. Open in an IDE

- **IntelliJ IDEA**: File -> Open -> select the project folder (the one with `pom.xml`); set the Project SDK to
  JDK 21; add the environment variables in Run -> Edit Configurations -> GitHubSearchApplication ->
  Environment variables.
- **VS Code**: install the "Extension Pack for Java" and "Spring Boot Extension Pack", open the folder, and
  start PowerShell from VS Code so it uses the variables set above.

## 5. Run tests

```powershell
mvn clean test
```

- `GitHubRepoRepositoryTest` connects to the PostgreSQL **test** database and rolls back everything it writes.
- All other tests need no database.
- Run only the DB-free tests: `mvn test "-Dtest=!GitHubRepoRepositoryTest"`
- If you see `Repository tests must run against a database whose name ends with '_test'`, fix `TEST_DB_NAME`.
- If you see `password authentication failed` or `Connection refused`, check `TEST_DB_*` and that the
  PostgreSQL service is running.

## 6. Run the application

```powershell
mvn spring-boot:run
```

Then open:

- Swagger UI: http://localhost:8080/swagger-ui/index.html
- Health: http://localhost:8080/actuator/health  (expect `{"status":"UP"}`)

## 7. Package

```powershell
mvn clean package
java -jar target\github-repository-searcher-1.0.0.jar
```

(`mvn clean package` also runs the tests; add `-DskipTests` to skip them.)

## 8. Example requests

```powershell
# search + save
Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/github/search `
  -ContentType "application/json" `
  -Body '{"query":"spring boot","language":"Java","sort":"stars","order":"desc","page":1,"perPage":5}'

# list stored repositories
Invoke-RestMethod "http://localhost:8080/api/github/repositories?language=Java&minStars=100&sortBy=stars&direction=desc&page=0&size=10"

# one repository, then delete it
Invoke-RestMethod http://localhost:8080/api/github/repositories/1
Invoke-RestMethod -Method Delete http://localhost:8080/api/github/repositories/1
```

Or with curl (use `curl.exe`, because `curl` is a PowerShell alias):

```powershell
curl.exe -X POST http://localhost:8080/api/github/search -H "Content-Type: application/json" -d "{\"query\":\"spring boot\",\"language\":\"Java\"}"
```

Abbreviated search response:

```json
{ "githubQuery": "spring boot language:Java", "page": 1, "perPage": 5, "totalCount": 1234,
  "returnedCount": 5, "inserted": 5, "updated": 0,
  "results": [ { "id": 1, "githubId": 6296790, "fullName": "spring-projects/spring-boot",
                 "language": "Java", "stargazersCount": 70000 } ] }
```

Run the same search again: `inserted` becomes 0 and `updated` becomes 5 (no duplicates).

## 9. Using Neon (hosted PostgreSQL) instead of local

Neon shows a connection string like `postgresql://USER:PASSWORD@HOST/DBNAME?sslmode=require`. Split it into
`DB_HOST=HOST`, `DB_NAME=DBNAME`, `DB_USERNAME=USER`, `DB_PASSWORD=PASSWORD`, `DB_SSLMODE=require`
(we build the JDBC URL ourselves, so you never paste the `postgresql://` form). Keep running the automated
tests against your LOCAL test database.
