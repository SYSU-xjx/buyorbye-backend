# 《钱包请等等 / Buy or Bye》数据库表结构 · 最终版

**数据库**：MySQL 8.4 / InnoDB / utf8mb4 / `utf8mb4_0900_ai_ci`

**技术栈**：Spring Boot（Java）+ Android Studio + MySQL

**规模**：30 张表（P0 必做 19 张 · P1 建议 8 张 · P2 可选 3 张）

**通用约定**

| 项 | 约定 |
|---|---|
| 主键 | `BIGINT UNSIGNED AUTO_INCREMENT`；关联表用复合主键 |
| **键标注** | MySQL 列中直接标出 <span class="k k-pk">PRIMARY KEY</span> 与 <span class="k k-fk">FOREIGN KEY</span> <span class="kref">→ 引用表.字段</span>，与建表 DDL 一一对应。注意：`AUTO_INCREMENT` 只是自增，**不等于主键**，DDL 仍须单独声明 `PRIMARY KEY` |
| 时间 | `DATETIME(6)` 存 **UTC**，Java 侧 `LocalDateTime`，展示时按用户时区转换 |
| 金额 | 一律 `DECIMAL`，Java 侧 `BigDecimal`，**禁止 float/double** |
| 状态 | `VARCHAR` 存枚举字符串，Java 侧用 `enum` + `@Enumerated(EnumType.STRING)` |
| 命名 | 表名小写下划线；`app_groups` 避开 SQL 关键字 `GROUP` |
| 字符 | 全库 `utf8mb4`，否则 emoji 昵称入库报错 |

**评审图例**：<span class="r r-keep">保留</span> 原设计正确 · <span class="r r-mod">改</span> 需调整 · <span class="r r-new">新增</span> 原设计没有

---

## 表清单总览

| # | 表名 | 用途 | 优先级 | 相对原设计 |
|---|---|---|---|---|
| 1 | users | 账号与公开资料 | P0 | 改 |
| 2 | auth_sessions | 登录会话与刷新令牌 | P0 | 新增 |
| 3 | user_devices | 推送设备（FCM） | P0 | 新增 |
| 4 | reputation_accounts | 信誉分汇总 | P0 | 保留 |
| 5 | reputation_ledger | 信誉分流水 | P1 | 小改 |
| 6 | user_streaks | 理性消费坚持天数 | P1 | 新增 |
| 7 | app_groups | 审批群 | P0 | 改 |
| 8 | group_members | 群成员关系 | P0 | 保留 |
| 9 | channels | 广场频道 | P0 | 保留 |
| 10 | user_channels | 用户订阅频道 | P0 | 小改 |
| 11 | tags | 内容标签 | P0 | 新增 |
| 12 | proposal_tags | 审批 ↔ 标签 | P0 | 新增 |
| 13 | purchase_proposals | 审批单（核心） | P0 | 改 |
| 14 | poll_options | 选项投票的候选项 | P0 | 新增 |
| 15 | purchase_proposal_images | 审批配图 | P0 | 小改 |
| 16 | purchase_proposal_channels | 审批 ↔ 频道 | P0 | 保留 |
| 17 | decision_votes | 投票事件 | P0 | 改 |
| 18 | proposal_watches | 围观记录 | P0 | 新增 |
| 19 | proposal_comments | 回应/评论 | P0 | 新增 |
| 20 | proposal_updates | 作者补充 | P1 | 新增 |
| 21 | flowers | 送花 | P1 | 新增 |
| 22 | exchange_rate_snapshots | 汇率快照 | P1 | 保留 |
| 23 | weekly_rankings | 周榜快照头 | P2 | 新增 |
| 24 | weekly_ranking_entries | 周榜明细 | P2 | 新增 |
| 25 | blocked_words | 屏蔽词库 | P1 | 保留 |
| 26 | blocked_users | 黑名单 | P1 | 保留 |
| 27 | feedback | 意见反馈 | P0 | 改 |
| 28 | feedback_images | 反馈截图 | P0 | 新增 |
| 29 | notifications | 站内通知 | P1 | 新增 |
| 30 | wheel_runs | 转盘历史（可选） | P2 | 新增 |

---

# 一、用户与鉴权

## 3.1 users

注册账号与公开资料。认证只存 BCrypt 哈希。

| 字段 | MySQL | 含义 | Java 类型 | 评审 | 说明 |
|---|---|---|---|---|---|
| id | BIGINT UNSIGNED AUTO_INCREMENT | 内部用户主键 | Long | <span class="r r-keep">保留</span> | 所有外键都指向此列，不要改成 UUID |
| username | VARCHAR(64) NOT NULL | 登录名，统一存小写 | String | <span class="r r-keep">保留</span> | 存小写，避免 MySQL 与客户端 SQLite 的唯一约束行为不一致 |
| password_hash | VARCHAR(255) NOT NULL | BCrypt 密码哈希 | String | <span class="r r-keep">保留</span> | 安卓是自有账号体系，密码是主凭证，必须保留 |
| display_name | VARCHAR(80) NOT NULL | 展示昵称 | String | <span class="r r-mod">改</span> | 原为可空，但每张卡片都要显示昵称，空值会让界面塌陷 |
| avatar_url | VARCHAR(2048) NULL | 头像地址 | String | <span class="r r-keep">保留</span> | 只存 URL，图片文件放对象存储 |
| public_no | BIGINT UNSIGNED NULL | 对外展示 ID（如 4094713） | Long | <span class="r r-new">新增</span> | 界面"ID: 4094713"；暴露自增主键会泄露用户总量且可被遍历 |
| preferred_currency | CHAR(3) DEFAULT 'CNY' | 首页展示币种 | String | <span class="r r-new">新增</span> | 首页有币种切换，属用户级偏好，不能只存前端 |
| account_status | VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' | ACTIVE / ANONYMIZED | enum | <span class="r r-keep">保留</span> | 注销时置 ANONYMIZED，保留审计链不断裂 |
| last_active_at | DATETIME(6) NULL | 最后活跃时间 | LocalDateTime | <span class="r r-new">新增</span> | 榜单"最活跃审判官"与僵尸会话清理都依赖它 |
| created_at | DATETIME(6) NOT NULL | 创建时间 | LocalDateTime | <span class="r r-keep">保留</span> | |
| updated_at | DATETIME(6) NOT NULL | 更新时间 | LocalDateTime | <span class="r r-keep">保留</span> | 由 `ON UPDATE CURRENT_TIMESTAMP(6)` 自动维护 |

