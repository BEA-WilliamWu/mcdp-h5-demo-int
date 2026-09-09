# BCOH2H-1204 Technical Design

> 2026-09-09 合并更新：Code 表、密文校验、787 生成/审批接入及部署顺序以 [787/788/790/1204 合并说明](BCOH2H-787-788-790-1204-INTEGRATION.md) 为准。以下保留原始设计记录。

## First-Time API Password Setup Notification

| 项目 | 内容 |
| --- | --- |
| Story | `BCOH2H-1204` First-Time API Password Setup Notification |
| 文档状态 | Proposed（当前代码库尚无对应事件/模板配置） |
| 日期 | 2026-09-07 |
| 依赖设计 | `BCOH2H-788-TECHNICAL-DESIGN.md`；Code 生成通知 Story `BCOH2H-789` |
| 后续 Story | `BCOH2H-1205` API Password Reset Notification（不在本设计范围） |
| 影响范围 | 首次 API Password Setup 成功事件、通知模板、收件人解析、派送与失败/Bounce handling |
| 不影响范围 | Password Code 生成通知、Reset 通知、密码创建事务、BCO 现有 Login Password/PIN 模板 |

## 1. 目标

HTH API User 首次成功设置 API Password 后，系统向指定收件人发送确认通知，告知 HTH API
authentication credential 已激活。通知沿用 BCO Logon PIN Code 的 Event/Template/Recipient/
Dispatch 基础设施和现有投递失败/Bounce Back 处理方式，同时使用 HTH 专用 event/template，避免
覆盖 BCO 现有通知。

通知是 Setup 成功后的派生结果：通知失败不得回滚已经生效的 API Password，也不得把 Setup API
返回为失败；系统应保存可追踪的派送状态，按平台机制重试或进入 Bounce/人工跟进流程。

## 2. Story 解读与边界

### 2.1 触发条件

只有以下条件全部满足时触发：

- 操作为 `FIRST_TIME_SETUP`，不是 Reset；
- UAM 已确认新的 HTH API credential 创建成功并处于 `ACTIVE`；
- 对应 Setup Code 已最终置为 `USED`；
- 本地 setup operation/reference 已完成；
- 同一 `requestId/referenceNumber` 尚未成功发布过该业务事件。

不得在以下时间点触发：

- 打开 Setup 页面或弹出首次登录提示时；
- Password/Code 校验通过但 UAM 尚未成功时；
- Code 生成时（属于 789）；
- Reset password 时（属于 1205）；
- UAM timeout 且最终结果未知时。

### 2.2 “指定收件人”的解释

Story 的 user story 是“HTH API User receives a notification”，因此本设计默认主收件人为 **完成
首次设置的 HTH API User 本人**，使用其当前有效且已验证的 BCO profile contact。若正式业务要求
同时通知 Authorized Person/Company，需要 Product 在实施前确认，并配置为额外 recipient rule，
不能在代码中把 AP 写死。

推荐 recipient priority：

1. HTH API User 的 verified email/mobile（按既有 BCO Logon PIN Code channel preference）；
2. 如果既有机制允许 alternative System Administrator/Company contact，则按同一配置执行；
3. 不得从前端 request 接受任意 email/mobile 作为收件地址。

### 2.3 AC3 重复语句处理

AC3 同时写了“follow existing BCO Logon Password notification mechanism”和“follow existing BCO
Logon PIN Code notification mechanism”。本设计不建立两条重复失败链：

- 事件生成、收件人和模板路由以 **BCO Logon PIN Code** 机制为主要基线（与 AC1 一致）；
- 邮件退回、无法投递、alternative contact、提醒/人工跟进等复用两者共用的 Alert/Bounce Back
  framework；
- 如两个现有机制的重试次数或 fallback recipient 不同，由 BA/Security 在实施前确定单一配置。

### 2.4 明确不做

- 通知不包含 API Password、Password Code、password hash、UAM secret/token。
- 不复用/修改现有 `PIN_RESET_SUCCESS` event/template 内容。
- 不因通知失败而撤销、重置或重新生成 API Password。
- 不在前端直接调用邮件/SMS gateway。
- 不把通知 dispatch 状态作为用户可以登录 BCO 或使用 API Password 的授权依据。
- 不负责 Reset notification（1205）或 Code generation notification（789）。

