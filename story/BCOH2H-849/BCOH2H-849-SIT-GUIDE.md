# BCOH2H-849 — SIT 验收指引

本指引供 SIT 同事在已获授权的环境验证 **HTH CM/BM 操作是否进入 `HTH_BEA.HTH_CRM_EVENT_DETAILS`**。它是 [849 完整技术设计](BCOH2H-849-TECH-DESIGN.md) 的执行附件。849 验的是业务事件入库；CSV、SFTP、MTB 接收和 `HTH_CRM_EXTRACT_RUN` 属于 1293，不能用文件是否产生判定 849。

## 1. 开始前请确认

1. 记录环境、应用版本/commit、实例名、测试时间（HKT）、公司 partyId、目标用户和业务/审批参考号。使用 SIT 测试账号与测试数据；证据中遮盖个人联系方式。不要把密码、Code、密文、hash 或密钥放进截图、SQL 输出、工单或日志附件。
2. DBA 用 **HTH_BEA 连接**执行 `consulting/db/branch_change_history/20260923_HTH_MTB_849/1_HTH_CRM_849_Schema.sql`、`3_HTH_CRM_849_HTH_Verify.sql`；用 **DIGX 配置连接**执行同目录的 `2_HTH_CRM_849_DIGX_Config.sql`、`4_HTH_CRM_849_DIGX_Verify.sql`。脚本 1 已含注释；脚本 5 只用于单独补注释。两套连接不能混用。具体可重跑条件和 Verify 输出解释以同目录 README.md 为准。
3. 确认同批部署了 hosttohost、common、approval、sms 的编译产物，以及 `config_cz/Preferences.xml` 和 HTH CRM ORM XML，并重启所有相关实例。只更新 SQL 或单个 Java 包不能证明运行时已经采用新配置。
4. DIGX Verify 中最终配置组应为 `HTHCRMConfiguration`，确认对应环境/实体实际读取的 `ENABLED=Y`，没有冲突的旧配置组或 Factory。新安装默认 N。检查应用日志没有 `Found incorrect preference setup for HTHCRMConfiguration`。数据库有 Y 仍不等于应用成功加载；必须看新操作的日志。
5. 由运行时 **NONXA datasource 账号**确认对 `HTH_BEA.HTH_CRM_EVENT_DETAILS` 有 SELECT、INSERT 权限；DBA 部署账号能查询不能替代这项检查。`3_HTH_CRM_849_HTH_Verify.sql` 的表、113 列、注释、主键和索引检查应 PASS。

如果环境已经完成上述部署，SIT 不必为了每笔测试重跑安装 SQL，只需保存 Verify 结果并做新的真实操作。旧操作不会自动补录，历史行的新增字段为空不代表本次部署失败。

## 2. 建议测试顺序

先做一笔 **HTH User Access Submit → 最终 Approve**，因为这条链能同时检验提交、审批和生效记录。随后按实际可用业务覆盖：

| 场景 | 预期检查点 |
| --- | --- |
| BM 公司 Enable / Edit / Disable | 分别查 `COMPANY_ENABLE`、`COMPANY_EDIT`、`COMPANY_DISABLE`；核对 Submit、审批及 Apply 的实际阶段 |
| CM HTH 用户 Create / Edit | 查 `USER_CREATE`、`USER_EDIT`；目标用户与操作人字段应各归其位 |
| HTH Account & Service Access Create / Edit / Delete | 查 `ACCESS_CREATE`、`ACCESS_EDIT`、`ACCESS_DELETE`；一次操作按阶段记录，不按账户或服务数量拆行 |
| API Password Code Generate / 再生成及审批激活 | 查 `CODE_GENERATE`；用 `PHASE` 分辨 Generate/Apply，不应看到 Code 明文 |
| API Password Setup / Reset | 查 `PASSWORD_SETUP`、`PASSWORD_RESET`，`PHASE=EXECUTE`；不应看到密码或 hash |
| 审批拒绝、业务失败、事务回滚 | 核对审批动作与业务结果的区别；成功执行 Reject 动作可记录 `APPROVAL_REJECT` 且状态 A，不能解释为业务获批 |
| 普通 BCO 同类操作 | 不应写入 HTH CRM 表；BCO 原有 CRM 流程照常工作 |

**不要固定要求每笔都有同样数量的行。** 有些操作不走审批，有些阶段来自不同入口。先记下操作实际经历的阶段，再检查相应事件。查询、Reveal、通知及提醒不因 849 额外写一条 HTH CRM 事件。

## 3. 用 SQL 查本次操作

先运行 `3_HTH_CRM_849_HTH_Verify.sql` 中的近期事件和 User Access 查询。下面的查询便于按本次测试窗口缩小范围；把两个时间替换成**香港时间**，有业务参考号时再按 `SOURCE_TRX_REF_NBR` 精确过滤。`CREATED_AT` 是保存时间，`EVENT_DTE/EVENT_TIME` 是业务发生时间。不要只凭“今天”或账号名称判断是哪笔操作。

