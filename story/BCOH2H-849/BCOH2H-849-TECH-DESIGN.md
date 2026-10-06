# BCOH2H-849 — HTH CM / BM CRM 数据保存技术设计

状态：已按 MVP1 范围实现，待 UAT 业务链路验收与 AC2 文本更新。设计基线：2026-10-06。

## 1. 目标与需求边界

849 的目标是在 HTH 客户操作发生时保存结构化 CRM 事件，供后续 MTB 批处理抽取。原 story 的 AC1 要求保存已定义的交易数据及必需字段；AC2 要求异步入库以降低 API 延迟。经项目确认，本期先采用同步保存，失败只记录日志，不自动重试或补录。**同步实现不满足原文的“异步”条件**；BA/TL 需在 Jira 更新 AC2 或记录正式偏离，UAT 不应按异步已实现签收。

本设计覆盖 CM 用户维护、HTH 账户及服务授权、API Password，以及 BM 公司 HTH 管理。HTH 事件存入独立表，BCO 继续使用原表和原 CRM 链路，MTB 能依据来源和活动区分两类操作。849 只负责采集与保存；CSV 文件、调度、SFTP 和 MTB 接收属于后续抽取/传输 story（1293 等），不作为本 story 的入库验收条件。849 的 61 字段 sample 和文件规格不能直接代替已确认的 CRM 字段映射或文件合同。

依据：原始 BCOH2H-849 story、MVP1 清单及用户确认结果、仓库中的 BCO CRM ORM/保存实现。早期 spike 作为背景，不覆盖后续已确认的范围与同步决策。

## 2. BCO 对照与隔离原则

| 方面 | BCO 现状 | HTH 设计 |
| --- | --- | --- |
| 事件入口 | CRMAsserter 根据业务结果、配置和 evaluator 采集 | 只在明确的 HTH 业务/审批入口采集；共享入口先判断 HTH |
| 处理链路 | CRMAsserter → CRMInputData → CRMRequestAssembler → CRMEvent3DomainDTO → CRMLocalRepository | HthCRMAsserter → HthCRMInputData/Context → HthCRMRequestAssembler → HthCRMEvent3DomainDTO → HthCRMLocalRepository |
| 落库 | DIGX_CZ_CRM_EVENT3_DETAILS | HTH_BEA.HTH_CRM_EVENT_DETAILS |
| 配置 | CRMConfiguration | HTHCRMConfiguration 控制 HTH 开关和活动码；通用元数据只读复用 CRMConfiguration |
| 保存时机 | 当前可见 BCO CRM 调用链为直接调用，并非已有 MTB 异步队列 | 同步采集；按事务结果在完成回调后或无活动事务时独立写入 |
| 后续抽取 | BCO GenTxnLog2CRM 使用 BATCH_PROCESSED_DATE 标记，生成原 BCO 文件 | 849 不改 BCO batch；HTH 独立抽取由 1293 处理 |

BCO 原表、原 CRM entity/ORM、CRMAsserter、evaluator、批处理及现有配置保持原样。共享的用户维护和审批代码只做 HTH 门控与接口转发；普通 BCO 请求在进入 HTH adapter 前返回。HTH 实现位于 hosttohost 模块，common/hth 只放薄接口、输入快照和渠道判断，不把 HTH 持久化依赖加到 BCO 类中。

## 3. 业务事件、粒度和阶段

每个业务动作或审批阶段按操作级保存事件；不按关联账户数、API 权限数或通知收件人数拆行。实际是否出现 SUBMIT、审批及 APPLY 取决于业务是否经过这些阶段，不能要求每笔固定产生同样数量的记录。

| 范围 | ACTIVITY_KEY | 事件来源与保存点 | 关键关联 |
| --- | --- | --- | --- |
| BM 公司 HTH 管理 | COMPANY_ENABLE / COMPANY_EDIT / COMPANY_DISABLE | 公司管理 submit/edit/disable；提交、审批动作及生效执行分别标记阶段 | partyId、操作人、审批/业务引用 |
| CM HTH 用户维护 | USER_CREATE / USER_EDIT | UserExtensionData create/update，且权威新/旧 userChannelType 涉及 HTH；审批动作单独采集 | partyId、目标用户、操作人 |
| HTH 账户及服务授权 | ACCESS_CREATE / ACCESS_EDIT / ACCESS_DELETE | HostToHostUserAccess submit/edit/delete 及审批；按 BCO function 授权的操作级粒度 | partyId、目标用户、Related/Associated、业务引用 |
| API Password Code | CODE_GENERATE | 生成/再生成和审批后的 Code 激活共享活动；用 PHASE 区分 GENERATE 与 APPLY | 目标用户、Code 记录动作 ID、审批引用；不保存 Code |
| API Password | PASSWORD_SETUP / PASSWORD_RESET | 设置或重设的直接业务结果，PHASE=EXECUTE | 目标用户、requestId；不保存密码/hash |