## 3. 现有通知机制分析

### 3.1 现有代码路径

BCO Login PIN Reset 成功流程的代码模式位于：

```text
com.ofss.digx.cz.bea.app.sms.service.user.ResetPassword.alertPinResetSuccess()
  -> registerActivityAndGenerateEvent(
       sessionContext,
       THIS_COMPONENT_NAME + ".reset",
       PIN_RESET_SUCCESS,
       date,
       activityLog)
  -> Alert/Event Framework
  -> Recipient resolution
  -> Template rendering
  -> Email/SMS dispatch
```

HTH API Password Setup 应复用 `IModuleToAlertAdapter.registerActivityAndGenerateEvent` 的平台链路，
但 activity/event id 和 DTO 独立，防止错误套用 Login PIN 的字段与模板。

### 3.2 现有数据库配置层

通知配置主要涉及：

| 配置/表 | 用途 | 本 Story 动作 |
| --- | --- | --- |
| `DIGX_EP_ACT_EVT_B` | 注册 Activity + Event 组合 | 新增 HTH Setup success event |
| `DIGX_EP_ACT_EVT_ACN_B` | 事件动作、派送、重试等配置 | 新增 HTH action mapping |
| `DIGX_EP_EVT_REC_B` | recipient/channel/template 路由 | 新增 HTH user recipient mapping |
| `DIGX_EP_MSG_TMPL_B` | 多语言消息模板 | 新增 HTH Setup 模板，不覆盖 PIN 模板 |
| 平台 notification/outbox 表 | 运行时派送状态 | 复用，不另建业务通知表 |
| Bounce Back/Batch 机制 | returned/undeliverable follow-up | 配置新 event 纳入现有处理范围 |

字段名/表名应以目标环境 OBDX 版本 DDL 为准；SQL 使用幂等 MERGE/存在性检查，不能复制测试环境的
技术主键后直接 INSERT。

### 3.3 复用而不复制业务代码

推荐只在 788 Setup Service 成功出口发布一个标准 Activity/Event。模板解析、locale、email/SMS、
retry、bounce 和 alternative recipient 由现有 framework 配置完成。不要在
`HostToHostApiPassword` 中直接 new 邮件客户端或自行重试。

## 4. 事件设计

### 4.1 Activity/Event 标识（建议）

以下名称为建议，当前仓库尚不存在，实施时需按 Event Governance 最终确认：

| 项目 | 建议值 |
| --- | --- |
| Activity ID | `com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostApiPassword.setup` |
| Event ID | `HTH_API_PASSWORD_SETUP_SUCCESS` |
| Event Type | `OTHER` 或平台对 Credential/Security 的标准类型 |
| Activity/Event Type | `ONLINE` |
| Domain Extension | `CZ` |
| Action ID | `A` |
| Alert Type | 沿用 BCO Logon PIN notification 的 message alert type |
| Transactional | `Y`（安全确认通知不可被营销偏好关闭；最终由 Compliance 确认） |

不得直接使用 `PIN_RESET_SUCCESS`，因为：

- 操作对象不同（API Password vs Login PIN）；
- 模板内容和变量不同；
- 后续 1205 Reset 也需要独立统计；
- 共用 event 会造成错误的 bounce、报表和审计分类。

### 4.2 Event Payload DTO

建议新增 `HthApiPasswordNotificationDTO`，只包含模板需要的非敏感字段：

| 字段 | 来源 | 用途 |
| --- | --- | --- |
| `partyId` | Session/HTH profile | corporate recipient/context resolution |
| `userId` | Session canonical user id | user recipient resolution |
| `userDisplayName` | User profile | greeting，可为空 |
| `companyName` | Party profile | 模板显示，可为空 |
| `setupDateTime` | server clock | 安全事件时间 |
| `referenceNumber` | 788 setup operation | 查询/客服参考号 |
| `channel` | constant `BCO` | 说明操作来源，不是 dispatch channel |
| `locale` | user preference/session | 模板选择 |

禁止加入：

- API Password/确认密码；
- HTH API Password Code 或 hash；
- encrypted payload/RSA token；
- UAM client secret、access token、credential hash；
- 完整 IP/device fingerprint（可进入安全审计，不进入通知模板）。

