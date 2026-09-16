# BCOH2H-1288 部署及验证

本次业务改动仅 `HostToHostManagement.java` 和本目录的通知 SQL。新增测试/说明不需要部署。

## 行为

- BM 公司 HTH Disable：最终审批、实际 ENABLE → DISABLE 保存成功后，登记一次公司通知。
- BM Edit：最终审批时比较保存前后的 API code 集合，实际增加/删除才通知；只调整顺序、审批流程或收费账户不发本通知。
- 有 `officeEmailId`：只发公司邮件。邮箱无效记录错误，不自动改发短信。
- 无邮箱：使用公司 `officeTelNo`，当前支持 `+852-61234567` / `+86 13800138000` 等明确分隔国家码的格式。现有 BCO 公司联系人适配器已把这个字段映射为 WORK_MOBILE。
- 无号码、缺少国家码、无分隔的号码（如 `+85261234567`）暂不发送，日志为 `MissingOrAmbiguousCompanyMobile`，不猜测国家码。其他存储格式仍待业务确认。
- 公司邮箱和手机都没有时不发送；不通知目标用户或审批人。
- Enable/597、851、1216 继续原流程；不改 batch、公共 DTO、发送类或普通 BCO 业务代码。

## 模板范围

当前 SQL **仅包含 `en` 映射**，正文是附件 #2/#3 的英文；没有可读取的完整繁简中文模板。繁简页面是否暂用英文仍待确认，未擅自绑定。此版本可做英文 UAT；不要当作三语通知已齐全。

邮件电话 `2211 1056`、短信电话 `2211 1321` 保留附件原文。模板没有动态占位符，无须新增公共 DTO 属性映射。

## 执行

1. 在 UAT 先导出这两个事件在 `DIGX_PM_EVENT_ALL_B`、`DIGX_EP_ACT_EVT_B`、`DIGX_EP_ACT_EVT_ACN_B`、`DIGX_EP_EVT_REC_B` 的原行，及两个 HTH 活动在 `DIGX_EP_ACT_B` 的原行；若已有 `HTH1288_` 模板，同时备份模板、属性、来源行。
2. 备份 `DIGX_CZ_FW_CONFIG_ALL_O` 的 `DayOneConfig` / `OBDX_BU` / `SMS_DISPATCHER_SKIP_COUNTRY_CODE_EVENTID_LIST` 原值，并记下原行是否存在。
3. 在 OBDX 配置 schema 完整执行 `1288_HTH_Management_Notification.sql` 的 `DECLARE…END;` 块。DBeaver 用执行语句；SQL*Plus/SQLcl 在块后另输入 `/`。不要单独执行块内语句。
4. 部署包含修改后 `HostToHostManagement` 的后端。按环境既有流程刷新 Alert / CustomConfig 缓存，或重启承载节点，再开始测试。

脚本重复执行会替换本 story 的 Alert 收件映射，不叠加第二组；事务成功只提交一次，异常回滚本脚本。其他 Alert 动作、原有模板和启用事件不删除。原短信国家码跳过名单只追加两个 HTH 事件；原行缺失/空值时保留发送类的默认五项再追加。若发现 HTH 事件被其他活动使用、模板被其他事件引用、公司地址会被公共名单重算，则报错并回滚，避免带着歧义上线。

这次没有连接 Oracle/UAT，SQL 未实际执行；“可重复执行”是脚本设计，仍需在 UAT 连续执行两次确认。

## 核对与测试

```sql
SELECT COD_ACT_ID, COD_EVENT_ID, TXT_DEST_TYP, LOCALE, COD_MSG_TMPL_ID,
       SUBSCRIBER_TYPE, COUNT(*) AS ROWS_PER_BINDING
FROM DIGX_EP_EVT_REC_B
WHERE COD_EVENT_ID IN ('HTH_API_SERVICE_DISABLE_SUCCESS', 'HTH_API_SERVICE_EDIT_SUCCESS')
GROUP BY COD_ACT_ID, COD_EVENT_ID, TXT_DEST_TYP, LOCALE, COD_MSG_TMPL_ID, SUBSCRIBER_TYPE;
-- 当前预期 4 行：2 个事件 × EMAIL/SMS × en，每行 COUNT=1，SUBSCRIBER_TYPE=EXTERNAL。

SELECT PROP_VALUE FROM DIGX_CZ_FW_CONFIG_ALL_O
WHERE PROP_ID = 'SMS_DISPATCHER_SKIP_COUNTRY_CODE_EVENTID_LIST'
  AND PREFERENCE_NAME = 'DayOneConfig' AND DETERMINANT_VALUE = 'OBDX_BU';
```

- Disable 单级审批、Edit 多级审批：中间审批没有成功通知，最终审批只登记一次；保存失败/真实事务回滚不得投递成功通知。
- 公司同时有邮箱和手机：只邮件。移除邮箱后用有国家码号码：只短信。公司 `+86` 与 BM 用户 `+852` 不同，核对 MNG 目标号码仍为公司 `86…`。
- 本地执行了真实 `ActivityData` 序列化、SDK `ExternalRecipientDerivationHelper`、原 `SMSDispatcher`，其 MNG 网络出口替换为 fixture；真实队列、数据库事务和邮件/SMS送达需 UAT 验证。
- `[HTH-1288] … outcome=Registered` 仅代表登记返回成功，不代表送达。`UnsuccessfulStatus` / 异常类型表示登记失败；`MissingSmsCountryRouting` 表示 SQL 配置未生效，已停止短信以防错号。
- 回归 Enable/597 及普通 BCO、851、1216 通知。

## 回退

回退本次 Java 后，按备份恢复上述两个事件的原有活动关系、Alert 动作和收件映射；若原来不存在则仅删除这两个事件的新增行。仅清理本次 `HTH1288_` 模板，不删除被其他配置引用的记录。活动父记录及原 597 数据保留。短信跳过名单只移除本次新增的两项；不要用旧整串覆盖他人后续追加的事件。原来已存在的 HTH 项不得移除。按相同流程刷新缓存。
