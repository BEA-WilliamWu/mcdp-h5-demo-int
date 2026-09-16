# BCOH2H-851 — Profile Contact Update Notification

小范围版本 · 2026-09-17 · 已实现，待 UAT 验收

**本次只补齐 H2H 最终审批人的通知，复用 BCO 的事件、模板和发送流程。普通 BCO 保持原流程。**

## 各 AC 怎么处理

以下 BCO 行为按本轮修改前源码 `dca7ea48` 对照。

| AC | BCO 原有行为 | 本次 H2H 修改 |
| --- | --- | --- |
| AC1：Email、Mobile 都改 | 邮件发用户旧、新邮箱、当前执行人及公司邮箱；短信发用户旧、新手机号 | 用户和公司的通知沿用；当前执行人邮件改为最终审批人邮件，补最终审批人短信 |
| AC2：只改 Email | 邮件发用户旧、新邮箱、当前执行人及公司邮箱；用户未变手机号也收到提醒 | 同上；审批人使用原 BCO Email 修改提醒模板 |
| AC3：只改 Mobile | 短信发用户旧、新手机号；邮件发用户未变邮箱、当前执行人及公司邮箱 | 同上；审批人使用原 BCO Mobile 修改提醒模板 |
| AC4：邮件退信 | 已有 BCO 退信处理，按条件转短信、站内信或公司邮箱 | 本次不增强退信，也不承诺新增的退信关联、防重复规则；继续原 BCO 行为，后续单独评审 |

## 通知规则

- 根据更新前已读出的用户资料 `userChannelType=HTH/H2H` 识别 H2H；普通 BCO 不增加审批记录或 HTH 表查询。
- 从本笔已批准的 `UserExtensionData.update` 审批记录取得最后一位审批人，取该人的邮箱、手机号和国家码。Maker、前面的审批人及后台执行账号不作为本次新增通知的收件人。
- 用户资料更新成功后，沿用原来的 Alert 事件登记和事务处理。审批人无法确认时不猜收件人，记录阶段及异常类型；用户和公司仍走原通知流程。
- 邮件沿用原 BCO 邮件事件和模板；最终审批人地址已在本次邮件列表中时不再加入。
- 审批人短信复用 `USER_EMAIL_ADDRESS_UPDATE`、`USER_MOBILE_NUMBER_UPDATED_REMINDER`。Email、Mobile 都改时，审批人收到两种对应提醒；不向审批人发送用户的新号码欢迎短信。
- 模板中的用户仍指被修改的用户，短信实际收件人为最终审批人。审批人手机号及国家码与本次相同事件的用户收件号码相同时不重复加入；不同内容的提醒保留。
- 保留 BCO 的未变化通道提醒和首次公司 `officeEmail` 通知。此次只对新增审批人做去重，不重写原有用户/公司之间的去重，也不新增跨重试去重或自动重发机制。

## 代码范围与 BCO 影响

相对 `dca7ea48`，业务代码只改 **2 个现有公共类，新增 2 个 H2H 类**。

| 文件/模块 | 作用及范围 |
| --- | --- |
| `UserExtensionData.java` / `com.ofss.digx.cz.bea.module.sms` | H2H 联系方式更新时确认最终审批人，替换原当前执行人邮箱，并登记审批人短信。普通 BCO 继续原分支；原公开方法签名保留 |
| `SMSDispatcher.java` / `com.ofss.digx.cz.bea.domain.service.dispatch` | 仅对 H2H 审批人 DTO 和上述两个事件使用审批人号码、国家码，防止原逻辑改回目标用户号码；其他通知维持原处理 |
| `HthProfileApproverNotification.java` / `com.ofss.digx.cz.bea.module.sms`（新增） | 最终审批人查询、联系方式和本次通知去重 |
| `HthProfileApproverActivityLogDTO.java` / `com.ofss.digx.cz.bea.app.xface`（新增） | 分开保存模板中的目标用户和实际审批人收件信息，随原 Alert 活动数据传递 |

上一版的 Outbox 类、SQL、Preferences、EmailDispatcher、BatchExecutionScheduler、三份退信批处理改动已撤回。没有新的表、事件配置、模板配置或定时任务；依赖环境已有的 BCO 通知配置。

## 部署与验收

构建并部署上述三个模块的 JAR。若已部署过上一版，还需把该版改动过的配置、调度和退信批处理更新为本次恢复后的版本；重新构建时避免残留已删除的 Outbox 类。本次没有 SQL 要执行，也不会删除环境中已建的表或历史数据。若旧 Outbox 已产生待发记录，切换前核对这些记录，避免与新流程重复发送。

本地已完成 Java 8 目标编译、实际通知方法与旧 BCO 方法的对照测试，以及短信路由/MNG 请求构造测试。数据库、银行网络和服务器时钟使用测试替身，尚未证明 UAT 实际送达。

验证脚本：`devtools/backend-compile/tests/verify_hth_contact_compile.py`、`verify_hth_contact_runtime.py`（需设置 `JAVA_HOME`）。

UAT 验收：H2H 的 AC1–AC3 分别核对旧/新联系方式、未变化通道、公司及最终审批人；验证多级审批只增加最终审批人、相同地址不重复加入、跨国家码短信送达。再用普通 BCO 跑同样三种修改，确认原通知未变。
