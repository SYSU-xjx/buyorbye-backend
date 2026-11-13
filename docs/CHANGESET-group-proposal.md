# 本次开发全部文件清单

分支：`feature/group-and-proposal-api`。仅本地修改，未提交、未推送。

本次新增 26 个文件，修改 5 个文件，共 31 个。

## 新增文件

| 文件 | 用途 |
|---|---|
| [docs/API-group-proposal.md](/Users/hanyining/hku/smartphoneproject/buyorbye-backend/docs/API-group-proposal.md) | 接口契约、示例、配置与联调说明 |
| [docs/CHANGESET-group-proposal.md](/Users/hanyining/hku/smartphoneproject/buyorbye-backend/docs/CHANGESET-group-proposal.md) | 本次完整文件清单 |
| [src/main/java/com/hku/money/common/WriteRetry.java](/Users/hanyining/hku/smartphoneproject/buyorbye-backend/src/main/java/com/hku/money/common/WriteRetry.java) | 事务回滚后重试数据库死锁／锁冲突 |
| [src/main/java/com/hku/money/exchange/ExchangeMapper.java](/Users/hanyining/hku/smartphoneproject/buyorbye-backend/src/main/java/com/hku/money/exchange/ExchangeMapper.java) | ECB 汇率、历史回退与不可变 HKD 快照 |
| [src/main/java/com/hku/money/exchange/ExchangeService.java](/Users/hanyining/hku/smartphoneproject/buyorbye-backend/src/main/java/com/hku/money/exchange/ExchangeService.java) | ECB 汇率、历史回退与不可变 HKD 快照 |
| [src/main/java/com/hku/money/exchange/FrankfurterRateProvider.java](/Users/hanyining/hku/smartphoneproject/buyorbye-backend/src/main/java/com/hku/money/exchange/FrankfurterRateProvider.java) | ECB 汇率、历史回退与不可变 HKD 快照 |
| [src/main/java/com/hku/money/exchange/RateProvider.java](/Users/hanyining/hku/smartphoneproject/buyorbye-backend/src/main/java/com/hku/money/exchange/RateProvider.java) | ECB 汇率、历史回退与不可变 HKD 快照 |
| [src/main/java/com/hku/money/group/controller/GroupController.java](/Users/hanyining/hku/smartphoneproject/buyorbye-backend/src/main/java/com/hku/money/group/controller/GroupController.java) | 群组接口、请求响应、持久化或事务逻辑 |
| [src/main/java/com/hku/money/group/dto/GroupModels.java](/Users/hanyining/hku/smartphoneproject/buyorbye-backend/src/main/java/com/hku/money/group/dto/GroupModels.java) | 群组接口、请求响应、持久化或事务逻辑 |
| [src/main/java/com/hku/money/group/mapper/GroupMapper.java](/Users/hanyining/hku/smartphoneproject/buyorbye-backend/src/main/java/com/hku/money/group/mapper/GroupMapper.java) | 群组接口、请求响应、持久化或事务逻辑 |
| [src/main/java/com/hku/money/group/service/GroupService.java](/Users/hanyining/hku/smartphoneproject/buyorbye-backend/src/main/java/com/hku/money/group/service/GroupService.java) | 群组接口、请求响应、持久化或事务逻辑 |
| [src/main/java/com/hku/money/proposal/controller/ProposalController.java](/Users/hanyining/hku/smartphoneproject/buyorbye-backend/src/main/java/com/hku/money/proposal/controller/ProposalController.java) | 审批、投票、结算、权限或私有配图实现 |
| [src/main/java/com/hku/money/proposal/controller/ProposalImageController.java](/Users/hanyining/hku/smartphoneproject/buyorbye-backend/src/main/java/com/hku/money/proposal/controller/ProposalImageController.java) | 审批、投票、结算、权限或私有配图实现 |
| [src/main/java/com/hku/money/proposal/dto/ProposalModels.java](/Users/hanyining/hku/smartphoneproject/buyorbye-backend/src/main/java/com/hku/money/proposal/dto/ProposalModels.java) | 审批、投票、结算、权限或私有配图实现 |
| [src/main/java/com/hku/money/proposal/entity/ProposalState.java](/Users/hanyining/hku/smartphoneproject/buyorbye-backend/src/main/java/com/hku/money/proposal/entity/ProposalState.java) | 审批、投票、结算、权限或私有配图实现 |
| [src/main/java/com/hku/money/proposal/mapper/ProposalMapper.java](/Users/hanyining/hku/smartphoneproject/buyorbye-backend/src/main/java/com/hku/money/proposal/mapper/ProposalMapper.java) | 审批、投票、结算、权限或私有配图实现 |
| [src/main/java/com/hku/money/proposal/service/ProposalAccess.java](/Users/hanyining/hku/smartphoneproject/buyorbye-backend/src/main/java/com/hku/money/proposal/service/ProposalAccess.java) | 审批、投票、结算、权限或私有配图实现 |
| [src/main/java/com/hku/money/proposal/service/ProposalImageService.java](/Users/hanyining/hku/smartphoneproject/buyorbye-backend/src/main/java/com/hku/money/proposal/service/ProposalImageService.java) | 审批、投票、结算、权限或私有配图实现 |
| [src/main/java/com/hku/money/proposal/service/ProposalImageStore.java](/Users/hanyining/hku/smartphoneproject/buyorbye-backend/src/main/java/com/hku/money/proposal/service/ProposalImageStore.java) | 审批、投票、结算、权限或私有配图实现 |
| [src/main/java/com/hku/money/proposal/service/ProposalJobs.java](/Users/hanyining/hku/smartphoneproject/buyorbye-backend/src/main/java/com/hku/money/proposal/service/ProposalJobs.java) | 审批、投票、结算、权限或私有配图实现 |
| [src/main/java/com/hku/money/proposal/service/ProposalService.java](/Users/hanyining/hku/smartphoneproject/buyorbye-backend/src/main/java/com/hku/money/proposal/service/ProposalService.java) | 审批、投票、结算、权限或私有配图实现 |
| [src/main/java/com/hku/money/proposal/service/SettlementService.java](/Users/hanyining/hku/smartphoneproject/buyorbye-backend/src/main/java/com/hku/money/proposal/service/SettlementService.java) | 审批、投票、结算、权限或私有配图实现 |
| [src/main/java/com/hku/money/proposal/service/VoteService.java](/Users/hanyining/hku/smartphoneproject/buyorbye-backend/src/main/java/com/hku/money/proposal/service/VoteService.java) | 审批、投票、结算、权限或私有配图实现 |
| [src/main/resources/mapper/proposal/ProposalMapper.xml](/Users/hanyining/hku/smartphoneproject/buyorbye-backend/src/main/resources/mapper/proposal/ProposalMapper.xml) | 审批和投票 MyBatis SQL |
| [src/test/java/com/hku/money/FrankfurterRateProviderTest.java](/Users/hanyining/hku/smartphoneproject/buyorbye-backend/src/test/java/com/hku/money/FrankfurterRateProviderTest.java) | 隔离 MySQL / HTTP 验证与并发回归 |
| [src/test/java/com/hku/money/ProposalIntegrationTest.java](/Users/hanyining/hku/smartphoneproject/buyorbye-backend/src/test/java/com/hku/money/ProposalIntegrationTest.java) | 隔离 MySQL / HTTP 验证与并发回归 |