## 3.2 auth_sessions

登录会话。小程序由微信托管登录态，安卓必须自己建。

| 字段 | MySQL | 含义 | Java 类型 | 评审 | 说明 |
|---|---|---|---|---|---|
| id | BIGINT UNSIGNED AUTO_INCREMENT | 会话主键 | Long | <span class="r r-new">新增</span> | 每条 = 一次设备登录 |
| user_id | BIGINT UNSIGNED NOT NULL | 用户 | Long | <span class="r r-new">新增</span> | 一个用户可多设备同时在线 |
| refresh_token_hash | VARCHAR(255) NOT NULL | Refresh Token 的 SHA-256 | String | <span class="r r-new">新增</span> | 只存哈希，库被拖走也拿不到可用 token |
| device_id | VARCHAR(64) NOT NULL | 客户端生成的稳定设备标识 | String | <span class="r r-new">新增</span> | 支持按设备踢下线 |
| device_name | VARCHAR(100) NULL | 如 "Pixel 7" | String | <span class="r r-new">新增</span> | 多设备管理列表展示 |
| app_version | VARCHAR(32) NULL | 客户端版本号 | String | <span class="r r-new">新增</span> | 排查兼容问题、判断是否强制升级 |
| ip_address | VARCHAR(45) NULL | 登录 IP（兼容 IPv6） | String | <span class="r r-new">新增</span> | 异地登录告警 |
| issued_at | DATETIME(6) NOT NULL | 签发时间 | LocalDateTime | <span class="r r-new">新增</span> | |
| expires_at | DATETIME(6) NOT NULL | 过期时间（建议 30 天） | LocalDateTime | <span class="r r-new">新增</span> | 不设过期等于永久凭证，安全上不允许 |
| revoked_at | DATETIME(6) NULL | 吊销时间 | LocalDateTime | <span class="r r-new">新增</span> | 改密码/登出时置值，**这是"踢下线"的唯一依据** |
| last_used_at | DATETIME(6) NULL | 最近使用时间 | LocalDateTime | <span class="r r-new">新增</span> | 滑动续期与不活跃清理 |

## 3.3 user_devices

推送设备。FCM token 会因重装、清数据、换机而失效。

| 字段 | MySQL | 含义 | Java 类型 | 评审 | 说明 |
|---|---|---|---|---|---|
| id | BIGINT UNSIGNED AUTO_INCREMENT | 设备主键 | Long | <span class="r r-new">新增</span> | |
| user_id | BIGINT UNSIGNED NOT NULL | 用户 | Long | <span class="r r-new">新增</span> | 一人可多设备 |
| fcm_token | VARCHAR(255) NOT NULL | FCM 注册 token | String | <span class="r r-new">新增</span> | 安卓推送的唯一凭据，必须落库 |
| device_id | VARCHAR(64) NOT NULL | 与 auth_sessions 对齐 | String | <span class="r r-new">新增</span> | 同一设备换 token 时覆盖而非新增 |
| platform | VARCHAR(16) NOT NULL DEFAULT 'ANDROID' | 平台 | enum | <span class="r r-new">新增</span> | 预留 iOS 扩展 |
| app_version | VARCHAR(32) NULL | 客户端版本 | String | <span class="r r-new">新增</span> | |
| is_active | TINYINT UNSIGNED NOT NULL DEFAULT 1 | 是否有效 | Boolean | <span class="r r-new">新增</span> | FCM 返回 UNREGISTERED 时置 0，避免无限重试 |
| last_seen_at | DATETIME(6) NULL | 最近活跃时间 | LocalDateTime | <span class="r r-new">新增</span> | 清理长期不活跃设备 |

## 3.4 reputation_accounts

信誉分汇总。流水是审计源，这里是查询快照。

| 字段 | MySQL | 含义 | Java 类型 | 评审 | 说明 |
|---|---|---|---|---|---|
| user_id | BIGINT UNSIGNED NOT NULL | 用户 | Long | <span class="r r-keep">保留</span> | 一人一行，避免每次从流水求和 |
| score | INT UNSIGNED NOT NULL DEFAULT 0 | 当前信誉分 | Integer | <span class="r r-keep">保留</span> | 等级/徽章由分数区间推导，**不落库**，避免两处真相 |
| created_at | DATETIME(6) NOT NULL | 创建时间 | LocalDateTime | <span class="r r-keep">保留</span> | |
| updated_at | DATETIME(6) NOT NULL | 更新时间 | LocalDateTime | <span class="r r-keep">保留</span> | |

## 3.5 reputation_ledger

信誉分流水。每次加减分都要可追溯、可重算。

| 字段 | MySQL | 含义 | Java 类型 | 评审 | 说明 |
|---|---|---|---|---|---|
| id | BIGINT UNSIGNED AUTO_INCREMENT | 流水主键 | Long | <span class="r r-keep">保留</span> | |
| user_id | BIGINT UNSIGNED NOT NULL | 受益用户 | Long | <span class="r r-keep">保留</span> | |
| proposal_id | BIGINT UNSIGNED NOT NULL | 关联审批 | Long | <span class="r r-keep">保留</span> | |
| source_vote_id | BIGINT UNSIGNED NOT NULL UNIQUE | 来源投票 | Long | <span class="r r-keep">保留</span> | 唯一约束保证同一票不重复加分，是**幂等最后一道防线** |
| score_change | SMALLINT NOT NULL | 分值变化，-100 ~ 100 | Integer | <span class="r r-mod">改</span> | 原 CHECK 只允许 +1，无法支持扣分与送花奖励 |
| source_type | VARCHAR(32) NOT NULL | VOTE_CORRECT / VOTE_WRONG / FLOWER_RECEIVED / DAILY_ACTIVE / ADMIN_ADJUST | enum | <span class="r r-new">新增</span> | 区分加分来源，便于统计与规则演进 |
| rule_version | VARCHAR(32) NOT NULL | 规则版本号 | String | <span class="r r-keep">保留</span> | 规则调整后历史分数仍可解释 |
| created_at | DATETIME(6) NOT NULL | 记账时间 | LocalDateTime | <span class="r r-keep">保留</span> | |