PHASE 的含义：SUBMIT 为等待审批的提交；APPROVAL_APPROVE / APPROVAL_REJECT / APPROVAL_ACTION 为实际审批动作；APPLY 为业务变更或 Code 激活；GENERATE 为 Code 生成；EXECUTE 为直接操作。审批动作成功与底层业务生效是两回事：成功拒绝动作可以记录 EVENT_STATUS_CODE=A 且 PHASE=APPROVAL_REJECT，不表示业务获批。结果码 A/R 对应本条事件所代表阶段的成功/失败；PENDING_APPROVAL 视为已受理，不可仅凭 HTTP 200 或外层 result=SUCCESSFUL 判定业务成功。

查询、列表、Reveal、通知及提醒不因本设计额外产生一条 HTH CRM 事件；它们继续遵循各自现有 BCO/业务规则。审计日志、通知历史与 CRM 事件是不同的数据；不把投递通知当作第二次客户交易。用户类型迁移、DSP 交互、sample 中其他金融交易不在该采集白名单。

## 4. 采集和保存链路

入口通过 HthCRMScope 或 HthUserCRMScope 取得允许的业务快照；审批提交后由 HthCRMApproval 经 HTH 渠道判断，再经 IHthCRMAdapter 转发到 hosttohost。公司、授权、密码业务在 HTH 服务内直接进入 HthCRMAsserter。入口只提供活动、阶段、业务结果、参与者及引用，不把整个请求/响应、headers 或秘密交给 CRM。

HthCRMAsserter 首先将服务和操作匹配白名单，用户维护再检查 HTH 新/旧渠道，然后读取 HTHCRMConfiguration/ENABLED。开关不是 Y 时停止采集。启用后，HthCRMContext 在请求线程采集适用的 BCO 通用元数据，HthCRMRequestAssembler 映射事件并读取 ACTIVITY_<ACTIVITY_KEY> 外部活动码。若活动码未配置，字段保持空值，不伪造 BCO 编码；投产前必须按接口要求核对这些活动码。

写入过程：

1. 业务数据与线程元数据先形成不可泄密的事件快照。
2. 存在活动 JTA 时注册事务完成回调：COMMITTED 写原事件；ROLLEDBACK 写结果为 R 的独立副本；事务结果未知则不写。
3. 没有活动事务时在当前调用链写入。若仅有 resource-local 活动事务且无法获得完成回调，记录 LOCAL_TX_PENDING 并跳过，不能宣称已记录最终结果。
4. HthCRMWriter 使用独立 NONXA Session/事务经 HthCRMLocalRepository 和 HTH ORM 写表，不提交或回滚原业务事务。保存失败只记 HTH 日志，不改变已完成的业务结果，也不自动补录。

这属于**同步采集和同步写入或同步事务回调**，没有 JMS、后台线程池、outbox 或可靠异步投递。业务事务与 CRM 写入相互隔离，但 CRM 仍可能因独立写库失败而缺行；这是已确认的失败策略，须由监控和人工对账发现。

## 5. 数据模型与字段规则

落库目标为 HTH_BEA.HTH_CRM_EVENT_DETAILS。字段基线是仓库 BCO CustomerRelationshipManagement_Event3.orm.xml 的 **99 个不同 ORM 字段**，再加 **14 个 HTH 专用字段**，合计 113 列。原 24 列 HTH 表按增量升级补 89 列；保留历史行，不重建或插入假事件。这里的 99 是 BCO ORM 映射数，不代表 UAT BCO 物理表恰有 99 列；完整字段、Oracle 类型、来源和 NULL 策略见 HTH-CRM-FIELD-MAPPING.md。

