# BCOH2H-849 部署和验证

## 修改范围

HTH 独立同步采集，写 `HTH_BEA.HTH_MTB_EVENT_DETAILS`。不写原 BCO CRM 表，不修改 batch、前端、通知模板或 CRMAsserter。普通 BCO 的原 CRM 记录继续原流程。

实现位于 hosttohost 模块的 `com.ofss.digx.cz.bea.app.hosttohost.crm`：HthCRMAsserter、HthCRMRequestAssembler、HthCRMEvent3DomainDTO、HthCRMLocalRepository 及 Scope、Writer、LocalAdapter，以及跨模块 Adapter 实现与 Factory。common 的 `common/hth` 保留 `IHthCRMAdapter` 接口、`HthCRMInputData` 数据对象和 `HthChannelSupport` 纯判断工具，不含事务、配置读取或数据库实现。

SMS 的 `HthUserCRMScope` 只负责收集调用结果及 HTH 渠道门控，再通过现有 AdapterFactoryConfigurator 调用 HTH 实现；审批 helper 同样走 Adapter。两者不直接依赖 HTH MTB 实现类。普通 BCO 在查找 Adapter 前返回。

既有生产文件仅增加以下采集调用：

- `HthOnboardingAudit.Entry.close`：移除上版新增的 MTB 调用，恢复审计独立性。
- `UserExtensionData.create/update`：独立 SMS scope 捕获操作结果，支持从数据库补充 HTH 渠道；不依赖 audit 是否启用。
- `HostToHostUserAccess`：独立 MTB scope 捕获授权提交/生效结果；既有 audit 调用保留。
- 审批 `Transaction`：两个单笔/worker 执行路径在最终事务更新 commit 后调用 HthCRMApproval。该 helper 对普通 BCO snapshot 在读配置/访问数据库前返回；不修改审批结果或状态。
- `HostToHostManagement`：专用 MTB scope 记录 Enable/Edit/Disable；不增加公司 Audit Log 事件。
- `HostToHostApiPassword`：Code 实际激活成功时采集 APPLY；Setup/Reset/生成操作由独立 MTB scope 采集。

分层为 HthCRMAsserter / HthCRMRequestAssembler / HthCRMEvent3DomainDTO / HthCRMLocalRepository / LocalHthCRMRepositoryAdapter。持久化使用现有 ORM Session 的参数化 SQL，参考 HTH Password repository 的方式；没有额外注册 Entity ORM，也不修改共享 persistence 映射。此为最终实现对设计中拟新增 ORM Entity 的收敛。

## 数据含义

- `SOURCE_SYSTEM=HTH`；`CHANNEL_TYPE` 区分 CM/BM；`ACTIVITY_KEY` 区分业务。
- User ID 是当前操作人，TARGET_USER_ID 是目标，ACCT_NBR 按 BCO 维护业务保存公司标识。
- 授权保留 Related/Associated；一次操作一条，不按账户/API 数量拆行。
- `PHASE=SUBMIT` 只代表提交；`APPROVAL_APPROVE/APPROVAL_REJECT/APPROVAL_ACTION` 是审批动作；`APPLY` 是有效变更；`GENERATE` 是 Code 生成（不代表激活）；`EXECUTE` 是 Setup/Reset 执行。
- `EVENT_STATUS_CODE=A/R` 是该阶段操作结果。审批拒绝动作成功可为 A；不能把它解释成业务获批。业务事务回滚则改 R，并写 BUSINESS_ROLLBACK。
- Code Generate/再生成沿用同一个内部 CODE_GENERATE 活动，Setup/Reset 分开。
- REVEAL、查询和通知没有新增 HTH 活动；保留已有 BCO 处理。
- 只有可靠动作标识才生成 DEDUP_KEY。Setup/Reset 成功用 requestId，Code 激活用 codeId 与业务引用；审批 transactionId 本身不作去重键。
- 外部正式活动码配置键是 `ACTIVITY_<ACTIVITY_KEY>`，分组 `HTHMtbConfiguration`。未给定正式码时列为空，不能声称已满足下游正式编码映射；不会编造 CDC 编码。