DTO 的 `toString()` 必须安全；推荐显式实现只输出 allow-list 字段。

### 4.3 发布时点与事务边界

推荐采用 Transactional Outbox/平台 Activity 记录作为可靠事件源：

```text
UAM create success
  -> 本地 finalize transaction
       - Code = USED
       - Credential state = ACTIVE（若有投影）
       - Setup operation = SUCCESS
       - 写唯一 event/outbox key
  -> commit
  -> Alert dispatcher 异步派送
```

若 `registerActivityAndGenerateEvent` 必须同步调用，应放在 credential、本地状态成功之后，并对异常
单独捕获、记录 `NOTIFICATION_PENDING/FAILED`，不能抛出后回滚/改写 Setup 成功响应。

### 4.4 幂等键

事件唯一键建议：

```text
HTH_API_PASSWORD_SETUP_SUCCESS:{partyId}:{userId}:{setupReferenceNumber}
```

同一 788 `requestId` 重试、UAM timeout 对账补完成、应用节点重启后重放，都只能生成一次逻辑事件。
派送 framework 自己的 retry 可多次尝试，但不能生成多个业务通知记录。

## 5. 收件人解析

### 5.1 主收件人

由 Alert Framework 按 `userId + partyId` 读取 HTH API User 当前有效 contact：

- Email：verified、未删除、未 bounce-disable 的地址；
- SMS：verified、格式合法、未 opt-out（若安全通知允许 opt-out 由 Compliance 决定）；
- 用户首选语言决定模板 locale，找不到时 fallback `en`。

不得使用 Setup request 中传入的 email/mobile；这会允许攻击者把安全通知重定向到任意地址。

### 5.2 Authorized Person/Company fallback

只有目标 BCO Logon PIN failure mechanism 已定义并且 Product 明确要求时，才允许在用户地址不可达
时通知 alternative System Administrator/Company contact。Fallback 内容应说明“用户的安全通知无法
投递”，但不包含 Password/Code。

如果用户无任何有效 contact：

- Setup 仍成功；
- notification 状态为 `NO_ELIGIBLE_RECIPIENT`；
- 生成可监控的操作告警/报表记录；
- 不静默标记 Delivered。

### 5.3 多收件人去重

同一标准化 email/mobile 在 User 与 AP/Company 路由中重复时只发送一次。多个 Authorized Person
是否全部通知需由 Product 定义，默认不扩大发送范围。

## 6. Notification Template

### 6.1 建议模板 ID

按渠道和 locale 分开：

```text
HTH API Password Setup Success_USER_EMAIL_en
HTH API Password Setup Success_USER_EMAIL_zh-Hans-CN
HTH API Password Setup Success_USER_EMAIL_zh-Hant
HTH API Password Setup Success_USER_SMS_en
HTH API Password Setup Success_USER_SMS_zh-Hans-CN
HTH API Password Setup Success_USER_SMS_zh-Hant
```

实际命名长度和字符限制以 `DIGX_EP_MSG_TMPL_B` 为准；实施 SQL 中维护稳定 code，不使用环境技术 ID。

### 6.2 内容原则

模板至少包括：

- HTH API Password 已成功设置/credential 已激活；
- 操作日期时间和时区；
- 用户可识别的公司/用户信息（按现有模板脱敏）；
- reference number；
- 如果不是本人操作，立即联系银行/Authorized Person 的安全提示；
- 官方联系方式或安全入口（使用配置，不硬编码测试 URL）。

禁止包括：

- 新 password、Password Code、旧 password；
- password 长度、首尾字符或任何可用于猜测的提示；
- 可点击直接登录并携带 token 的 URL；
- UAM client secret、client id 全值或内部 exception。

### 6.3 示例内容（非最终文案）

Email subject：

```text
Your HTH API Password has been set up successfully
```

Email body 要点：

```text
Your HTH API Password was successfully set up on {setupDateTime}.
Reference: {referenceNumber}
If you did not perform this action, please contact the Bank immediately.
```

这是设计示例，不是最终 legal-approved template。正式 en/zh-Hans-CN/zh-Hant 文案需由 Product、
Legal/Compliance 审核并作为 SQL 数据交付。

## 7. 后端接入