```sql
SELECT EVENT_ID, CREATED_AT, EVENT_DTE, EVENT_TIME,
       SOURCE_SYSTEM, CHANNEL_TYPE, ACTIVITY_KEY, PHASE,
       EVENT_STATUS_CODE, USER_ID, TARGET_USER_ID,
       SOURCE_TRX_REF_NBR, ERROR_CODE, ERR_CODE
FROM HTH_BEA.HTH_CRM_EVENT_DETAILS
WHERE CREATED_AT >= TO_TIMESTAMP('2026-10-06 21:30:00', 'YYYY-MM-DD HH24:MI:SS')
  AND CREATED_AT <  TO_TIMESTAMP('2026-10-06 21:45:00', 'YYYY-MM-DD HH24:MI:SS')
  -- AND SOURCE_TRX_REF_NBR = '本次业务参考号'
ORDER BY CREATED_AT DESC, EVENT_ID DESC;
```

记录实际 `EVENT_ID`、`ACTIVITY_KEY`、`PHASE`、`EVENT_STATUS_CODE`，再核对 `SOURCE_SYSTEM=HTH`、`CHANNEL_TYPE=CM/BM`、目标用户及适用的活动外部码 `EVENT_ACTV_TYPE_CODE`。若参考号在该场景为空，可改用测试时间、活动和目标用户缩小范围，但不要据此假定所有来源都填同一引用列。历史数据可能没有 `CREATED_AT`；本次新操作应有。

配置开关的数据库初查可用下列 SQL，但最终以应用实际加载和新事件为准。实体覆盖配置也要看 `4_HTH_CRM_849_DIGX_Verify.sql` 的明细。

```sql
SELECT PREFERENCE_NAME, PROP_ID, PROP_VALUE, DETERMINANT_VALUE
FROM DIGX_FW_CONFIG_ALL_O
WHERE PREFERENCE_NAME = 'HTHCRMConfiguration'
  AND (PROP_ID = 'ENABLED' OR PROP_ID LIKE 'ACTIVITY_%')
ORDER BY DETERMINANT_VALUE, PROP_ID;
```

## 4. 表里没新数据时怎么定位

在**处理该请求的应用实例日志**中按测试时间和 `HTH_CRM stage=` 搜索；`/setup` 等 URL 可能不会出现在同一份日志，优先用阶段日志。按顺序记录：

| 看到的阶段 | SIT 下一步 |
| --- | --- |
| 没有任何 `HTH_CRM stage=` | 先确认请求到达的实例、部署包版本、相关入口是否在白名单、目标是否确为 HTH、审批是否完成；不要直接归因于数据库 |
| `DISABLED` | 核对 `HTHCRMConfiguration`、determinant/实体覆盖、Preferences.xml、Factory 和重启后的配置缓存 |
| `COLLECT` 后 `WAITING_FOR_JTA` | 等业务事务完成；继续找相同 eventId 的 `WRITE` 或失败阶段 |
| `LOCAL_TX_PENDING` / `TX_OUTCOME_UNKNOWN` / `WRITE_TX_ACTIVE` | 本次不保证写入；记录 eventId 和时间交开发排查事务边界，不要手工补一条假 CRM 事件 |
| `COLLECT_FAILED` / `APPROVAL_CAPTURE_FAILED` | 记录异常**类型**、活动、阶段及实例；交开发查采集/适配入口 |
| `WRITE_FAILED` / `ROLLBACK_FAILED` / `CLOSE_FAILED` | 交 DBA/开发核对 NONXA datasource 指向、账号权限、表结构/ORM 版本、约束及连接状态 |
| `WRITE` 但查询不到 | 用该日志的 eventId 在实际 datasource 指向的 HTH 库查；核对是否查错 schema、环境、时间区间或实例 |

日志 `WRITE` 表示 HTH 独立事务提交；若只是 `COLLECT`，不能报告为“已入库”。CRM 保存失败不会自动重试或补录，也不应为了造出 CRM 行重复执行真实业务操作。

## 5. 交付证据及验收口径

每个场景保留：操作时间和业务/审批参考号、应用版本与实例、审批结果、对应 `HTH_CRM stage=` 日志中的 eventId、脱敏 SQL 结果、与预期阶段的比较。失败时附首个异常类型和对应 datasource/配置 Verify 结果。SIT 缺权限查日志或库时，给 DBA/开发上述定位信息即可，不要请求或分享凭据。

通过条件是**真实 HTH 操作在正确阶段产生可关联的新行，适用字段和外部活动码正确，普通 BCO 操作不误写 HTH**。Verify 全 PASS 只表示结构和配置合格，不等于业务链路通过。原 849 AC2 写的是异步，当前实现是同步；SIT 可按已确认的同步方案验证实际行为，但不能把 AC2 原文标成已满足，需 BA/TL 在 Jira 确认变更或偏离。
