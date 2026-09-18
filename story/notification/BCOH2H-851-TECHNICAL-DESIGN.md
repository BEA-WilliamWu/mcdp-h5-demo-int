# BCOH2H-851 — 联系方式更新通知（最小版本）

2026-09-17 · 基线：本次同步并回退后的 `dca7ea48`

**补齐 H2H 最终审批人的通知，复用原 BCO 模板和发送流程。另修正 HTH 联系方式编辑时的 PIN 通知误触发，并固定保存前的旧邮箱。普通 BCO 保持原样。**

## AC 对照

| AC | BCO 原有行为 | 本次 H2H 调整 |
| --- | --- | --- |
| AC1：邮箱、手机都改 | 邮件通知用户旧、新邮箱、当前执行人及公司；短信通知用户旧、新手机 | 用户、公司通知沿用；执行人邮件改为最终审批人邮件，补审批人的邮箱修改、手机修改两种短信提醒 |
| AC2：只改邮箱 | 邮件通知用户旧、新邮箱、当前执行人及公司；用户未变手机号也收提醒 | 用户、公司通知沿用；执行人邮件改为最终审批人邮件，补审批人邮箱修改短信提醒 |
| AC3：只改手机 | 短信通知用户旧、新手机；邮件通知用户未变邮箱、当前执行人及公司 | 用户、公司通知沿用；执行人邮件改为最终审批人邮件，补审批人手机修改短信提醒 |
| AC4：退信补发 | BCO 已有退信处理，按条件转短信、站内信或公司联系人 | 本次沿用，不增强退信关联或跨发送去重；如仍有差异，后续单独评审 |

## 实现方式

1. 根据更新前已读取的 `userChannelType=HTH/H2H` 识别 H2H，普通 BCO 不额外查询审批记录或 HTH 表。
2. 从本笔已批准的 `UserExtensionData.update` 审批记录中取得最后一位审批人，读取其邮箱、手机号及国家码。只在资料更新成功后走原 Alert 登记流程。
3. 邮件沿用 `INFO_UPDATE_BY_CORP_ADMIN`。将 H2H 原先的“当前执行人”收件位置改为“最终审批人”；邮箱已在本次用户/公司列表中则不再加入。
4. 短信沿用 `USER_EMAIL_ADDRESS_UPDATE`、`USER_MOBILE_NUMBER_UPDATED_REMINDER`。不向审批人发送新手机号欢迎短信；同一事件、相同号码及国家码不重复加入。
5. 复用现有 `UserProfUpdateActivityLogDTO`：`ProfileUser` 表示被修改的用户，`UserId` 表示本条短信的审批人收件身份；`NotificationDetail` 保留公司 ID、EXTERNAL 类型和审批人号码。
6. 同时设置框架已有的 `inputfacts.recipientUserId`。本地 SDK 的 `ExternalRecipientDerivationHelper` 会把它带到收件信息，再交给 `AlertRequestDTO.userId`；原 `SMSDispatcher` 据此查询审批人的国家码。事件配置为按 `UserId` 查号码时，也会查审批人。因此无需新增公共 DTO 或修改发送类。

审批记录或联系人无法确认时记录阶段与异常类型，不回退到 Maker、前序审批人或后台执行账号。缺少可用手机号/国家码时不新增该审批人短信；原用户、公司通知仍走原流程。

去重仅针对本次加入的审批人通知，不重写 BCO 原有用户/公司去重，不新增跨重试去重、Outbox、重发或退信补偿。

## 修改范围及 BCO 影响

| 文件 | 修改 |
| --- | --- |
| `UserExtensionData.java` | 在 H2H 分支接入审批人及旧邮箱快照；保存前比较 PIN Reset Code 状态，并仅在本次 postUpdate 调用期间传递 HTH 通知标记；原公开方法签名保留 |
| `HthProfileApproverNotification.java` | 同一用户管理模块内处理最终审批人识别、收件人去重、旧邮箱快照及 HTH PIN 状态变化判断 |
| `ext/CZUserExtensionDataExt.java` | HTH 未改变 PIN Reset Code 状态时跳过对应启用／停用通知；仍执行其他更新后处理；没有 HTH 标记的 BCO 和其他入口保留原逻辑 |

三个生产文件均在 `com.ofss.digx.cz.bea.module.sms`。**不改 batch、scheduler、SMSDispatcher、EmailDispatcher、公共 DTO/common 包、Preferences、SQL 或前端；不新增表、事件或模板。**