## 3.6 user_streaks

理性消费坚持天数。按"连续自然日"判断，无法用单条 SQL 实时算出。

| 字段 | MySQL | 含义 | Java 类型 | 评审 | 说明 |
|---|---|---|---|---|---|
| user_id | BIGINT UNSIGNED NOT NULL | 用户 | Long | <span class="r r-new">新增</span> | 界面"已坚持理性消费 2 天" |
| current_days | INT UNSIGNED NOT NULL DEFAULT 0 | 当前连续天数 | Integer | <span class="r r-new">新增</span> | 由每日定时任务扫描结算记录后更新 |
| longest_days | INT UNSIGNED NOT NULL DEFAULT 0 | 历史最长天数 | Integer | <span class="r r-new">新增</span> | 成就感展示 |
| last_counted_date | DATE NULL | 最后计数日期 | LocalDate | <span class="r r-new">新增</span> | 防止同一天被重复计数 |
| updated_at | DATETIME(6) NOT NULL | 更新时间 | LocalDateTime | <span class="r r-new">新增</span> | |

---

# 二、群组

## 3.7 app_groups

审批群。表名避开 SQL 关键字 `GROUP`。

| 字段 | MySQL | 含义 | Java 类型 | 评审 | 说明 |
|---|---|---|---|---|---|
| id | BIGINT UNSIGNED AUTO_INCREMENT | 群主键 | Long | <span class="r r-keep">保留</span> | |
| name | VARCHAR(100) NOT NULL | 群名 | String | <span class="r r-keep">保留</span> | |
| owner_user_id | BIGINT UNSIGNED NOT NULL | 群主 | Long | <span class="r r-keep">保留</span> | 群主转让的唯一权威源，不要靠 member_role 判断 |
| invite_code | CHAR(8) NULL UNIQUE | 8 位群号（明文，如 A7K2M9QX） | String | <span class="r r-new">新增</span> | 界面要求"输入群号加入"；原 `invite_code_hash` 单向哈希**无法反查** |
| invite_code_expires_at | DATETIME(6) NULL | 群号有效期 | LocalDateTime | <span class="r r-new">新增</span> | 明文短码需配合过期 + 输错限流防爆破 |
| group_status | VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' | ACTIVE / CLOSED | enum | <span class="r r-keep">保留</span> | |
| member_count | INT UNSIGNED NOT NULL DEFAULT 0 | 成员数 | Integer | <span class="r r-new">新增</span> | 群列表要显示人数，避免每次 `COUNT(*)` |
| created_at | DATETIME(6) NOT NULL | 创建时间 | LocalDateTime | <span class="r r-keep">保留</span> | |
| updated_at | DATETIME(6) NOT NULL | 更新时间 | LocalDateTime | <span class="r r-keep">保留</span> | |

> **已删除** `invite_code_hash`：哈希不可逆，与"输入群号加入"的功能直接冲突。

## 3.8 group_members

一条记录 = 一个用户在一个群里的成员关系。

| 字段 | MySQL | 含义 | Java 类型 | 评审 | 说明 |
|---|---|---|---|---|---|
| id | BIGINT UNSIGNED AUTO_INCREMENT | 成员记录主键 | Long | <span class="r r-keep">保留</span> | |
| group_id | BIGINT UNSIGNED NOT NULL | 群 | Long | <span class="r r-keep">保留</span> | |
| user_id | BIGINT UNSIGNED NOT NULL | 用户 | Long | <span class="r r-keep">保留</span> | |
| member_role | VARCHAR(16) NOT NULL DEFAULT 'MEMBER' | OWNER / MEMBER | enum | <span class="r r-keep">保留</span> | 仅用于展示；权威判断看 `app_groups.owner_user_id` |
| member_status | VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' | ACTIVE / LEFT / REMOVED | enum | <span class="r r-keep">保留</span> | 便于 `WHERE status='ACTIVE'` 走索引 |
| joined_at | DATETIME(6) NOT NULL | 加入时间 | LocalDateTime | <span class="r r-keep">保留</span> | |
| left_at | DATETIME(6) NULL | 退出/被移除时间 | LocalDateTime | <span class="r r-keep">保留</span> | |

> **关键约束** `UNIQUE(group_id, user_id)`：重进群是更新原行而非插新行，投票历史留在 `decision_votes` 不受影响。

---

# 三、广场与频道

## 3.9 channels

系统运营的广场频道（如"随便看看""奶茶"）。

| 字段 | MySQL | 含义 | Java 类型 | 评审 | 说明 |
|---|---|---|---|---|---|
| id | BIGINT UNSIGNED AUTO_INCREMENT | 频道主键 | Long | <span class="r r-keep">保留</span> | |
| slug | VARCHAR(64) NOT NULL UNIQUE | 稳定标识，如 `random` | String | <span class="r r-keep">保留</span> | 前端用 slug 而非 id 做路由，换 id 不断链 |
| name | VARCHAR(100) NOT NULL | 展示名 | String | <span class="r r-keep">保留</span> | |
| description | VARCHAR(500) NULL | 频道描述 | String | <span class="r r-keep">保留</span> | |
| is_active | TINYINT UNSIGNED NOT NULL DEFAULT 1 | 是否启用 | Boolean | <span class="r r-keep">保留</span> | 下架频道不必物理删除 |
| created_at | DATETIME(6) NOT NULL | 创建时间 | LocalDateTime | <span class="r r-keep">保留</span> | |

## 3.10 user_channels

用户订阅的频道，对应"我的频道 / 管理频道"。