### 7.1 788 Service 成功出口

`HostToHostApiPassword.setup` 在完成 UAM 和本地 finalize 后构造安全 DTO：

```java
HthApiPasswordNotificationDTO notification = new HthApiPasswordNotificationDTO();
notification.setPartyId(sessionContext.getTransactingPartyCode());
notification.setUserId(canonicalUserId);
notification.setSetupDateTime(serverTime);
notification.setReferenceNumber(referenceNumber);

alertAdapter.registerActivityAndGenerateEvent(
    ACTIVITY_ID,
    EVENT_SETUP_SUCCESS,
    serverTime,
    notification);
```

上述代码只是逻辑示意。实际调用应沿用现有 Service 基类/Adapter 签名，并满足：

- 只在 setup operation 从未完成到成功的状态迁移时触发；
- Alert exception 被单独记录，不改变 API `SUCCESSFUL`；
- Event correlation id 使用 788 request/reference/context；
- 日志不打印整个 DTO 或敏感下游对象。

### 7.2 不在 Channel 触发

成功页面渲染、用户点击 OK、回到 Home 都不能作为通知触发点：页面关闭、网络中断或重复刷新会造成
漏发/重复发。唯一触发源必须是服务端首次 Setup 的最终业务状态。

### 7.3 状态与可观测性

如果平台 Alert Framework 已提供运行时 delivery/outbox 状态，直接复用；不再新增业务通知表。
至少能够按以下字段排查：

- setup reference/request/context id；
- activity/event id；
- recipient type/channel（地址需 mask）；
- rendered template id/locale/version；
- dispatch status、attempt count、last error category、next retry time；
- provider message id（若允许存储）。

## 8. 派送、重试与 Bounce Back

### 8.1 状态模型

```text
PENDING -> DISPATCHED -> DELIVERED（provider 支持 delivery receipt 时）
   |           |
   |           +-> RETURNED/BOUNCED -> fallback/follow-up
   +-> RETRY_WAIT -> DISPATCHED
   +-> FAILED_FINAL
   +-> NO_ELIGIBLE_RECIPIENT
```

Setup 页面只显示 credential setup success，不等待 `DELIVERED`。

### 8.2 重试分类

| 失败类型 | 处理 |
| --- | --- |
| Gateway timeout/5xx/temporary | 按 BCO Logon PIN 机制指数/配置重试 |
| Rate limit | 尊重 retry-after；不紧密循环 |
| Invalid email/mobile | 不盲目重试；进入 bounce/fallback |
| Hard bounce/undeliverable | 标记 contact 问题并走既有 alternative notification/follow-up |
| Template/config missing | 立即告警运维；修复配置后可安全重放 |
| Recipient resolution failed | `NO_ELIGIBLE_RECIPIENT` + 操作告警 |
| Duplicate event | 幂等忽略，不再发送 |

重试次数、间隔和最终失败动作复用 BCO Logon PIN/Password 的目标环境配置，不在 Java 中硬编码。

### 8.3 Bounce Back 处理

- 新 Event ID 必须加入适用的 Bounce Back event allow-list/config，不能默认假设 framework 自动识别。
- Hard bounce 后按现有机制设置/读取用户 bounce reminder；是否通知 alternative SysAdmin 按配置。
- fallback 通知使用独立安全模板，不能把原邮件 raw content 直接转发。
- 记录 masked destination、bounce reason category 和原 provider reference。
- Bounce handling 失败继续进入 batch exception/reporting，而不影响 credential 状态。

## 9. 数据库/配置脚本设计

建议在 API Password 上线 SQL 中独立提供 `Notification` 文件，并与 Schema、Permission、Process、
Adapters、Error_Messages、Verification 分开。Notification SQL 包含：

1. `DIGX_EP_ACT_EVT_B`：Activity/Event 注册；
2. `DIGX_EP_ACT_EVT_ACN_B`：Action、transactional/retry/dispatch 配置；
3. `DIGX_EP_EVT_REC_B`：User Email/SMS recipient mapping；
4. `DIGX_EP_MSG_TMPL_B`：en、zh-Hans-CN、zh-Hant 模板；
5. Bounce Back event list/config 更新（如平台需要）；
6. Verification 查询；
7. Fallback SQL：按业务 code 精确停用/删除新增配置，不影响 BCO PIN 模板。

