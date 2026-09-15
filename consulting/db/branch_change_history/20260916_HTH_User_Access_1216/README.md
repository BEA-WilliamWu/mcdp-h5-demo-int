# BCOH2H-1216 部署与验证

2026-09-16：已实现 User Accounts & Service Access 的 Link/Edit 最终生效通知。当前独立开关默认关闭，只绑定 `OBDX_BU`。本地编译、运行及 SQL 检查通过；未连接 UAT、未执行 Oracle PL/SQL、未发送真实 Email/SMS。

## 业务范围与当前规则

| 操作 | 1216 行为 |
| --- | --- |
| To link（submit / CREATE）最终批准且权限变化 | `HTH_USER_ACCESS_LINKED` |
| Edit 最终批准且权限变化 | `HTH_USER_ACCESS_UPDATED`；覆盖新增/移除账户及只增删 API 服务 |
| 无实际变化、仅数组重排、Maker、中间审批、VALIDATE | 不产生 1216 成功通知 |
| 整组 User Access Delete、公司 HTH 启停 | 本次未接入；原 AC1/AC2 明确 Link/Edit，矩阵 #3 另有公司停用歧义 |

当前实现假设：通知 HTH 用户（API）、最终审批人（AP）、所属公司（COMPANY）的 Email + SMS；相同通道和地址的相同内容只发一次，优先 API → AP → COMPANY。最终审批人是批准记录 `signedBy` 最后一个非空用户，不是所有历史签署人。上述 1216 收件通道、最终审批人、无变化不发和一次 fallback 范围已向用户提出确认，**尚未收到确认答复**；不能把 851 的规则确认当作 1216 已签定。

API 联系方式取经批准身份对应的 CM User Email/Mobile + UserExtensionData 国家码。HTH Profile 和批准快照校验身份，不信任请求内 username/收件地址。公司联系方式取该用户 owner party 的 `getPartyPreferences()`，不是 Associated accessParty 的公司。公司手机支持 `本地号码~国家码` 或完整国际号码；缺失/无法确认国家码就跳过该通道并记角色/通道日志，不猜 `852`。

只比较服务器生效的 ACTIVE 账户与 API 主键集合，忽略行 ID 和排序。邮件沿用 BCO `userNameId`、`userSysDate`、`compName` 语义：短 User ID、香港生效时间、按原 BCO 方法掩码的公司名称。消息不含账号明细、权限清单、密码或 Code。

## 必须成套发布

1216 复用已实现的 851 派送组件，基线需包含 851 的 xface policy、Email/SMS Dispatcher 精确事件分支及批次入口；不能只拷贝一个新 Java 类。

| 模块/配置 | 本次内容 |
| --- | --- |
| `com.ofss.digx.cz.bea.app.xface` | 新 `HthUserAccessNotificationPlan`、`HthUserAccessActivityLogDTO`；保留 851 类 |
| `com.ofss.digx.cz.bea.module.hosttohost` | 新 `HthUserAccessNotification`；`HostToHostUserAccess` 两个最终生效分支前后挂钩 |
| `com.ofss.digx.cz.bea.domain.service.dispatch` | 新 `HthUserAccessNotificationService`；851 Repository/Dispatch 增加独立 1216 表、事件和 DTO 分支 |
| `com.ofss.digx.cz.bea.scheduler.impl` | 在原 BCO 和 851 批次之后调用 1216；851/1216 独立异常处理 |
| `consulting/config/Preferences.xml` | 新 `HthUserAccessNotification` 类别 DB provider，90 秒同步 |
| `ValidateAndSendBounceNotify` 实际环境 batch | 排除两个 1216 事件，交由有次数限制的专用 fallback；root/UAT/PRD 三份源码均已改 |
| 本目录 SQL | 新 ledger、配置/12 个模板与完整元数据、只读验证 |

应用、Alert/MDB 消费者、Scheduler 必须加载同版 xface 与 dispatch JAR。无需前端、REST API、Login PIN、API Password 或 DSP 的改动；BCO `UserAccountAccessExt` 只作参考，没有修改。普通 BCO 不匹配新事件，仍走原派送路径；851 默认构造、表、事件、HC reference 和开关继续保留。