| 字段 | MySQL | 含义 | Java 类型 | 评审 | 说明 |
|---|---|---|---|---|---|
| user_id | BIGINT UNSIGNED NOT NULL | 订阅者 | Long | <span class="r r-keep">保留</span> | 复合主键之一 |
| channel_id | BIGINT UNSIGNED NOT NULL | 频道 | Long | <span class="r r-keep">保留</span> | 复合主键之一 |
| sort_order | SMALLINT UNSIGNED NOT NULL DEFAULT 0 | 频道排序 | Integer | <span class="r r-new">新增</span> | "管理频道"若支持拖拽排序，必须有地方存顺序 |
| subscribed_at | DATETIME(6) NOT NULL | 订阅时间 | LocalDateTime | <span class="r r-keep">保留</span> | |

## 3.11 tags

内容标签。发布审批时必选 1–3 个。

| 字段 | MySQL | 含义 | Java 类型 | 评审 | 说明 |
|---|---|---|---|---|---|
| id | BIGINT UNSIGNED AUTO_INCREMENT | 标签主键 | Long | <span class="r r-new">新增</span> | 界面有 16 个预设标签，必须落表才能筛选与统计 |
| slug | VARCHAR(64) NOT NULL UNIQUE | 稳定标识，如 `food` | String | <span class="r r-new">新增</span> | |
| name | VARCHAR(50) NOT NULL | 展示名，如"食物" | String | <span class="r r-new">新增</span> | |
| display_order | SMALLINT UNSIGNED NOT NULL DEFAULT 0 | 展示顺序 | Integer | <span class="r r-new">新增</span> | 标签栏顺序要固定，不能靠 id 排序 |
| is_active | TINYINT UNSIGNED NOT NULL DEFAULT 1 | 是否启用 | Boolean | <span class="r r-new">新增</span> | 下线标签不影响历史数据 |
| created_at | DATETIME(6) NOT NULL | 创建时间 | LocalDateTime | <span class="r r-new">新增</span> | |

## 3.12 proposal_tags

审批与标签的多对多关系。

| 字段 | MySQL | 含义 | Java 类型 | 评审 | 说明 |
|---|---|---|---|---|---|
| proposal_id | BIGINT UNSIGNED NOT NULL | 审批 | Long | <span class="r r-new">新增</span> | 复合主键之一 |
| tag_id | BIGINT UNSIGNED NOT NULL | 标签 | Long | <span class="r r-new">新增</span> | 复合主键之一，按标签筛广场内容 |

> 复合主键只能防重复，"1–3 个"的数量限制由 Spring Boot 服务层在事务内校验。

---

# 四、审批核心

## 3.13 purchase_proposals

审批单主表。一条记录 = 一次"要不要买 / 选哪个"的决策请求。

| 字段 | MySQL | 含义 | Java 类型 | 评审 | 说明 |
|---|---|---|---|---|---|
| id | BIGINT UNSIGNED AUTO_INCREMENT | 审批主键 | Long | <span class="r r-keep">保留</span> | |
| creator_user_id | BIGINT UNSIGNED NOT NULL | 发起人 | Long | <span class="r r-keep">保留</span> | |
| participation_type | VARCHAR(16) NOT NULL DEFAULT 'APPROVE_REJECT' | APPROVE_REJECT / OPTION_VOTE | enum | <span class="r r-new">新增</span> | 界面"参与方式"二选一；原结构无法区分这两条完全不同的业务线 |
| approval_mode | VARCHAR(16) NOT NULL DEFAULT 'MULTI' | SINGLE / MULTI | enum | <span class="r r-new">新增</span> | 界面"单人审批 / 多人审批"，卡片要显示"多人审批" |
| publication_scope | VARCHAR(8) NULL | GROUP / PLAZA | enum | <span class="r r-keep">保留</span> | 草稿态可为空 |
| group_id | BIGINT UNSIGNED NULL | 目标群 | Long | <span class="r r-keep">保留</span> | 发群必填、发广场必须为空 |
| item_name | VARCHAR(200) NULL | 商品名（二元模式用） | String | <span class="r r-keep">保留</span> | |
| poll_question | VARCHAR(300) NULL | 纠结什么（投票模式用） | String | <span class="r r-new">新增</span> | 投票模式没有"商品名"，语义必须分开，否则字段含义混乱 |
| amount | DECIMAL(12,2) NULL | 价格（二元模式用） | BigDecimal | <span class="r r-keep">保留</span> | 投票模式的价格在 `poll_options.option_amount` |
| currency | CHAR(3) NULL | HKD / CNY / USD | String | <span class="r r-keep">保留</span> | 金额必须与币种成对出现 |
| purchase_reason | TEXT NULL | 购买理由 / 纠结点 | String | <span class="r r-keep">保留</span> | |
| alternative_product_tip | TEXT NULL | 更便宜的替代品提示 | String | <span class="r r-keep">保留</span> | 这是产品"理性消费"的核心卖点 |
| proposal_status | VARCHAR(32) NOT NULL DEFAULT 'DRAFT' | DRAFT / OPEN / SETTLED / WITHDRAWN / INSUFFICIENT_PARTICIPATION | enum | <span class="r r-keep">保留</span> | 状态机清晰，已覆盖超时无人的情况 |
| decision_result | TINYINT UNSIGNED NULL | 1=BUY / 0=NOT_BUY | Boolean | <span class="r r-keep">保留</span> | 仅 APPROVE_REJECT 模式使用；SETTLED 时必填 |
| winning_option_id | BIGINT UNSIGNED NULL | 获胜选项 | Long | <span class="r r-new">新增</span> | 投票模式的结算结果；`TINYINT` 存不下"选 B" |
| settlement_mode | VARCHAR(16) NOT NULL DEFAULT 'TIME' | TIME / QUORUM | enum | <span class="r r-new">新增</span> | 界面"限时投票 / 定额人数投票"，两种结束条件逻辑完全不同 |
| minimum_voter_count | INT UNSIGNED NOT NULL DEFAULT 3 | 最低有效票数 / 目标票数 | Integer | <span class="r r-mod">改</span> | 原默认值 1 有陷阱：限时模式下第一个人投票就会触发结算 |
| voter_count | INT UNSIGNED NOT NULL DEFAULT 0 | 已表态人数 | Integer | <span class="r r-new">新增</span> | 卡片"已有 8 人表态"；列表页逐条 `COUNT(DISTINCT)` 会拖垮分页 |
| watch_count | INT UNSIGNED NOT NULL DEFAULT 0 | 围观数 | Integer | <span class="r r-new">新增</span> | 同上，列表页高频读取 |
| comment_count | INT UNSIGNED NOT NULL DEFAULT 0 | 回应数 | Integer | <span class="r r-new">新增</span> | "收到回应"统计与卡片角标 |
| client_request_id | CHAR(36) NULL | 客户端幂等 UUID | String | <span class="r r-new">新增</span> | 弱网下重复点"发布"不会产生两条审批 |
| published_at | DATETIME(6) NULL | 发布时间 | LocalDateTime | <span class="r r-keep">保留</span> | |
| deadline_at | DATETIME(6) NULL | 投票截止时间 | LocalDateTime | <span class="r r-keep">保留</span> | 界面"剩余 2 小时"由它算出 |
| settled_at | DATETIME(6) NULL | 结算时间 | LocalDateTime | <span class="r r-keep">保留</span> | 周榜按此列切周 |
| withdrawn_at | DATETIME(6) NULL | 撤回时间 | LocalDateTime | <span class="r r-keep">保留</span> | |
| created_at | DATETIME(6) NOT NULL | 创建时间 | LocalDateTime | <span class="r r-keep">保留</span> | |
| updated_at | DATETIME(6) NOT NULL | 更新时间 | LocalDateTime | <span class="r r-keep">保留</span> | |

