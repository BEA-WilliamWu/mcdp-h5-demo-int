# BCOH2H-849 — HTH CM / BM MTB 数据保存技术设计

> 2026-09-23 实现补充：采用 HTH 专用 common/mtb 包供现有 HTH 快照、审批和业务模块共用，Repository/Adapter 使用 ORM 参数化 SQL，未新增共享 ORM 注册。JTA 完成回调立即保存；活动 resource-local 事务无完成回调时跳过并诊断，不能声称该路径已验收。部署、测试及限制见 [849 实施说明](../../consulting/db/branch_change_history/20260923_HTH_MTB_849/README.md)。

日期：2026-09-23。实施设计基线；本次只出设计，尚未修改生产代码、执行 SQL 或完成 UAT 验证。

## 1. 已确定的方向

按 `HTH_BCO_CM&BM_MVP1.docx` 范围实现 CM 和 BM 数据采集，参考 BCO CRM 的业务规则及分层方式，新增 HTH 专用实现。MTB 数据必须能够区分 HTH 与 BCO 用户操作。

本期采用**同步保存**：在当前业务执行线程中完成保存尝试，不增加线程池、JMS、outbox、重试或补录。保存失败只留诊断日志，不主动将已经完成的业务改成失败。同步会增加一次数据库调用耗时，不承诺零延迟或任意故障下不丢记录。

用户已确认暂按同步实施。因此原 AC2 的“异步保存”不属于本期实现，应在 Jira 中记录这一调整；本文件不能代替 Jira 的变更，也不宣称同步满足原文异步 AC。

CSV、XLSX 文件生成、Control-M、SFTP、DSP 交易 API 数据采集不在本期实现。sample 和 spike 只作技术参考，不据此加入余额查询、支付或额外文件字段。

## 2. 范围及活动矩阵

下列名称是应用内活动标识，不冒用 MTB 正式外部活动编码。外部编码通过配置映射，不能随意编造 CDC 编码。

| 业务 | 应用内活动 | 实现方向 |
|---|---|---|
| BM 公司 HTH 管理 | COMPANY_ENABLE / COMPANY_EDIT / COMPANY_DISABLE | 从 HostToHostManagement 真实操作提取公司及审批上下文；不能仅凭 submit 方法名认定为 Enable |
| CM HTH 用户创建/修改 | USER_CREATE / USER_EDIT | 共享 UserExtensionData 路径增加 HTH 分支，普通 BCO 保持原逻辑 |
| HTH 账户及服务授权 | ACCESS_CREATE / ACCESS_EDIT；实际开放的删除沿用对应 BCO 规则 | 参考 BCO function 授权动作和保存粒度，转换 HostToHostUserAccessDTO；Related/Associated 作为关系类型保留 |
| HTH API Password Code 生成 | CODE_GENERATE | 与用户维护分开记录，不保存 Code 内容；审批阶段和实际激活阶段分清 |
| 首次设置 API Password | PASSWORD_SETUP | 直接执行操作，按真实业务结果记录 |
| 重设 API Password | PASSWORD_RESET | 与 Setup 分开记录，按真实业务结果记录 |
| 查询、通知、提醒等 | 仅沿用对应 BCO 已有规则 | 不新增 HTH 特例统计；不能把“无例外”理解为全部删除查询记录，也不因有通知/audit 就额外产生 CRM 活动 |

用户渠道显示列、历史用户类型迁移不因本设计自动产生新的维护事件。再生成、Reveal 不因已有 Audit Log 活动就自动新增独立 MTB 活动；若在实际 BCO 对应规则中有记录，按对应规则处理。

## 3. BCO 能直接参考什么

BCO 当前链路：

```text
checkResponsePolicy / failure policy
 → CRMAsserter
 → CRM_GLOBAL_FLAG / CRM_ALLOWED_TASK_CODES
 → fetchCommonInfo
 → CRM_EVALUATOR_<task> 业务转换
 → CRMRequestAssembler → Domain → Repository → LocalAdapter → ORM
 → DIGX_CZ_CRM_EVENT3_DETAILS
```

