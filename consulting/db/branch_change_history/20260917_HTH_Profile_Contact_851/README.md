# BCOH2H-851 部署及验收

本版按 2026-09-17 确认的规则实现，使用现有 BCO 通知内容。只改变 HTH 用户的 CM Profile Contact 更新通知，普通 BCO 继续原流程。

## 部署

1. 在 OBDX 用户资料／配置 schema 执行 `1_HTH_Contact_Notification_Outbox.sql`，再执行 `2_HTH_Contact_Notification_Config.sql`。可重跑；保留数据和已有开关值。不要执行历史版本的六个 HTH event／36 个模板 SQL。
2. 部署本次 xface、module.sms、domain.service.dispatch、scheduler.impl 及 `consulting/config/Preferences.xml`。批处理部署对应环境的 `ValidateAndSendBounceNotify`；root、UAT、PRD 已同步修改。
3. 执行 `3_Verify_HTH_Contact_Notification.sql`，核对原 BCO 四个 event 的模板、语言、metadata 和中文配置。核对 AP 收到的内容描述的是被修改用户；本仓库不含 UAT 实际模板数据，不能代替现场正文验收。
4. 确认现有 `BatchExecutionScheduler` 和退信批处理正常运行。新通知在该 scheduler 下一轮发送，不在资料事务提交前发送。`dispatchDataSource` 默认 `NONXA`，用于独立记录发送结果；须指向同一个 OBDX schema。资料和待发送记录仍使用原 DIGX ORM 事务，不改变 BCO 数据源。
5. 新安装默认关闭。在上述部署完成后启用；若之前配置已存在，重跑 SQL 会保留原值，须先检查：

```sql
UPDATE DIGX_FW_CONFIG_ALL_B SET PROP_VALUE = 'true'
WHERE CATEGORY_ID = 'HthProfileContactNotification'
  AND PROP_ID = 'HTH_PROFILE_CONTACT_NOTIFICATION_ENABLED';
COMMIT;
```

Preferences 新节点需随应用加载；之后开关刷新间隔为 5 分钟。开关关闭时新资料更新沿用 BCO 原通知，已排队的 851 通知暂停；重新启用后继续处理。

`DIGX_CZ_HTH_CONTACT_OUTBOX` 是本版独立发送记录表，避开旧 `DIGX_CZ_HTH_CONTACT_NOTIFY` 的不同结构。不会迁移或重放旧表历史。每条记录含审批引用、原收件地址、角色、事件及发送状态，用于提交后发送、去重及识别旧邮箱退信；应按银行通知资料保留政策管理。

## 收件人和模板

| 修改 | 用户 Email | 用户 SMS | 最终 AP | 公司 |
| --- | --- | --- | --- | --- |
| Email + Mobile | 旧、新邮箱 | 旧号码更新提醒；新号码通知；新号码 Email 更新提醒 | Email；Mobile 更新和 Email 更新两种 SMS | officeEmail |
| Email only | 旧、新邮箱 | 未变号码 Email 更新提醒 | Email；Email 更新 SMS | officeEmail |
| Mobile only | 未变邮箱 | 旧号码更新提醒、新号码通知 | Email；Mobile 更新 SMS | officeEmail |

重复的“事件内容＋地址”合并为一条记录，并保留 API/AP/COMPANY 角色。AP 不接收新号码欢迎短信。模板中 `userId`、`profileUser` 始终为被修改用户，不会被替换成审批人。最终 AP 取 APPROVED 审批链最后一位，第一位 maker 不算审批人。

复用事件：`INFO_UPDATE_BY_CORP_ADMIN`（Email）、`USER_EMAIL_ADDRESS_UPDATE`、`USER_MOBILE_NUMBER_UPDATED_REMINDER`、`CORPORATEPLUS_WELCOME_MAIL`（新手机号 SMS）。没有新增或覆盖 BCO 模板。独立的 `USER_MANAGEMENT_EDIT` 保留。

## 退信和排错

MNG 引用为 `H851` 加发送记录 ID。依据原引用关联退信，不用当前用户邮箱判断。

- `SUBMITTED` 仅表示 MNG 接收成功，不代表邮件／短信最终送达。
- `UNKNOWN` 表示调用超时、响应不明确或进程中断；先按 MNG 引用对账，不自动重发。
- 用户邮件失败且当前公司邮箱不同：已有同地址计划则 `FALLBACK_LINKED`；否则生成一次 `FALLBACK_QUEUED`。
- 公司地址本身失败：`OFFICE_FOLLOWUP`，退信批处理沿用 BCO 的 AP 登录提醒和系统管理员站内信；写入成功后为 `OFFICE_NOTIFIED`。跟进和标记在同一事务中，失败回滚，不反复补发公司邮件。
- 普通 BCO 回执仍走原批处理；仅精确匹配新发送表的 H851 引用被独立处理。旧六个 event 的历史排除配置保持原状。

应用新增日志只记录 notification ID、阶段、状态和异常类型。排查时先查发送表和 `DIGX_CZ_EMAIL_MNG`，再按 MNG 引用查询回执。勿把收件地址、正文写入新增诊断日志。

## 测试

本地已提供自动测试，银行资料查询、MNG 网络调用等环境边界使用 fixture，不会发送真实通知：

```sh
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
python3 devtools/backend-compile/tests/verify_hth_contact_compile.py
python3 devtools/backend-compile/tests/verify_hth_contact_sql.py
python3 devtools/backend-compile/tests/verify_hth_contact_runtime.py
python3 devtools/backend-compile/tests/verify_hth_contact_bounce.py
```

运行测试需要仓库现有依赖及 H2 1.4.200，可用 `HTH_TEST_H2_JAR` 指定路径。覆盖真实 EclipseLink/OBDX ORM 提交回滚、SDK ActivityLog 序列化、BCO metadata getter 解析、原 MNG 请求构造器、收件矩阵、国家码、最终 AP、并发去重、失败／超时、退信去重、公司跟进回滚和开关隔离。

UAT 仍需验证：AC1–AC3 实际邮件/SMS 及中英文正文、多人审批只通知最终 AP、旧新地址退信、同地址跨角色去重、公司退信跟进，以及相同操作的普通 BCO 回归。Oracle SQL、真实审批执行、调度与 Alert 引擎、MNG 到手机/邮箱的完整链路未在本机运行。