| 字段组 | 规则 |
| --- | --- |
| 主键与时间 | EVENT_ID 使用 HTH UUID；EVENT_DTE/EVENT_TIME 由业务发生时刻换算香港时间；CREATED_AT 是实际保存时刻，历史缺失值不伪造 |
| 来源与渠道 | SOURCE_SYSTEM=HTH；CHANNEL_TYPE 区分 CM/BM；TARGET_USER_CHANNEL 表示 HTH 目标用户；BCO 的 CHNL_ID/CHNL_TYPE_CODE 仍按 CRMConfiguration 及登录渠道取得，不能用 CM/BM 代替 |
| 活动与结果 | ACTIVITY_KEY 是 HTH 内部白名单活动；EVENT_ACTV_TYPE_CODE 来自 HTH 配置；PHASE 与 EVENT_STATUS_CODE 分别描述阶段及该阶段结果；FIN_IND=N |
| 人与公司 | USER_ID 保存 HTH 完整操作人 ID；TARGET_USER_ID 保存被维护用户；ACCT_NBR 在这些维护活动中保存 partyId，不表示具体银行账户 |
| 授权与关联 | RELATIONSHIP_TYPE 保留 Related/Associated；SOURCE_TRX_REF_NBR 保存业务/审批引用；EVENT_REM 保存最多 40 字符的业务备注 |
| 通用上下文 | 从允许的配置、SessionContext、线程属性取得 IP、语言、角色、设备、认证分类、交易引用等；拿不到的选填值留空，不复制整套 headers |
| 错误与隐私 | ERROR_CODE 是 HTH 安全业务代码；BCO 模型 ERR_CODE 优先用审批首条 ProcessingError 的代码，其次 TFA 错误代码；不保存异常正文 |
| 不适用字段 | 金额、FPS、EDDA、商户、司库等字段保留 BCO 列名与 Java 类型；MVP1 没有对应数据时为 NULL，不写假值 |

TOKEN_ID、AR_TOKEN 仅为模型兼容字段，不采集凭据。密码、Code、密文、hash、密钥、原始请求/响应和完整 headers 均不得入表或日志。PHONE_NBR/ELECT_ADD 等 BCO 字段可能承载代理登记语义，不能直接拿来填修改用户的 Email/Mobile。BigDecimal 对应 Oracle NUMBER，不凭猜测设置小数精度。选填配置、语言或设备解析失败时记录阶段和异常类型，其他事件信息继续保存。

14 个 HTH 专用字段为 SOURCE_SYSTEM、CHANNEL_TYPE、TARGET_USER_CHANNEL、ACTIVITY_KEY、PHASE、TARGET_USER_ID、RELATIONSHIP_TYPE、SERVICE_ID、TASK_CODE、SOURCE_ACTION_ID、REQUEST_ID、ERROR_CODE、DEDUP_KEY、CREATED_AT。可靠动作标识存在且事件成功时生成 DEDUP_KEY 摘要，由唯一索引防重复；缺少稳定动作标识时为空，不能宣称所有重复请求都能自动识别。

## 6. ORM、事务和 BCO 保护

HTH 使用独立 HthCRMEvent3DomainDTO/Key、HthCRMRequestAssembler、HthCRMLocalRepository、LocalHthCRMRepositoryAdapter 和 HthCRMEvent3DomainDTO.orm.xml。HTH entity 与 BCO entity 映射到各自表，可在同一应用加载，BCO 的 ORM 不修改。

Writer 只在无活动 JTA 的边界打开独立 NONXA Session。追加事件的 basic 列不可更新，缓存设为 ISOLATED；按 EVENT_ID 检查已有实体后保存，避免相同 ID 的 merge 覆盖已保存行。回滚副本复制全部字段、Key 和 Timestamp，再独立变更结果/错误/去重信息。DEDUP_KEY 冲突只回滚 HTH 写入，不回滚调用方事务。查询/DML 超时限定在 HTH descriptor，错误日志只记阶段和异常类型。

共享审批和 SMS 模块存在极薄的 HTH 门控/adapter 调用，不应称作“完全没有碰公共代码”；BCO 请求被门控排除，原 CRM 保存逻辑和数据目标不变。回归应验证普通 BCO create/edit/approval 不触发 HTH 入库，也不改变原 BCO 表与通知流程。

## 7. 配置、SQL 与部署顺序

运行时配置组最终名称是 HTHCRMConfiguration（CRM 全大写），通过 config_cz/Preferences.xml 的 MultiEntityDBBasedPropProvider 加载。ENABLED 默认为 N；UAT/生产启用时明确设为 Y。旧 HTHMtbConfiguration、HTHCrmConfiguration 和 HTH_MTB_ADAPTER_FACTORY 是迁移来源，不是运行时目标。配置迁移保留原开关及活动码；同键冲突必须先人工核实，不能自动覆盖。BCO 的 CRMConfiguration 只用于只读通用字段，不迁移或修改。

SQL 在 consulting/db/branch_change_history/20260923_HTH_MTB_849，**HTH 与 DIGX 使用不同连接**：

