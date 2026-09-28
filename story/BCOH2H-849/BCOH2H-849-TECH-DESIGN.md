# BCOH2H-849 — HTH CM / BM CRM 数据保存技术设计

更新：2026-09-28。按已确认的 MVP1 范围和同步保存方案实现；本次将字段模型进一步对齐 BCO。

## 1. 本次调整

以仓库中 BCO `CustomerRelationshipManagement_Event3.orm.xml` 的 **99 个不同字段**为基线，建立独立 HTH Entity 和 ORM，保留 HTH 已有 14 个专用字段，共 **113 列**。已有 HTH 表为 24 列，本次增补 89 列。表名保持 `HTH_BEA.HTH_CRM_EVENT_DETAILS`，已有数据不删除、不重新造数。

字段名称及 Java 类型对照 BCO；新增列容量是明确的 HTH 存储定义。仓库没有完整 BCO 物理表 DDL，因此不能将此称为部署环境 BCO 表的逐字复制。金额/汇率的 `BigDecimal` 映射为 Oracle `NUMBER`，不擅自假定精度和小数位。

**本次只修改 HTH 采集、映射、实体、HTH ORM、SQL、测试及文档。BCO 表、Entity、ORM、CRMAsserter、evaluator、batch、公共 Java 和现有审批接入点保持原样。** HTH 只读复用 `CRMConfiguration` 和现有设备/语言工具，不写 BCO 配置。

完整逐字段定义见 [HTH CRM 字段对照](HTH-CRM-FIELD-MAPPING.md)。

## 2. 业务范围保持不变

| 业务 | 内部活动 | 保存粒度 |
|---|---|---|
| BM 公司 HTH 管理 | COMPANY_ENABLE / COMPANY_EDIT / COMPANY_DISABLE | 每次操作阶段一条，保留公司、操作人、业务引用 |
| CM HTH 用户维护 | USER_CREATE / USER_EDIT | 只采集权威旧/新渠道符合 HTH 的维护 |
| HTH 账户及服务授权 | ACCESS_CREATE / ACCESS_EDIT / ACCESS_DELETE | 沿用 BCO 授权 evaluator 的操作级粒度，不按账户/API 数量拆行 |
| API Password Code 生成/再生成/激活 | CODE_GENERATE | 与用户维护分开，用阶段区分生成及激活 |
| API Password 首次设置 / 重设 | PASSWORD_SETUP / PASSWORD_RESET | 按真实业务结果分别记录 EXECUTE |
| 查询、通知、提醒、Reveal | 无新增 HTH 特例 | 原 BCO 规则继续执行，不因增加字段而扩大范围 |

SUBMIT 代表已受理等待审批；APPROVAL_APPROVE / APPROVAL_REJECT / APPROVAL_ACTION 分别是批准、拒绝及其他审批动作；APPLY 是实际生效执行；EXECUTE 是直接执行。不是每笔固定产生四条。已有审批捕获逻辑不因字段扩展改变，不能用 HTTP 200 或 `result=SUCCESSFUL` 单独判成功。

CSV/XLSX 抽取、Control-M、SFTP、DSP 交易采集、其他 sample 金融业务均不在本次范围。BCO batch 使用但未在 BCO ORM 中映射的文件/地理等字段，本次不根据猜测新增。

## 3. BCO 与 HTH 的分层

BCO 原链路：

```text
CRMAsserter → CRMInputData → CRMRequestAssembler
 → CRMEvent3DomainDTO → CRMLocalRepository → BCO ORM / BCO 表
```

HTH 独立链路：

```text
既有 HTH 范围门控与业务/审批入口
 → HthCRMInputData（已有白名单操作快照）
 → HthCRMAsserter
    → HthCRMContext（请求线程中采集适用的 BCO 通用信息）
    → HthCRMRequestAssembler
 → HthCRMEvent3DomainDTO / HthCRMEvent3DomainKey
 → HthCRMWriter → HthCRMLocalRepository → LocalHthCRMRepositoryAdapter
 → HTH ORM → HTH_BEA.HTH_CRM_EVENT_DETAILS
```

实现位于 `hosttohost` 的 `app.hosttohost.crm` 和 `domain.hosttohost.entity.crm`。common/hth 的既有接口、输入 DTO、渠道工具不扩展；共享服务仍先判断 HTH，再通过平台 Adapter 调用。普通 BCO 不进入新增 HTH 元数据采集。

## 4. 哪些字段实际取值

