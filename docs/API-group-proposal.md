# 群组、审批、投票与结算接口

本地实现于 `feature/group-and-proposal-api`。使用现有 V1～V3 表结构，无新增迁移。
所有 `/api/groups`、`/api/purchase-proposals` 接口需要 `Authorization: Bearer <accessToken>`。
除图片字节外，响应为 `Result<T>`；创建群组、草稿、图片返回 HTTP/code 201，其他成功操作为 200。
分页使用 `page=1&size=20`，size 上限 100，数据为 `items/page/size/total`。
ID 沿用现有广场接口的 JSON 数字；金额为精确十进制；时间返回 UTC `Z` 格式。

## 群组

| 方法 | 路径 | 请求与行为 |
|---|---|---|
| POST | `/api/groups` | `{"name":"购物讨论群"}`；创建并加入群主 |
| GET | `/api/groups` | 当前成员关系为 ACTIVE、群状态为 ACTIVE 的群，分页 |
| GET | `/api/groups/{id}` | 当前成员可见的群详情，包含邀请码和到期时间 |
| PATCH | `/api/groups/{id}` | 群主修改名称：`{"name":"新群名"}` |
| GET | `/api/groups/{id}/members` | 当前成员列表，分页 |
| POST | `/api/groups/join` | `{"inviteCode":"03827164"}` |
| DELETE | `/api/groups/{id}/members/me` | 普通成员退出；重复退出不重复减人数 |
| POST | `/api/groups/{id}/invite` | 群主重置邀请码并续期 7 天，旧码失效 |
| PUT | `/api/groups/{id}/owner` | `{"userId":123}`；新群主须为有效当前成员 |
| DELETE | `/api/groups/{id}/members/{userId}` | 群主移除其他成员 |
| DELETE | `/api/groups/{id}` | 群主解散；重复请求成功 |

名称非空白且不超过 100 字符，无图标字段、无好友功能。
邀请码为保留前导零的八位数字字符串，有效期 7 天。过期码不能加入。
创建人数为 1；重复加入不重复计数；退出后重新加入更新原关系和最近加入时间。
被移除者可持有效邀请码重入。群主退出前必须转让。

解散将群设为 CLOSED，不删除历史成员、审批或投票。禁止加入、新发布和继续投票。
对于仍 OPEN 且尚未截止的群内审批，将 deadline_at 提前到关闭时刻；已截止的保留原时间。
后台下一次扫描结算，通常在 15 秒左右；数据库故障时保留待处理状态并在后续扫描重试。
关闭后当前成员也不能通过群入口读取审批；作者仍可从个人入口查看自己的记录。

## 审批草稿与发布

| 方法 | 路径 | 行为 |
|---|---|---|
| POST | `/api/purchase-proposals` | 创建草稿；clientRequestId 为 UUID，重复创建返回同一审批当前内容 |
| PUT | `/api/purchase-proposals/{id}` | 完整保存表单，不是 PATCH；省略可选内容表示清空／使用默认值 |
| GET | `/api/purchase-proposals/{id}` | 通用详情，包含 proposal、范围、群组、模式、选项、图片、最新票及权限 |
| DELETE | `/api/purchase-proposals/{id}` | 仅作者删除草稿；图片行和受管理文件同时清理 |
| POST | `/api/purchase-proposals/{id}/publish` | 可不传 body，或 `{"durationHours":24}` |
| POST | `/api/purchase-proposals/{id}/withdraw` | 作者撤回未截止 OPEN 审批；重复撤回成功 |
| GET | `/api/groups/{id}/purchase-proposals` | 当前成员查询已发布且未撤回审批；可按 status 筛选 |

我的草稿/记录复用 `/api/users/me/records?type=SUBMITTED&status=DRAFT` 等现有接口。
频道复用 `/api/channels`，标签复用 `/api/tags`。

创建二元广场草稿（tagIds/channelIds 请使用真实查询结果）：