本地框架 JAR 的调用是同步方法调用。业务运行于审批 worker，不等于 CRM 又独立异步执行。

已检查的 BCO 用户维护 evaluator 识别 UserExtensionDataDTO，但没有把 userChannelType 转成 HTH 标识。UserAccountAccessCRMEvaluator 识别 BCO 授权 DTO，并填写公司及当前操作用户；不识别 HostToHostUserAccessDTO，也没有在该 evaluator 中逐个账户拆行。因此不能只配置旧 evaluator 就宣布 HTH 已支持。

“BCO function 授权”要按页面实际服务 → task → evaluator → 活动配置核对，不能凭 UserAccountAccess 类名代替该核对。实施时先输出这条准确映射和对应行数作为测试基准；若本地配置不足，部署前以 UAT 只读配置核对补齐。这是开发检查项，不再次让 BA 定义已存在的 BCO 规则。

## 4. HTH 保存链路与隔离

```text
HTH 操作的响应 / 失败 / 审批动作
 → HTH 范围识别、活动及阶段判定
 → HthMtbCollector 提取白名单字段
 → HTH 业务 Mapper
 → HthMtbEventDTO
 → HthMtbEventAssembler
 → HthMtbEvent Entity / Repository / LocalAdapter
 → HTH_MTB_EVENT_DETAILS
```

设计选择：新建 HTH 专用表及持久化实现。目的是让本期 HTH 数据不会被原 BCO batch 自动抽取；这是本设计的隔离方案，不是声称 AC 已指定表名。现有 BCO CRM 表、公共 CRMAsserter、已有 evaluator 和 batch 不重写。

对于已有 BCO CRM 的共享用户路径，原 BCO 记录仍按现状保留，HTH 表补充可区分的 HTH 记录；这两个存储用途要明确。未来文件抽取必须选择唯一来源，不得直接把两表 union 当成两笔业务。本期不修改 BCO 既有输出规则。

HTH 独立操作不要同时从 endpoint、service、通知和审计各保存一次。每个操作阶段选一个拥有真实结果的入口。优先沿用平台现有策略扩展点；若扩展点不能可靠取得 HTH DTO 或结果，在 HTH service 加受控调用。共享入口只增加渠道判断和调用，转换/持久化放在 HTH 新类。

HTH 判定使用目标业务对象/用户资料中的权威渠道字段及现有渠道常量；不能只看登录者渠道，因为 BCO AP 可以维护 HTH 用户。无可靠识别信息时不猜测，记录跳过原因；普通 BCO 不进入新增保存链路。

## 5. 记录时点和审批

BCO CRMAsserter 可从普通响应进入，也可在 PA_APT 审批动作中解析 transaction snapshot 和原业务 service/task。不能据此断言所有活动只在最终审批后产生。

本设计沿用对应 BCO 操作阶段，并显式保存 PHASE：

| 场景 | 记录方式 |
|---|---|
| Maker 提交、等待审批 | 对应 BCO 有记录时保存 SUBMIT；只代表提交结果，不能标作已生效 |
| Approver 动作 | 对应 BCO 有记录时保存 APPROVAL_ACTION，关联相同业务 transactionId，保留当前操作人和结果 |
| 最终审批执行并成功 | 标识实际 APPLY 阶段/已生效结果；不能把“批准已接受、执行尚未完成”当作完成 |
| Code 生成与审批激活 | CODE_GENERATE 独立于 USER_CREATE/EDIT；草稿生成不等于 Code 已激活。用真实激活执行结果识别生效记录 |
| Setup / Reset | 在直接执行结果确定后记录 EXECUTE；HTTP 200 或 result=SUCCESSFUL 不足以判断成功，必须检查业务 ERROR/异常 |
| 驳回、业务失败 | 参考相同 BCO 行为记录阶段及失败结果；不能与 MTB 写库失败混为一谈 |