| 字段 | HTH 取值与 BCO 对照 |
|---|---|
| RECORD_TYPE、FILLER_01、FEE_CHRG_CODE、EVENT_COUNTRY_CODE | 读取 BCO `CRMConfiguration` 相同配置键；未配置留空，不编造默认编码 |
| CHNL_ID | 与 BCO 一样，Login-Channel=BCM 时为 ELE-BCM，其余取 CRM_CHNL-ID_INTERNET |
| CHNL_TYPE_CODE | 取 CRM_CHNL-TYPE-CODE_INTERNET；不能用 CM/BM 代替 |
| SELF_SRV_IND | 与 BCO 一样，isAdmin=true 为 N，false 为 Y；缺失时留空，不猜测 |
| EVENT_ID | 保留 HTH UUID；不使用 BCO sequence |
| EVENT_DTE / EVENT_TIME | 原业务发生时间转香港日期/时间；CREATED_AT 是独立保存时刻 |
| EVENT_STATUS_CODE、FIN_IND | A/R 为本阶段结果，FIN_IND=N；业务事务回滚则 R |
| USER_ID | 保留既有 HTH 完整操作人 ID。BCO 通用采集会去 @ 后缀，但其授权 evaluator 又使用完整 session userId；HTH 为审批追踪保留完整标识，不新增截断行为 |
| ACCT_NBR | 取业务快照 partyId；缺失时取 SessionContext 的 transactingPartyCode，含义为公司编号，不是某个被授权银行账户 |
| EVENT_REM | 先取审批引用、业务 referenceNumber，再取 FC/DIGX TRANS_REF；与 BCO assembler 一样最多 40 字符 |
| SOURCE_TRX_REF_NBR | 保留 HTH 已有完整业务/审批引用，供关联和去重；不改成 BCO common 中的空默认值 |
| IP_ADDRESS | FMO_IP_ADDRESS；缺失留空 |
| MOBILE_BRAND / DEVICE_MODEL / DEVICE_OS_VERSION | 复用 BCO BeaParser：分别为操作系统、浏览器名称、浏览器版本；不按字段名称误填硬件型号 |
| USER_ROLE / BIO_TYPE / REG_METHOD / AUTH_METHOD / APP_VERSION | 读取 BCO 同名线程属性，只取这些分类/版本值，不复制整套 headers |
| LANG | 复用 BCO locale 判断，简体 sc、繁体 tc，其余 en |
| ERR_CODE | 优先取审批 Transaction 首条 ProcessingError 的安全错误代码，其次 TFA_ERR_CODE，保留最后 20 字符；与 HTH ERROR_CODE（业务快照错误码）分开 |
| OMB_FLAG | 仅 CM 用户/授权活动采集平台已形成的 One-Man-Bank 肯定上下文（IS_OMB_ENABLED=true 且非银行操作人）为 Y；平台 false 也可能是尚未评估的默认值，未知留 NULL。BM/直接密码操作留空，不为采集重新调用审批/host 查询 |
| TT_AUTO_ROUTE | 沿用 BCO common 默认 N |
| 金额、币种、FPS、EDDA、付款、商户、司库、资金归集等 | 保留 BCO 字段与类型；MVP1 维护活动没有对应业务数据，明确留 NULL，不写假金额/假编号 |
| TOKEN_ID / AR_TOKEN | 仅保留模型兼容字段；HTH 不采集、不填入认证凭据 |

OMB 的值表示已观察到的审批上下文，不声称复制 BCO CRM_OMB_GLOBAL_FLAG + task/aspect + 规则重查询的完整门控。审批错误直接读取现有 domain Transaction.getErrors() 的第一条 getErrorCode()；这是 BCO TransactionActionResponse 的同一来源，使用公开 API，无须修改共享审批接口。只接收错误标识，不读取错误正文；业务结果/阶段判定不变。

本次新增 20 个有明确取值来源的通用字段，其余 69 个新增字段作为不适用/保留列为空。既有 24 列继续按已有业务逻辑赋值。选填上下文不可取得时允许留空；配置、语言或设备解析异常只记录阶段及异常类型，不丢弃其他已采集信息。

不将修改用户的 Email/Mobile 填到 `ELECT_ADD/PHONE_NBR`：这些 BCO 字段还涉及代理登记及结果语义，当前 BCO 用户维护 evaluator 也未这样映射。不能为了填满列而混用含义。

## 5. HTH 专用字段

| 字段 | 用途 |
|---|---|
| SOURCE_SYSTEM、CHANNEL_TYPE、TARGET_USER_CHANNEL | 来源 HTH、入口 CM/BM、目标用户渠道；与 BCO CHNL_ID 分开 |
| ACTIVITY_KEY、PHASE | 内部活动和提交/审批/生效阶段；外部正式代码仍由 HTH 配置给 EVENT_ACTV_TYPE_CODE |
| TARGET_USER_ID、RELATIONSHIP_TYPE | 被维护用户及 Related/Associated 关系 |
| SERVICE_ID、TASK_CODE | 追溯业务服务与 task |
| SOURCE_ACTION_ID、REQUEST_ID | 单次审批动作或直接请求关联 |
| ERROR_CODE | 业务错误码或 BUSINESS_ROLLBACK，不保存异常文本 |
| DEDUP_KEY | 稳定动作标识存在时生成摘要，数据库唯一约束去重；缺少时为空 |
| CREATED_AT | 独立落库时刻，香港时区；不伪造历史缺失值 |

