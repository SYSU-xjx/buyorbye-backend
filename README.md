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

Compose creates the database and application user from the `MYSQL_*` values in `.env`. The backend runs on your computer and connects to `localhost:3306` using `SPRING_DATASOURCE_*`.

Load `.env` into the environment, then start Spring Boot:

macOS / Linux:

```sh
set -a && source .env && set +a && ./mvnw spring-boot:run
```

Windows PowerShell:

```powershell
Get-Content .env | Where-Object { $_ -match '^[^#].*=' } | ForEach-Object {
    $name, $value = $_ -split '=', 2
    Set-Item -Path "Env:$name" -Value $value
}
./mvnw.cmd spring-boot:run
```

Check `http://localhost:8080/actuator/health`; API docs are at `http://localhost:8080/swagger-ui/index.html`.

Stop MySQL with `docker compose down`. Database files remain in the `mysql_data` volume. The `MYSQL_*` values are applied only when that volume is first initialized; changing `.env` later does not update an existing database or its accounts. Do not remove the volume unless you intend to delete the local database.

## Alternative: Install MySQL locally

Install MySQL Server 8.4 using the official installer for [macOS](https://dev.mysql.com/downloads/mysql/) or [Windows](https://dev.mysql.com/downloads/installer/), then start the MySQL service from the installer or operating system service manager. On macOS, the MySQL preference pane can start/stop the service; on Windows, use the Services app and start the MySQL service installed by the installer.

Connect with a MySQL client or MySQL Workbench as an administrator and create the project database and application account. Replace the example password with the same value set in `SPRING_DATASOURCE_PASSWORD` in `.env`:

```sh
mysql -u root -p
```

If the `mysql` command is not available in the terminal, connect with MySQL Workbench and run the SQL below in its query editor.

```sql
CREATE DATABASE IF NOT EXISTS buyorbye
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;
CREATE USER IF NOT EXISTS 'buyorbye_user'@'localhost'
  IDENTIFIED BY 'local-dev-change-me';
GRANT ALL PRIVILEGES ON buyorbye.* TO 'buyorbye_user'@'localhost';
```

For this option, `SPRING_DATASOURCE_URL` should point to `localhost:3306`, and `SPRING_DATASOURCE_USERNAME` / `SPRING_DATASOURCE_PASSWORD` must match the account created above. The `MYSQL_*` variables are only for Docker Compose and are not read by a separately installed MySQL Server. Run the backend using the platform-specific commands above. Do not start the Docker database at the same time if the local server already uses port `3306`.

## Initialize the schema from `init.sql`

Use this option when the team provides an `init.sql` file in this repository's root. That file has not been added yet. It must create the same schema as `src/main/resources/db/migration/V1__initial_schema.sql`; otherwise the Flyway baseline below would incorrectly mark V1 as applied.

First create the empty `buyorbye` database and application account using either setup above, and make sure MySQL is running. Do not start the backend before importing the SQL. From macOS/Linux, run in the backend directory:

```sh
set -a && source .env && set +a
mysql --host=127.0.0.1 --port=3306 --user="$SPRING_DATASOURCE_USERNAME" --password "$MYSQL_DATABASE" < init.sql
```

Enter the application user's password from `.env` when prompted. In MySQL Workbench, select the `buyorbye` schema, open `init.sql`, and execute the script instead.

Because the imported tables do not yet have a Flyway history record, start the backend once with Flyway's baseline options:

```sh
SPRING_FLYWAY_BASELINE_ON_MIGRATE=true SPRING_FLYWAY_BASELINE_VERSION=1 ./mvnw spring-boot:run
```

Flyway records version `1` as the baseline and skips `V1__initial_schema.sql`, avoiding duplicate table creation. Verify the `flyway_schema_history` table contains a successful `BASELINE` row at version `1`. After this first startup, stop the backend and use the normal startup command without the baseline environment variables.

For an empty database, do not import `init.sql` or enable baseline mode: start the backend normally and Flyway will create the schema from V1. Flyway remains enabled in both setups and applies future migrations such as V2 on later starts. It does not rerun completed migrations just because the project was pulled again.

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