> **`minimum_voter_count` 语义**：`TIME` 模式下是"最低有效票数门槛"（不足则 `INSUFFICIENT_PARTICIPATION`）；`QUORUM` 模式下是"目标票数"（达到即结算）。

## 3.14 poll_options

选项投票的候选项（A/B/C，各自可带价格）。

| 字段 | MySQL | 含义 | Java 类型 | 评审 | 说明 |
|---|---|---|---|---|---|
| id | BIGINT UNSIGNED AUTO_INCREMENT | 选项主键 | Long | <span class="r r-new">新增</span> | 没有这张表，"选项投票"整条业务线无法实现 |
| proposal_id | BIGINT UNSIGNED NOT NULL | 所属审批 | Long | <span class="r r-new">新增</span> | |
| option_label | VARCHAR(8) NOT NULL | A / B / C | String | <span class="r r-new">新增</span> | 界面固定显示字母编号 |
| option_text | VARCHAR(200) NOT NULL | 选项内容 | String | <span class="r r-new">新增</span> | |
| option_amount | DECIMAL(12,2) NULL | 该选项的价格 | BigDecimal | <span class="r r-new">新增</span> | 界面每个选项都有独立价格，不能共用主表 `amount` |
| display_order | TINYINT UNSIGNED NOT NULL | 1–6 | Integer | <span class="r r-new">新增</span> | 配合唯一约束固定顺序并限制选项数量 |
| created_at | DATETIME(6) NOT NULL | 创建时间 | LocalDateTime | <span class="r r-new">新增</span> | |

> 约束 `UNIQUE(proposal_id, display_order)` + `CHECK(display_order BETWEEN 1 AND 6)`。
> 这张表同时是**转盘的扇区数据源**，所以转盘不需要再建表。

## 3.15 purchase_proposal_images

审批配图。图片存对象存储，库里只存 URL。

| 字段 | MySQL | 含义 | Java 类型 | 评审 | 说明 |
|---|---|---|---|---|---|
| id | BIGINT UNSIGNED AUTO_INCREMENT | 图片主键 | Long | <span class="r r-keep">保留</span> | |
| proposal_id | BIGINT UNSIGNED NOT NULL | 所属审批 | Long | <span class="r r-keep">保留</span> | |
| image_url | VARCHAR(2048) NOT NULL | 对象存储 URL | String | <span class="r r-keep">保留</span> | MySQL 不放二进制，避免拖慢备份与查询 |
| display_order | TINYINT UNSIGNED NOT NULL | 1–4 | Integer | <span class="r r-mod">改</span> | 原为 1–6，但界面明确"最多 4 张"，约束应与 UI 一致 |
| created_at | DATETIME(6) NOT NULL | 创建时间 | LocalDateTime | <span class="r r-keep">保留</span> | |

## 3.16 purchase_proposal_channels

审批发布到哪些广场频道。可进多个频道，但**共用一个票池**。

| 字段 | MySQL | 含义 | Java 类型 | 评审 | 说明 |
|---|---|---|---|---|---|
| proposal_id | BIGINT UNSIGNED NOT NULL | 审批 | Long | <span class="r r-keep">保留</span> | 复合主键之一 |
| channel_id | BIGINT UNSIGNED NOT NULL | 频道 | Long | <span class="r r-keep">保留</span> | 复合主键之一 |

> 设计意图很关键：同一审批出现在多个频道时，投票和结果仍是同一份，不会分裂。

---

# 五、投票与互动

## 3.17 decision_votes

投票事件表。**追加写入**，改票是新增一行，只取每人最新一行计票。

| 字段 | MySQL | 含义 | Java 类型 | 评审 | 说明 |
|---|---|---|---|---|---|
| id | BIGINT UNSIGNED AUTO_INCREMENT | 事件主键 | Long | <span class="r r-keep">保留</span> | 同时用于选出"最新一票" |
| proposal_id | BIGINT UNSIGNED NOT NULL | 审批 | Long | <span class="r r-keep">保留</span> | |
| voter_user_id | BIGINT UNSIGNED NOT NULL | 投票人 | Long | <span class="r r-keep">保留</span> | |
| vote_type | VARCHAR(16) NOT NULL DEFAULT 'BINARY' | BINARY / OPTION | enum | <span class="r r-new">新增</span> | 二元裁决与多选一共用一张表，靠此列区分 |
| choice | TINYINT UNSIGNED NULL | 1=BUY / 0=NOT_BUY | Boolean | <span class="r r-mod">改</span> | 改为可空：仅 BINARY 模式有值，OPTION 模式必须为 NULL |
| option_id | BIGINT UNSIGNED NULL | 所选选项 | Long | <span class="r r-new">新增</span> | 投票模式记录"选了 B"；原 `choice TINYINT` 根本存不下 |
| reason | TEXT NULL | 投票理由 | String | <span class="r r-mod">改</span> | **必须改为可空**：界面"通过/驳回"是一键按钮，无理由输入框，NOT NULL 会让每次点击都报错 |
| reputation_score_snapshot | INT UNSIGNED NOT NULL | 投票时的信誉分 | Integer | <span class="r r-keep">保留</span> | 快照而非实时值，规则调整后历史投票仍可解释 |
| weight_snapshot | TINYINT UNSIGNED NOT NULL | 投票时的票权 1/2/3 | Integer | <span class="r r-keep">保留</span> | 高信誉用户票权更高，必须在投票瞬间冻结 |
| client_request_id | CHAR(36) NOT NULL | 客户端幂等 UUID | String | <span class="r r-new">新增</span> | 弱网重试不会投出两票，这是移动端最容易出的 bug |
| cast_at | DATETIME(6) NOT NULL | 投票时间 | LocalDateTime | <span class="r r-keep">保留</span> | |

