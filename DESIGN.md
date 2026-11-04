# Buy or Bye System Design

**Status:** Architecture baseline for team review
**Last updated:** 2026-11-03
**Repositories:** [`buyorbye-android`](https://github.com/SYSU-xjx/buyorbye-android) and [`buyorbye-backend`](https://github.com/SYSU-xjx/buyorbye-backend)

## 1. Purpose and Scope

`Buy or Bye` is an Android application for collaborative purchase decisions. A user posts an item they are unsure about buying, explains why they want it, and asks a group or the public plaza to decide whether to buy it. Each voter must explain their choice. The application also provides groups, personal records, reputation, weekly rankings, content controls, and a local decision wheel. These posts are purchase proposals, not formal approval requests.

The first implementation covers:

- Home dashboard and purchase proposal creation/drafts
- Group creation, invitation, membership, and group purchase proposals
- Public plaza feeds, channels, filtering, and interactions
- Personal profile and activity records
- Reputation and voting-weight display
- Weekly rankings with cross-currency conversion to HKD
- Local decision-wheel data on the Android device
- Authentication, authorization, loading states, error states, and empty states

Product details that are not explicitly settled in this document remain **To Be Confirmed (TBC)** and must not be encoded in a migration or API implementation without review.

## 2. Repositories and Technology Baseline

| Area | Choice |
|---|---|
| Android repository | `SYSU-xjx/buyorbye-android` |
| Backend repository | `SYSU-xjx/buyorbye-backend` |
| Android language | Java 17 |
| Android UI | One Activity, Fragments, XML layouts, Material components |
| Android baseline | `minSdk 24`, `compileSdk 37`, `targetSdk 37` |
| Android data/network | ViewModel/LiveData, Navigation Component, Retrofit, OkHttp, Gson, Room |
| Backend | Java 17, Spring Boot 3.5.5, modular monolith |
| Persistence | MySQL 8.4 LTS, Spring Data JPA, Flyway |
| Authentication | Spring Security, BCrypt, JWT (implementation pending) |
| API description | REST/JSON with OpenAPI |
| Local database | Docker Compose |
| Build checks | Android Gradle checks and Maven `verify` |

## 3. System Architecture

```mermaid
flowchart LR
    A[Android app<br/>Java + XML] -->|HTTPS REST/JSON| B[Spring Boot API<br/>modular monolith]
    B --> C[(MySQL 8.4)]
    B --> D[Flyway migrations]
    B --> E[Exchange-rate provider<br/>Frankfurter / ECB reference rates]
    A --> F[(Room<br/>local decision wheel)]
```

The Android app owns presentation state and the local decision wheel. The backend owns identity, authorization, purchase proposal state, voting, reputation, rankings, currency-rate snapshots, and server-side audit data. MySQL is the source of truth for server data. The backend may cache exchange rates later, but cached values must retain their source date and cannot silently rewrite historical results.

The backend remains a single deployable service. Packages are separated by business capability so that the team can work in parallel without introducing operational complexity from multiple services.

Suggested package boundaries:

```text
com.hku.money
  auth/
  user/
  group/
  proposal/
  plaza/
  reputation/
  ranking/
  exchange/
  feedback/
  common/
```

## 4. Android Client Structure

The initial shell uses four bottom-navigation destinations:

1. Home
2. Plaza
3. Groups
4. Profile

The decision wheel is entered from Home and stores its first-version data locally with Room. Server-backed screens should follow this flow:

```text
Fragment -> ViewModel -> Repository -> Retrofit service -> REST API
                         |                         |
                         +--> LiveData             +--> loading/error mapping
```

The emulator uses `http://10.0.2.2:8080/api/` to reach a backend running on the host machine. Production and physical-device URLs must be supplied through environment/build configuration and must not be committed with credentials.

## 5. Purchase Proposal and Voting Workflow

A `PurchaseProposal` has exactly one publication scope: `GROUP` or `PLAZA`. A draft is the same record in `DRAFT` status and may be incomplete. Publishing requires an item name, exact amount, currency, nonblank purchase reason, scope, deadline, minimum voter count, and one to three predefined content tags. An alternative-product tip is optional. A plaza proposal must belong to one or more public channels; a group proposal belongs to one group and no plaza channels. The creator cannot vote on their own proposal. A proposal uses one participation type: binary (`BINARY`, buy or not buy) or option selection (`OPTION`, choose one of one to six options).

```mermaid
stateDiagram-v2
    [*] --> DRAFT
    DRAFT --> OPEN: publish
    OPEN --> OPEN: eligible user casts or changes a reasoned vote
    OPEN --> WITHDRAWN: creator withdraws before deadline
    OPEN --> SETTLED: deadline + minimum voters reached
    OPEN --> INSUFFICIENT_PARTICIPATION: deadline + minimum voters not reached
    SETTLED --> [*]
    WITHDRAWN --> [*]
    INSUFFICIENT_PARTICIPATION --> [*]
```

Confirmed rules:

- The creator chooses a deadline from one hour to seven days after publishing; the default is 24 hours.
- The creator sets a minimum number of participating voters; the default is 1. For a group proposal, the threshold cannot exceed the number of currently eligible group members other than the creator when published.
- Eligible users submit a nonblank reason with each vote. Binary voters choose `BUY` or `NOT_BUY`; option voters choose one proposal option. They may change their choice and reason before the deadline, but cannot withdraw a vote. Each change appends a `DecisionVote` event; only that user's latest event for the proposal counts.
- Group proposals are votable by current group members; plaza proposals are votable by authenticated users. Eligibility is checked when voting. A group member's existing vote remains valid after they leave, but they cannot cast another vote after leaving.
- The creator is excluded from the voter count and weighted result.
- Each vote event stores the voter's reputation level and weight snapshot at the time of that event.
- The minimum voter count uses distinct users' latest valid votes. For a binary proposal, at the deadline a strictly greater than half weighted `BUY` vote produces result `BUY`; a tie or non-majority produces `NOT_BUY`. For an option proposal, settlement stores the winning option; the detailed option tally and tie rule must be specified in the API/schema contract before implementation.
- Lifecycle status and result are separate: `DRAFT`, `OPEN`, `SETTLED`, `WITHDRAWN`, and `INSUFFICIENT_PARTICIPATION` are statuses. A settled binary proposal has `decision_result`; a settled option proposal has `winning_option_id`. Results are unset before settlement and for withdrawn or insufficient-participation proposals.
- The creator may edit the item, amount, purchase reason, and images only before the first vote. Once anyone has voted, those fields are frozen; the creator can still withdraw before the deadline and create a new proposal.
- A proposal can have up to four optional images. Image records store an object-storage URL and display order; the provider and per-image size limit are TBC.
- A proposal may have one to six options when its participation type is `OPTION`; each option may have its own exact amount. `BINARY` proposals do not have poll options. Settlement stores a binary `decision_result` or a `winning_option_id`, according to participation type.
- Public channels are system-managed; users may subscribe to them. One plaza proposal may appear in multiple channels, but it has one vote pool, one voter threshold, and one result. An all-plaza feed shows it once even if it belongs to multiple channels.
- Watching a proposal, commenting, receiving author updates, and sending a flower are separate interactions and never count as votes. Comments support one reply level and soft deletion. A user can send at most one flower per proposal. Eligible viewers see only each voter's latest reason; earlier reasons remain available to that voter and for necessary audit access.
- Withdrawal stops new votes, is excluded from decision rankings, and creates no reputation score event. Existing vote events remain auditable.

## 6. Reputation and Voting Weight

When a proposal reaches the minimum voter count and is settled, a voter whose latest choice matches the final result receives `+1` reputation. Incorrect votes do not subtract points. Withdrawn and insufficient-participation proposals create no reputation event.

| Reputation score | Voting weight |
|---:|---:|
| 0–9 | 1x |
| 10–29 | 2x |
| 30+ | 3x (cap) |

The score change must be recorded in an append-only reputation ledger. The current account total is a summary that can be rebuilt from the ledger. The exact reputation transaction idempotency key and rule-version format are TBC.

## 7. Weekly Rankings and Currency Conversion

Rankings use the Hong Kong time zone and run from Monday 00:00 to the following Monday 00:00. The first version exposes:

- **Most NOT_BUY Decisions:** the creator with the most binary proposals settled as `NOT_BUY`
- **Highest Total BUY Amount:** the creator with the highest total amount for binary proposals settled as `BUY`
- **Most Votes Cast:** the voter with the most latest valid votes, counting at most once per proposal

All ranking amounts are converted to HKD. The selected source is Frankfurter's ECB reference rates. At proposal settlement, the backend stores the source, rate date, and applied rate used for conversion. If the provider is unavailable, the most recent saved rate may be used and its date must be recorded. A currency without any usable saved rate is excluded from amount rankings until a rate exists.

Persist weekly ranking snapshots and their entries so historical rankings remain stable when user data, rules, or exchange rates later change. `week_start_date` uses UTC+8 and is unique with `ranking_type`.

## 8. Candidate Data Model

This target model is based on `数据库设计文档.pdf` and contains 30 tables across eight business domains. It is a design baseline for team review, not a claim about the current database. The current Flyway migration `V1__initial_schema.sql` still contains the earlier 15-table baseline and does not implement this target model; update the migration separately after the team reviews and freezes the schema. Internal primary keys use auto-incrementing unsigned `BIGINT` unless a composite key is stated; APIs should expose identifiers as opaque values and must not rely on their sequence.

```mermaid
erDiagram
    USER ||--o| REPUTATION_ACCOUNT : owns
    USER ||--o{ GROUP_MEMBER : joins
    GROUP ||--o{ GROUP_MEMBER : contains
    USER ||--o{ PURCHASE_PROPOSAL : creates
    GROUP ||--o{ PURCHASE_PROPOSAL : scopes
    PURCHASE_PROPOSAL ||--o{ POLL_OPTION : offers
    PURCHASE_PROPOSAL ||--o{ DECISION_VOTE : receives
    USER ||--o{ DECISION_VOTE : casts
    PURCHASE_PROPOSAL ||--o{ PURCHASE_PROPOSAL_IMAGE : displays
    PURCHASE_PROPOSAL ||--o{ PURCHASE_PROPOSAL_CHANNEL : belongs_to
    CHANNEL ||--o{ PURCHASE_PROPOSAL_CHANNEL : categorizes
    TAG ||--o{ PROPOSAL_TAG : classifies
    PURCHASE_PROPOSAL ||--o{ PROPOSAL_TAG : tagged_as
    USER ||--o{ USER_CHANNEL : subscribes
    CHANNEL ||--o{ USER_CHANNEL : has_subscribers
    REPUTATION_ACCOUNT ||--o{ REPUTATION_LEDGER : records
    PURCHASE_PROPOSAL ||--o| EXCHANGE_RATE_SNAPSHOT : uses
    WEEKLY_RANKING ||--o{ WEEKLY_RANKING_ENTRY : contains
    USER ||--o{ FEEDBACK : submits
    FEEDBACK ||--o{ FEEDBACK_IMAGE : includes
```

The 30-table inventory is:

| Domain | Tables |
|---|---|
| Identity and authentication (6) | `users`, `auth_sessions`, `user_devices`, `reputation_accounts`, `reputation_ledger`, `user_streaks` |
| Groups (2) | `app_groups`, `group_members` |
| Proposal core (4) | `purchase_proposals`, `poll_options`, `purchase_proposal_images`, `purchase_proposal_channels` |
| Plaza, channels, and tags (4) | `channels`, `user_channels`, `tags`, `proposal_tags` |
| Votes and interactions (5) | `decision_votes`, `proposal_watches`, `proposal_comments`, `proposal_updates`, `flowers` |
| Rates and rankings (3) | `exchange_rate_snapshots`, `weekly_rankings`, `weekly_ranking_entries` |
| Governance and operations (5) | `blocked_words`, `blocked_users`, `feedback`, `feedback_images`, `notifications` |
| Optional (1) | `wheel_runs` (P2; add only if wheel history or shared wheels are required) |

The PDF cover states a priority split of P0 19, P1 8, and P2 3, but its detailed table inventory labels only `wheel_runs` as P2. Confirm the remaining per-table priority assignments before treating the tier split as authoritative.

Key table responsibilities and constraints:

| Table | Key data and constraints |
|---|---|
| `users` | Unique username and public number; BCrypt password hash; display name and account status. |
| `auth_sessions` | User, unique refresh-token hash, device, expiry, and revocation state for revocable multi-device sessions. |
| `user_devices` | User, unique FCM token, and active flag; supports token rotation and inactive devices. |
| `user_streaks` | One row per user with current/longest days and unique last-counted date. |
| `app_groups` / `group_members` | Group owner, unique plain 8-character invite code, group/member counts and status; unique membership per user/group. Rejoining updates the membership row. |
| `purchase_proposals` | Creator, one publication scope, participation type, approval/settlement modes, status, binary result or winning option, minimum voter count, creator-scoped idempotency key, and denormalized `voter_count`, `watch_count`, and `comment_count`. Keep drafts in this table. |
| `poll_options` | One to six ordered options per option-selection proposal, with label and optional exact amount. |
| `purchase_proposal_images` | Object-storage URL and display order; at most four images per proposal. |
| `purchase_proposal_channels` | Unique proposal/channel pair; plaza proposals need one or more channels and group proposals must have none. |
| `channels` / `user_channels` | System-managed channels; user subscription uses a composite key and stores `sort_order`. |
| `tags` / `proposal_tags` | Sixteen predefined content tags (general, lifestyle, food, fashion, beauty, digital, fandom, anime, games, art, travel, sports, pets, study, workplace, relationships) and proposal/tag composite key. Each published proposal requires one to three tags; enforce the count in the service transaction. |
| `decision_votes` | Append-only vote events, vote type, mutually exclusive binary `choice` or `option_id`, required nonblank reason, idempotency key unique per voter, weight snapshot, and timestamp. The latest event by `MAX(id)` per proposal/voter counts. The PDF diagram omits the reason column; retain it because every vote is required to include a reason. |
| `proposal_watches` | Proposal/user composite key; watching never contributes to voter count. |
| `proposal_comments` | Proposal, author, optional parent comment, content, and status; only one reply level and soft deletion. |
| `proposal_updates` | Append-only author additions to a published proposal. |
| `flowers` | Proposal, giver, and proposal creator as receiver; unique proposal/giver pair prevents repeat sending. |
| `reputation_accounts` / `reputation_ledger` | Current score plus append-only score events; unique `source_vote_id` provides reward idempotency. Derive level/badge from score rather than storing another truth. |
| `exchange_rate_snapshots` | One frozen applied rate and converted HKD amount per settled proposal. |
| `weekly_rankings` / `weekly_ranking_entries` | Historical ranking header and entries; unique week/type and ranking/rank-number keys. Use UTC+8 week boundaries. |
| `blocked_words` / `blocked_users` | Per-user filters; normalize blocked words to lowercase and prohibit self-blocking. |
| `feedback` / `feedback_images` | Optional user, message, separate contact type/value and status; at most three ordered images per feedback. |
| `notifications` | User notification type and polymorphic `ref_type`/`ref_id`, read and push status. |
| `wheel_runs` | Optional history row referencing user and, when applicable, proposal/option; omit by default because wheel sectors can reuse `poll_options`. |

Data design rules:

- Store monetary values as exact decimals with a separate currency code.
- Store binary vote choice and binary proposal result as `TINYINT UNSIGNED` with `CHECK (value IN (0, 1))`: `1 = BUY`, `0 = NOT_BUY`. For `OPTION` proposals, a vote references one option and settlement stores `winning_option_id`; enforce mutually exclusive result fields. API and Java code use named values, not numeric values.
- Store server timestamps in UTC; convert to the user's display time zone at the edge.
- Keep business facts separate from summary values.
- Treat `voter_count` as the number of distinct users with a latest valid vote; update it with watch/comment counts in the same transaction as interaction records.
- Preserve every vote event, its reason, weight, and reputation-level snapshot for historical interpretation; only the latest event per voter/proposal participates in counting.
- Treat `DecisionVote` rows as append-only in application code; do not update or delete an earlier cast/change event.
- Enforce ownership, membership, vote validity, channel/tag relationships, image/option limits, and counter consistency at the database and service boundaries as appropriate. Update denormalized interaction counters in the same transaction as their source records.
- MySQL enforces local row rules with foreign keys, unique keys, and `CHECK` constraints. Cross-row rules such as at least one channel for a published plaza proposal, group membership eligibility, and final-vote selection are enforced transactionally by the service.
- Use Flyway as the versioned schema-change path; do not use automatic JPA table creation. The target inventory above is not yet represented by the existing V1 migration.
- On account deletion, anonymize user identity while retaining the minimum proposal, vote, and reputation audit history. The precise retention period is TBC.

## 9. API Contract

The backend exposes versioned REST/JSON endpoints under `/api`. The endpoint list is a design target, not a frozen contract:

| Area | Candidate resources |
|---|---|
| Auth | `/api/auth/register`, `/api/auth/login` |
| Users | `/api/users/me`, `/api/users/me/records` |
| Groups | `/api/groups`, `/api/groups/{id}/members`, `/api/groups/{id}/invite` |
| Purchase proposals | `/api/purchase-proposals`, `/api/purchase-proposals/{id}`, `/api/purchase-proposals/{id}/votes`, `/api/purchase-proposals/{id}/watches`, `/api/purchase-proposals/{id}/comments`, `/api/purchase-proposals/{id}/updates`, `/api/purchase-proposals/{id}/flowers` |
| Plaza | `/api/channels`, `/api/tags`, `/api/plaza` |
| Rankings | `/api/rankings/weekly` |
| Feedback and notifications | `/api/feedback`, `/api/notifications` |

Contract conventions:

- JSON field names use `camelCase`; IDs are opaque strings or UUIDs once chosen.
- Errors use a stable error code, a human-readable message, and optional field details.
- Lists define explicit pagination, sorting, and filtering parameters.
- Mutating requests that can be retried should accept an idempotency key.
- Authentication uses `Authorization: Bearer <JWT>` after login.
- Binary choice and result values in JSON are `BUY` and `NOT_BUY`; option-selection votes reference an option ID. Every vote request contains a nonblank reason. Proposal publication requires item name, amount, currency, purchase reason, deadline, minimum voter count, one to three tags, participation type, and the scope-specific group or channels. Binary and option-selection results are returned through distinct fields.
- OpenAPI examples are required before Android and backend parallel implementation begins.

The team must freeze the first API contract together with the first Flyway migration. Changes after the freeze require a short decision record and notification to affected modules.

## 10. Authentication and Authorization

The first version uses username/password registration and login. Passwords are stored only as BCrypt hashes. JWT signing material is supplied through an environment variable and is never committed. Access tokens are planned to expire after 24 hours. Refresh-token hashes, expiry, device association, and revocation state are stored in `auth_sessions` so sessions can be revoked per device.

Authorization checks apply to every resource, not just the UI:

- A group member can read group proposals while membership permits it and vote while membership is active. A vote cast before leaving remains valid.
- A plaza proposal is visible according to its publication and blocking rules; authenticated users can vote.
- A creator can edit proposal content only before the first vote and withdraw it only before the deadline.
- The group owner manages membership and invitation-code rotation.
- The creator cannot vote on their own proposal. Eligible viewers see only the latest reason for each voter's decision; vote history is restricted to the voter and necessary audit access.

Exact role names, blocked-user precedence, and audit-access roles remain TBC.

## 11. Local Development

Backend:

```sh
cp .env.example .env
docker compose up -d mysql
set -a && source .env && set +a
./mvnw spring-boot:run
```

Android:

```sh
./gradlew test lint assembleDebug
```

If encounter `Permission denied`, execute:
```shell
chmod +x gradlew
```

The backend repository's `compose.yaml` starts MySQL only. The service runs through Maven, and Android runs through Android Studio or Gradle. Flyway applies `V1__initial_schema.sql` when the backend starts against a new database. That migration currently creates the earlier 15-table baseline; it must be reviewed and brought into line with the 30-table target model before business APIs rely on the target schema.

## 12. Team Workflow and Responsibilities

Two members establish the repositories, database baseline, migration conventions, and shared development configuration. The remaining three members build feature modules on top of the reviewed baseline.

Before parallel implementation, the team reviews and accepts:

1. User flows and the purchase proposal state machine
2. ER diagram, data dictionary, constraints, and indexes
3. API list with request/response examples
4. Permission matrix and reputation/ranking rules
5. Initial Flyway migration and API mock/contract

Each repository uses feature branches and Pull Requests. `main` should reject direct pushes, require at least one teammate approval, and require the relevant CI checks. No license is added at this stage.

## 13. CI and Verification

Android CI runs `./gradlew test lint assembleDebug`. Backend CI runs `./mvnw -B verify`. The current scaffold has no business entities or integration tests; those should be added with the first reviewed contract.

Minimum feature verification should cover:

- Append-only vote history and latest-vote counting
- Minimum voter count and weighted settlement
- Frozen vote-weight snapshots
- Reputation-ledger idempotency
- Authorization for group, plaza, creator, viewer, and withdrawn states
- Binary and option voting, required vote reasons, multi-channel deduplication, and the four-image limit
- One-to-three tag validation, append-only vote idempotency, and transactional interaction-counter updates
- Historical weekly snapshot stability and feedback-image limits
- Currency-rate fallback and HKD ranking calculations

## 14. Risks and Mitigations

| Risk | Mitigation |
|---|---|
| Rules change after schema/API work starts | Freeze a first contract; record changes as decisions |
| Weighted settlement is inconsistent under concurrent votes | Use a transactional settlement path and immutable vote snapshots |
| Exchange rates change historical ranking results | Store the applied rate and source date at settlement |
| Denormalized counters drift from interaction records | Update counts transactionally and add reconciliation checks |
| Current migration lags the target model | Review and align the Flyway baseline before implementing dependent APIs |
| Secrets enter public repositories | Use `.env`, environment variables, ignore rules, and CI secret storage |
| Five parallel branches drift | Require API/schema review and CI before merge |

## 15. Open Decisions

The following items remain open for review and may require follow-up migrations before the schema and API are frozen:

- Exact entity fields, indexes, and deletion/anonymization retention period
- Exact meanings and allowed values for `approval_mode` and `settlement_mode`
- The PDF's per-table priority assignments: its cover totals P0 19, P1 8, and P2 3, while detailed sections explicitly mark only `wheel_runs` as P2
- Option-vote weighted tally, option settlement rule, and tie handling
- Error-code catalogue, pagination format, idempotency-key scope, and API versioning policy
- JWT claim set, signing algorithm, and account recovery policy
- System channel management, detailed plaza visibility, and moderation behavior
- Object-storage provider, per-image size limit, and upload lifecycle
- Ranking tie display and privacy/anonymization policy
- Exchange-rate provider outage policy and supported currency list beyond HKD, CNY, and USD
- Group invitation expiry and maximum membership rules
- Whether the optional `wheel_runs` table will be added for history or shared wheels

No implementation should silently decide these items. Add the decision and update this document before changing the schema or public API.