| 顺序 | 脚本 | 连接与作用 |
| --- | --- | --- |
| 1 | 1_HTH_CRM_849_Schema.sql | HTH_BEA：新建 113 列表，或把旧 24 列增量升级；保留数据并设置注释、键和索引 |
| 2 | 2_HTH_CRM_849_DIGX_Config.sql | DIGX 配置 schema：迁移 HTH 配置组和 Factory 名称，注册 HTH 基础 Factory；默认不开启 |
| 3 | 3_HTH_CRM_849_HTH_Verify.sql | HTH_BEA：只读检查字段、类型/容量、注释、键、索引和近期事件 |
| 4 | 4_HTH_CRM_849_DIGX_Verify.sql | DIGX 配置 schema：只读检查配置、Factory、重复键和旧名称残留 |
| 可选 | 5_HTH_CRM_849_Comments.sql | HTH_BEA：仅在结构已正确时单独修复注释；脚本 1 已包含注释 |

数据库脚本先于新 Java/ORM 部署；随后同批发布 hosttohost、common、approval、sms 编译产物和 config_cz 中的 HTH ORM/Preferences.xml，重启相关实例。接口字符串常量会内联，所以配置名称/Factory 更名时不能只换 hosttohost 包。核对 NONXA datasource 的实际账号拥有 HTH 表 SELECT/INSERT 权限；脚本不猜测账号、不向 PUBLIC 授权。

脚本设计为可重跑：兼容已有 24 列和已升级 113 列，缺列补 nullable，允许安全扩容；不 DROP/TRUNCATE、不删除旧行、不修改 BCO 或旧 HTH_MTB_EVENT_DETAILS/HTH_MTB_API_CONFIG。Oracle DDL 隐式提交，中断后需按校验结果排障再重跑，不能声称整份脚本可事务回滚。详细执行方式、兼容性检查及输出解释见同目录 README.md。

## 8. 验收、排查与未关闭事项

本地验证覆盖 HTH/BCO ORM 同时加载、113 列及 BigDecimal/NULL 往返、去重与并发保护、事务隔离、上下文白名单、配置组/Factory 迁移，以及 DBeaver 分段与隔离 Oracle 的新建、旧表升级、重复执行。验证使用合成数据，不能代替 UAT。SQL Verify 的 PASS 只证明元数据符合规则，不证明真实业务已记录。

UAT 至少覆盖 BM Enable/Edit/Disable，CM HTH User Create/Edit，HTH Access Create/Edit/Delete，Code Generate/审批激活，Password Setup/Reset，以及审批批准/拒绝、业务失败、事务回滚、普通 BCO 不误写 HTH。每笔按业务参考号、目标用户、ACTIVITY_KEY、PHASE、EVENT_DTE/CREATED_AT 核对真实新行；再核对外部活动码及适用字段。日志关键字是 HTH_CRM stage=；重点查看 DISABLED、COLLECT、WAITING_FOR_JTA、WRITE、WRITE_FAILED、LOCAL_TX_PENDING、TX_OUTCOME_UNKNOWN、APPROVAL_CAPTURE_FAILED。密码或原始请求不应出现在日志。

上线前仍须关闭以下事项：

1. BA/TL 在 Jira 正式处理 AC2 的异步要求与已确认同步方案之间的差异；若恢复异步要求，需另行设计可靠队列/事务一致性、重试与补录，当前实现不能冒充异步。
2. MTB/BA 核对 HTH 活动外部代码、哪些字段必填及 113 列中适用字段的接受规则；未配置外部码时不能以“表里有行”视为接口就绪。
3. UAT 在真实 WebLogic/datasource/配置缓存下完成上述提交、审批、失败和 BCO 隔离验收。LOCAL_TX_PENDING 或 WRITE_FAILED 说明可能缺记录，必须按事件和日志查清。
4. 1293 的文件字段、排序、编码、文件名、空文件、交接和下游去重属于独立文件合同；849 表设计完成不代表文件接口已经通过验收。

本设计的详细字段表见 HTH-CRM-FIELD-MAPPING.md；部署检查和可重跑 SQL 的技术细节见 consulting/db/branch_change_history/20260923_HTH_MTB_849/README.md。

## 9. SIT 执行指引

交给 SIT 同事的逐步检查、测试矩阵、查询 SQL、证据要求和空表排查见 [BCOH2H-849-SIT-GUIDE.md](BCOH2H-849-SIT-GUIDE.md)。该指引只验证 849 的 HTH CRM 入库；1293 的文件抽取和下游接收须单独验收。