## SQL 和启用顺序

1. 在 OBDX 配置/资料 schema 执行 `1_HTH_Access_Notification_Ledger.sql` 完整 `DECLARE ... END;` 块。它创建 22 列的 `DIGX_CZ_HTH_ACCESS_NOTIFY`、8 项具名约束及 pending index；DDL 隐式提交。重跑不删流水；已有表结构异常须先核对，脚本不会擅自改表。
2. 执行 `2_HTH_Access_Notification_Config.sql` 完整 `BEGIN ... END;` 块，再单独 `COMMIT;`。失败回滚本次配置块并抛错；SQL*Plus/SQLcl 执行块后另行输入 `/`。不要逐条运行块内部语句。
3. 执行 `3_Verify_HTH_Access_Notification.sql`：应有 2 个 Activity/Event/Action，12 个 template/recipient（2 操作 × Email/SMS × 3 locale），6 个服务属性、18 个 Email 属性/数据源映射；SMS 无变量。检查共享 generic attr 的 String 类型和有效状态，脚本只补缺失项，不覆盖现有 BCO 定义。
4. 确认业务 DIGX ORM、配置库、`dispatchDataSource`（默认 `NONXA`）使用一致 schema/权限。DIGX 在同一现有业务事务写 ledger；派送数据源必须能独立提交短事务，读写 ledger/MNG、读公司邮箱及 CCBEMAIL 负回执。这里不修改原权限业务或 BCO 的数据源。
5. 确认 MNG `REFNUMBER` 与 CCBEMAIL `SRC_SYS_REF_NUM` 能完整保存 34 字符 `HA` + ledger ID。应用、批次、模板配置成套发布，按环境流程刷新 Event/Template/Preferences 缓存或重启相关应用。
6. 确认现有 Scheduler、Alert/MDB 和 CCBEMAIL 回执导入任务运行，核对通知矩阵和正式文案后启用，在 UAT 执行下面的场景。

首次 flag 为 `false`，重跑保留当前值，不会关闭已启用功能或重发历史记录。模板 SQL 重置 **1216 自己的**版本文案，手工改过的 UAT 模板需先回存源码。EN 短信按矩阵 #4/#5 原文，TC/SC 与中英邮件提供可运行文案，仍需业务签定；模板完整不等于文案已批准。

```sql
UPDATE DIGX_FW_CONFIG_ALL_B
SET PROP_VALUE='true', LAST_UPDATED_DATE=SYSDATE, LAST_UPDATED_BY=USER
WHERE CATEGORY_ID='HthUserAccessNotification'
  AND PROP_ID='HTH_USER_ACCESS_NOTIFICATION_ENABLED';
COMMIT;
```

停用改为 `false`，等待配置刷新。开关同时停止新增意图和专用派送；已有记录保留，恢复开关后继续处理，在途 MNG 请求不能撤回。两个通知 Story 开关彼此独立。不要清表作为回退；不要把本次两个事件加入通用 `DIGX_CZ_BATCH_HIGH_ALERT_EVENT_LIST`。

## 事务、去重与补发

权限保存前读取实际 grants，成功保存后比较，通知行经当前 DIGX ORM 事务写入。业务回滚时通知意图随之回滚，后台只读取已提交行。新表只存派送上下文，不是另一套权限/用户/密码表。

唯一 ID 包含业务单位、批准 reference、用户 owner/closeId、accessParty/linkage、变更类型、通道和目的地址。记录持久化联系方式快照，后台不把原地址替换成后来更新的用户联系方式。重入/并发由 PK、publication claim 和 dispatch claim 控制；5 分钟未消费的 publication 可重新注册，但同一派送只允许一次 MNG 提交。

MNG 调用前先独立提交 `SENDING` 和 MNG 行；提交行失败就不外发。使用原 BCO request builder/adapter：Email messageType 6，SMS messageType 1，appID 89。MNG `MESSAGEBODY` 保存正文 SHA-256 的 Base64，`ORG_REF_NO` 保存批准 reference。

