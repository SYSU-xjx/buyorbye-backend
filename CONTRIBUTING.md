# Contributing

The team reviews the API contract, permissions, approval state machine, and database model before parallel feature work. Track undecided product rules rather than encoding assumptions in migrations.

1. Branch from the latest `main` using `feature/<short-name>` or `fix/<short-name>`.
2. Keep Pull Requests focused and include relevant tests.
3. Add a new Flyway migration for schema changes; do not edit a migration already merged to `main`.
4. Run `./mvnw -B verify` locally.
5. Request one teammate review; merge only after required CI checks pass.

Never commit `.env`, passwords, tokens, signing keys, or production data.
