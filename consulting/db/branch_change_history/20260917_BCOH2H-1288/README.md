# BM HTH Enable / Disable / Edit 通知部署（597 / 1288）

生产改动为 `HostToHostManagement.java` 和 `1288_HTH_Management_Notification.sql`；测试、说明和只读核查 SQL 不需要打包。

## 发送规则

| 操作 | 发送条件 | 事件 |
| --- | --- | --- |
| Enable | 最终审批、首次启用或 DISABLE → ENABLE 保存成功 | HTH_API_SERVICE_SUBMIT_SUCCESS |
| Disable | 最终审批、ENABLE → DISABLE 保存成功 | HTH_API_SERVICE_DISABLE_SUCCESS |
| Edit | 最终审批、公司启用中，API code 集合实际增加或删除，保存成功 | HTH_API_SERVICE_EDIT_SUCCESS |

公司有 `officeEmailId` 时只邮件；邮箱为空才使用 `officeTelNo` 发短信；两者都没有则不登记。邮箱格式无效会记录错误，不自动换短信。不通知目标用户或审批人。API 顺序调整、仅审批流程或收费账户改变不触发 Edit 通知。

Enable 已移除旧私有通知方法，三个操作共用公司通知路径，每次符合条件的执行只登记一次；不承诺跨请求 exactly-once。审批、业务保存和权限流程沿用原有代码。

公司号码支持明确国家码格式，如 `+852-61234567`、`+86 13800138000`。无国家码或未分隔号码（如 `+85261234567`）不猜测，记录 `MissingOrAmbiguousCompanyMobile`。收件人来自公司资料，不使用 BM 操作员手机或国家码。

## 模板完成范围

以 `story/notification/1.Customer Onboarding BM - Notification (Clear)-v12-20260917_192646.docx` 的 #1/#2/#3 为准：

- 邮件主题及正文完整保留英文＋繁体中文，各语言均使用这份双语内容；文档没有独立简体邮件，不自行翻译。
- 短信分别使用 EN、TC、SC 原文，对应 `en`、`zh-hant`、`zh-hans-cn`。
- 3 操作 × 2 通道 × 3 locale，共 18 条收件映射及模板。模板 ID 如 `HTH1288_ENABLE_EMAIL_en`、`HTH1288_ENABLE_SMS_zh-hant`。
- 邮件电话 2211 1056、短信电话 2211 1321 保留原文。Disable 简体短信的「详情请参阅电邮。」也按原文保留，即使此通道是无邮箱时使用；如业务要删除，应先更新模板要求。
- 文档仍标注 H2H API 名称未最终确定，本次保持指定原文。没有动态占位符。

先前 PDF 缺中文，现已从 DOCX 的原始表格文字补齐，并做逐字比对；不再以旧 PDF 或本地缺字体的渲染作为中文内容依据。

## 部署和重跑

1. 备份三个事件的活动、事件、Alert 动作、收件映射及 `HTH1288_` 模板、属性、来源记录；备份短信国家码跳过名单的原值及是否存在。
2. 在 OBDX 配置 schema 以 UTF-8 打开并完整执行通知 SQL 的 `DECLARE…END;` 块；不要选取块内部分语句。SQL*Plus/SQLcl 在块后另输入 `/`。
3. 配套部署 Java。按现有部署流程刷新 Alert / CustomConfig 缓存或承载节点；尤其 Enable 新路径需要 SUBMIT 事件的短信国家码配置。
4. 执行 `1288_Verify_Notification.sql`，应有18 条收件映射（3 操作 × EMAIL/SMS × 3 locale）、每条 EXTERNAL，模板有效。

脚本仅重建这三个事件的三个受支持语言的 EMAIL/SMS Alert 收件映射，模板原位更新，允许重跑；单次成功提交，失败回滚。保留其他语言映射及其他动作。共享模板/事件发生歧义时主动报错，避免覆盖其他配置。公共短信国家码跳过名单只追加三个 HTH 事件，保留已有值和默认项；不修改 batch、公共 DTO、邮件/短信发送类或 BCO 业务。

**Enable 英文短信长 220 字符**，Disable 123、Edit 133。原 SMSDispatcher 在发往 MNG 前检查 `AlertPollerPool.SMSLength`；需核对运行时上限至少覆盖 220，且 `DispatchDetails.isDispatchMocked` 不为 true。不要为本功能直接盲改全局上限；需要环境负责人确认现有 MNG 支持及 BCO 影响。保留 DOCX 原文，未擅自缩短短信。

## 验证与排查

本地定向 Java 8 编译、三操作审批/状态/联系人测试、真实 ActivityData 序列化及 SDK EXTERNAL 收件解析、真实 `dispatchMNGSms` 到模拟 MNG 的号码与 DOCX 三语正文检查通过。双语邮件及三语短信对照 DOCX 检查通过。数据库、队列及 MNG 网络使用替身；没有执行 Oracle SQL、真实完整发送器或真实投递验证。

UAT 需执行脚本两次确认无重复，并分别测试三个 locale 的邮件和短信；检查首次启用/重新启用、停用及多级 Edit 最终审批；中间审批、保存失败、无实际变化不通知。验证 Email 优先及无 Email 的 SMS；公司 +86、BM 操作员 +852 时号码仍应为公司号码。回归 BCO、851、1216。

`[HTH-1288] action=… stage=… outcome=Registered` 仅表示登记返回成功，不代表送达。`MissingSmsCountryRouting` 表示配置缺失；`MissingOrAmbiguousCompanyMobile` 表示号码不可明确解析。MNG 表 Success 表示接口接受请求；无数据需继续检查 Alert 登记、队列消费、模板解析、短信长度/模拟配置以及发送日志，不能直接认定未发送。

## 回退

Java 与三个事件配置一起按备份回退。只清理本次新增且没有其他引用的模板/配置；国家码名单仅移除本次新增项，保留他人后续改动，不覆盖整个名单。回退后按既有流程刷新缓存。
