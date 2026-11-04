-- =============================================================================
--  V1 — buy or bye 初始表结构（30 张表 / 38 条外键）
--
--  目标库 : MySQL 8.4 · InnoDB · utf8mb4 · utf8mb4_0900_ai_ci
--  执行方 : Flyway（Spring Boot 启动时自动执行）
--
--  说明
--    · 库名由 SPRING_DATASOURCE_URL 指定（默认 buyorbye），
--      故本文件不含 CREATE DATABASE / USE，可在任意环境复用
--    · 参考数据（16 个标签、5 个频道）在 V2__seed_reference_data.sql
--    · 已进入 main 的迁移不要再改内容，后续变更请新增 V3、V4……
--
--  约定
--    · DATETIME(6) 一律存 UTC，时区转换在应用层完成
--    · 金额一律 DECIMAL，Java 侧必须用 BigDecimal，禁止 double
--    · purchase_proposals.winning_option_id 与 poll_options 互相引用，
--      该外键在建表后用 ALTER TABLE 补充（见 §4 末尾）
-- =============================================================================

SET NAMES utf8mb4;

-- =============================================================================
--  1. 身份与鉴权域（6 张）
-- =============================================================================

-- 1.1 users —— 账号与公开资料
CREATE TABLE users (
  id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '内部用户主键',
  username            VARCHAR(64)     NOT NULL                COMMENT '登录名，统一存小写',
  password_hash       VARCHAR(255)    NOT NULL                COMMENT 'BCrypt 密码哈希',
  display_name        VARCHAR(80)     NOT NULL                COMMENT '展示昵称',
  avatar_url          VARCHAR(2048)   NULL                    COMMENT '头像 URL',
  public_no           BIGINT UNSIGNED NULL                    COMMENT '对外展示 ID（如 4094713）',
  preferred_currency  CHAR(3)         NOT NULL DEFAULT 'CNY'  COMMENT '首页展示币种',
  account_status      VARCHAR(16)     NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE / ANONYMIZED',
  last_active_at      DATETIME(6)     NULL                    COMMENT '最后活跃时间',
  created_at          DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at          DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  UNIQUE KEY uk_users_username  (username),
  UNIQUE KEY uk_users_public_no (public_no),
  KEY idx_users_last_active (last_active_at),
  CONSTRAINT ck_users_status CHECK (account_status IN ('ACTIVE', 'ANONYMIZED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='账号与公开资料';


-- 1.2 auth_sessions —— 登录会话（Android 必须自建，小程序由微信托管）
CREATE TABLE auth_sessions (
  id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id             BIGINT UNSIGNED NOT NULL,
  refresh_token_hash  VARCHAR(255)    NOT NULL COMMENT 'Refresh Token 的 SHA-256',
  device_id           VARCHAR(64)     NOT NULL COMMENT '客户端生成的稳定设备标识',
  device_name         VARCHAR(100)    NULL     COMMENT '如 Pixel 7',
  app_version         VARCHAR(32)     NULL,
  ip_address          VARCHAR(45)     NULL     COMMENT '兼容 IPv6',
  issued_at           DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  expires_at          DATETIME(6)     NOT NULL COMMENT '建议 30 天',
  revoked_at          DATETIME(6)     NULL     COMMENT '改密码/登出时置值，踢下线的依据',
  last_used_at        DATETIME(6)     NULL     COMMENT '滑动续期依据',
  PRIMARY KEY (id),
  UNIQUE KEY uk_sessions_token (refresh_token_hash),
  KEY idx_sessions_user (user_id, revoked_at),
  KEY idx_sessions_expire (expires_at),
  CONSTRAINT fk_sessions_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='登录会话与刷新令牌';


-- 1.3 user_devices —— 推送设备（FCM）
CREATE TABLE user_devices (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id       BIGINT UNSIGNED NOT NULL,
  fcm_token     VARCHAR(255)    NOT NULL COMMENT 'FCM 注册 token',
  device_id     VARCHAR(64)     NOT NULL COMMENT '与 auth_sessions 对齐',
  platform      VARCHAR(16)     NOT NULL DEFAULT 'ANDROID',
  app_version   VARCHAR(32)     NULL,
  is_active     TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT 'FCM 返回 UNREGISTERED 时置 0',
  last_seen_at  DATETIME(6)     NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_devices_token (fcm_token),
  KEY idx_devices_user (user_id, is_active),
  CONSTRAINT fk_devices_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
  CONSTRAINT ck_devices_active CHECK (is_active IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='推送设备（FCM token 轮换）';


-- 1.4 reputation_accounts —— 信誉分汇总
CREATE TABLE reputation_accounts (
  user_id     BIGINT UNSIGNED NOT NULL COMMENT '既主键又外键，保证一人一行',
  score       INT UNSIGNED    NOT NULL DEFAULT 0 COMMENT '等级/徽章由分数区间推导，不落库',
  created_at  DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at  DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (user_id),
  CONSTRAINT fk_rep_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='信誉分汇总（流水表为审计源）';


-- 1.5 user_streaks —— 理性消费坚持天数
CREATE TABLE user_streaks (
  user_id            BIGINT UNSIGNED NOT NULL,
  current_days       INT UNSIGNED    NOT NULL DEFAULT 0 COMMENT '当前连续天数',
  longest_days       INT UNSIGNED    NOT NULL DEFAULT 0 COMMENT '历史最长',
  last_counted_date  DATE            NULL              COMMENT '防止同一天重复计数',
  updated_at         DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (user_id),
  CONSTRAINT fk_streaks_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='理性消费坚持天数（每日定时任务更新）';


-- =============================================================================
--  2. 群组域（2 张）
-- =============================================================================

-- 2.1 app_groups —— 审批群
CREATE TABLE app_groups (
  id                     BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  name                   VARCHAR(100)    NOT NULL,
  owner_user_id          BIGINT UNSIGNED NOT NULL COMMENT '群主，群主转让的唯一权威源',
  invite_code            CHAR(8)         NULL     COMMENT '8 位群号（明文可反查），如 A7K2M9QX',
  invite_code_expires_at DATETIME(6)     NULL     COMMENT '群号有效期',
  group_status           VARCHAR(16)     NOT NULL DEFAULT 'ACTIVE',
  member_count           INT UNSIGNED    NOT NULL DEFAULT 0,
  created_at             DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at             DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  UNIQUE KEY uk_groups_invite (invite_code),
  KEY idx_groups_owner (owner_user_id),
  CONSTRAINT fk_groups_owner FOREIGN KEY (owner_user_id) REFERENCES users (id),
  CONSTRAINT ck_groups_status CHECK (group_status IN ('ACTIVE', 'CLOSED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='审批群';


-- 2.2 group_members —— 群成员关系
CREATE TABLE group_members (
  id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  group_id       BIGINT UNSIGNED NOT NULL,
  user_id        BIGINT UNSIGNED NOT NULL,
  member_role    VARCHAR(16)     NOT NULL DEFAULT 'MEMBER',
  member_status  VARCHAR(16)     NOT NULL DEFAULT 'ACTIVE',
  joined_at      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  left_at        DATETIME(6)     NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_members_group_user (group_id, user_id) COMMENT '重进群是更新原行，不是插新行',
  KEY idx_members_user (user_id, member_status),
  CONSTRAINT fk_members_group FOREIGN KEY (group_id) REFERENCES app_groups (id) ON DELETE CASCADE,
  CONSTRAINT fk_members_user  FOREIGN KEY (user_id)  REFERENCES users (id),
  CONSTRAINT ck_members_role   CHECK (member_role   IN ('OWNER', 'MEMBER')),
  CONSTRAINT ck_members_status CHECK (member_status IN ('ACTIVE', 'LEFT', 'REMOVED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='群成员关系（群内权限唯一来源）';


-- =============================================================================
--  3. 广场与频道域（4 张）
-- =============================================================================

-- 3.1 channels —— 广场频道
CREATE TABLE channels (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  slug        VARCHAR(64)     NOT NULL COMMENT '稳定标识，如 random',
  name        VARCHAR(100)    NOT NULL,
  description VARCHAR(500)    NULL,
  is_active   TINYINT UNSIGNED NOT NULL DEFAULT 1,
  created_at  DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  UNIQUE KEY uk_channels_slug (slug),
  CONSTRAINT ck_channels_active CHECK (is_active IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='广场频道（运营配置）';


-- 3.2 user_channels —— 用户订阅频道
CREATE TABLE user_channels (
  user_id       BIGINT UNSIGNED NOT NULL,
  channel_id    BIGINT UNSIGNED NOT NULL,
  sort_order    SMALLINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '管理频道拖拽排序',
  subscribed_at DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (user_id, channel_id),
  KEY idx_user_channels_channel (channel_id),
  CONSTRAINT fk_uc_user    FOREIGN KEY (user_id)    REFERENCES users (id)    ON DELETE CASCADE,
  CONSTRAINT fk_uc_channel FOREIGN KEY (channel_id) REFERENCES channels (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='用户订阅的频道';


-- 3.3 tags —— 内容标签（16 个预设）
CREATE TABLE tags (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  slug          VARCHAR(64)     NOT NULL COMMENT '如 food',
  name          VARCHAR(50)     NOT NULL COMMENT '如 食物',
  display_order SMALLINT UNSIGNED NOT NULL DEFAULT 0,
  is_active     TINYINT UNSIGNED NOT NULL DEFAULT 1,
  created_at    DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  UNIQUE KEY uk_tags_slug (slug),
  CONSTRAINT ck_tags_active CHECK (is_active IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='内容标签（发布审批时必选 1-3 个）';


-- =============================================================================
--  4. 审批核心域（4 张）
-- =============================================================================

-- 4.1 purchase_proposals —— 审批单（核心表）
--     注意：winning_option_id 的外键在 poll_options 建表后补充，见本节末尾
CREATE TABLE purchase_proposals (
  id                     BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  creator_user_id        BIGINT UNSIGNED NOT NULL COMMENT '发起人',
  participation_type     VARCHAR(16)     NOT NULL DEFAULT 'APPROVE_REJECT'
                         COMMENT 'APPROVE_REJECT 二元裁决 / OPTION_VOTE 选项投票',
  approval_mode          VARCHAR(16)     NOT NULL DEFAULT 'MULTI'
                         COMMENT 'SINGLE 单人审批 / MULTI 多人审批',
  publication_scope      VARCHAR(8)      NULL     COMMENT 'GROUP 发群 / PLAZA 发广场，草稿可空',
  group_id               BIGINT UNSIGNED NULL     COMMENT '发群必填、发广场必须为空',
  item_name              VARCHAR(200)    NULL     COMMENT '商品名（二元模式用）',
  poll_question          VARCHAR(300)    NULL     COMMENT '纠结什么（投票模式用）',
  amount                 DECIMAL(12,2)   NULL     COMMENT '价格（二元模式用）',
  currency               CHAR(3)         NULL     COMMENT 'HKD / CNY / USD',
  purchase_reason        TEXT            NULL     COMMENT '购买理由 / 纠结点',
  alternative_product_tip TEXT           NULL     COMMENT '更便宜的替代品提示',
  proposal_status        VARCHAR(32)     NOT NULL DEFAULT 'DRAFT',
  decision_result        TINYINT UNSIGNED NULL    COMMENT '1=BUY / 0=NOT_BUY，仅二元模式',
  winning_option_id      BIGINT UNSIGNED NULL     COMMENT '投票模式获胜选项，外键见 ALTER',
  settlement_mode        VARCHAR(16)     NOT NULL DEFAULT 'TIME'
                         COMMENT 'TIME 限时 / QUORUM 定额人数',
  minimum_voter_count    INT UNSIGNED    NOT NULL DEFAULT 3
                         COMMENT 'TIME=最低有效票数门槛；QUORUM=目标票数。切勿用 1',
  voter_count            INT UNSIGNED    NOT NULL DEFAULT 0 COMMENT '冗余计数，避免列表页 COUNT',
  watch_count            INT UNSIGNED    NOT NULL DEFAULT 0,
  comment_count          INT UNSIGNED    NOT NULL DEFAULT 0,
  client_request_id      CHAR(36)        NULL     COMMENT '客户端幂等 UUID',
  published_at           DATETIME(6)     NULL,
  deadline_at            DATETIME(6)     NULL     COMMENT '剩余时间由它算出',
  settled_at             DATETIME(6)     NULL     COMMENT '周榜按此列切周',
  withdrawn_at           DATETIME(6)     NULL,
  created_at             DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at             DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  UNIQUE KEY uk_proposals_idem (creator_user_id, client_request_id) COMMENT '幂等键',
  KEY idx_plaza_feed  (proposal_status, publication_scope, published_at DESC, id DESC) COMMENT '广场游标分页',
  KEY idx_group_feed  (group_id, proposal_status, published_at DESC) COMMENT '群内审批列表',
  KEY idx_creator     (creator_user_id, proposal_status, created_at DESC) COMMENT '个人页我提交的',
  KEY idx_due_settle  (proposal_status, deadline_at) COMMENT '定时任务扫描待结算',
  CONSTRAINT fk_proposals_creator FOREIGN KEY (creator_user_id) REFERENCES users (id),
  CONSTRAINT fk_proposals_group   FOREIGN KEY (group_id)        REFERENCES app_groups (id),

  CONSTRAINT ck_prop_participation CHECK (participation_type IN ('APPROVE_REJECT', 'OPTION_VOTE')),
  CONSTRAINT ck_prop_mode          CHECK (approval_mode      IN ('SINGLE', 'MULTI')),
  CONSTRAINT ck_prop_settlement    CHECK (settlement_mode    IN ('TIME', 'QUORUM')),
  CONSTRAINT ck_prop_status        CHECK (proposal_status IN
      ('DRAFT', 'OPEN', 'SETTLED', 'WITHDRAWN', 'INSUFFICIENT_PARTICIPATION')),
  CONSTRAINT ck_prop_scope         CHECK (publication_scope IS NULL
      OR publication_scope IN ('GROUP', 'PLAZA')),
  -- 发布范围与 group_id 必须配套
  CONSTRAINT ck_prop_scope_group   CHECK (publication_scope <> 'GROUP' OR group_id IS NOT NULL),
  CONSTRAINT ck_prop_scope_plaza   CHECK (publication_scope <> 'PLAZA' OR group_id IS NULL),
  -- 金额与币种成对出现
  CONSTRAINT ck_prop_amount        CHECK ((amount IS NULL AND currency IS NULL)
      OR (amount IS NOT NULL AND currency IS NOT NULL AND amount > 0)),
  -- 结算后必须有结果：二元模式看 decision_result，投票模式看 winning_option_id
  CONSTRAINT ck_prop_settled       CHECK (proposal_status <> 'SETTLED'
      OR decision_result IS NOT NULL OR winning_option_id IS NOT NULL),
  CONSTRAINT ck_prop_result        CHECK (decision_result IS NULL OR decision_result IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='审批单（含草稿；二元裁决与多选一共用）';


-- 4.2 poll_options —— 选项投票的候选项（同时是转盘扇区数据源）
CREATE TABLE poll_options (
  id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  proposal_id    BIGINT UNSIGNED NOT NULL,
  option_label   VARCHAR(8)      NOT NULL COMMENT 'A / B / C',
  option_text    VARCHAR(200)    NOT NULL,
  option_amount  DECIMAL(12,2)   NULL     COMMENT '该选项独立价格',
  display_order  TINYINT UNSIGNED NOT NULL,
  created_at     DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  UNIQUE KEY uk_options_proposal_order (proposal_id, display_order),
  CONSTRAINT fk_options_proposal FOREIGN KEY (proposal_id) REFERENCES purchase_proposals (id) ON DELETE CASCADE,
  CONSTRAINT ck_options_order    CHECK (display_order BETWEEN 1 AND 6)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='选项投票的候选项';


-- 4.2.1 补充循环外键：purchase_proposals.winning_option_id -> poll_options.id
--       两张表互相引用，无法在 CREATE 时同时声明，必须建表后 ALTER
ALTER TABLE purchase_proposals
  ADD CONSTRAINT fk_proposals_winning_option
  FOREIGN KEY (winning_option_id) REFERENCES poll_options (id);


-- 4.3 purchase_proposal_images —— 审批配图（最多 4 张）
CREATE TABLE purchase_proposal_images (
  id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  proposal_id    BIGINT UNSIGNED NOT NULL,
  image_url      VARCHAR(2048)   NOT NULL COMMENT '对象存储 URL，不存二进制',
  display_order  TINYINT UNSIGNED NOT NULL,
  created_at     DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  UNIQUE KEY uk_images_proposal_order (proposal_id, display_order),
  CONSTRAINT fk_images_proposal FOREIGN KEY (proposal_id) REFERENCES purchase_proposals (id) ON DELETE CASCADE,
  CONSTRAINT ck_images_order    CHECK (display_order BETWEEN 1 AND 4)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='审批配图（界面最多 4 张）';


-- 4.4 purchase_proposal_channels —— 审批与频道（一稿多频道，共享一个票池）
CREATE TABLE purchase_proposal_channels (
  proposal_id  BIGINT UNSIGNED NOT NULL,
  channel_id   BIGINT UNSIGNED NOT NULL,
  PRIMARY KEY (proposal_id, channel_id),
  KEY idx_ppc_channel (channel_id),
  CONSTRAINT fk_ppc_proposal FOREIGN KEY (proposal_id) REFERENCES purchase_proposals (id) ON DELETE CASCADE,
  CONSTRAINT fk_ppc_channel  FOREIGN KEY (channel_id)  REFERENCES channels (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='审批发布到哪些广场频道';


-- 4.5 proposal_tags —— 审批与标签
CREATE TABLE proposal_tags (
  proposal_id  BIGINT UNSIGNED NOT NULL,
  tag_id       BIGINT UNSIGNED NOT NULL,
  PRIMARY KEY (proposal_id, tag_id),
  KEY idx_pt_tag (tag_id, proposal_id) COMMENT '按标签筛选广场',
  CONSTRAINT fk_pt_proposal FOREIGN KEY (proposal_id) REFERENCES purchase_proposals (id) ON DELETE CASCADE,
  CONSTRAINT fk_pt_tag      FOREIGN KEY (tag_id)      REFERENCES tags (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='审批与标签的多对多（1-3 个由服务层校验）';


-- =============================================================================
--  5. 投票与互动域（5 张）
-- =============================================================================

-- 5.1 decision_votes —— 投票事件（append-only，改票是插新行）
CREATE TABLE decision_votes (
  id                        BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  proposal_id               BIGINT UNSIGNED NOT NULL,
  voter_user_id             BIGINT UNSIGNED NOT NULL,
  vote_type                 VARCHAR(16)     NOT NULL DEFAULT 'BINARY' COMMENT 'BINARY / OPTION',
  choice                    TINYINT UNSIGNED NULL     COMMENT '1=BUY / 0=NOT_BUY，仅 BINARY',
  option_id                 BIGINT UNSIGNED NULL     COMMENT '所选选项，仅 OPTION',
  reason                    TEXT            NOT NULL COMMENT '每次投票必须填写理由',
  reputation_score_snapshot INT UNSIGNED    NOT NULL COMMENT '投票瞬间的信誉分快照',
  weight_snapshot           TINYINT UNSIGNED NOT NULL COMMENT '投票瞬间的票权 1/2/3',
  client_request_id         CHAR(36)        NOT NULL COMMENT '幂等键，防弱网重复投票',
  cast_at                   DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  UNIQUE KEY uk_votes_idem (voter_user_id, client_request_id) COMMENT '幂等：重复请求返回首次结果',
  KEY idx_votes_latest (proposal_id, voter_user_id, id) COMMENT '取每人最新一票',
  KEY idx_votes_voter  (voter_user_id, cast_at DESC)    COMMENT '个人页我处理的',
  CONSTRAINT fk_votes_proposal FOREIGN KEY (proposal_id)   REFERENCES purchase_proposals (id),
  CONSTRAINT fk_votes_voter    FOREIGN KEY (voter_user_id) REFERENCES users (id),
  CONSTRAINT fk_votes_option   FOREIGN KEY (option_id)     REFERENCES poll_options (id),
  CONSTRAINT ck_votes_type     CHECK (vote_type IN ('BINARY', 'OPTION')),
  -- 两种模式互斥：BINARY 有 choice 无 option_id；OPTION 反之
  CONSTRAINT ck_votes_payload  CHECK (
      (vote_type = 'BINARY' AND choice IS NOT NULL AND option_id IS NULL) OR
      (vote_type = 'OPTION' AND option_id IS NOT NULL AND choice IS NULL)),
  CONSTRAINT ck_votes_choice   CHECK (choice IS NULL OR choice IN (0, 1)),
  CONSTRAINT ck_votes_weight   CHECK (weight_snapshot BETWEEN 1 AND 3)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='投票事件（追加写入，只取最新一票计票）';


-- 5.2 reputation_ledger —— 信誉分流水（依赖 decision_votes）
CREATE TABLE reputation_ledger (
  id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id        BIGINT UNSIGNED NOT NULL COMMENT '受益用户',
  proposal_id    BIGINT UNSIGNED NULL     COMMENT '来源审批单；DAILY_ACTIVE / ADMIN_ADJUST 无审批单，可为空',
  source_vote_id BIGINT UNSIGNED NULL     COMMENT '来源投票；无投票来源时为空，UNIQUE 允许多个 NULL',
  score_change   SMALLINT        NOT NULL COMMENT '-100 ~ 100',
  source_type    VARCHAR(32)     NOT NULL
                 COMMENT 'VOTE_CORRECT / VOTE_WRONG / FLOWER_RECEIVED / DAILY_ACTIVE / ADMIN_ADJUST',
  rule_version   VARCHAR(32)     NOT NULL COMMENT '规则调整后历史分数仍可解释',
  created_at     DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  UNIQUE KEY uk_ledger_vote (source_vote_id) COMMENT '幂等最后一道防线',
  KEY idx_ledger_user (user_id, created_at DESC),
  KEY idx_ledger_proposal (proposal_id),
  CONSTRAINT fk_ledger_user     FOREIGN KEY (user_id)        REFERENCES users (id),
  CONSTRAINT fk_ledger_proposal FOREIGN KEY (proposal_id)    REFERENCES purchase_proposals (id),
  CONSTRAINT fk_ledger_vote     FOREIGN KEY (source_vote_id) REFERENCES decision_votes (id),
  CONSTRAINT ck_ledger_change   CHECK (score_change BETWEEN -100 AND 100)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='信誉分流水（审计与重算）';


-- 5.3 proposal_watches —— 围观（关注但不表态，不参与计票）
CREATE TABLE proposal_watches (
  proposal_id  BIGINT UNSIGNED NOT NULL,
  user_id      BIGINT UNSIGNED NOT NULL,
  created_at   DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (proposal_id, user_id),
  KEY idx_watches_user (user_id, created_at DESC) COMMENT '个人页我围观的',
  CONSTRAINT fk_watches_proposal FOREIGN KEY (proposal_id) REFERENCES purchase_proposals (id) ON DELETE CASCADE,
  CONSTRAINT fk_watches_user     FOREIGN KEY (user_id)     REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='围观记录';


-- 5.4 proposal_comments —— 回应/评论（一层楼中楼）
CREATE TABLE proposal_comments (
  id                 BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  proposal_id        BIGINT UNSIGNED NOT NULL,
  author_user_id     BIGINT UNSIGNED NOT NULL,
  parent_comment_id  BIGINT UNSIGNED NULL COMMENT '只做一层，不做无限嵌套',
  content            TEXT            NOT NULL,
  comment_status     VARCHAR(16)     NOT NULL DEFAULT 'VISIBLE',
  created_at         DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  KEY idx_comments_proposal (proposal_id, created_at),
  KEY idx_comments_author   (author_user_id, created_at DESC),
  CONSTRAINT fk_comments_proposal FOREIGN KEY (proposal_id)       REFERENCES purchase_proposals (id) ON DELETE CASCADE,
  CONSTRAINT fk_comments_author   FOREIGN KEY (author_user_id)    REFERENCES users (id),
  CONSTRAINT fk_comments_parent   FOREIGN KEY (parent_comment_id) REFERENCES proposal_comments (id) ON DELETE CASCADE,
  CONSTRAINT ck_comments_status   CHECK (comment_status IN ('VISIBLE', 'HIDDEN', 'DELETED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='回应/评论（软删除）';


-- 5.5 proposal_updates —— 作者补充（独立时间戳）
CREATE TABLE proposal_updates (
  id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  proposal_id  BIGINT UNSIGNED NOT NULL,
  content      TEXT            NOT NULL,
  created_at   DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '详情页单独显示',
  PRIMARY KEY (id),
  KEY idx_updates_proposal (proposal_id, created_at),
  CONSTRAINT fk_updates_proposal FOREIGN KEY (proposal_id) REFERENCES purchase_proposals (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='作者补充（发布后追加说明）';


-- 5.6 flowers —— 送花（首页"收到花总数"）
CREATE TABLE flowers (
  id                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  proposal_id       BIGINT UNSIGNED NOT NULL,
  giver_user_id     BIGINT UNSIGNED NOT NULL COMMENT '送花人',
  receiver_user_id  BIGINT UNSIGNED NOT NULL COMMENT '收花人，冗余存储免 JOIN',
  created_at        DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  UNIQUE KEY uk_flowers_once (proposal_id, giver_user_id) COMMENT '同一人对同一条只能送一次',
  KEY idx_flowers_receiver (receiver_user_id),
  CONSTRAINT fk_flowers_proposal FOREIGN KEY (proposal_id)      REFERENCES purchase_proposals (id) ON DELETE CASCADE,
  CONSTRAINT fk_flowers_giver    FOREIGN KEY (giver_user_id)    REFERENCES users (id),
  CONSTRAINT fk_flowers_receiver FOREIGN KEY (receiver_user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='送花';


-- =============================================================================
--  6. 汇率与榜单域（3 张）
-- =============================================================================

-- 6.1 exchange_rate_snapshots —— 结算时刻的汇率快照
CREATE TABLE exchange_rate_snapshots (
  id                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  proposal_id       BIGINT UNSIGNED NOT NULL COMMENT '唯一，一条审批一个快照',
  source_currency   CHAR(3)         NOT NULL,
  target_currency   CHAR(3)         NOT NULL DEFAULT 'HKD',
  rate              DECIMAL(20,10)  NOT NULL COMMENT 'Java 侧必须用 BigDecimal',
  converted_amount  DECIMAL(16,2)   NOT NULL COMMENT '周榜"批准金额最大"直接读它',
  rate_source       VARCHAR(100)    NOT NULL,
  rate_date         DATE            NOT NULL COMMENT '可能早于结算日，必须分开记',
  created_at        DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  UNIQUE KEY uk_rate_proposal (proposal_id),
  CONSTRAINT fk_rate_proposal FOREIGN KEY (proposal_id) REFERENCES purchase_proposals (id) ON DELETE CASCADE,
  CONSTRAINT ck_rate_positive CHECK (rate > 0),
  CONSTRAINT ck_rate_amount   CHECK (converted_amount >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='汇率快照（保证跨币种榜单可复现）';


-- 6.2 weekly_rankings —— 周榜快照头（P2：只做当前周榜可不建）
CREATE TABLE weekly_rankings (
  id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  week_start_date  DATE            NOT NULL COMMENT '该周周一，按 UTC+8 切周',
  ranking_type     VARCHAR(32)     NOT NULL
                   COMMENT 'MOST_REJECTED / HIGHEST_APPROVED_AMOUNT / TOP_JUDGE',
  rule_version     VARCHAR(32)     NOT NULL,
  generated_at     DATETIME(6)     NOT NULL,
  created_at       DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  UNIQUE KEY uk_ranking_week_type (week_start_date, ranking_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='周榜快照头（历史榜可回看时才需要）';


-- 6.3 weekly_ranking_entries —— 周榜明细
CREATE TABLE weekly_ranking_entries (
  ranking_id    BIGINT UNSIGNED NOT NULL,
  rank_no       SMALLINT UNSIGNED NOT NULL COMMENT '名次，从 1 起',
  user_id       BIGINT UNSIGNED NOT NULL,
  metric_value  DECIMAL(20,4)   NOT NULL COMMENT '兼容金额与次数两种量纲',
  PRIMARY KEY (ranking_id, rank_no),
  KEY idx_entries_user (user_id),
  CONSTRAINT fk_entries_ranking FOREIGN KEY (ranking_id) REFERENCES weekly_rankings (id) ON DELETE CASCADE,
  CONSTRAINT fk_entries_user    FOREIGN KEY (user_id)    REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='周榜明细';


-- =============================================================================
--  7. 内容治理与运营域（5 张）
-- =============================================================================

-- 7.1 blocked_words —— 屏蔽词库（个人偏好，非平台审核）
CREATE TABLE blocked_words (
  id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  owner_user_id  BIGINT UNSIGNED NOT NULL,
  word           VARCHAR(100)    NOT NULL COMMENT '统一存小写，保证跨端唯一约束一致',
  created_at     DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  UNIQUE KEY uk_blocked_words (owner_user_id, word),
  CONSTRAINT fk_bw_user FOREIGN KEY (owner_user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='屏蔽词库';


-- 7.2 blocked_users —— 黑名单
CREATE TABLE blocked_users (
  blocker_user_id  BIGINT UNSIGNED NOT NULL,
  blocked_user_id  BIGINT UNSIGNED NOT NULL,
  created_at       DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (blocker_user_id, blocked_user_id),
  KEY idx_bu_blocked (blocked_user_id),
  CONSTRAINT fk_bu_blocker FOREIGN KEY (blocker_user_id) REFERENCES users (id) ON DELETE CASCADE,
  CONSTRAINT fk_bu_blocked FOREIGN KEY (blocked_user_id) REFERENCES users (id) ON DELETE CASCADE,
  -- 禁止拉黑自己（CHECK 约束不支持 COMMENT，故用行注释）
  CONSTRAINT ck_bu_self    CHECK (blocker_user_id <> blocked_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='黑名单';


-- 7.3 feedback —— 意见反馈
CREATE TABLE feedback (
  id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id          BIGINT UNSIGNED NULL COMMENT '用户注销后置 NULL，内容保留',
  message          TEXT            NOT NULL,
  contact_type     VARCHAR(16)     NULL COMMENT 'EMAIL / PHONE / QQ',
  contact_value    VARCHAR(128)    NULL,
  feedback_status  VARCHAR(16)     NOT NULL DEFAULT 'OPEN',
  created_at       DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at       DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  KEY idx_feedback_status (feedback_status, created_at),
  CONSTRAINT fk_feedback_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE SET NULL,
  CONSTRAINT ck_feedback_status CHECK (feedback_status IN ('OPEN', 'IN_PROGRESS', 'RESOLVED')),
  CONSTRAINT ck_feedback_contact CHECK (contact_type IS NULL
      OR contact_type IN ('EMAIL', 'PHONE', 'QQ', 'WECHAT'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='意见反馈';


-- 7.4 feedback_images —— 反馈截图（最多 3 张）
CREATE TABLE feedback_images (
  id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  feedback_id    BIGINT UNSIGNED NOT NULL,
  image_url      VARCHAR(2048)   NOT NULL,
  display_order  TINYINT UNSIGNED NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_fbimg_feedback_order (feedback_id, display_order),
  CONSTRAINT fk_fbimg_feedback FOREIGN KEY (feedback_id) REFERENCES feedback (id) ON DELETE CASCADE,
  CONSTRAINT ck_fbimg_order    CHECK (display_order BETWEEN 1 AND 3)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='反馈截图（界面最多 3 张）';


-- 7.5 notifications —— 站内通知
--     ref_id 是多态关联（可能指向审批/群组/评论），无法建物理外键
CREATE TABLE notifications (
  id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id      BIGINT UNSIGNED NOT NULL COMMENT '接收人',
  notify_type  VARCHAR(32)     NOT NULL
               COMMENT 'PROPOSAL_SETTLED / NEW_VOTE / COMMENT / FLOWER / GROUP_INVITE',
  ref_type     VARCHAR(32)     NOT NULL COMMENT 'PROPOSAL / GROUP / COMMENT',
  ref_id       BIGINT UNSIGNED NOT NULL COMMENT '多态关联，由服务层保证一致性',
  is_read      TINYINT UNSIGNED NOT NULL DEFAULT 0,
  push_status  VARCHAR(16)     NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING / SENT / FAILED',
  created_at   DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  KEY idx_notify_user (user_id, is_read, created_at DESC) COMMENT '未读通知列表',
  CONSTRAINT fk_notify_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
  CONSTRAINT ck_notify_read   CHECK (is_read IN (0, 1)),
  CONSTRAINT ck_notify_push   CHECK (push_status IN ('PENDING', 'SENT', 'FAILED')),
  CONSTRAINT ck_notify_reftype CHECK (ref_type IN ('PROPOSAL', 'GROUP', 'COMMENT'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='站内通知（ref_id 多态，无物理外键）';


-- =============================================================================
--  8. 可选表（P2）
-- =============================================================================

-- 8.1 wheel_runs —— 转盘历史（默认不建；扇区直接读 poll_options）
CREATE TABLE wheel_runs (
  id                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id           BIGINT UNSIGNED NOT NULL,
  proposal_id       BIGINT UNSIGNED NULL COMMENT '为空表示自由转盘',
  result_option_id  BIGINT UNSIGNED NULL,
  result_text       VARCHAR(200)    NULL COMMENT '自由转盘的文本结果',
  created_at        DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  KEY idx_wheel_user (user_id, created_at DESC),
  CONSTRAINT fk_wheel_user    FOREIGN KEY (user_id)          REFERENCES users (id) ON DELETE CASCADE,
  CONSTRAINT fk_wheel_proposal FOREIGN KEY (proposal_id)     REFERENCES purchase_proposals (id) ON DELETE SET NULL,
  CONSTRAINT fk_wheel_option  FOREIGN KEY (result_option_id) REFERENCES poll_options (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='转盘历史（可选）';