这里定义阶段含义，不意味着强制每笔产生四条记录。实际保留哪些阶段、每阶段几条，以对应 BCO function/用户维护规则为基准。新 HTH 直接生效动作默认每次执行结果一条；不按账户/API 数量自行展开。

同一业务事务不同审批动作是不同事件；同一个回调的重复调用不是新事件。有稳定 transactionId + actionId/executionId 时，以活动、阶段、动作标识及明细序号去重。没有稳定动作 ID 时只做单次调用内防重复，不声称跨重试恰好一次，也不把所有相同 requestId 的不同审批阶段合并。

## 6. 数据设计

表建议名 `HTH_MTB_EVENT_DETAILS`，按 BCO 事件明细模型设计显式列。本期是 CM/BM 非金融事件，金额、支付、文件头尾及 sample 独有字段不强行加入。后续若扩展金融交易另做迁移。

以下为本设计的新表类型建议，并非推断 BCO 实际 DDL。上线脚本按应用实际 ID 长度作预检，禁止静默截断。

| 字段 | 类型建议 | 来源 / 含义 |
|---|---|---|
| EVENT_ID | VARCHAR2(36 CHAR), PK | 应用生成 UUID；不使用或改变 BCO crm_sequence |
| EVENT_DTE / EVENT_TIME | VARCHAR2(8 CHAR) / VARCHAR2(6 CHAR) | 事件发生时刻，香港时区 yyyyMMdd / HHmmss；不是重试时间 |
| CREATED_AT | TIMESTAMP(6) | 实际保存时间，约定香港时区 |
| SOURCE_SYSTEM | VARCHAR2(16 CHAR) | 固定 HTH；区分存储来源 |
| CHANNEL_TYPE | VARCHAR2(16 CHAR) | CM / BM 入口 |
| TARGET_USER_CHANNEL | VARCHAR2(16 CHAR) | 权威目标用户渠道；公司管理无目标用户时允许空 |
| ACTIVITY_KEY | VARCHAR2(64 CHAR) | 上述内部业务活动标识 |
| EVENT_ACTV_TYPE_CODE | VARCHAR2(64 CHAR), nullable | 正式 MTB 活动编码，来自配置；未配置时保留内部活动并记诊断，不伪造编码 |
| EVENT_STATUS_CODE | VARCHAR2(16 CHAR) | 沿用 BCO 对应状态映射；不能脱离 PHASE 解释为业务已生效 |
| PHASE | VARCHAR2(32 CHAR) | SUBMIT / APPROVAL_ACTION / APPLY / EXECUTE 等明确阶段 |
| FIN_IND | VARCHAR2(1 CHAR) | 按 BCO 非金融业务配置，不存金额 |
| USER_ID | VARCHAR2(256 CHAR) | 当前操作人；不能用目标用户覆盖审批人 |
| TARGET_USER_ID | VARCHAR2(256 CHAR), nullable | 被维护用户，保留完整标识 |
| ACCT_NBR | VARCHAR2(64 CHAR) | 对应 BCO 维护业务的公司/Party 标识，不误当银行账户 |
| RELATIONSHIP_TYPE | VARCHAR2(32 CHAR), nullable | Related / Associated，对应真实 DTO |
| SERVICE_ID / TASK_CODE | VARCHAR2(256 CHAR) / VARCHAR2(100 CHAR) | 实际服务与 task，方便追溯配置 |
| SOURCE_TRX_REF_NBR | VARCHAR2(128 CHAR), nullable | 业务/审批 transactionId |
| SOURCE_ACTION_ID | VARCHAR2(128 CHAR), nullable | 独立审批动作或执行 ID；不是简单取最后审批人 |
| REQUEST_ID | VARCHAR2(128 CHAR), nullable | 已有可信请求关联标识 |
| IP_ADDRESS | VARCHAR2(64 CHAR), nullable | 原调用上下文；无 IP 不伪造，记录可诊断原因 |
| ERROR_CODE | VARCHAR2(100 CHAR), nullable | 业务错误代码，不保存原始异常消息或请求内容 |
| DEDUP_KEY | VARCHAR2(64 CHAR), nullable, UNIQUE | 有稳定来源动作标识时生成摘要；无稳定来源时留空 |