> `UNIQUE(voter_user_id, client_request_id)` 幂等键：重复请求返回首次结果而非报错。
> `CHECK((vote_type='BINARY' AND choice IS NOT NULL AND option_id IS NULL) OR (vote_type='OPTION' AND option_id IS NOT NULL AND choice IS NULL))`。

## 3.18 proposal_watches

围观记录。"关注但不表态"，与投票是两种不同行为。

| 字段 | MySQL | 含义 | Java 类型 | 评审 | 说明 |
|---|---|---|---|---|---|
| proposal_id | BIGINT UNSIGNED NOT NULL | 审批 | Long | <span class="r r-new">新增</span> | 复合主键之一，卡片右上"围观"按钮 |
| user_id | BIGINT UNSIGNED NOT NULL | 用户 | Long | <span class="r r-new">新增</span> | 复合主键之一，个人页"我围观的"依赖它 |
| created_at | DATETIME(6) NOT NULL | 围观时间 | LocalDateTime | <span class="r r-new">新增</span> | |

> 不能复用 `decision_votes`：围观不参与计票，混表会导致结算统计出错。

## 3.19 proposal_comments

回应/评论。广场"给点你的想法"与个人页"收到回应"都靠它。

| 字段 | MySQL | 含义 | Java 类型 | 评审 | 说明 |
|---|---|---|---|---|---|
| id | BIGINT UNSIGNED AUTO_INCREMENT | 评论主键 | Long | <span class="r r-new">新增</span> | 原设计没有评论表，但界面有"收到回应"统计 |
| proposal_id | BIGINT UNSIGNED NOT NULL | 审批 | Long | <span class="r r-new">新增</span> | |
| author_user_id | BIGINT UNSIGNED NOT NULL | 作者 | Long | <span class="r r-new">新增</span> | |
| parent_comment_id | BIGINT UNSIGNED NULL | 父评论（一层楼中楼） | Long | <span class="r r-new">新增</span> | 只做一层，不做无限嵌套，控制复杂度 |
| content | TEXT NOT NULL | 内容 | String | <span class="r r-new">新增</span> | |
| comment_status | VARCHAR(16) NOT NULL DEFAULT 'VISIBLE' | VISIBLE / HIDDEN / DELETED | enum | <span class="r r-new">新增</span> | 软删除，保留审计与统计口径 |
| created_at | DATETIME(6) NOT NULL | 发表时间 | LocalDateTime | <span class="r r-new">新增</span> | |

## 3.20 proposal_updates

作者补充。发布后追加的说明，**带独立时间戳**。

| 字段 | MySQL | 含义 | Java 类型 | 评审 | 说明 |
|---|---|---|---|---|---|
| id | BIGINT UNSIGNED AUTO_INCREMENT | 补充主键 | Long | <span class="r r-new">新增</span> | 详情页有独立"作者补充"区块，时间与主帖不同 |
| proposal_id | BIGINT UNSIGNED NOT NULL | 审批 | Long | <span class="r r-new">新增</span> | |
| content | TEXT NOT NULL | 补充内容 | String | <span class="r r-new">新增</span> | |
| created_at | DATETIME(6) NOT NULL | 补充时间 | LocalDateTime | <span class="r r-new">新增</span> | 界面上单独显示（如"2026-11-03 15:22"） |

## 3.21 flowers

送花。首页三宫格的"收到花总数"。

| 字段 | MySQL | 含义 | Java 类型 | 评审 | 说明 |
|---|---|---|---|---|---|
| id | BIGINT UNSIGNED AUTO_INCREMENT | 主键 | Long | <span class="r r-new">新增</span> | 原设计没有这个实体，但首页有统计 |
| proposal_id | BIGINT UNSIGNED NOT NULL | 关联审批 | Long | <span class="r r-new">新增</span> | 花是送给某条内容的 |
| giver_user_id | BIGINT UNSIGNED NOT NULL | 送花人 | Long | <span class="r r-new">新增</span> | |
| receiver_user_id | BIGINT UNSIGNED NOT NULL | 收花人 | Long | <span class="r r-new">新增</span> | 冗余存储，统计时免去 JOIN |
| created_at | DATETIME(6) NOT NULL | 送花时间 | LocalDateTime | <span class="r r-new">新增</span> | |

> `UNIQUE(proposal_id, giver_user_id)`：同一人对同一条内容只能送一次，天然防刷。

---

# 六、汇率与榜单

## 3.22 exchange_rate_snapshots

结算时刻的汇率快照，让跨币种金额事后仍可复现。