```json
{
  "clientRequestId": "f298505c-6865-4fc0-b718-44ecf7c43304",
  "participationType": "BINARY",
  "approvalMode": "MULTI",
  "publicationScope": "PLAZA",
  "itemName": "无线耳机",
  "amount": 799.00,
  "currency": "HKD",
  "purchaseReason": "通勤需要",
  "alternativeProductTip": null,
  "settlementMode": "TIME",
  "minimumVoterCount": 3,
  "tagIds": [1],
  "channelIds": [1],
  "options": []
}
```

草稿可不完整，但主表金额/币种必须同时为空或同时填写；填写金额须大于 0，最多 10 位整数、2 位小数。
GROUP 必须有有效的 groupId，不能关联频道；PLAZA 的 groupId 必须为空。
发布要求：非空白理由、1～3 个有效且不同的标签；广场另外至少一个有效频道。
群组发布要求作者当前为成员且群未关闭，最低/目标人数不超过发布时除作者外的有效成员人数。
二元发布须商品名、金额、币种齐全。商品名最多 200 字符；投票问题最多 300；理由和替代品最多 10000。

选项审批用 `participationType: "OPTION"`、`pollQuestion` 和 `options: [{"text":"选项A"},{"text":"选项B"}]`。
有 1～6 个非空白且不重复的选项；首轮不带价格，amount/currency 为空；不使用 itemName。
草稿保存时可重建选项 ID；发布后选项、ID 和展示顺序固定。
编辑已发布选项审批时，PUT 应回传每个选项原 `id` 与 `text`，不能更换。

### 模式

- 默认 MULTI + TIME，24 小时、最低人数 3。
- TIME：截止后按最低人数判断是否有效。
- QUORUM：达到目标人数立即结算；未达到则在截止时处理。
- SINGLE：固定 QUORUM、人数 1；可省略这两个字段让服务器补齐，显式冲突的设置返回 400。
  不指定审批人，任何合资格的非作者用户首票决定结果；首票与结算同一事务。
- durationHours 为 1～168；单人/定额也有截止兜底。
- 重复发布 OPEN 审批不会延长截止时间。已撤回或已结束的记录不能重新发布。

发布后范围、群组、频道、标签、模式、最低人数和选项固定。
未截止且没有任何投票时，作者可修改商品/问题、金额、理由、替代品与图片。
首票后冻结内容；多人尚未截止/结束时作者可撤回。单人首票即结束，不能再撤回。
撤回不删除历史、不结算、不奖励。重新发布需新建草稿。

## 投票

| 方法 | 路径 | 行为 |
|---|---|---|
| POST | `/api/purchase-proposals/{id}/votes` | 提交新票或改票，返回投票事件及分数/票权快照 |
| GET | `/api/purchase-proposals/{id}/votes/me` | 当前用户最新票，无票为 null |
| GET | `/api/purchase-proposals/{id}/vote-reasons` | 最新理由列表，每个用户一条，分页 |

```json
{
  "clientRequestId": "ee68fa3a-1450-4f89-b130-779cde7f00ba",
  "choice": "BUY",
  "reason": "确实有需求且预算允许"
}
```

二元 choice 为 BUY/NOT_BUY，不传 optionId；选项改传 optionId，不传 choice。
reason 非空白，最多 10000 字符。作者不能自投；必须 OPEN 且未截止。
群内要求群有效且投票人当前为成员；广场要求有效登录及广场可见。
每次改票插入新事件，只取每人最大事件 ID 的票，不允许撤销。
每次按当时信誉保存分数/票权快照：0～9=1，10～29=2，30+=3。
改票不增加 voterCount。退群前的票继续有效，退群后不能投/改票。
相同 UUID+相同内容返回原事件，即使已经结算；同 UUID 不同内容返回 409。
结束后新投票/改票返回 409；无权访问或审批不可见返回 404。

## 结算、信誉与金额榜