授权账户及 function 明细：先按 BCO function 映射确认是否实际保存。BCO 未保存的细项不擅自变成 HTH 新需求；需要的同义列在最终建表 SQL 中显式列出。不以原始 JSON 代替字段设计，不保存整个用户资料快照。

密码、Code、密文、密码 hash、RSA 私钥、Token、认证 header 不入表、不入日志。正常金额/敏感字段之外的数据也按白名单采集。

索引：主键、DEDUP_KEY 唯一索引、事件日期+活动索引、SOURCE_TRX_REF_NBR 索引。按实际查询验证，避免为每列建索引。本期不增加 batch 状态或清理 job，不修改 BCO 保留策略。

## 7. 同步事务和失败策略

同步与共用事务是两件事。HthMtbEventWriter 必须使用独立持久化边界，不能 commit/rollback 调用方业务事务。优先使用项目已有受支持的独立事务机制；实施时验证平台事务挂起/恢复能力，不假设仅 openSession 就是独立事务，不靠改 NONXA 来掩盖问题。

成功生效记录必须在业务提交结果已确定后写入。优先使用平台已有完成回调；在同一执行线程同步执行写入。平台若没有可用回调，应在拥有 commit 结果的 HTH 执行层接入，不能在 checkResponsePolicy 前后猜测 commit 已完成。前置采集只保存白名单快照，不保留 Session/HTTP request。

写库失败：回滚 HTH 写入、关闭它拥有的资源、恢复调用上下文，写安全日志；不重试、不补录。提交结果不明也不主动重试，避免重复。不吞掉原业务异常、不改变原业务返回。事务隔离实测未通过之前不可部署，不能把 catch 当作“不影响主业务”的证据。

同一配置/映射错误应明确记录原因；本期允许保存失败导致数据缺失，不能承诺完整性保障或事后恢复。

## 8. 拟新增及修改代码

类名为设计名，实施时按项目包约定落位，不为复用而引入模块循环依赖。

| 位置 | 内容 | BCO 影响 |
|---|---|---|
| HTH 模块 | HthMtbCollector / HthMtbActivityResolver | 新增采集、活动与阶段解析 |
| HTH 模块 | 用户、公司管理、授权、密码四类 Mapper | 新增白名单转换；不改旧 evaluator 的语义 |
| HTH DTO 模块 | HthMtbEventDTO | 新增专用 DTO |
| HTH domain/repository | Entity、Key、Assembler、Repository、LocalAdapter、Writer | 新增存储分层及独立事务边界 |
| HTH 服务 | Management、UserAccess、ApiPassword 的受控采集入口 | 不改原业务判断、审批、通知结果 |
| 共享 UserExtensionData / 必要审批扩展点 | HTH 门控及最小调用 | 唯一可能的公共 Java 差异，评审单独列出；普通 BCO 分支保持原样 |
| ORM 注册及映射 | 新 HTH entity 映射，按项目发布方式同步 dist | 只追加新映射 |
| HTH 配置 SQL | 功能开关、活动映射、表/索引 | 不覆盖 CRMConfiguration 原允许列表，不复写 BCO 编码 |
| batch、前端、通知、Audit Log | 不修改 | 不借本 story 扩展既有功能 |

若 HTH 模块依赖方向不允许共享用户服务直接引用 collector，复用已有 adapter factory/接口扩展机制；不能通过反射调用私有方法。

## 9. 配置和 SQL 交付

新增 HTH 专用配置组及 `ENABLED` 开关，默认关闭，完成配置验证后显式开启。活动内部标识与正式 MTB 编码分离。编码没有定稿不阻止采集内部记录，但不得宣称下游映射已完成。

最终交付一份安装/增量脚本和一份只读验证脚本：