| 字段 | MySQL | 含义 | Java 类型 | 评审 | 说明 |
|---|---|---|---|---|---|
| id | BIGINT UNSIGNED AUTO_INCREMENT | 快照主键 | Long | <span class="r r-keep">保留</span> | |
| proposal_id | BIGINT UNSIGNED NOT NULL UNIQUE | 审批 | Long | <span class="r r-keep">保留</span> | 一条审批最多一个快照 |
| source_currency | CHAR(3) NOT NULL | 原币种 | String | <span class="r r-keep">保留</span> | |
| target_currency | CHAR(3) NOT NULL DEFAULT 'HKD' | 目标币种 | String | <span class="r r-keep">保留</span> | 榜单统一折算到 HKD |
| rate | DECIMAL(20,10) NOT NULL | 汇率，必须 > 0 | BigDecimal | <span class="r r-keep">保留</span> | 精度足够，Java 侧必须用 `BigDecimal` |
| converted_amount | DECIMAL(16,2) NOT NULL | 折算后金额 | BigDecimal | <span class="r r-keep">保留</span> | 周榜"批准金额最大"直接读它 |
| rate_source | VARCHAR(100) NOT NULL | 汇率来源 | String | <span class="r r-keep">保留</span> | 数据可追溯 |
| rate_date | DATE NOT NULL | 汇率生效日 | LocalDate | <span class="r r-keep">保留</span> | 可能早于结算日，必须分开记 |
| created_at | DATETIME(6) NOT NULL | 存储时间 | LocalDateTime | <span class="r r-keep">保留</span> | |

## 3.23 weekly_rankings

周榜快照头表。**仅当需要回看历史周榜时创建**。

| 字段 | MySQL | 含义 | Java 类型 | 评审 | 说明 |
|---|---|---|---|---|---|
| id | BIGINT UNSIGNED AUTO_INCREMENT | 榜单主键 | Long | <span class="r r-new">新增</span> | 规则会变、用户会注销，事后重算的"上周榜"与当时看到的不一致 |
| week_start_date | DATE NOT NULL | 该周周一（UTC+8） | LocalDate | <span class="r r-new">新增</span> | 切周必须按香港时区，否则错位 8 小时 |
| ranking_type | VARCHAR(32) NOT NULL | MOST_REJECTED / HIGHEST_APPROVED_AMOUNT / TOP_JUDGE | enum | <span class="r r-new">新增</span> | 三类榜单共用一张头表 |
| rule_version | VARCHAR(32) NOT NULL | 计算规则版本 | String | <span class="r r-new">新增</span> | 榜单口径变更后可解释 |
| generated_at | DATETIME(6) NOT NULL | 生成时间 | LocalDateTime | <span class="r r-new">新增</span> | |
| created_at | DATETIME(6) NOT NULL | 创建时间 | LocalDateTime | <span class="r r-new">新增</span> | |

## 3.24 weekly_ranking_entries

周榜明细行。

| 字段 | MySQL | 含义 | Java 类型 | 评审 | 说明 |
|---|---|---|---|---|---|
| ranking_id | BIGINT UNSIGNED NOT NULL | 榜单 | Long | <span class="r r-new">新增</span> | 复合主键之一 |
| rank_no | SMALLINT UNSIGNED NOT NULL | 名次，1 起 | Integer | <span class="r r-new">新增</span> | 与 ranking_id 组成主键，天然保证同榜名次不重复 |
| user_id | BIGINT UNSIGNED NOT NULL | 上榜用户 | Long | <span class="r r-new">新增</span> | |
| metric_value | DECIMAL(20,4) NOT NULL | 指标值 | BigDecimal | <span class="r r-new">新增</span> | 金额或次数，用 DECIMAL 兼容两种量纲 |

---

# 七、内容治理与运营

## 3.25 blocked_words

用户自定义屏蔽词，对应"屏蔽词库"。

| 字段 | MySQL | 含义 | Java 类型 | 评审 | 说明 |
|---|---|---|---|---|---|
| id | BIGINT UNSIGNED AUTO_INCREMENT | 主键 | Long | <span class="r r-keep">保留</span> | |
| owner_user_id | BIGINT UNSIGNED NOT NULL | 配置者 | Long | <span class="r r-keep">保留</span> | 屏蔽是个人偏好，不是全局审核 |
| word | VARCHAR(100) NOT NULL | 屏蔽词，**存小写** | String | <span class="r r-keep">保留</span> | 统一小写，避免 MySQL（不敏感）与客户端 SQLite（敏感）行为不一致 |
| created_at | DATETIME(6) NOT NULL | 添加时间 | LocalDateTime | <span class="r r-keep">保留</span> | |

> `UNIQUE(owner_user_id, word)`。

## 3.26 blocked_users

黑名单。

| 字段 | MySQL | 含义 | Java 类型 | 评审 | 说明 |
|---|---|---|---|---|---|
| blocker_user_id | BIGINT UNSIGNED NOT NULL | 拉黑者 | Long | <span class="r r-keep">保留</span> | 复合主键之一 |
| blocked_user_id | BIGINT UNSIGNED NOT NULL | 被拉黑者 | Long | <span class="r r-keep">保留</span> | 复合主键之一 |
| created_at | DATETIME(6) NOT NULL | 拉黑时间 | LocalDateTime | <span class="r r-keep">保留</span> | |

> `CHECK(blocker_user_id <> blocked_user_id)`，禁止拉黑自己。

## 3.27 feedback

意见反馈。

| 字段 | MySQL | 含义 | Java 类型 | 评审 | 说明 |
|---|---|---|---|---|---|
| id | BIGINT UNSIGNED AUTO_INCREMENT | 主键 | Long | <span class="r r-keep">保留</span> | |
| user_id | BIGINT UNSIGNED NULL | 提交人 | Long | <span class="r r-keep">保留</span> | 用户注销后置 NULL，反馈内容仍保留 |
| message | TEXT NOT NULL | 反馈内容 | String | <span class="r r-keep">保留</span> | |
| contact_type | VARCHAR(16) NULL | EMAIL / PHONE / QQ | enum | <span class="r r-new">新增</span> | 界面明确有"联系方式"选择器，原表无此字段 |
| contact_value | VARCHAR(128) NULL | 联系方式内容 | String | <span class="r r-new">新增</span> | 与类型分开存，便于按渠道统计回复率 |
| feedback_status | VARCHAR(16) NOT NULL DEFAULT 'OPEN' | OPEN / IN_PROGRESS / RESOLVED | enum | <span class="r r-keep">保留</span> | |
| created_at | DATETIME(6) NOT NULL | 提交时间 | LocalDateTime | <span class="r r-keep">保留</span> | |
| updated_at | DATETIME(6) NOT NULL | 更新时间 | LocalDateTime | <span class="r r-keep">保留</span> | |

## 3.28 feedback_images

反馈截图，最多 3 张。