## UAT 反馈：修改联系方式后收到 PIN 停用邮件

- 截图的 `Notification of Login PIN Reset Code Disablement` 对应 `LOGIN_PIN_RESET_DISABLE_REMINDER_CORPORATE_USER`／`...COMPANY`，是独立 PIN 通知。原 `postUpdate` 按当前 bypassFlag 选启用或停用模板，没有判断状态是否变化；读取的是更新后的邮箱，因此这封邮件本来就不会使用旧邮箱。
- HTH 修正：更新前比较实际存储的 N→Y／Y→N，资料保存成功才允许对应 PIN 通知。只改联系方式、状态不变或校验阶段不触发。临时标记在 finally 中恢复，避免污染后续调用；其他 iToken／BM 处理保留。
- 联系方式修改邮件仍使用 `INFO_UPDATE_BY_CORP_ADMIN`。HTH 在保存前复制旧邮箱，发送时不再从可能已更新的 User 对象取旧值；AC1、AC2 都使用该快照。旧手机号码原本已有独立快照。
- 本地已复现“User 对象原地更新导致旧邮箱丢失”的用例，并验证修复后旧、新邮箱各保留一份。该测试证明快照有效，**不能单凭截图认定 UAT 此次漏发就是对象更新导致**；还需查看 `INFO_UPDATE_BY_CORP_ADMIN` 的事件、发送记录及实际邮件。
- 回归覆盖真实 postUpdate 分支的通知门控：HTH 联系方式编辑不误发，真正 PIN 启停仍通知，iToken hook 仍执行，异常时清理标记。网络／数据库使用测试替身，未在 UAT 发送邮件。

## 部署与验收

- 构建并部署 `com.ofss.digx.cz.bea.module.sms`，使用环境已有 BCO 通知配置。本版无 SQL 执行步骤。
- 测试入口：`devtools/backend-compile/tests/verify_hth_851_compile.py`、`verify_hth_851_approver.py`，需配置 `JAVA_HOME`。
- 本地验证已通过：Java 8 目标编译；实际 BCO 通知方法与基线比较；H2H 最终审批人识别、去重；真实 SDK 外部收件人解析；原 SMSDispatcher 路由及 MNG 请求构造。数据库、网络和服务器时钟使用测试替身。另已检查 SDK 事件登记链路，现有 `inputfacts` 会继续传入事件处理。
- UAT：AC1–AC3 的原用户和公司通知仍在，最终审批人收到对应邮件/短信；多级审批不增加前序审批人，跨国家码正确，相同事件/地址不重复；普通 BCO 回归三种修改。
- 环境模板正文/属性配置不在这份源码中；UAT 应核对正文仍显示目标用户 `ProfileUser`，以及实际邮件、短信送达。本地验证不能代替真实送达验收。

以本次干净基线实施；mcdp 已清空，本次不恢复之前交付文件。

## 2026-09-18 UAT 日志补充

交易 `1809ABDF0AF0` 在 09:10:57 返回 replyCode=0、errorCode="0"、validationErrors=null；原入口仅接受 errorCode=null，因此未调用联系方式通知方法。HTH 分支现接受 replyCode=0 且 errorCode 为 null/"0"、无 validationErrors 的结果；普通 BCO 保留原判断。失败或空状态不发送成功通知。

日志同时显示 postUpdate 调用了 PIN reset disable 的 sendNotifications。该点不能由上述成功判定缺陷单独解释：若之前的 HTH 门控完整生效，资料通知未执行时也应禁止 PIN 通知。请求中的 userChannelType=HTH 不能证明更新前持久化资料或运行时 targetUnit 命中门控，也不能证明 UAT 已部署全部类。新增无敏感值日志：

- `HTH_851 stage=PROFILE_RESULT success=true/false`：HTH 更新结果判定。
- `HTH_851 stage=POST_UPDATE hthGate=true/false pinNotify=true/false/null`：是否命中 HTH 门控及是否允许 PIN 通知。

若 hthGate=false，核对更新前持久化 userChannelType 与 targetUnit；若 hthGate=true、pinNotify=false 仍进入 PIN sendNotifications，核对部署的 CZUserExtensionDataExt 是否包含门控。日志缺少新标记时先核对运行节点/包版本及日志级别。不要通过修改 PIN 模板掩盖错误事件。

本地已验证日志中的零值成功状态、错误状态、旧新邮箱快照、原通知方法及 postUpdate 门控；未执行 UAT 真实投递，未修改通知模板或公共发送类。