BCO 授权对照依据：`account-transactions-mapping.js` 中 USER 用 UAT_N_CA/UA，USERLINKAGE 用 LAT_N_CA/UA；历史 CRM SQL 为 LAT_N_CA/UA/DA 指向 UserAccountAccessCRMEvaluator。该 evaluator 输出公司和当前操作人，不按账户明细生成多条。实际 UAT 对应配置仍用只读脚本核对。

## 部署顺序

1. 同批打包部署 common（接口/DTO、纯判断工具及移除旧审计回调）、SMS、HTH module 和 approval module。重新打包时清理旧 common/mtb、common/audit/HthOnboardingAudit 和 hosttohost/mtb 的 class，避免残留。
2. 先用 HTH_BEA 连接运行 `1_HTH_CRM_849_Schema.sql`，再切换 DIGX 配置连接运行 `2_HTH_CRM_849_DIGX_Config.sql`。首次创建表及索引；已存在时先检查、再增量补列/扩容；ENABLED 配置只在不存在时插入；HTH Adapter 注册按 HTH 专用 key 幂等更新。重跑不会清空数据或覆盖开关。Oracle DDL 自动提交。
3. 确认 NONXA datasource 对 HTH_BEA 新表拥有 SELECT/INSERT 权限；脚本不假设环境的实际 datasource 用户，不授予 PUBLIC。
4. 注册 SQL 与新包就绪后重启应用：AdapterFactoryConfigurator 在初始化时缓存 Factory，新注册不能假设热更新生效。保持默认 `ENABLED=N` 完成部署检查。显式改为 Y、按环境配置缓存机制刷新后再进行 UAT 验证。
5. 分别用 HTH_BEA 连接运行 `3_HTH_CRM_849_HTH_Verify.sql`、DIGX 配置连接运行 `4_HTH_CRM_849_DIGX_Verify.sql`，按时间和业务引用对照数据。回退只需关闭开关，保留表和记录。

## 事务边界及明确限制

在 JTA transaction 存在时注册完成回调，在提交/回滚后立即尝试保存，不新增线程或队列。无活动事务时直接同步保存。Writer 只打开独立 NONXA session；不 suspend/resume、commit 或 rollback 调用方事务。

项目的 resource-local Transaction 接口没有完成回调。若收集时仍存在这种活动事务，当前实现**跳过并记录 LOCAL_TX_PENDING**，避免保存尚未提交的成功。若 UAT 出现此日志，不能算该路径已完成验收；需在该具体事务拥有者的完成位置补接入，而不是放宽判断。部分多审批 worker 的实际事务模式也必须实测。

Oracle/WebLogic afterCompletion、配置缓存、NONXA session 是否保持外层 Session、权限及超时效果均未在本机实测。同步回调不承诺运行于与请求相同的物理线程，取决于事务管理器；没有应用自行异步派发。

SQL 查询超时设为 5 秒，但连接获取/commit 的超时仍取决于数据源。失败记录异常类型，不输出请求、密码、Code、密文、Hash 或异常消息。不重试、不补录。

## 本地验证

```sh
JAVA_HOME=<JDK21> H2_JAR=<local-h2.jar> python3 devtools/backend-compile/tests/verify_hth_849.py
JAVA_HOME=<JDK21> python3 devtools/backend-compile/tests/verify_hth_audit_runtime.py
```

已跑：Java 8 定向编译；49 项映射/事务/SQL参数行为检查；真实 H2 插入、去重约束、独立提交/回滚、表不存在时业务仍能提交；原 791 回归；普通 BCO 审批不读 HTH 配置的测试；新增 Adapter 边界、数据库渠道补充、审计独立性和 Adapter 异常隔离测试；密码 setup/reset 的既有 EclipseLink/H2 事务回归。

