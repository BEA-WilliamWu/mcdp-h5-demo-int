# BCOH2H-849 MTB - CM：代码分析与技术设计建议

> 2026-09-23：用户已确认本期同步保存。实施以 [最终技术设计](BCOH2H-849-TECH-DESIGN.md) 为准；下文早期异步建议及待确认状态仅保留作历史分析。

> 2026-09-22 更新：范围及业务确认以 [MVP1 对照中的用户确认结果](BCOH2H-849-MVP1-BCO-COMPARISON.md#用户确认结果2026-09-22-更新) 为准。本文早期问题不代表仍需逐项询问 BA；同步/异步尚待确认。


日期：2026-09-22。状态：评审稿；未实施。依据当前源码及本次四份附件，未连接 UAT 数据库或确认运行时配置。

## 1. 结论和本次范围

849 的 AC1 是保存规定的交易数据供后续抽取；AC2 是异步写入数据库。本次先完成“采集、映射、异步保存”，CSV、Control-M、SFTP、MTB 下游建表属于后续交付。数据库仍要按最终字段映射设计，不能等做 CSV 时再发现必需数据没保存。

推荐沿用 BCO 的 DTO → 按业务映射 → Assembler → Domain Entity → Repository/ORM 模式。HTH 新增业务代码放在 HTH 模块；不要为了 849 改写 BCO 的 CRMAsserter、原 CRM 表及现有 batch。

**范围需先分清：**标题是 CM，story 指定 `HTH_CRM_MAPPING_BCO`。spike 明确分两路：

| 路径 | spike 的要求 | 本次建议 |
|---|---|---|
| BCO/CM create、reset、change password | 用现有 BCO 后端记录到 DIGX_CZ_CRM_EVENT3_DETAILS，沿用 BCO 文件 | 先核对缺失的 BCO mapping，确定是否包括 HTH API Password setup/reset；不能直接把 login PIN 和 API Password 当同一事件 |
| HTH API 各 use case | 新表，类似 BCO 并增加 HTH 字段；后续独立文件 | 如这些 API 属于 849，再新增独立 HTH 采集/异步保存链路 |

因此“所有 849 数据一律写新表”或“一律写 BCO 表”目前都不能定为最终设计。下文的新表建议适用于 spike 的 HTH API 路径；若 849 仅负责 CM，实施范围应收窄到 mapping 中的 CM 操作。

## 2. BCO 现在怎么实现

### 2.1 表结构

主表是 `DIGX_CZ_CRM_EVENT3_DETAILS`。它是交易事件明细宽表，不是邮件通知历史表，也不是前端 Audit Log 表。ORM 的 embedded key 对应 EVENT_ID；通常每个事件保存一条记录，个别交易 evaluator 会拆成多条（例如借贷两侧）。

| 字段组 | 已有字段例子 | 用途 |
|---|---|---|
| 标识及时间 | EVENT_ID、EVENT_DTE、EVENT_TIME | 唯一事件、发生日期和时间 |
| 渠道及结果 | CHNL_ID、CHNL_TYPE_CODE、EVENT_STATUS_CODE、EVENT_ACTV_TYPE_CODE、FIN_IND | 渠道、业务活动、成功失败、是否金融交易 |
| 用户及账户 | USER_ID、ACCT_NBR、DEBIT_ACCT_NBR、ELECT_ADD | 用户/账户/联系方式；注意 ACCT_NBR 在部分 evaluator 中取公司 Party Code，不能凭名称认定是结算账户 |
| 金额和币种 | EVENT_AMT、FEE_CHRG_AMT、EVENT_AMT_HKE、EVENT_EX_RATE、ACCT_AMT、各币种字段 | Java 使用 BigDecimal 的金额/汇率 |
| 关联及业务扩展 | REF_NBR、SOURCE_TRX_REF_NBR、EVENT_REM、FPS/EDDA/支付字段 | 交易关联和不同业务字段 |
| 设备/安全上下文 | IP_ADDRESS、DEVICE_ID、USER_ROLE、AUTH_METHOD 等 | 交易发生时的上下文 |
| 批处理控制 | BATCH_PROCESSED_DATE | 抽取控制，出现在 batch SQL 中 |

当前 ORM 有 **99 个 column 映射**，不等于真实数据库只有 99 列。batch 还使用 Country_Name、Region、PARTY_INT_NBR 等 ORM 未映射字段。未找到完整 CREATE TABLE，真实长度、精度、默认值、索引及约束要以数据库 DDL 为准。batch 的 ColumnDetails 长度是文件输出长度，不能当数据库字段长度。

BCO EVENT_ID 由 `CDC + yyyyMMdd + crm_sequence.NEXTVAL` 组成。新 HTH 标识需单独确定；不要擅自占用 BCO 的活动编码或复用其前缀。

### 2.2 保存链路

```text
业务返回/失败
  → CRMAsserter.doResponseAssertion / doFailureAssertion
  → sendCRMData
  → 检查 CRM_GLOBAL_FLAG、CRM_ALLOWED_TASK_CODES、认证/审批状态
  → fetchCommonInfo（用户、渠道、时间、IP、结果等）
  → CRM_EVALUATOR_<taskCode> 对应的 evaluator 补业务字段
  → CRMRequestAssembler
  → CRMEvent3DomainDTO
  → CRMLocalRepository.create
  → CRMLocalAdapter → super.create → ORM 写表
```

配置组是 `CRMConfiguration`，包括允许 task 列表、各 task 的 evaluator、活动编码。BCO 的做法是同一保存框架配不同业务 mapper，并不是每个 API 单独写一段 INSERT。

`AccountDetailsCRMEvaluator` 可作简单查询业务例子，但它补的字段很少，不能认为复制它就满足 61 列映射。

### 2.3 异步：已确认和未确认的区别

当前可见的 `CRMAsserter → Repository` 调用是直接调用，没有在此处提交线程池或 JMS。`CRMAsserterCallAdapter` 也直接转调。补充检查本地 OBDX appcore JAR：AbstractSecureApplication.checkResponsePolicy 直接调用 AbstractAsserter.assertResponsePolicy，后者直接调用 doResponseAssertion，未进行异步分派。因此本地标准调用链的 CRM 写库在调用线程执行；若上游业务本身由异步 worker 执行，CRM 随该 worker 执行。此结论基于本地 JAR，UAT 版本仍需一致性核对。

BCO 其他功能存在异步参考：`CZApprovalExecutorService` 用固定线程池提交 `CZApprovalWorker`；worker 自己打开数据库会话/事务并设置所需线程上下文。注释中的 ManagedExecutor lookup 已被注释，实际用的是 JDK executor。这属于审批实现，不是已存在的 MTB 异步服务。

可参考其“请求线程先取上下文、worker 独立事务”原则；不要让 MTB 共用审批线程池，也不要把整个请求、Session 或密码 DTO 交给后台线程。

### 2.4 后续抽取参考

`GenTxnLog2CRM` 会先将待处理记录标为一个约定日期（1990-01-01 11:11:11），按该状态抽取，再更新 BATCH_PROCESSED_DATE；条件还包含活动编码非空及 user_id 非 anonymous。不是简单按 EVENT_DTE 查当天。

后续 HTH 可参考这种抽取分层，但不应现在复制其全表标记更新逻辑。异步入库会出现迟到记录，未来需固定抽取集合/批次，避免导出过程中新增记录被误标已处理。

## 3. HTH 数据表建议（待 mapping 定稿）

HTH API 路径拟用 `HTH_MTB_EVENT_DETAILS`（建议名称，不是已建表）。按最终映射设计显式列，不把原始请求/响应 JSON 当成唯一数据存储。

- 通用业务列：复用 BCO 同名字段及相同业务语义，实际类型/长度先核对 DDL。
- HTH 扩展列：sample 中的 API_URL、MESSAGE_ID、NO_FINANCIAL_TRANSACTIONS、TRANSACTION_STATUS、REPORT_TYPE。语义、必填、枚举、长度仍须 mapping 确认；NO_FINANCIAL_TRANSACTIONS 看似数量但不能凭名称定值规则。
- 技术控制列建议：独立 EVENT_ID 主键、SOURCE_SYSTEM、SOURCE_EVENT_ID、SCHEMA_VERSION、CREATED_AT；如采用多明细模型增加 DETAIL_SEQUENCE。
- 事件时间取 API 实际执行时间，CREATED_AT 取落库时间，明确香港时间/时区转换，不能用异步执行时间代替事件时间。
- EVENT_STATUS_CODE 和 TRANSACTION_STATUS 同时存在，必须分别映射；不能随意把 HTTP 200 或 result=SUCCESSFUL 当业务成功。
- API_URL 仅保存规范化路径，不保存敏感 query/token。密码、Code、私钥、Authorization 不采集。
- 金额用 NUMBER/BigDecimal，不用浮点；用户 ID/账户是字符串，保留前导零。
- 主键用于技术唯一性；重复投递去重另用稳定来源事件键，例如 `(SOURCE_SYSTEM, SOURCE_EVENT_ID, EVENT_ACTV_TYPE_CODE, DETAIL_SEQUENCE)`。消息重试保留同一来源事件键，不能每次重新生成。
- 不直接对 MESSAGE_ID 单列加 UNIQUE：需确认重试、分页、一请求多事件的基数。新的一次用户调用和同一事件的重复投递是两件事。
- 索引以事件时间、抽取状态及来源关联查询为主，依据流量/保留期定最终组合；不预先给每个业务列建索引。

spike 的文字提到 dedicated table for each use case，但图示是多个 HTH UC 汇入一个 HTH MTB Table。结合 BCO 宽表方式，建议一张 HTH 事件明细表供多个兼容 UC 使用；若 mapping 存在一对多交易明细，可加子表。表数需要评审确认，不能按 sample 数量推定每个 API 一张表。

## 4. 异步落库方案

### 推荐：HTH 独立持久化消息队列 + 消费者

```text
业务执行完成，得到真实结果
  → HTH 专用 hook/mapper 生成不可变、白名单字段快照
  → 发布持久化事件到 HTH MTB 独立队列
  → API 继续返回（不等待最终明细表 INSERT）
  → 消费者开启独立事务
  → 校验版本及必填字段 → Assembler → HTH Repository/ORM
  → 提交成功后确认消息；失败回滚并重投
  → 超过重试次数进入错误队列，报警、修复后重放
```

这是**新增建议**，当前并没有查到可直接调用的 HTH MTB queue/listener。要确认 WebLogic/JMS 部署支持、持久化存储、消费者事务方式和运维负责人后才能定稿。可以沿用平台部署方式，但不借用 Audit/Notification 队列发送 MTB payload。

为什么不直接照搬固定线程池：进程重启或部署时，内存排队事件会丢失；仅打印异常也无法补发。AC1 要完整保存，建议至少用可重试、可恢复的持久化通道。

### 事务和失败处理必须定清楚

1. 消费者写库提交后才确认消息。若提交后确认前崩溃，消息会重投，依靠唯一键识别同一事件；只处理该去重键冲突，不能吞掉所有数据库异常。
2. 业务成功事件须对应业务真实提交结果。不能业务未提交就异步记成成功。若业务事务和 JMS 不能原子提交，可用同事务 outbox 再异步投递，但会增加一张 outbox 和后台 dispatcher，需单独评审。
3. API 查询/业务失败也可能需要记录；不能让主事务回滚把失败记录一起回滚，失败事件的发布边界须单独处理。
4. “业务完全不等待、任意故障绝不丢记录、不增加持久化步骤”不能同时保证。发布持久化消息仍有短暂耗时；要用压力测试验证影响。
5. JMS 不可用时，是让 API 失败、还是持久化待补发，必须明确。仅忽略发布失败并继续返回成功不能称为完整满足 AC1。
6. consumer 不依赖请求线程的 ThreadLocal。主线程提取租户、用户、渠道、业务结果等必要上下文；后台使用自己的连接和事务，结束后清理线程上下文。

如平台暂时无法提供 JMS，可评审“容器管理的独立有界 executor”作为较小方案，但它仍需持久化恢复机制才能保证不丢。不要直接用 new Thread，也不要为队列满设计 CallerRunsPolicy 把慢 INSERT 转回 API 请求线程。

## 5. 拟修改位置及 BCO 影响

| 内容 | 拟实施 | 对现有 BCO 的影响 |
|---|---|---|
| 业务采集入口 | 在最终确认的 HTH/CM 操作完成处加小 hook | 只允许 mapping 指定的操作；若共享入口，显式 HTH 条件 |
| 事件 DTO/Mapper | 新增 HthMtbEventDTO、HthMtbEventMapper，按用例分映射 | 参考 CRMInputData 模式，不往公共 DTO 塞无关字段 |
| 异步层 | 新增 HthMtbPublisher、HthMtbConsumer/持久化配置 | 独立队列、并发和连接预算；评估共享服务器资源 |
| 存储层 | 新 Domain/Key/Assembler/Repository/LocalAdapter/ORM | 沿用现有项目分层；新 HTH 表，不修改 BCO 表 |
| CM → BCO 表路径 | 只有 mapping 明确要求时才增加对应映射/受控调用 | 不复写既有 BCO task 配置；异步入口须避免双写 |
| 配置 SQL | 新对象/索引/专用配置，重复执行校验结构 | 不清空公共配置，不改原任务允许列表整串 |
| batch/前端/通知 | 本次不实施 | 保持现状 |

以上类名是建议，未新增生产类。当前 hosttohost 模块主要可见管理、用户授权、证书和 API Password 服务，尚未定位到 sample 对应的 HTH 余额查询执行入口。因此需确定 API 实际在哪个应用运行；若在 DSP/API 应用，单改 CM 模块不能自动捕获其调用。

## 6. 现有实现不能原样复制的点

- `crmInsertData` 在 IP 为 null 时跳过保存：HTH 服务调用若没有浏览器 IP 不能因此静默丢记录，应按 mapping 明确来源/允许为空。
- `fetchCommonInfo` 依赖用户 Subject、SessionContext 和多个 ThreadAttribute，还读取浏览器信息；不能在后台空线程直接调用。
- 原代码部分异常只打印后继续：HTH consumer 必须失败回滚并触发重试。
- 原 EVENT_ID 生成依赖当前 ORM session 和共享 sequence：HTH 需明确生成时机及格式，重投不重生成。
- 部分现有日志打印整个 DTO/上下文：HTH 仅记录 eventId、阶段、结果/异常类型、耗时和重试次数，避免敏感交易信息外泄。

## 7. 验收测试

| 场景 | 验证 |
|---|---|
| 每个 mapping 用例成功/失败 | 行数、活动码、状态、字段逐列对应；业务失败不能漏记 |
| 业务回滚 | 不产生已成功提交的假记录；失败事件按规则保留 |
| API 响应 | 不等待消费者落库；对比启停采集的 P95/P99 延迟 |
| DB 暂停后恢复 | 消息保留、重试、恢复后写入 |
| 进程重启/重新部署 | 已接收事件可恢复，不静默丢失 |
| 重复投递/提交后断连 | 同一来源事件不重复，不吞其他约束错误 |
| 高并发/队列满 | 资源有界、不挤占审批处理；有报警和可操作恢复方式 |
| 跨日及延迟消费 | 事件时间保留，未来抽取能覆盖迟到数据 |
| BCO 回归 | 现有 CRM 表记录、审批、通知及 batch 保持原流程 |

## 8. 开发前需要补齐的内容

1. `API_MTB_latest.xlsx` 的 `HTH_CRM_MAPPING_BCO` 及相关 HTH 页：确定这次具体用例、字段来源、活动码、必填/长度、成功失败和一对多规则。
2. UAT BCO 表实际 DDL（可用下方只读 SQL）：确认类型、默认值和索引，避免拿文件长度作字段长度。
3. HTH 交易 API 的实际执行应用/类和事件来源标识；明确 CM 和 DSP 的实施分工。
4. 异步基础设施及发布失败策略：是否允许独立持久化 JMS；事务一致性是否需 outbox。

目前可确定架构方向，但缺 mapping 时不应交付号称覆盖所有字段的最终建表 SQL。

## 9. 数据库只读核对 SQL

将 OWNER 换为表实际所属 schema；可先查 ALL_TABLES。

```sql
SELECT OWNER, TABLE_NAME
FROM ALL_TABLES
WHERE TABLE_NAME = 'DIGX_CZ_CRM_EVENT3_DETAILS';

SELECT COLUMN_ID, COLUMN_NAME, DATA_TYPE, DATA_LENGTH,
       CHAR_LENGTH, CHAR_USED, DATA_PRECISION, DATA_SCALE,
       NULLABLE, DATA_DEFAULT
FROM ALL_TAB_COLUMNS
WHERE OWNER = UPPER(:owner)
  AND TABLE_NAME = 'DIGX_CZ_CRM_EVENT3_DETAILS'
ORDER BY COLUMN_ID;

SELECT DBMS_METADATA.GET_DDL('TABLE', 'DIGX_CZ_CRM_EVENT3_DETAILS', UPPER(:owner))
FROM DUAL;

SELECT I.INDEX_NAME, I.UNIQUENESS, C.COLUMN_POSITION, C.COLUMN_NAME
FROM ALL_INDEXES I
JOIN ALL_IND_COLUMNS C
  ON C.INDEX_OWNER = I.OWNER AND C.INDEX_NAME = I.INDEX_NAME
WHERE I.TABLE_OWNER = UPPER(:owner)
  AND I.TABLE_NAME = 'DIGX_CZ_CRM_EVENT3_DETAILS'
ORDER BY I.INDEX_NAME, C.COLUMN_POSITION;

SELECT PROP_ID, PROP_VALUE, DETERMINANT_VALUE
FROM DIGX_FW_CONFIG_ALL_O
WHERE PREFERENCE_NAME = 'CRMConfiguration'
ORDER BY PROP_ID, DETERMINANT_VALUE;
```

DBMS_METADATA 可能需要相应权限；无需执行任何写入 SQL。

## 10. 附件对照

- 主 story：AC1/AC2 和 `HTH_CRM_MAPPING_BCO` 引用。
- spike：BCO/HTH 两路入库和后续独立导出；没有给出详细异步实现。
- CSV：1 条文件头、1 行 61 列列名、10 行明细、1 条文件尾。属于 sample，不是完整数据字典。
- Excel：只有 Sheet1，55 行，规定文件头/尾、字符编码、换行及两种明细格式，没有 61 列业务字段映射。表头/尾补空格不应提前存进业务库。

sample 61 列中有 49 列名称可在当前 BCO ORM 中找到；另有 7 列在 BCO batch SELECT 中可见但不在该 ORM；余下 5 列是 API_URL、Message_ID、No_Financial_Transactions、Transaction_Status、Report_Type。这个对比仅说明源码覆盖，不能证明 UAT 表已具备或缺少某一列。

### sample 的字段清单（保持原顺序）

1. `record_type`
2. `Filler_01`
3. `Event_Id`
4. `Event_Dte`
5. `Event_Time`
6. `Chnl_Id`
7. `Chnl_Type_Code`
8. `Event_Status_Code`
9. `Cr_Dr_Ind`
10. `Fee_Chrg_Code`
11. `Event_Actv_Type_Code`
12. `Fin_Ind`
13. `User_Id`
14. `Event_Country_Code`
15. `Acct_Nbr`
16. `Elect_Add`
17. `FPS_ID`
18. `FPS_Acct_Nbr`
19. `FPS_Req_Result`
20. `Debit_Acct_Nbr`
21. `Event_Ccy_Code`
22. `Event_Amt`
23. `Fee_Chrg_Amt`
24. `Fee_Ccy_Code`
25. `Trf_Dte`
26. `Trf_Freq`
27. `Proxy_ID_Type`
28. `Proxy_ID`
29. `Payee_Name`
30. `Payee_Bank_Code`
31. `Event_Rem`
32. `Ref_Nbr`
33. `From_Dte`
34. `To_Dte`
35. `Mandate_Id`
36. `eDDA_Maint_Action`
37. `Source_Trx_Ref_Nbr`
38. `Pay_Cat_Purp_Code`
39. `Pay_Purp_Code`
40. `IP_Address`
41. `Event_Amt_Hke`
42. `Event_Ex_Rate`
43. `Mrch_Id`
44. `Acct_Ccy_Code`
45. `Acct_Amt`
46. `Suspicious_Activity`
47. `Country_Name`
48. `Region`
49. `Suspicious_Ind`
50. `Party_Int_Nbr`
51. `Event_Credit_Acct_Type`
52. `Event_Credit_Acct_Nbr`
53. `FPS_Bus_Service_Cd`
54. `Event_Remitter_Name`
55. `Adv_Freq_Type`
56. `Adv_Type`
57. `API_URL`
58. `Message_ID`
59. `No_Financial_Transactions`
60. `Transaction_Status`
61. `Report_Type`

## 11. 代码依据

- [CRMAsserter.java](/Users/devs/CProj/hth-application/consulting/middleware/projects/adapter/com.ofss.digx.cz.bea.adapter.impl/src/com/ofss/digx/cz/bea/app/generic/asserter/impl/CRMAsserter.java)
- [CRMAsserterCallAdapter.java](/Users/devs/CProj/hth-application/consulting/middleware/projects/adapter/com.ofss.digx.cz.bea.adapter.impl/src/com/ofss/digx/cz/bea/app/crm/adapter/impl/CRMAsserterCallAdapter.java)
- [AccountDetailsCRMEvaluator.java](/Users/devs/CProj/hth-application/consulting/middleware/projects/adapter/com.ofss.digx.cz.bea.adapter.impl/src/com/ofss/digx/cz/bea/app/crm/evaluator/impl/AccountDetailsCRMEvaluator.java)
- [CRMRequestAssembler.java](/Users/devs/CProj/hth-application/consulting/middleware/projects/module/com.ofss.digx.cz.bea.module.common/src/com/ofss/digx/cz/bea/domain/common/entity/crm/repository/assembler/CRMRequestAssembler.java)
- [CRMLocalRepository.java](/Users/devs/CProj/hth-application/consulting/middleware/projects/module/com.ofss.digx.cz.bea.module.common/src/com/ofss/digx/cz/bea/domain/common/entity/crm/repository/CRMLocalRepository.java)
- [CRMLocalAdapter.java](/Users/devs/CProj/hth-application/consulting/middleware/projects/module/com.ofss.digx.cz.bea.module.common/src/com/ofss/digx/cz/bea/domain/common/entity/crm/repository/adapter/CRMLocalAdapter.java)
- [CustomerRelationshipManagement_Event3.orm.xml](/Users/devs/CProj/hth-application/consulting/config/orm/eclipselink/mappings/cz/crm/CustomerRelationshipManagement_Event3.orm.xml)
- [CustomerRelationshipManagement_Event3.orm.xml](/Users/devs/CProj/hth-application/consulting/dist/config_cz/orm/eclipselink/mappings/cz/crm/CustomerRelationshipManagement_Event3.orm.xml)
- [CRMConstants.java](/Users/devs/CProj/hth-application/consulting/middleware/projects/common/com.ofss.digx.cz.bea.common/src/com/ofss/digx/cz/bea/common/framework/crm/CRMConstants.java)
- [CZApprovalExecutorService.java](/Users/devs/CProj/hth-application/consulting/middleware/projects/module/com.ofss.digx.cz.bea.module.approval/src/com/ofss/digx/cz/bea/app/approval/service/transaction/CZApprovalExecutorService.java)
- [CZApprovalWorker.java](/Users/devs/CProj/hth-application/consulting/middleware/projects/module/com.ofss.digx.cz.bea.module.approval/src/com/ofss/digx/cz/bea/app/approval/service/transaction/CZApprovalWorker.java)
- [GenTxnLog2CRM.java](/Users/devs/CProj/hth-application/consulting/middleware/batchJobs/outboundbatchprocessor/GenTxnLog2CRM.java)

## 12. 补充：哪些业务进入 CRM，HTH 如何划范围

BCO 源码存在登录/登出、忘记密码/Signer PIN、用户维护、账户授权、账户查询、定存、转账/汇款/FPS/eDDA/缴费、批量支付/工资/收款、审批规则/工作流、文件上传查询、结单、投资、流动性管理等 evaluator。是否实际记录取决于部署的 CRM_ALLOWED_TASK_CODES、CRM_EVALUATOR_<task> 和活动码配置，以及认证/审批状态；存在 evaluator 不等于 UAT 已启用。

本地标准调用链：AbstractSecureApplication.checkResponsePolicy → AbstractAsserter.assertResponsePolicy → CRMAsserter.doResponseAssertion → sendCRMData → Repository。appcore-20.1.0.4.1-SNAPSHOT.jar 字节码显示直接方法调用，没有线程池/消息队列分派。HostUserSession 登出还有显式 callCRMAsserter 调用，其他显式调用包括定存、汇款、批量支付及风险评估。

已有 HTH User Access SQL 将 UAT_N_HUA_NEW/EDT/DEL 加到 CRM_ALLOWED_TASK_CODES，证书上传 SQL 也加入 PP_N_HTH_CERT_UPL。但允许进入与拥有正确 MTB 字段映射不是同一件事，不能据此宣布 849 已覆盖这些操作。

HTH 当前只可确定：spike 提及 CM create/reset/change password；sample 示范 Account Balance Inquiry。API Password setup/reset 是 CM 路径需优先核对的候选，但尚不能确认它与 spike 的 password 指同一业务。用户创建/修改、账户服务授权、Code 生成、证书、公司启停等不可因已有 audit/notification 就自动纳入 MTB；最终按 HTH_CRM_MAPPING_BCO 及 HTH mapping 确认。

按用户偏好优先参考 BCO 直接保存，不把新增 JMS 作为已决定实现。第 4 节属于满足异步/可靠性的备选设计，是否实施须结合 AC2 的确认结果。
