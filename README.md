# buy or bye Backend

Spring Boot REST API for Buy or Bye purchase proposals, reasoned `BUY` / `NOT_BUY` votes, and natural daily/weekly/monthly rankings, backed by MySQL.

## Requirements

- JDK 17
- Docker Desktop with Docker Compose for the recommended database setup, or MySQL 8.4 if installing it locally

Run all commands below from the `buyorbye-backend` directory. Create `.env` from the example before starting:

macOS / Linux:

```sh
cp .env.example .env
```

Windows PowerShell:

```powershell
Copy-Item .env.example .env
```

Keep `.env` local and never commit credentials.

## Recommended: Docker MySQL

This uses the same MySQL version and credentials for every group member on macOS and Windows. Install Docker Desktop, then run:

```sh
docker compose up -d mysql
```

The MySQL container creates the database and application user from the `MYSQL_*` values in `.env` on first initialization. Start the backend using the instructions below.

Stop MySQL with `docker compose down`. Database files remain in the `mysql_data` volume. The `MYSQL_*` values are applied only when that volume is first initialized; changing `.env` later does not update an existing database or its accounts. Do not remove the volume unless you intend to delete the local database.

## Alternative: Install MySQL locally

Install MySQL Server 8.4 using the official installer for [macOS](https://dev.mysql.com/downloads/mysql/) or [Windows](https://dev.mysql.com/downloads/installer/), then start the MySQL service from the installer or operating system service manager. On macOS, the MySQL preference pane can start/stop the service; on Windows, use the Services app and start the MySQL service installed by the installer.

Connect with a MySQL client or MySQL Workbench as an administrator and create the application account. The JDBC URL enables `createDatabaseIfNotExist=true`, so the first backend startup creates `buyorbye` and Flyway applies V1 and V2. The application account therefore needs the global MySQL `CREATE` privilege as well as privileges on the project database. Replace the example password with the same value set in `SPRING_DATASOURCE_PASSWORD` in `.env`:

```sh
mysql -u root -p
```

If the `mysql` command is not available in the terminal, connect with MySQL Workbench and run the SQL below in its query editor.

```sql
CREATE USER IF NOT EXISTS 'buyorbye_user'@'localhost'
  IDENTIFIED BY 'local-dev-change-me';
GRANT CREATE ON *.* TO 'buyorbye_user'@'localhost';
GRANT ALL PRIVILEGES ON buyorbye.* TO 'buyorbye_user'@'localhost';
```

For this option, set `SPRING_DATASOURCE_URL` in `.env` to `localhost:3306` with `createDatabaseIfNotExist=true`; set `SPRING_DATASOURCE_USERNAME` / `SPRING_DATASOURCE_PASSWORD` to the account created above. Spring Boot reads these values from `.env`. The `MYSQL_*` variables are only for Docker Compose and are not read by a separately installed MySQL Server. Do not start the Docker database at the same time if the local server already uses port `3306`.

V1 and V2 are the only schema setup files: V1 creates tables and V2 inserts reference data. They do not create a database. Flyway runs them automatically when the backend starts and does not rerun completed migrations on later starts.

## Start the backend

Start MySQL first using one of the setup options above. Run the backend from the `buyorbye-backend` directory; Spring Boot imports `.env` from this working directory. On startup, Flyway applies any pending migrations before the application serves requests.

From a terminal:

macOS / Linux:

```sh
./mvnw spring-boot:run
```

Windows PowerShell:

```powershell
./mvnw.cmd spring-boot:run
```

If your Windows default Java version is different, use the helper below. It selects JDK 17
from `JAVA_HOME` or your user `.jdks` directory, waits for the Docker MySQL health check,
and starts the backend without changing your global Java settings:

```powershell
.\scripts\dev.ps1
# Or provide a JDK 17 installation explicitly:
.\scripts\dev.ps1 -JdkHome 'C:\path\to\jdk-17'
# Build and verify with the same Java selection:
.\scripts\dev.ps1 -Action verify
```

From Android Studio or IntelliJ IDEA, open this backend project, select JDK 17, and run `com.hku.money.MoneyBackApplication`. Set the run configuration's working directory to the `buyorbye-backend` project root so the application can find `.env`.

Verify startup at `http://localhost:8080/actuator/health`; the API documentation is at `http://localhost:8080/swagger-ui/index.html`. Register and log in through `/api/auth`, then use the returned Bearer token with Swagger's Authorize button. MySQL credentials are not API credentials. Stop the backend with `Ctrl+C` in the terminal or the IDE Stop button.

User, authentication, check-in, wheel decisions and natural day/week/month rankings are implemented with MyBatis XML. Rankings are recomputed for current or historical periods; legacy weekly snapshot tables are unused. JPA/Hibernate remains available for other modules.

Business responses use `{ "code": 200, "message": "操作成功", "data": ... }`, with numeric
codes matching HTTP status (registration returns 201). Register/login/refresh use `/api/auth`;
personal APIs use `/api/users/me`, including `POST /check-in` for one check-in per Hong Kong
calendar day. A missed full day resets the current streak; the longest streak is retained.
The proposal creator can call `POST /api/purchase-proposals/{id}/wheel/spin` for an integer
`data` of 1 (buy), 2 (do not buy), or 3 (wait until tomorrow), without changing the proposal.
Rankings use `GET /api/rankings` and `/api/rankings/me` with `type=MOST_REJECTED`,
`HIGHEST_APPROVED_AMOUNT`, or `TOP_JUDGE`, `period=DAY|WEEK|MONTH`, optional `date=YYYY-MM-DD`,
and list pagination `page=1&size=20` (maximum size 100). Historical dates select their natural
period. Votes count once per voter/proposal per period by vote time, including cross-period changes.

Run `./mvnw -B verify` (JDK 17 and Docker required) for unit and isolated MySQL 8.4 integration
tests. Tests use disposable containers, not the development database. Generated logs, reports,
PDFs and local working notes are excluded from commits.

The Android emulator reaches the service at `http://10.0.2.2:8080`. Never commit `.env`, credentials, tokens, or production data.

## Project setup

- Java 17, Spring Boot 3.5.5, Maven Wrapper
- Modular monolith, MySQL 8.4 LTS, MyBatis XML, Spring Data JPA and Flyway
- Spring Security with BCrypt passwords, JWT access tokens and revocable refresh-token sessions
- Hibernate uses `ddl-auto=validate`; all schema changes belong in versioned Flyway migrations
- OpenAPI UI is available at `/swagger-ui/index.html`

The preliminary schema is in `src/main/resources/db/migration/V1__initial_schema.sql`. Add follow-up Flyway migrations for schema changes; do not edit a migration after it has been shared or applied.

The shared [system design](DESIGN.md) defines the candidate data model and confirmed voting rules.

## Team workflow

- Branch from `main`, open a Pull Request, and do not push directly to `main`.
- Run `./mvnw -B verify`; merge after one teammate approval and passing backend CI.
- Coordinate API and schema changes with the Android team.
- Never edit a migration already merged to `main`; add a new one instead.

See [CONTRIBUTING.md](CONTRIBUTING.md).