MTB H2 测试用代理适配 Session 到 JDBC，Oracle 时间表达式改成 CURRENT_TIMESTAMP；Adapter 配置加载使用测试替身，Factory/实现为生产类。密码回归的解密、DSP 和通知为替身。不等同于真实 Oracle/WebLogic 或 DSP 联调。尚未跑真实 UAT CM/BM 操作、Oracle 重跑脚本和性能测试。开关启用前至少验证：公司启停/编辑、用户创建/修改、Related/Associated 授权、Code 生成及审批激活、Setup/Reset、多级审批/拒绝、故意写库失败，以及普通 BCO 回归。

### 2026-09-23：按 BCO CRM 链路命名及入口隔离

对照链路：`HthCRMAsserter → HthCRMInputData → HthCRMRequestAssembler → HthCRMEvent3DomainDTO → HthCRMLocalRepository`。
`HthCRMInputData` 是跨模块传入的操作数据，Asserter 使用它调用 Assembler 生成 Domain DTO，再通过既有独立事务 Writer/Repository 保存。这里对齐名称和分层职责，不继承 BCO CRMAsserter，也不修改 BCO 表、batch 或事务。

公共判断统一使用 `HthChannelSupport`：渠道 HTH/H2H；用户更新判断旧、新渠道；审批按精确 HTH 服务白名单及用户渠道判断。工具只比较值，不查数据库、不读配置、不加载 HTH Adapter。
SMS/Approval 先判断，再动态查找 Adapter。Approval 不再引用 HostToHost DTO 或维护 HTH 字段映射；这些处理移至 hosttohost 的 `HthCRMApprovalAsserter`。普通 BCO 不查找 HTH 实现；HTH Adapter 查找/调用异常及类加载链接错误捕获后只记阶段和异常类型。

这减少共用模块对 HTH 实现的依赖，但公共接口/数据对象仍需随应用正确打包，不能保证任意混用新旧包。Factory 配置键保留，注册类名已改变，需重跑注册 SQL 并完整打包。HTH 包缺失时，HTH MTB 可能漏记且不会补录，应检查诊断日志。

### CRM 命名与 common/hth 目录整理

849 代码包为 `app.hosttohost.crm`；Adapter、Factory、Scope、Writer、审批入口和测试类统一使用 HthCRM 命名。common 中的 HTH 接口、InputData、渠道判断及 HthOnboardingAudit 统一位于 `com.ofss.digx.cz.bea.common.hth`，原引用同步更新。原 BCO 公共类不移动。

诊断关键字改为 `HTH_CRM stage=`。数据库表 `HTH_MTB_EVENT_DETAILS`、配置分组 `HTHMtbConfiguration` 及 `HTH_MTB_ADAPTER_FACTORY`/`HTH_MTB_ADAPTER` 暂保留既有标识，避免因整理 Java 名称切断既有数据和配置。注册值更新为 `com.ofss.digx.cz.bea.app.hosttohost.crm.HthCRMAdapterFactory`：需在 DIGX 配置连接重新执行 2_HTH_CRM_849_DIGX_Config.sql（保留数据/开关）并重启。同批干净打包 common、SMS、approval、hosttohost 及引用 HthOnboardingAudit 的模块，避免残留旧 class。

## 按 schema 分开执行（2026-09-28）

旧版把 HTH DDL 和 DIGX 配置放在一个块，导致 HTH 账号没有 DIGX 配置表访问权限时整个块编译失败。现已移除旧的两个混合脚本，改为以下四份；不要再执行旧副本。

| 顺序 | 文件 | 执行连接 | 内容 |
| --- | --- | --- | --- |
| 1 | `1_HTH_CRM_849_Schema.sql` | HTH_BEA | 创建/检查 HTH 表和索引，无 DIGX 表引用 |
| 2 | `2_HTH_CRM_849_DIGX_Config.sql` | DIGX 配置 schema（与此前 story 一致） | HTH Factory 注册、默认关闭开关，无 HTH 表访问 |
| 3 | `3_HTH_CRM_849_HTH_Verify.sql` | HTH_BEA | 查表结构、最近 24 小时数据和重复键 |
| 4 | `4_HTH_CRM_849_DIGX_Verify.sql` | DIGX 配置 schema | 查开关、Factory 和只读 BCO 参考配置 |

