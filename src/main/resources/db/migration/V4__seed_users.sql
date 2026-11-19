-- =============================================================================
--  V4 — 测试数据：用户
--
--  依赖：V1__initial_schema.sql
--  说明：
--    · 数据库中已存在 3 个用户，因此本迁移用户 id 从 4 开始，到 23
--    · 所有用户登录密码均为 password
--    · password_hash 使用 BCrypt 哈希，不存明文
-- =============================================================================

SET NAMES utf8mb4;

START TRANSACTION;

-- -----------------------------------------------------------------------------
-- users：20 个用户，id 4–23
-- -----------------------------------------------------------------------------
INSERT INTO users
  (id, username, password_hash, display_name, public_no, last_active_at)
VALUES
  (4,  'alice',     '$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG', 'alice',     4094701, UTC_TIMESTAMP(6)),
  (5,  'benjamin',  '$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG', 'benjamin',  4094702, UTC_TIMESTAMP(6)),
  (6,  'charlotte', '$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG', 'charlotte', 4094703, UTC_TIMESTAMP(6)),
  (7,  'daniel',    '$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG', 'daniel',    4094704, UTC_TIMESTAMP(6)),
  (8,  'emily',     '$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG', 'emily',     4094705, UTC_TIMESTAMP(6)),
  (9,  'frederick', '$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG', 'frederick', 4094706, UTC_TIMESTAMP(6)),
  (10, 'grace',     '$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG', 'grace',     4094707, UTC_TIMESTAMP(6)),
  (11, 'henry',     '$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG', 'henry',     4094708, UTC_TIMESTAMP(6)),
  (12, 'isabella',  '$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG', 'isabella',  4094709, UTC_TIMESTAMP(6)),
  (13, 'james',     '$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG', 'james',     4094710, UTC_TIMESTAMP(6)),
  (14, 'katherine', '$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG', 'katherine', 4094711, UTC_TIMESTAMP(6)),
  (15, 'leonard',   '$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG', 'leonard',   4094712, UTC_TIMESTAMP(6)),
  (16, 'mia',       '$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG', 'mia',       4094713, UTC_TIMESTAMP(6)),
  (17, 'nathan',    '$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG', 'nathan',    4094714, UTC_TIMESTAMP(6)),
  (18, 'olivia',    '$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG', 'olivia',    4094715, UTC_TIMESTAMP(6)),
  (19, 'peter',     '$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG', 'peter',     4094716, UTC_TIMESTAMP(6)),
  (20, 'quincy',    '$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG', 'quincy',    4094717, UTC_TIMESTAMP(6)),
  (21, 'rachel',    '$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG', 'rachel',    4094718, UTC_TIMESTAMP(6)),
  (22, 'samuel',    '$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG', 'samuel',    4094719, UTC_TIMESTAMP(6)),
  (23, 'tiffany',   '$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG', 'tiffany',   4094720, UTC_TIMESTAMP(6));

COMMIT;
