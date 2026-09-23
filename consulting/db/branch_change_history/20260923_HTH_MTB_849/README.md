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
2. 使用有权限的账号运行 `1_HTH_MTB_849.sql`。首次创建表及索引；已存在时验证结构；ENABLED 配置只在不存在时插入；HTH Adapter 注册按 HTH 专用 key 幂等更新。重跑不会清空数据或覆盖开关。Oracle DDL 自动提交。
3. 确认 NONXA datasource 对 HTH_BEA 新表拥有 SELECT/INSERT 权限；脚本不假设环境的实际 datasource 用户，不授予 PUBLIC。
4. 注册 SQL 与新包就绪后重启应用：AdapterFactoryConfigurator 在初始化时缓存 Factory，新注册不能假设热更新生效。保持默认 `ENABLED=N` 完成部署检查。显式改为 Y、按环境配置缓存机制刷新后再进行 UAT 验证。
5. 运行 `2_HTH_MTB_849_verify.sql`，按时间和业务引用对照数据。回退只需关闭开关，保留表和记录。

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

诊断关键字改为 `HTH_CRM stage=`。数据库表 `HTH_MTB_EVENT_DETAILS`、配置分组 `HTHMtbConfiguration` 及 `HTH_MTB_ADAPTER_FACTORY`/`HTH_MTB_ADAPTER` 暂保留既有标识，避免因整理 Java 名称切断既有数据和配置。注册值更新为 `com.ofss.digx.cz.bea.app.hosttohost.crm.HthCRMAdapterFactory`：需重新执行 1_HTH_MTB_849.sql（保留数据/开关）并重启。同批干净打包 common、SMS、approval、hosttohost 及引用 HthOnboardingAudit 的模块，避免残留旧 class。