- 表不存在才创建；存在则逐项检查类型、长度、主键和索引，不 DROP、不 TRUNCATE。
- 配置按精确键 MERGE；重跑不重置管理员设置的开关，不删除其他配置。
- 不复制整串 BCO CRM_ALLOWED_TASK_CODES，不占用 BCO 事件编号 sequence。
- Oracle DDL 自动提交，不能声称整份脚本可以靠 rollback 全撤回；结构差异时报明确错误。
- 验证表结构、活动映射、开关和重复事件查询；只读检查不打印密码相关字段。

停用回退：关闭 HTH 开关，恢复新增入口/配置版本；保留已采集数据，BCO 表与 batch 不变。

## 10. 日志

统一前缀 `HTH_MTB`。阶段为 COLLECT / MAP / WRITE / SKIP / ERROR，包含内部 eventId、activity、phase、task、耗时、结果及异常类型。禁止打印整个 DTO、SQL 参数、密码、Code、密钥、联系方式。业务关联标识按现有日志脱敏标准处理。

常见定位：NOT_HTH、DISABLED、MISSING_SOURCE_ID、MAPPING_NOT_FOUND、DUPLICATE、WRITE_FAILED。外部编码未配置与持久化失败分别记录。

## 11. 实施顺序及验收

1. 固化 BCO function 授权服务/task/evaluator/活动/条数对照，核对 UAT 配置和审批执行边界；这是代码实现前的首个开发步骤。
2. 新增 HTH DTO、实体、ORM、SQL 和同步 Writer；先证明独立事务不会提交/回滚调用方事务。
3. 依次接入公司管理、用户维护、授权和三个密码活动；接入一类就验证成功、失败、审批及重复调用。
4. 运行 BCO 回归；确认普通 BCO 没有新增 HTH 数据，原 CRM 行数/字段/通知不变。
5. UAT 开启开关，提交真实操作，按 transactionId 对照记录，检查时点、身份、活动及字段。

| 验证场景 | 必须看到的结果 |
|---|---|
| CM / BM 每个纳入活动 | 正确活动标识、入口、目标及操作人；HTH 与 BCO 可区分 |
| BCO AP 维护 HTH 用户 | 正确识别目标 HTH，不因操作者是 BCO 而漏记 |
| 普通 BCO 用户操作 | 保持原 CRM 记录和界面，新 HTH 分支不执行 |
| BCO function 授权对照 | 动作、条数和字段语义一致；HTH DTO 转换正确 |
| 多级审批、驳回、执行失败 | 阶段清晰，不把待审批标作已生效，不重复记录相同回调 |
| Code Generate / Setup / Reset | 三个独立活动；不采集输入及安全材料 |
| 主事务回滚 / 提交失败 | 无错误的已生效成功记录；业务失败按 BCO 对应规则处理 |
| HTH 表不可写 / 超时 | 原业务结果不被修改；HTH 写入资源释放，有安全日志，无重试 |
| SQL 重复执行 | 不丢数据、不覆盖 BCO 配置、不重置开关 |
| 同步性能 | 对比开关前后的提交延迟及数据库连接使用，记录额外耗时 |

编译和单元测试不能代替这些真实事务及 UAT 测试。实现交付必须分别列出已通过测试、未执行测试和运行环境限制。

## 12. 参考及设计边界

- [MVP1 全量对照与用户确认](BCOH2H-849-MVP1-BCO-COMPARISON.md)
- [原代码分析（历史参考，冲突以本设计为准）](BCOH2H-849-TECHNICAL-ANALYSIS.md)
- 原 BCO CRMAsserter、UserManagementCRMEvaluator、UserAccountAccessCRMEvaluator、CRMRequestAssembler、CRMLocalRepository 和 ORM 路径见对照文档。

同步、CM/BM 范围、区分 HTH、三个密码活动、BCO 授权参照及失败不补录已确定，不再重复提问。平台事务 API、正式外部活动码、UAT 配置属于实施核验项，不能伪装成已经验证；如发现不能满足隔离要求，需提出具体技术差异后调整，而不是扩大公共代码修改。