| 字段 | MySQL | 含义 | Java 类型 | 评审 | 说明 |
|---|---|---|---|---|---|
| id | BIGINT UNSIGNED AUTO_INCREMENT | 主键 | Long | <span class="r r-new">新增</span> | 界面"上传图片（最多 3 张）"，原表无处存 |
| feedback_id | BIGINT UNSIGNED NOT NULL | 反馈 | Long | <span class="r r-new">新增</span> | |
| image_url | VARCHAR(2048) NOT NULL | 对象存储 URL | String | <span class="r r-new">新增</span> | |
| display_order | TINYINT UNSIGNED NOT NULL | 1–3 | Integer | <span class="r r-new">新增</span> | 配合唯一约束限制数量并固定顺序 |

## 3.29 notifications

站内通知。FCM 是"尽力送达"，必须有落库记录才能补发与去重。

| 字段 | MySQL | 含义 | Java 类型 | 评审 | 说明 |
|---|---|---|---|---|---|
| id | BIGINT UNSIGNED AUTO_INCREMENT | 通知主键 | Long | <span class="r r-new">新增</span> | 首页"新的审批会在这里提醒你" |
| user_id | BIGINT UNSIGNED NOT NULL | 接收人 | Long | <span class="r r-new">新增</span> | |
| notify_type | VARCHAR(32) NOT NULL | PROPOSAL_SETTLED / NEW_VOTE / COMMENT / FLOWER / GROUP_INVITE | enum | <span class="r r-new">新增</span> | 客户端据此选图标与跳转目标 |
| ref_type | VARCHAR(32) NOT NULL | PROPOSAL / GROUP / COMMENT | enum | <span class="r r-new">新增</span> | 多态关联，避免为每种通知建一张表 |
| ref_id | BIGINT UNSIGNED NOT NULL | 关联业务主键 | Long | <span class="r r-new">新增</span> | |
| is_read | TINYINT UNSIGNED NOT NULL DEFAULT 0 | 是否已读 | Boolean | <span class="r r-new">新增</span> | 红点角标依赖它 |
| push_status | VARCHAR(16) NOT NULL DEFAULT 'PENDING' | PENDING / SENT / FAILED | enum | <span class="r r-new">新增</span> | FCM 失败可重试，避免通知静默丢失 |
| created_at | DATETIME(6) NOT NULL | 创建时间 | LocalDateTime | <span class="r r-new">新增</span> | |

---

# 八、可选表（P2）

## 3.30 wheel_runs

转盘历史。**建议不建**，只有产品要求"保存转盘记录"时才需要。

| 字段 | MySQL | 含义 | Java 类型 | 评审 | 说明 |
|---|---|---|---|---|---|
| id | BIGINT UNSIGNED AUTO_INCREMENT | 主键 | Long | <span class="r r-new">新增</span> | 转盘扇区直接读 `poll_options`，正常无需建表 |
| user_id | BIGINT UNSIGNED NOT NULL | 用户 | Long | <span class="r r-new">新增</span> | |
| proposal_id | BIGINT UNSIGNED NULL | 关联审批 | Long | <span class="r r-new">新增</span> | 为空表示自由转盘（选项来自本地） |
| result_option_id | BIGINT UNSIGNED NULL | 抽中的选项 | Long | <span class="r r-new">新增</span> | |
| result_text | VARCHAR(200) NULL | 自由转盘的文本结果 | String | <span class="r r-new">新增</span> | |
| created_at | DATETIME(6) NOT NULL | 抽取时间 | LocalDateTime | <span class="r r-new">新增</span> | |

---

# 附录：唯一约束清单

| 表 | 唯一约束 | 作用 |
|---|---|---|
| users | `UNIQUE(username)` | 登录名不重复 |
| users | `UNIQUE(public_no)` | 对外 ID 不重复 |
| app_groups | `UNIQUE(invite_code)` | 群号不重复且可反查 |
| group_members | `UNIQUE(group_id, user_id)` | 一人一群只有一条成员记录 |
| channels | `UNIQUE(slug)` | 频道标识稳定 |
| tags | `UNIQUE(slug)` | 标签标识稳定 |
| poll_options | `UNIQUE(proposal_id, display_order)` | 选项顺序不冲突，限 6 个 |
| purchase_proposal_images | `UNIQUE(proposal_id, display_order)` | 图片顺序不冲突，限 4 张 |
| feedback_images | `UNIQUE(feedback_id, display_order)` | 限 3 张 |
| decision_votes | `UNIQUE(voter_user_id, client_request_id)` | **幂等键**，防弱网重复投票 |
| reputation_ledger | `UNIQUE(source_vote_id)` | 同一票不重复加分 |
| exchange_rate_snapshots | `UNIQUE(proposal_id)` | 一审批一快照 |
| flowers | `UNIQUE(proposal_id, giver_user_id)` | 防刷花 |
| auth_sessions | `UNIQUE(refresh_token_hash)` | token 全局唯一 |
| user_devices | `UNIQUE(fcm_token)` | 一 token 只绑一台设备 |
| weekly_rankings | `UNIQUE(week_start_date, ranking_type)` | 同周同榜不重复生成 |
| blocked_words | `UNIQUE(owner_user_id, word)` | 同一用户不重复加词 |

---

# 附录 B：关于外键的两点说明

| 情况 | 说明 |
|---|---|
| `purchase_proposals.winning_option_id` → `poll_options.id` | 与 `poll_options.proposal_id` 构成**循环依赖**。实际建表时先建 `purchase_proposals` 和 `poll_options`（此时 `winning_option_id` 不建外键），再用 `ALTER TABLE purchase_proposals ADD CONSTRAINT fk_winning_option FOREIGN KEY (winning_option_id) REFERENCES poll_options(id)` 补上 |
| `notifications.ref_id` | **多态关联**（可能指向审批、群组或评论），无法建物理外键，由服务层保证一致性。表中已标注"多态，无外键" |

> 除上述两处，其余 FOREIGN KEY 均可在建表时直接声明。建表顺序见《数据库设计文档》第六节。

---

*文档版本：最终版（含主键/外键标注）｜ 技术栈：Spring Boot（Java）+ Android + MySQL 8.4 ｜ 2026-11-03*