## 修改文件

| 文件 | 修改内容 |
|---|---|
| [DESIGN.md](/Users/hanyining/hku/smartphoneproject/buyorbye-backend/DESIGN.md) | 记录已确认规则，修正过期的 15 表描述 |
| [README.md](/Users/hanyining/hku/smartphoneproject/buyorbye-backend/README.md) | 新增接口文档入口与开发范围说明 |
| [src/main/java/com/hku/money/common/ApiErrors.java](/Users/hanyining/hku/smartphoneproject/buyorbye-backend/src/main/java/com/hku/money/common/ApiErrors.java) | 使图片和数据冲突错误提示适用于多个业务模块 |
| [src/main/resources/application.yml](/Users/hanyining/hku/smartphoneproject/buyorbye-backend/src/main/resources/application.yml) | 配置定时线程池、后台任务、私有图片目录和汇率来源 |
| [src/test/java/com/hku/money/ApiIntegrationTest.java](/Users/hanyining/hku/smartphoneproject/buyorbye-backend/src/test/java/com/hku/money/ApiIntegrationTest.java) | 旧测试禁用新定时任务，避免后台结算干扰旧测试夹具 |

## 原有本地改动

`.idea/modules.xml` 在本次开发前已是暂存删除，原样保留，不计入上述文件数。

## 数据库和 Android

V1、V2、V3 迁移文件和数据库表结构均未改动；Android 仓库未改动。

## 验证

执行 JDK 17 下的 `./mvnw -B verify`，包含现有回归及新增隔离 MySQL / 本地 HTTP 测试。最终结果见开发完成回复。
汇率业务测试使用受控供应器；HTTP 客户端测试验证真实本地 HTTP 编解码、ECB 历史参数、缓存、未来日期拒绝和响应大小限制，未以线上网络可用性作为测试条件。
