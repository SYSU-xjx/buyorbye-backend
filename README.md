# buy or bye Backend

Spring Boot REST API for Buy or Bye purchase proposals, reasoned `BUY` / `NOT_BUY` votes, and weekly rankings, backed by MySQL.

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

From Android Studio or IntelliJ IDEA, open this backend project, select JDK 17, and run `com.hku.money.MoneyBackApplication`. Set the run configuration's working directory to the `buyorbye-backend` project root so the application can find `.env`.

Verify startup at `http://localhost:8080/actuator/health`; the API documentation is at `http://localhost:8080/swagger-ui/index.html`. If prompted to sign in, use the temporary Spring Security username `user` and the password printed as `Using generated security password:` in the application startup log. This password changes on restart. The `SPRING_DATASOURCE_USERNAME` and `SPRING_DATASOURCE_PASSWORD` values in `.env` are for MySQL, not Swagger UI. Stop the backend with `Ctrl+C` in the terminal or the IDE Stop button.

The Android emulator reaches the service at `http://10.0.2.2:8080`. Never commit `.env`, credentials, tokens, or production data.

## Project setup

- Java 17, Spring Boot 3.5.5, Maven Wrapper
- Modular monolith, MySQL 8.4 LTS, Spring Data JPA and Flyway
- Spring Security, BCrypt and JWT dependencies are selected for authentication work
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
