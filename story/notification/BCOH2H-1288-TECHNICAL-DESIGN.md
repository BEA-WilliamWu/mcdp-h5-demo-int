# BM HTH Enable / Disable / Edit 通知技术设计（597 / 1288）

2026-09-17：按新增要求将 Enable 一并纳入。复用现有审批、Alert 队列及 MNG 发送，不修改 batch 或公共发送类。

| 动作 | 何时通知 | DOCX 模板 |
| --- | --- | --- |
| Enable | 最终审批并成功保存首次启用或重新启用 | #1 Activated |
| Disable | 最终审批并成功保存 ENABLE → DISABLE | #2 Deactivated |
| Edit | 最终审批并成功保存实际 API code 集合变更 | #3 Configuration Updated |

只通知公司：有公司邮箱只发 Email；没有邮箱才发公司 SMS；无可用联系方式不发。Edit 仅顺序变化、仅收费账户/审批流程改变不发本通知。公司号码必须含可明确解析的国家码。

## 改哪些

- `HostToHostManagement.java`：三个操作统一使用公司通知方法，保存前判断状态/变更，保存成功后登记；移除 Enable 旧私有方法，避免第二条发送路径。
- `1288_HTH_Management_Notification.sql`：沿用 SUBMIT / DISABLE / EDIT 三个既有成功事件，更新 18 份 EMAIL/SMS 模板及 EXTERNAL 映射（3 操作 × 2 通道 × 3 locale）。原位更新模板，可重跑，异常回滚；其他语言映射保留。
- 公共配置只追加这三个事件到短信国家码跳过名单，保留其他值，让发送器使用公司号码中的国家码。普通 BCO、851、1216、batch、公共 DTO/Java 发送类不改。

每次符合条件的调用只登记一个事件和一个通道；不新增跨请求去重表。可捕获通知异常记录阶段/异常类型，不输出联系方式或正文；原业务事务处理保留，真实回滚不投递须 UAT 验证。

## 模板与待完成项

模板来源为 `1.Customer Onboarding BM - Notification (Clear)-v12-20260917_192646.docx`：邮件主题和正文是英文＋繁体中文，各语言共用相同双语内容；短信 EN/TC/SC 分别映射 `en` / `zh-hant` / `zh-hans-cn`。逐字比对通过，没有自行翻译。邮件电话 2211 1056、短信电话 2211 1321 保留原文，无动态占位符。

Disable 简体短信原文额外含「详情请参阅电邮。」，当前按指定模板保留；H2H API 命名未最终确定也是原文备注，不自行改名。中文已由 DOCX 表格原文补齐，旧 PDF 不再作为模板依据。

**短信长度**：Enable 原文 220 字符，需确认 UAT 运行时 `SMSLength` 及 MNG 支持；不直接修改影响 BCO 的全局上限。无分隔国家码的公司号码仍需规范资料或确认独立国家码来源。

本地 Java 定向编译、实际业务方法/SDK 收件解析/模拟 MNG 路由测试、DOCX 双语邮件及三语短信内容检查通过；Oracle、真实审批队列和投递仍待 UAT。

[部署步骤、核查 SQL、测试及回退](../../consulting/db/branch_change_history/20260917_BCOH2H-1288/README.md)。
