# BCOH2H-1288 — BM 公司 HTH 停用及修改通知

2026-09-17 · 用户已认可总体设计；本地实现以 `45af793b` 为基线。未部署、未执行 UAT SQL；生产联调及待补模板范围见下文。

**公司 HTH 停用或 API access 实际修改，最终审批并保存成功后通知公司。有邮箱发邮件，没有邮箱才发短信。** 不新增目标用户、审批人通知。

## AC 对照

| AC | 现有情况 | 本次实现 |
| --- | --- | --- |
| 停用公司 HTH | 已有 APPROVED 分支及停用事件，原通知可能缺公司 ID | 保存前确认 ENABLE，停用保存成功后使用模板 #2，通知公司一次 |
| 修改公司 API access | 原来所有已审批 Edit 都尝试通知 | 比较保存前后 API code 集合，实际增加/删除才使用模板 #3；顺序变化不发；审批流程/收费账户单独改变也不发本通知 |
| 启用公司 HTH | 属于 597，使用模板 #1 | 原通知方法、收件规则和事件配置保持原样 |

附件两段 AC 都标作 AC1；正文残留 activated 与本次停用不符，本次按附件模板 #2 Deactivated / #3 Configuration Updated。划掉的旧 BCO 备注没有作为需求使用。

## 发给谁

| 公司联系方式 | 行为 |
| --- | --- |
| 有 `officeEmailId` | 只公司邮件；格式无效记录错误，不自动换短信 |
| 无邮箱，有能明确解析国家码的公司手机 | 只公司短信 |
| 没有可用联系方式 | 不登记通知，记录原因 |

公司资料沿用 `IUserExtensionAdapter.getPartyPreferences(partyId)`。`NotificationDetail.recipientId` 与活动的 `customerId` 都使用已校验的请求公司 ID，修复适配器未填 `partyIdValue` 的问题。

号码核查发现：BCO `LocalPartyContactRepositoryAdapter` 已把 `officeTelNo` 映射成 `WORK_MOBILE`，公司资料维护页面允许 `+国家码-号码`、`+国家码 号码`。当前先支持这两种有明确分隔的格式；无国家码、未分隔完整号码暂不猜测。例如 `+86-13800138000` 会组装为 `13800138000~86`，由原 SMSDispatcher 发送成 `8613800138000`。

## 具体改动及公共影响

**生产代码仅 1 个现有 HTH Java 类 + 1 份 SQL；另有测试和说明。**

1. `HostToHostManagement.java`：1288 私有分支完成旧状态/旧 API 集合判断、公司收件人、单通道选择、登记状态检查和脱敏日志。仅最终审批分支调用，并位于业务保存之后。Enable 保留 597 旧方法。
2. `1288_HTH_Management_Notification.sql`：沿用 `HTH_API_SERVICE_DISABLE_SUCCESS`、`HTH_API_SERVICE_EDIT_SUCCESS`，配置对应 `disable` / `edit` 活动、必发 Alert、EMAIL/SMS 模板及 EXTERNAL 收件映射。不新增业务表。
3. **唯一公共配置调整**：在 `SMS_DISPATCHER_SKIP_COUNTRY_CODE_EVENTID_LIST` 追加这两个 HTH 事件，保留全部原值/默认项；防止发送类读取 BM 操作员的国家码。Java 发现这项配置缺失会停止短信并记录原因。

不修改 batch、scheduler、公共 DTO/Java 包、EmailDispatcher、SMSDispatcher、普通 BCO 业务、851、1216 或前端；也不把 HTH 事件加入公共“重新推算收件人”名单。

通知异常只记录交易参考号、动作、阶段、通道、结果/异常类型，不打印地址、正文或异常消息。可捕获的通知异常不直接阻断业务；若事务已经 rollback-only，仍由原 `Interaction.close()` 处理，不能用吞异常保证提交。

每次调用只登记一个事件、一个通道；不新增跨请求去重表、batch 补偿或 exactly-once 承诺。SQL 用原异步 Alert 管道，真实事务回滚不投递成功通知仍需 UAT 验证。

## 模板

| 动作 | 英文主题 | 模板 ID 前缀 |
| --- | --- | --- |
| 停用 | BEA Corporate Online: H2H API Service Deactivated | `HTH1288_DISABLE_` |
| 修改 | BEA Corporate Online: H2H API Service Configuration Updated | `HTH1288_EDIT_` |

正文使用附件 #2/#3，无业务占位符，所以不增加属性映射或公共 DTO。邮件热线 2211 1056、短信热线 2211 1321 保留附件差异。

**尚待补齐：** 完整繁简中文模板（当前 PDF 缺正文），或确认繁简页面暂用英文；当前 SQL 只绑定 `en`。公司号码若在 UAT 不是上述明确格式，需要确认正式国家码来源，当前不使用 BM 用户号码/国家码替代。

## 已验证及上线验证

本地已通过：

- Java 8 定向编译（当前服务及其 DTO 源码、实际仓库依赖）。
- 真实 `processSave` 及本次通知方法：最终审批、失败不发、API 集合变化/顺序不变、Enable 保留原分支、单通道选择、缺失/非法联系方式、登记返回错误/空值和脱敏日志。
- 实际 `ActivityData` 序列化、SDK EXTERNAL 收件人解析、原 SMSDispatcher 到模拟 MNG 的公司号码检查；公司 +86 与 BM +852 不同也未读取 BM 国家码。
- 851、1216 原有测试回归。

测试脚本：`devtools/backend-compile/tests/verify_hth_1288_compile.py`、`verify_hth_1288.py`。测试替代了数据库、审批属性、时钟、Alert 登记和 MNG 网络，不能代表真实送达或 UAT 事务测试通过。

[部署、SQL 重跑核对、UAT 用例及回退说明](../../consulting/db/branch_change_history/20260917_BCOH2H-1288/README.md)。SQL 尚未在 Oracle 实际执行；上线前需两次重跑检查无重复、刷新配置缓存，验证中间审批/回滚不发送，并检查真实邮件、SMS、Alert/MNG 记录。

依据：本目录 `[#BCOH2H-1288] CLONE - BM - Corporate HTH Disable- Notification.pdf` 和 `292818690_59882f725158429986723c3eb9fa9038-170926-0237-58.pdf`（模板 #2/#3）。