### 9.1 SQL 原则

- 幂等：重复执行不会重复建 event/recipient/template。
- 使用业务 key 精确 MERGE；不依赖 SIT/UAT 的 sequence/id 值。
- 不 `DELETE` 或 `UPDATE` 现有 BCO Login PIN/Password event/template。
- 三个 locale 的变量集合完全一致；Verification 检查 placeholder 缺失/多余。
- 生产模板中不包含 `127.0.0.1`、测试邮箱、真实个人资料或临时密码。
- DML 明确 COMMIT 策略并与上线标准一致。

### 9.2 Verification 建议

```sql
-- 示意：实际字段以目标 OBDX 版本为准
SELECT *
  FROM DIGX_EP_ACT_EVT_B
 WHERE COD_ACT_ID = :activity_id
   AND COD_EVENT_ID = 'HTH_API_PASSWORD_SETUP_SUCCESS';

SELECT LOCALE, COD_MSG_TMPL_ID
  FROM DIGX_EP_EVT_REC_B
 WHERE COD_ACT_ID = :activity_id
   AND COD_EVENT_ID = 'HTH_API_PASSWORD_SETUP_SUCCESS'
 ORDER BY LOCALE, TXT_DEST_TYP;
```

还需验证 event action active、template active、recipient route、retry/bounce config 和实际 smoke dispatch。

## 10. 安全、隐私与合规

- 这是 security notification，默认应不可被普通 marketing preference 关闭；由 Compliance 最终确认。
- 只发送最少必要资料，company/user 名称按现有 BCO 模板规范脱敏。
- Email/SMS 地址只在受限 dispatch log 中存储，业务日志使用 mask。
- Template 禁止展示 password、Code、hash、credential id、UAM client secret/token。
- 事件 payload、outbox、dead-letter、batch exception report 同样执行 sensitive-field allow-list。
- 任何人工重发功能需鉴权、审计，并按同一 event idempotency 规则产生“重发尝试”，不能篡改原事件。
- 保留期、归档和删除规则沿用 BCO 安全通知记录政策。

## 11. 错误处理与监控

### 11.1 Setup API 行为

| 通知结果 | 788 API 结果 |
| --- | --- |
| Event 成功入队 | Setup `SUCCESSFUL` |
| Alert framework 暂时不可用但可记录 pending | Setup `SUCCESSFUL`；后台重试 |
| 无有效收件人 | Setup `SUCCESSFUL`；记录 `NO_ELIGIBLE_RECIPIENT` 告警 |
| Template 缺失 | Setup `SUCCESSFUL`；P1/P2 配置告警并支持重放 |
| Dispatch/Bounce 失败 | 不影响既有 Setup；按异步机制处理 |

如果系统无法可靠记录任何待发送事件，需记录高优先级 operational incident；仍不能通过撤销密码来“修复”
通知失败。

### 11.2 指标与告警

建议指标：

- setup success event created count；
- dispatch success/failure/retry count，按 channel/locale；
- event-to-dispatch latency p95/p99；
- hard bounce/no recipient/template missing count；
- duplicate suppressed count；
- outbox/dead-letter backlog age。

告警：

- 连续模板解析失败；
- pending/retry backlog 超阈值；
- 某 channel 失败率突增；
- 同一 setup reference 产生多个业务通知；
- Setup success 与 notification event 数量长期不一致。

## 12. 测试设计

### 12.1 单元测试

- 只在 `FIRST_TIME_SETUP + SUCCESS` 发布事件。
- Reset、invalid/expired Code、UAM failure/unknown 不发布。
- 同 request/reference 重试只产生一个 event。
- DTO allow-list，不包含敏感字段；`toString()` 安全。
- locale fallback、recipient resolution、重复地址去重。
- Alert exception 不改变 Setup response。

### 12.2 集成测试

- 788 成功后 event/action/recipient/template 正确串联。
- en、zh-Hans-CN、zh-Hant Email/SMS 渲染变量完整。
- 用户 contact 更新后按事件发生时/派送时的既有平台规则选取，行为与 BCO PIN 一致。
- Gateway temporary failure 后重试成功，无重复业务通知。
- Hard bounce 进入既有 Bounce Back/fallback 流程。
- 无 recipient、template disabled、event mapping missing 可被监控并可重放。

