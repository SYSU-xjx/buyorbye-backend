CREATE TABLE users (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    username VARCHAR(64) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    display_name VARCHAR(80) NULL,
    avatar_url VARCHAR(2048) NULL,
    account_status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uq_users_username UNIQUE (username),
    CONSTRAINT chk_users_username CHECK (CHAR_LENGTH(TRIM(username)) > 0),
    CONSTRAINT chk_users_password_hash CHECK (CHAR_LENGTH(TRIM(password_hash)) > 0),
    CONSTRAINT chk_users_account_status CHECK (account_status IN ('ACTIVE', 'ANONYMIZED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE app_groups (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    owner_user_id BIGINT UNSIGNED NOT NULL,
    invite_code_hash VARCHAR(255) NULL,
    group_status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uq_app_groups_invite_code_hash UNIQUE (invite_code_hash),
    CONSTRAINT fk_app_groups_owner FOREIGN KEY (owner_user_id) REFERENCES users (id),
    CONSTRAINT chk_app_groups_name CHECK (CHAR_LENGTH(TRIM(name)) > 0),
    CONSTRAINT chk_app_groups_status CHECK (group_status IN ('ACTIVE', 'CLOSED')),
    KEY ix_app_groups_owner (owner_user_id, group_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE group_members (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    group_id BIGINT UNSIGNED NOT NULL,
    user_id BIGINT UNSIGNED NOT NULL,
    member_role VARCHAR(16) NOT NULL DEFAULT 'MEMBER',
    member_status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    joined_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    left_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_group_members_group_user UNIQUE (group_id, user_id),
    CONSTRAINT fk_group_members_group FOREIGN KEY (group_id) REFERENCES app_groups (id),
    CONSTRAINT fk_group_members_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT chk_group_members_role CHECK (member_role IN ('OWNER', 'MEMBER')),
    CONSTRAINT chk_group_members_status CHECK (member_status IN ('ACTIVE', 'LEFT', 'REMOVED')),
    KEY ix_group_members_user_status (user_id, member_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE channels (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    slug VARCHAR(64) NOT NULL,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500) NULL,
    is_active TINYINT UNSIGNED NOT NULL DEFAULT 1,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uq_channels_slug UNIQUE (slug),
    CONSTRAINT chk_channels_active CHECK (is_active IN (0, 1)),
    CONSTRAINT chk_channels_name CHECK (CHAR_LENGTH(TRIM(name)) > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE user_channels (
    user_id BIGINT UNSIGNED NOT NULL,
    channel_id BIGINT UNSIGNED NOT NULL,
    subscribed_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (user_id, channel_id),
    CONSTRAINT fk_user_channels_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_user_channels_channel FOREIGN KEY (channel_id) REFERENCES channels (id),
    KEY ix_user_channels_channel (channel_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE purchase_proposals (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    creator_user_id BIGINT UNSIGNED NOT NULL,
    publication_scope VARCHAR(8) NULL,
    group_id BIGINT UNSIGNED NULL,
    item_name VARCHAR(200) NULL,
    amount DECIMAL(12, 2) NULL,
    currency CHAR(3) NULL,
    purchase_reason TEXT NULL,
    alternative_product_tip TEXT NULL,
    proposal_status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    decision_result TINYINT UNSIGNED NULL,
    minimum_voter_count INT UNSIGNED NOT NULL DEFAULT 1,
    published_at DATETIME(6) NULL,
    deadline_at DATETIME(6) NULL,
    settled_at DATETIME(6) NULL,
    withdrawn_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_purchase_proposals_creator FOREIGN KEY (creator_user_id) REFERENCES users (id),
    CONSTRAINT fk_purchase_proposals_group FOREIGN KEY (group_id) REFERENCES app_groups (id),
    CONSTRAINT chk_purchase_proposals_scope CHECK (
        publication_scope IS NULL OR publication_scope IN ('GROUP', 'PLAZA')
    ),
    CONSTRAINT chk_purchase_proposals_status CHECK (
        proposal_status IN ('DRAFT', 'OPEN', 'SETTLED', 'WITHDRAWN', 'INSUFFICIENT_PARTICIPATION')
    ),
    CONSTRAINT chk_purchase_proposals_result CHECK (
        decision_result IS NULL OR decision_result IN (0, 1)
    ),
    CONSTRAINT chk_purchase_proposals_result_status CHECK (
        (proposal_status = 'SETTLED' AND decision_result IS NOT NULL)
        OR (proposal_status <> 'SETTLED' AND decision_result IS NULL)
    ),
    CONSTRAINT chk_purchase_proposals_scope_group CHECK (
        proposal_status = 'DRAFT'
        OR (publication_scope = 'GROUP' AND group_id IS NOT NULL)
        OR (publication_scope = 'PLAZA' AND group_id IS NULL)
    ),
    CONSTRAINT chk_purchase_proposals_publish_fields CHECK (
        proposal_status = 'DRAFT'
        OR (
            publication_scope IS NOT NULL
            AND item_name IS NOT NULL AND CHAR_LENGTH(TRIM(item_name)) > 0
            AND amount IS NOT NULL AND amount > 0
            AND currency IS NOT NULL AND currency IN ('HKD', 'CNY', 'USD')
            AND purchase_reason IS NOT NULL AND CHAR_LENGTH(TRIM(purchase_reason)) > 0
            AND minimum_voter_count >= 1
            AND published_at IS NOT NULL
            AND deadline_at IS NOT NULL AND deadline_at > published_at
        )
    ),
    CONSTRAINT chk_purchase_proposals_deadline_duration CHECK (
        proposal_status = 'DRAFT'
        OR (
            deadline_at >= DATE_ADD(published_at, INTERVAL 1 HOUR)
            AND deadline_at <= DATE_ADD(published_at, INTERVAL 7 DAY)
        )
    ),
    CONSTRAINT chk_purchase_proposals_currency CHECK (
        currency IS NULL OR currency IN ('HKD', 'CNY', 'USD')
    ),
    KEY ix_purchase_proposals_creator_status_created (creator_user_id, proposal_status, created_at),
    KEY ix_purchase_proposals_group_status_deadline (group_id, proposal_status, deadline_at),
    KEY ix_purchase_proposals_plaza_status_created (publication_scope, proposal_status, created_at),
    KEY ix_purchase_proposals_status_deadline (proposal_status, deadline_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE purchase_proposal_channels (
    proposal_id BIGINT UNSIGNED NOT NULL,
    channel_id BIGINT UNSIGNED NOT NULL,
    PRIMARY KEY (proposal_id, channel_id),
    CONSTRAINT fk_proposal_channels_proposal FOREIGN KEY (proposal_id)
        REFERENCES purchase_proposals (id) ON DELETE CASCADE,
    CONSTRAINT fk_proposal_channels_channel FOREIGN KEY (channel_id) REFERENCES channels (id),
    KEY ix_proposal_channels_channel_proposal (channel_id, proposal_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE purchase_proposal_images (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    proposal_id BIGINT UNSIGNED NOT NULL,
    image_url VARCHAR(2048) NOT NULL,
    display_order TINYINT UNSIGNED NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uq_proposal_images_order UNIQUE (proposal_id, display_order),
    CONSTRAINT fk_proposal_images_proposal FOREIGN KEY (proposal_id)
        REFERENCES purchase_proposals (id) ON DELETE CASCADE,
    CONSTRAINT chk_proposal_images_order CHECK (display_order BETWEEN 1 AND 6),
    KEY ix_proposal_images_proposal (proposal_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE decision_votes (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    proposal_id BIGINT UNSIGNED NOT NULL,
    voter_user_id BIGINT UNSIGNED NOT NULL,
    choice TINYINT UNSIGNED NOT NULL,
    reason TEXT NOT NULL,
    reputation_score_snapshot INT UNSIGNED NOT NULL,
    weight_snapshot TINYINT UNSIGNED NOT NULL,
    cast_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_decision_votes_proposal FOREIGN KEY (proposal_id) REFERENCES purchase_proposals (id),
    CONSTRAINT fk_decision_votes_user FOREIGN KEY (voter_user_id) REFERENCES users (id),
    CONSTRAINT chk_decision_votes_choice CHECK (choice IN (0, 1)),
    CONSTRAINT chk_decision_votes_reason CHECK (CHAR_LENGTH(TRIM(reason)) > 0),
    CONSTRAINT chk_decision_votes_weight CHECK (weight_snapshot IN (1, 2, 3)),
    KEY ix_decision_votes_latest (proposal_id, voter_user_id, id),
    KEY ix_decision_votes_proposal (proposal_id, id),
    KEY ix_decision_votes_user_time (voter_user_id, cast_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE reputation_accounts (
    user_id BIGINT UNSIGNED NOT NULL,
    score INT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (user_id),
    CONSTRAINT fk_reputation_accounts_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE reputation_ledger (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id BIGINT UNSIGNED NOT NULL,
    proposal_id BIGINT UNSIGNED NOT NULL,
    source_vote_id BIGINT UNSIGNED NOT NULL,
    score_change SMALLINT NOT NULL,
    rule_version VARCHAR(32) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uq_reputation_ledger_source_vote UNIQUE (source_vote_id),
    CONSTRAINT fk_reputation_ledger_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_reputation_ledger_proposal FOREIGN KEY (proposal_id) REFERENCES purchase_proposals (id),
    CONSTRAINT fk_reputation_ledger_vote FOREIGN KEY (source_vote_id) REFERENCES decision_votes (id),
    CONSTRAINT chk_reputation_ledger_change CHECK (score_change = 1),
    KEY ix_reputation_ledger_user_time (user_id, created_at),
    KEY ix_reputation_ledger_proposal (proposal_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE exchange_rate_snapshots (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    proposal_id BIGINT UNSIGNED NOT NULL,
    source_currency CHAR(3) NOT NULL,
    target_currency CHAR(3) NOT NULL DEFAULT 'HKD',
    rate DECIMAL(20, 10) NOT NULL,
    converted_amount DECIMAL(16, 2) NOT NULL,
    rate_source VARCHAR(100) NOT NULL,
    rate_date DATE NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uq_exchange_rate_proposal UNIQUE (proposal_id),
    CONSTRAINT fk_exchange_rate_proposal FOREIGN KEY (proposal_id) REFERENCES purchase_proposals (id),
    CONSTRAINT chk_exchange_rate_currency CHECK (
        source_currency IN ('HKD', 'CNY', 'USD') AND target_currency = 'HKD'
    ),
    CONSTRAINT chk_exchange_rate_values CHECK (rate > 0 AND converted_amount >= 0),
    KEY ix_exchange_rate_date_currency (rate_date, source_currency)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE blocked_words (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    owner_user_id BIGINT UNSIGNED NOT NULL,
    word VARCHAR(100) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uq_blocked_words_owner_word UNIQUE (owner_user_id, word),
    CONSTRAINT fk_blocked_words_owner FOREIGN KEY (owner_user_id) REFERENCES users (id),
    CONSTRAINT chk_blocked_words_nonempty CHECK (CHAR_LENGTH(TRIM(word)) > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE blocked_users (
    blocker_user_id BIGINT UNSIGNED NOT NULL,
    blocked_user_id BIGINT UNSIGNED NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (blocker_user_id, blocked_user_id),
    CONSTRAINT fk_blocked_users_blocker FOREIGN KEY (blocker_user_id) REFERENCES users (id),
    CONSTRAINT fk_blocked_users_blocked FOREIGN KEY (blocked_user_id) REFERENCES users (id),
    CONSTRAINT chk_blocked_users_not_self CHECK (blocker_user_id <> blocked_user_id),
    KEY ix_blocked_users_target (blocked_user_id, blocker_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE feedback (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id BIGINT UNSIGNED NULL,
    message TEXT NOT NULL,
    feedback_status VARCHAR(16) NOT NULL DEFAULT 'OPEN',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_feedback_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT chk_feedback_message CHECK (CHAR_LENGTH(TRIM(message)) > 0),
    CONSTRAINT chk_feedback_status CHECK (feedback_status IN ('OPEN', 'IN_PROGRESS', 'RESOLVED')),
    KEY ix_feedback_status_created (feedback_status, created_at),
    KEY ix_feedback_user_created (user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
