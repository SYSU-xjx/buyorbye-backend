# buy or bye Backend

Spring Boot REST API for the Buy or Bye Android app, backed by MySQL.

## Requirements

- JDK 17
- Docker Desktop with Docker Compose

## Start locally

1. Copy `.env.example` to `.env`; change local passwords if needed.
2. Start MySQL: `docker compose up -d mysql`.
3. Start the service: `set -a && source .env && set +a && ./mvnw spring-boot:run`.
4. Check `http://localhost:8080/actuator/health` and openAPI docs at `http://localhost:8080/swagger-ui/index.html`.

The Android emulator reaches the service at `http://10.0.2.2:8080`. Never commit `.env`, credentials, tokens, or production data.

## Project setup

- Java 17, Spring Boot 3.5.5, Maven Wrapper
- Modular monolith, MySQL 8.4 LTS, Spring Data JPA and Flyway
- Spring Security, BCrypt and JWT dependencies are selected for authentication work
- Hibernate uses `ddl-auto=validate`; all schema changes belong in versioned Flyway migrations
- OpenAPI UI is available at `/swagger-ui/index.html`

The initial schema and business API are intentionally left for the team's shared architecture review. Add the first Flyway migration after entities, constraints, permissions, and API contracts are agreed.

## Team workflow

- Branch from `main`, open a Pull Request, and do not push directly to `main`.
- Run `./mvnw -B verify`; merge after one teammate approval and passing backend CI.
- Coordinate API and schema changes with the Android team.
- Never edit a migration already merged to `main`; add a new one instead.

See [CONTRIBUTING.md](CONTRIBUTING.md).