1、2 各自使用对应的 Oracle 连接，打开整个文件，使用 **Execute SQL Script / Alt+X**；也可选中完整 `DECLARE ... END;`，使用 **Execute SQL Statement / Ctrl+Enter**。交付文件不包含独立 `/`，不要另加。不要只选内部循环或在内部 `END;` 处执行片段。3、4 各 SELECT 分别执行。两个 schema 连接可以分别执行对应文件，不要求任一部署账号同时拥有两边权限。不要仅切换客户端显示的 schema 而继续使用没有权限的登录账号。

重复执行保留现有记录、ENABLED 和活动码，仅更新 HTH Factory 注册值。现有结构中可安全补齐的差异自动处理；类型冲突、约束冲突或配置键重复会明确报错；不会删除或重建已有表。HTH DDL 自动提交，已完成步骤不能回滚；DIGX 配置失败回滚至本文件的 savepoint。两个文件不是一个跨 schema 原子事务，任一步失败应先修复再重跑。

应用运行账号若不是 HTH_BEA，仍须由 DBA 根据实际 NONXA datasource 账号授予该表 SELECT/INSERT 权限；这与部署账号分开执行是两件事。脚本不猜测账号、不授予 PUBLIC。

脚本格式及 schema 引用隔离已静态核对；Java/H2 回归不等于 Oracle PL/SQL 执行，仍须在 UAT 验证权限和已有表结构。

## 旧表兼容与重复执行（2026-09-28）

直接执行原文件 `1_HTH_CRM_849_Schema.sql`，无需另选一个迁移脚本。它按实际元数据处理：

- 没有表：创建原定的 24 列表及索引。
- 已有表：保留全部数据及 BATCH_PROCESSED_DATE、RECORD_TYPE、FILLER_01 等额外字段；补齐当前 Repository 写入需要的缺列。
- 已有 VARCHAR2 字段：CHAR 长度足够则保留，更短则扩大；BYTE 改为 CHAR 时目标字符长度至少等于原字节容量，不缩短原容量。原有 DEFAULT 和约束不改。
- 历史表新增列允许 NULL，保留历史记录“未知”的状态；不虚构 ACTIVITY_KEY、PHASE、SERVICE_ID 或 CREATED_AT。当前应用为新增记录提供这些值。原已有 NOT NULL 约束不撤销；新建表仍使用原定 NOT NULL 约束。
- 已有同列、同唯一性的正常有效索引，即使名字不同也复用；已有 EVENT_ID 单列主键必须启用并验证；旧表没有主键时，先确认 EVENT_ID 无空值、无重复，再补主键。
- 重跑：已补列、已扩容及已建索引直接跳过，正常第二次输出 `DDL statements applied=0`。

先完成预检查，再执行 ALTER/CREATE INDEX。无法写入的字段类型、已有可空字段却被设为 NOT NULL、应用未提供值的额外 NOT NULL 列（即使有 DEFAULT 也保守要求明确核对）、生成列、分区键扩容、主键/索引冲突或重复 DEDUP_KEY 会停止，并列出原因。完整列表见客户端 DBMS Output。不会擅自删除数据、改业务约束或为历史行填猜测值。

Oracle DDL 自动提交，索引键长度上限、空间和锁等运行时错误仍可能发生，已完成的 DDL 不会整体回滚；错误包含当前语句，修复环境原因后可继续重跑。新增字段的历史行没有 CREATED_AT，因此最近 24 小时查询不包含这些行，验证脚本另提供全表数量/元数据数量对照。