| 状态 | 含义 |
| --- | --- |
| READY | 随业务提交，等待消费 |
| PUBLISHING / PUBLISHED | 正在注册或等待 Alert 消费；超时可重新注册 |
| SENDING | MNG 记录已提交，请求可能在途；15 分钟无结果转 UNKNOWN |
| SUBMITTED | 网关接受，不等于最终送达 |
| UNKNOWN | 超时/空响应/提交中断，按 HA reference 对账，不自动重发或补发 |
| REJECTED / DELIVERY_FAILED | 明确网关拒绝 / CCBEMAIL 负回执 |
| FALLBACK_QUEUED | 已产生唯一补发子记录 |
| FAILED_FINAL | 不适用补发、无可用地址、公司已在原收件中，或补发失败 |

仅 API EMAIL 且无 parent 的明确失败，才查当前 owner 的 `DIGX_PI_PARTY_PREFERENCES.OFFICE_EMAIL`。不同有效邮箱最多补发一次；若公司邮箱已在本次原通知收件记录内，不重复发相同内容，原公司记录继续按其状态处理（结果不明时先对账）。AP/COMPANY/SMS/fallback 自身失败不级联。`PARENT_ID` 唯一约束与行锁防重复补发；负回执用完整 MNG reference 关联，不按邮箱/时间猜测。

日志仅新增通知 ID、阶段、状态或异常类型；无密码、Code、账户号、收件地址/正文。数据库 ledger/MNG 为受控派送数据，原有业务日志不在本次重构范围。

## 本地验证与 UAT

项目根目录设置 `JAVA_HOME` 后：

```sh
python3 devtools/backend-compile/tests/verify_hth_contact_compile.py
python3 devtools/backend-compile/tests/verify_hth_access_sql.py
python3 devtools/backend-compile/tests/verify_hth_contact_runtime.py
```

需要已有 backend 编译产物、项目 JAR 及 H2 1.4.200（默认 `/tmp/hth-h2-1.4.200.jar`，可用 `HTH_TEST_H2_JAR` 指定）。测试使用生产 helper/DTO/repository/dispatcher、真实 EclipseLink/OBDX ORM wrapper、真实 OBDX metadata getter 和 BCO MNG request builder。用户/审批/权限仓库、配置、JNDI、Event 注册与网络为 fixture；SQL DML 用 SQLite 模拟重跑/回滚，ledger DDL 用 H2 Oracle 模式，不能代替 Oracle 验收。

本地覆盖最终审批/验证/非 HTH/非目标 BU/关闭开关，Link/Edit/API-only/移除/无变化/重排，Related/Associated owner，角色去重、缺地址不猜国家码，ORM 提交/回滚和重入，并发 publication/dispatch claim，MNG-before-IO、拒绝/超时/空响应/中断、mock/占位符阻断、负回执及一次补发终止。实际发布 DTO 经 OBDX metadata 取值后替换全部 12 个 SQL 模板，验证无残留占位符。851 原有测试同轮回归通过。

UAT 必须验证：

- To link/Edit 完整多级审批：最终批准前 ledger 为零，最终生效后数量与去重规则相符；同值编辑无新消息。
- Related/Associated、只增删服务、只增删账户；批准快照 serviceId/status/signedBy 符合当前平台；权限与 ledger 确实一起提交/回滚。
- 三方不同/相同/缺失联系方式、三种 locale；Email 和 SMS 分别核对真实收件及回执。
- 多节点、回调重入、批次重启、JMS 暂停恢复；HA reference 不截断；超时 UNKNOWN 不直接改 READY。
- API Email 确认失败，公司原本已收件不重复补发；公司邮箱发生合法变更时新地址最多补发一次，补发失败终止。
- 1216 开关开/关、851 独立开/关，普通 BCO 权限维护、eAdvice、通知/退信次数和批次时长回归。

本地测试不能证明真实 WebLogic/JTA/MDB、Oracle、网关送达和正式模板已通过验收。
