# Database migrations

Flyway runs these files in version order at application startup (`spring.flyway.enabled: true` in
`application.yml`). The database itself is created by `compose.yaml` through `MYSQL_DATABASE`, so
migrations never issue `CREATE DATABASE` or `USE` — the same files work against any schema name.

| File | Purpose |
|---|---|
| `V1__initial_schema.sql` | Full schema: 30 tables, 38 foreign keys, all `CHECK` constraints |
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