## 6. 事务与 BCO 隔离

暂按已确认方案同步保存，不增加线程池、JMS、outbox、重试或补录。原 AC2 的异步文字需在 Jira 记录调整，不能声称同步等于异步。

保留原独立事务边界：JTA 完成后在回调中保存，事务回滚则复制完整事件并修改状态/错误/去重信息；活动 resource-local 事务没有可用完成回调时记录 LOCAL_TX_PENDING 并跳过，不能宣称这条路径已在 UAT 验收。新增元数据在注册回调前快照，回调不再读取原请求线程信息。

Writer 使用显式独立 NONXA Session，不能提交/回滚调用方 Session。框架 `super.create()`/`save()` 会依赖线程 Session；HTH 保留已测试的“按实体别名检查已有 EVENT_ID 后 saveOrUpdate”调用。Entity 所有 basic 列 `updatable=false`、缓存 `ISOLATED`，防止并发同 ID merge 覆盖已有记录。此竞态可能正常结束但不新增，不保证总抛重复异常。

Entity 的复制方法复制全部字段，并独立复制 Key/Timestamp，确保回滚转换不丢新增元数据、不修改原事件。DEDUP_KEY 冲突仅回滚 HTH 写入；5 秒查询/DML 超时仅设置在 HTH descriptor。失败只记日志，不补录，不承诺零耗时或绝不丢记录。

## 7. SQL 与发布

部署目录：`consulting/db/branch_change_history/20260923_HTH_MTB_849`。

1. 在 **HTH_BEA** 执行 `1_HTH_CRM_849_Schema.sql`：新库建 113 列；已有 24 列补 89 个 nullable 列；缺失列补齐、兼容容量保留、必要时安全扩大 VARCHAR2。保留已有行、不回填猜测值、不 DROP/TRUNCATE。
2. 在同一 HTH 连接执行 `3_HTH_CRM_849_HTH_Verify.sql`，核对 113 列、类型/容量、英文 comments、主键、索引及数据。
3. 已部署 849 且配置正确时，无需为本次字段扩展修改 DIGX 配置；首次部署仍在 **DIGX** 连接执行 `2_HTH_CRM_849_DIGX_Config.sql`，再运行 `4_HTH_CRM_849_DIGX_Verify.sql`。重跑不重置已有开关/编码。
4. `5_HTH_CRM_849_Comments.sql` 仅作可选的 comments 修复；脚本 1 已包含全部注释，不需重复执行 5。
5. **先升级表，再部署匹配的 hosttohost 包及 HTH ORM XML**，按应用发布流程重启/重新部署加载映射。新实体遇到未升级旧表会写入失败，不能混版本验证。

Oracle DDL 会隐式提交，不能承诺整份脚本事务回滚。对不安全类型/约束差异明确报错。安装脚本可重复执行，不修改 `HTH_MTB_EVENT_DETAILS`、`HTH_MTB_API_CONFIG` 或 BCO 对象；关闭 HTH ENABLED 即停用新增写入，保留已采集数据。

## 8. 验证与排查

- 模型：逐列验证 HTH 包含 BCO ORM 的 99 列及相同 Java 类型，另有 14 列 HTH 信息。
- 取值：配置渠道、银行/客户身份、语种、设备、角色、错误码、交易引用、空上下文、隐私白名单及回滚快照。
- 持久化：真实 EclipseLink + 项目 Session/persister + H2，测试所有字段与 BigDecimal/NULL 的往返、独立事务、重复键、并发 merge 不覆盖、BCO/HTH mapping 同时加载。
- SQL：真实 DBeaver 解析；隔离 Oracle 测新建、旧 24 列带数据升级、重复执行、注释及类型/索引异常检查。结果见部署 README；这些不等同已完成 UAT 业务验证。
- UAT：真实提交/审批后按时间、SOURCE_TRX_REF_NBR、ACTIVITY_KEY、PHASE 对照新记录。查询未采到的新元数据，要同时看实际线程上下文与 CRMConfiguration 是否提供值，不用假数据补齐。

日志关键字为 `HTH_CRM stage=`。常用阶段 COLLECT、WAITING_FOR_JTA、WRITE、WRITE_FAILED、DISABLED、LOCAL_TX_PENDING、TX_OUTCOME_UNKNOWN；新增 CONTEXT_CONFIG_FAILED、CONTEXT_LOCALE_FAILED、CONTEXT_DEVICE_FAILED 只记录异常类型。密码、Code、密文、hash、密钥、Token、整套 headers 和原始请求不写表、不写日志。