### 12.3 E2E/UAT

- 首次 Setup 成功：页面立即成功，通知随后到达，内容/语言/时间/reference 正确。
- 失败 Setup：无成功通知。
- 相同请求刷新/重放：只收到一次逻辑通知。
- Reset 成功：不触发 1204 模板。
- BCO Login PIN Reset：仍使用原模板，证明未受影响。
- Notification 失败：API Password 仍可认证，证明没有错误回滚。

### 12.4 安全测试

- 数据库配置、provider request、application log、dead letter 中无 password/Code/hash。
- 伪造前端 email/mobile 不能改变收件人。
- 跨用户/跨 party reference 不可查询或重发。
- Template injection/HTML escaping、header injection、恶意 display name。
- 人工重发功能鉴权与审计。

## 13. Acceptance Criteria 映射

| AC | 设计覆盖 |
| --- | --- |
| AC1 首次设置成功后通知指定收件人，并沿用 BCO PIN 机制 | 第 2.1、2.2、3、4、5 节 |
| AC2 使用预定义模板 | 第 6、9 节 |
| AC3 returned/undeliverable 沿用 BCO Password/PIN 处理 | 第 2.3、8、11 节 |

## 14. 部署与回滚

### 14.1 上线顺序

1. 部署 788 Setup Service 的安全 event DTO/发布点（feature toggle 关闭）；
2. 执行 Notification SQL：activity/event/action/recipient/template/bounce config；
3. 清理 Alert/Event/Template configuration cache，重启实际缓存这些配置的 managed server；
4. 执行 Verification SQL 和模板变量校验；
5. 用受控测试用户做三语言 smoke dispatch、temporary failure、hard bounce 测试；
6. 开启 `ENABLE_HTH_API_PASSWORD_SETUP_NOTIFICATION`；
7. 监控 event-to-dispatch latency、失败率和 backlog。

### 14.2 回滚

- 先关闭 notification toggle/停用新 event action，停止新派送。
- 不回滚或删除已生效 API Password。
- 保留历史 event/delivery/audit 记录。
- Fallback SQL 只停用/删除本 Story 新增业务 key，不触碰 `PIN_RESET_SUCCESS` 和 BCO 模板。
- 处理完 pending/outbox 后再移除代码；不能留下无法消费的新 event。

## 15. 关键文件（实施建议）

| 类型 | 文件/目录 | 动作 |
| --- | --- | --- |
| Service | `...module.hosttohost/.../app/hosttohost/service/HostToHostApiPassword.java` | Setup 成功出口发布事件 |
| DTO | `...app.xface/.../hosttohost/dto/HthApiPasswordNotificationDTO.java` | 新增安全通知 payload |
| Constants | `...common/.../constants/HthApiPasswordConstants.java` | Event/error/config code |
| DB | `consulting/db/branch_change_history/<API_PASSWORD>/Notification.sql` | Event/action/recipient/template/bounce 配置 |
| DB | `consulting/db/branch_change_history/<API_PASSWORD>/Verification.sql` | 结构和 smoke data 验证 |
| 参考代码 | `...module.sms/.../service/user/ResetPassword.java` | 复用 registerActivityAndGenerateEvent 模式，不复制 PIN 业务语义 |
| 参考 SQL | `consulting/db/branch_change_history/251209_Self_Rest_Pin_WO112611/251209_Self_Rest_Pin_Notification_Template.sql` | 参考表配置结构，不能原样覆盖 |

## 16. 实施前待确认项

1. “指定收件人”是否仅 API User，还是同时包含 Authorized Person/Company。
2. 正式通知渠道：Email、SMS、Secure Inbox 的组合及优先级。
3. BCO Logon PIN 与 Logon Password 失败策略存在差异时，采用哪套 retry/fallback 参数。
4. Security notification 是否忽略用户 marketing opt-out。
5. 三语言 legal-approved template、官方联系方式和时区显示格式。
6. Alert Framework 是否已有业务幂等能力；若无，使用何种 outbox/unique key。
7. Hard bounce 后是否自动设置用户 reminder，以及 alternative System Administrator 的选择规则。