扩容和 BYTE/CHAR 变更依据 [Oracle ALTER TABLE 文档](https://docs.oracle.com/en/database/oracle/oracle-database/19/sqlrf/ALTER-TABLE.html)。迁移只操作 HTH_BEA 表；DIGX 配置仍用第二份文件单独执行，未修改 BCO Java、表或 batch。

### 本次实际 Oracle 验证

在隔离的本地 Oracle Free 容器中，使用合成数据执行了当前完整 PL/SQL（不是 H2 语法模拟）：新建并重跑、BCO 风格旧宽表补列扩容并重跑、保留历史行及额外列、按当前 Repository 的 24 列 INSERT、同定义不同名索引复用、额外必填列/错误类型在任何 ALTER 前停止、无主键且 ID 唯一时补主键、空/重复 ID 拦截、更宽 VARCHAR2(512 CHAR) 保持不缩短。五种重复执行均为 `DDL statements applied=0`，检查全部通过。

测试旧表的字段顺序参考用户截图，长度和约束为合成测试数据，不声称复原了 UAT 完整 DDL。本地 Oracle 版本与 UAT 可能不同；该结果不替代 UAT 空间、索引限制、权限和真实约束验证。Java/H2 的 849/791 回归也已通过。

### DBeaver 执行脚本的分段修复

旧版迁移脚本在 `DECLARE` 中定义局部 procedure。DBeaver 的脚本分段器可能将 `check_column` 的 `END;` 当成整条语句结束，主 `BEGIN` 还没有发送，因而出现 `PLS-00103: end-of-file`，预期 `begin function pragma procedure`。此前直接向 Oracle 提交完整块的验证没有覆盖此客户端分段问题。

建表脚本现已改成单一 `DECLARE ... BEGIN ... END;`，字段和索引检查使用循环，不再嵌套定义 procedure/function。保留既有预检查、补列、扩容、索引复用和数据保护规则。修复的核心是移除会被错误分段的局部子程序，并非补一个 `/`；DBeaver 的部分执行路径会把独立 `/` 当成另一条 SQL，造成 `ORA-00900`，因此交付文件继续不附加 `/`。

DBeaver 的 [SQL 执行说明](https://dbeaver.com/docs/dbeaver/SQL-Execution/) 区分整块语句执行与脚本分段执行。本次使用官方 DBeaver Community 26.2.1 的原始解析器和 Oracle 方言做了验证：旧文件拆成 6 条，第一条精确结束于第 101 行；新版识别为一条完整语句。三种空行分段模式与 Ignore native delimiter 开/关的六种组合均通过。测试仅模拟无连接的应用服务和驱动元数据，不替换解析器逻辑，也不是手写 SQL 分段模拟。

随后将解析器输出的 `SQLQuery.getText()` 原文通过 JDBC `Statement.execute(sql)` 送到隔离的本地 Oracle，未裁剪、包裹或补分隔符。15 步验证通过：首次建表、重复执行 0 DDL、旧表升级、历史行/主键保留、当前应用 24 列 INSERT，以及 DIGX 配置首次注册与重跑保留开关/映射。迁移逻辑另通过原有 32 步回归及多项冲突集中报错且零变更测试。Oracle 为 26ai Free 23.26.3，JDBC 驱动为项目现有 19.8；全部使用合成数据，没有连接 UAT。

分段回归已加入仓库，可在项目根目录运行；使用已有的官方 DBeaver 26.2.1 安装包，不下载依赖、不连接数据库：

```sh
JAVA_HOME=<JDK21> DBEAVER_HOME=<DBeaver26.2.1安装目录> python3 devtools/backend-compile/tests/verify_hth_849_sql_script.py
```

可加 `--output <临时目录>` 导出解析器实际生成的语句，供独立 JDBC 测试使用。解析测试与 Oracle 执行测试分开，不能用解析通过代替数据库执行通过。

## 表注释、既有数据和无新增记录的排查

`1_HTH_CRM_849_Schema.sql` 为表和当前应用使用的 24 个字段补齐英文 `COMMENT ON`。只有注释缺失或不同时执行对应 DDL；重复执行为 0 DDL。额外旧列的注释保留。注释是 Oracle 数据字典说明，与业务表中的 description/desc 数据是两回事。

当前 849 安装脚本不插入 `HTH_MTB_EVENT_DETAILS` 明细，也不创建或读取 `HTH_MTB_API_CONFIG`。活动映射读取 DIGX 配置表 `DIGX_FW_CONFIG_ALL_O` 的 `HTHMtbConfiguration` 分组，键为 `ACTIVITY_<ACTIVITY_KEY>`。不能仅凭另一张表的名字判断其用途、创建者或数据来源，也不能将新加元数据字段为空的历史记录判为假数据。`3_HTH_CRM_849_HTH_Verify.sql` 已增加表/列说明、对象创建时间、依赖、触发器、包含旧记录的最近明细查询；这些查询用于核对来源，不自动翻译、更新或删除未知业务配置。

当前应用写入的明细带 UUID 格式的 EVENT_ID、SOURCE_SYSTEM=HTH、ACTIVITY_KEY、PHASE 和 CREATED_AT；这些只是核对线索，不是“真/假数据”的判断标准。本地回归的样例写入隔离 Oracle 容器或 H2 内存库，测试数据没有包含在部署 SQL 中。没有访问或修改 UAT 数据。

### User Access submit/approve 没有新增记录

1. 用 DIGX 配置连接执行 `4_HTH_CRM_849_DIGX_Verify.sql` 第一条查询。新安装默认 `ENABLED=N`，缺值也默认关闭；重跑安装 SQL 保留既有值，不会自动开启。数据库值为 Y 时，仍须确认应用已刷新配置缓存；Factory 初次注册需重启。
2. 核对同批部署的 hosttohost、common、approval/SMS 包与当前版本一致。User Access 写入服务已有 HthCRMScope；审批两条路径已有 commit 后调用。确认服务方法与部署的 Factory 类路径正确。
3. 在操作时间附近搜索 `HTH_CRM`，按下表判断停在哪一步。新增的三个阶段日志需要部署本次 hosttohost 修改后才有；所有日志仅含阶段、内部活动名、随机事件 ID 和异常类型，不输出请求或密码等内容。
4. 用 HTH_BEA 连接执行 `3_HTH_CRM_849_HTH_Verify.sql` 的 ACCESS_CREATE/EDIT/DELETE 查询，结合 `SOURCE_TRX_REF_NBR` 对照业务交易号。提交的 SUBMIT、业务生效的 APPLY、审批动作的 APPROVAL_APPROVE 含义不同；多级审批按实际阶段核对，不强制假定固定条数。

| 日志阶段 | 含义与下一步 |
| --- | --- |
| `DISABLED`（新增） | 应用读到的 ENABLED 非 Y；不会尝试写库。检查开关及缓存。 |
| `COLLECT`（新增） | 已生成 HTH 事件，接下来检查事务状态。 |
| `WAITING_FOR_JTA`（新增） | 已注册事务完成回调，继续找同一 eventId 的 WRITE/失败日志。 |
| `LOCAL_TX_PENDING` | 仍有活动 resource-local 事务且没有 JTA 回调，本次跳过。需确认具体调用时点，不能认定采集成功。 |
| `TX_OUTCOME_UNKNOWN` / `WRITE_TX_ACTIVE` | 事务状态不允许可靠保存，本次未写库。 |
| `WRITE` | CRM 独立事务已提交；按 eventId 查表。如当前库查不到，应核对 NONXA 数据源指向的库及 schema。 |
| `COLLECT_FAILED` / `APPROVAL_CAPTURE_FAILED` | 采集或 Adapter 路径异常；核对包、Factory 配置及异常类型。 |
| `WRITE_FAILED` | 写库失败；核对 NONXA 用户的 SELECT/INSERT 权限、表结构与约束。 |

未取得本次 UAT 的开关值及运行日志前，不能将“不落库”归因为某一个条件或宣称已解决。本次补充诊断，不改变默认开关、事务处理或失败不补录规则，普通 BCO 仍在 HTH 配置/数据库访问前返回。

本次本地验证：DBeaver 原解析器六组设置通过；Oracle 注释首次写入、25 项英文注释核对、重复执行 0 DDL、旧行/额外列注释保留、只修复缺失或错误的注释、冲突零变更和脚本 3/4 只读查询共 36 步通过。新建表记录数为 0。Java/H2 回归包含 67 项 CRM 检查及既有 791/审批/BCO 隔离用例；新增日志门控用例确认关闭时不会触达事务或数据库。
