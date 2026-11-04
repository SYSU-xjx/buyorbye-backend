# Database migrations

Flyway runs these files in version order at application startup (`spring.flyway.enabled: true` in
`application.yml`). Neither migration issues `CREATE DATABASE` or `USE`. Docker Compose initializes
the database through the MySQL image's `MYSQL_DATABASE` setting. For a separately installed MySQL,
the JDBC URL uses `createDatabaseIfNotExist=true`; the configured application account must have the
MySQL `CREATE` privilege for this to work. See the repository README for account setup.

| File | Purpose |
|---|---|
| `V1__initial_schema.sql` | Full schema: 30 tables, 45 foreign keys, 34 `CHECK` constraints; minimum voter count defaults to 3 and every vote requires a reason |
| `V2__seed_reference_data.sql` | Reference data: 16 content tags and 5 plaza channels |

Table-by-table documentation lives in [`docs/database/README.md`](../../../../../docs/database/README.md).

## Rules

- A migration that has reached `main` is immutable. Change the schema by adding `V3__...`, never by
  editing an applied file — Flyway stores a checksum and refuses to start when it does not match.
- Keep DDL and reference data in separate migrations, so a deployment can skip seed data.
- Rules that span rows — at least one channel for a published plaza proposal, latest vote wins,
  membership eligibility — are enforced by the service layer, not by the schema.

## If you already ran the previous V1

The earlier `V1__initial_schema.sql` had 15 tables. If your local database has it applied, Flyway
reports a checksum mismatch after you pull this change. Reset the local database instead of
patching it:

```sh
docker compose down -v
docker compose up -d mysql
```

`-v` drops the `mysql_data` volume. That is safe while the project holds no real data.