后台默认每 15 秒扫描到期 OPEN 审批；定额/单人达标在投票事务中立即结算。
人数按最新有效票的不同用户数，排除作者。人数不足标记 INSUFFICIENT_PARTICIPATION，不产生结果/奖励。
二元：BUY 票权严格超过总票权一半才为 BUY，否则 NOT_BUY。
选项：总票权最高者唯一获胜；并列取 display_order 最小者，写 winning_option_id。
正确最新票 +1，错误不扣分；单人决定结果的首票同样 +1。
结果、信誉流水、总分原子提交。结束状态与 source_vote_id 唯一约束防止重复记账。
信誉写入不依赖用户当前是否仍为群成员；计算票权只使用投票快照。

二元 BUY 审批异步生成 exchange_rate_snapshots，HKD 为 1:1，其他使用结算日或此前的 ECB 历史汇率。
[Frankfurter 官方文档](https://frankfurter.dev/)支持按日期和 ECB 来源查询；服务使用 `/v2/providers/ecb/rate/{currency}/hkd`。
外部请求在数据库事务外，失败时使用同币种、HKD 目标、日期不晚于结算日的最近已存快照。
无可用汇率不阻塞结果/奖励，金额榜暂不计入，后台约每 60 秒分批重试。
已有快照不覆盖；补偿不重算结果、不重复奖励。超出当前 DECIMAL 精度的换算保留待处理并记录日志。
定时批次有上限，积压时可能需要多轮完成；生产部署需保持应用常驻并保留上传目录。

## 配图

| 方法 | 路径 | 行为 |
|---|---|---|
| POST | `/api/purchase-proposals/{id}/images` | multipart file，一次一张，返回 id/imageUrl/displayOrder |
| DELETE | `/api/purchase-proposals/{id}/images/{imageId}` | 作者在可编辑时删除 |
| GET | `/api/purchase-proposals/{id}/images/{imageId}/content` | 图片字节，Bearer 鉴权，禁止公共缓存 |

PNG/JPEG、单张 5MB、单请求 6MB、最多 4 张；复用头像安全解码/重编码和 4096×4096 尺寸上限。
URL 中 name 参数只用于服务端已保存文件定位，读取以数据库记录为准，不信任请求参数。
Android 加载这些私有图片时也必须携带 Bearer token，不能按公开头像链接处理。
事务回滚删除新文件，删除图片/草稿在提交后清理文件。原有外部图片 URL 保持可展示，本地字节接口不代理外部资源。

## 配置和联调边界

可选环境变量（默认无需填写）：

| 配置 | 默认值 |
|---|---|
| PROPOSAL_JOBS_ENABLED | true |
| PROPOSAL_IMAGE_DIR | ./uploads/proposals |
| PROPOSAL_IMAGE_BASE_URL | 请求的 scheme/host/context；模拟器联调可设为 http://10.0.2.2:8080 |
| EXCHANGE_BASE_URL | https://api.frankfurter.dev |

定时线程池为 2，汇率网络请求不占用结算扫描线程。测试禁用定时任务并使用隔离 MySQL。
群组/审批写入复用 AccountGuard；业务行按群组再审批锁定，信誉账户按用户 ID 顺序锁定。
跨用户外键可能触发 MySQL 死锁，HTTP 写操作在完整事务回滚后最多重试 3 次，仍冲突返回 409。
后台结算事务失败在下一轮重试，避免部分结果或部分奖励。

已有 `/api/plaza`、`/api/plaza/{id}`、互动、个人记录、用户和榜单响应不变。
广场投票理由旧路径继续可用。广场接口里的 voteTally 为人数，并非加权总分。
现有广场互动仍只支持 PLAZA；群内评论/围观/送花/作者补充由互动负责人扩展，复用 ProposalAccess 和群组状态校验。
Android 现有演示表单尚未接这些接口，需将六位邀请码改为八位，并按类型调整表单。

## 验证

JDK 17、Docker 运行后执行 `./mvnw -B verify`。
新增集成测试在临时 MySQL 8.4 验证群生命周期、隐私、字段校验、发布防重、单人并发首票、改票快照、
平票顺序、到期/解散/撤回、定额并发、跨审批信誉记账、汇率补偿不可覆盖及私有图片生命周期。
汇率业务测试使用受控 RateProvider，不依赖线上服务可用性。
