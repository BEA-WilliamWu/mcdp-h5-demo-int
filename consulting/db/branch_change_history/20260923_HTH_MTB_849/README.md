# BCOH2H-849 部署和验证

## 当前落库目标

849 的应用、建表、注释和验证统一使用 **`HTH_BEA.HTH_CRM_EVENT_DETAILS`**。

UAT 的 `HTH_MTB_EVENT_DETAILS` 可能属于其他功能，因此本版新建独立 CRM 表。**不重命名、不迁移、不复制、不清空或修改旧 MTB 表及其数据**；`HTH_MTB_API_CONFIG` 也不创建、不读取、不修改。先前 Verify 中针对它的来源排查查询已移除，不再混入本期部署验证。当前安装脚本不插入样例事件；新表初始为 0 行，部署新应用后才接收新事件。

主键和索引也使用独立名称，允许旧 MTB 表及旧索引同时存在：

| 对象 | 定义 |
| --- | --- |
| `PK_HTH_CRM_EVENT` | EVENT_ID 主键 |
| `UX_HTH_CRM_DEDUP` | DEDUP_KEY 唯一索引 |
| `IX_HTH_CRM_DATE_ACT` | EVENT_DTE、ACTIVITY_KEY 顺序索引 |
| `IX_HTH_CRM_SOURCE` | SOURCE_TRX_REF_NBR 索引 |

代码分层为 `HthCRMAsserter → HthCRMInputData → HthCRMRequestAssembler → HthCRMEvent3DomainDTO/Key → HthCRMLocalRepository → LocalHthCRMRepositoryAdapter → ORM`。实体、Key、Assembler、Repository 和 Adapter 位于 hosttohost 的 `domain.hosttohost.entity.crm` 下；采集入口、Factory 和独立事务 Writer 保留在 `app.hosttohost.crm`。Entity 使用与 BCO 相同的 `AbstractDomainObject` / `IPersistenceObject`，Key 使用 `AbstractDomainObjectKey`，由 EclipseLink XML 映射 24 列。

不再用 Map 冒充 Domain Entity，也不手写 INSERT。新映射明确指向 `HTH_BEA.HTH_CRM_EVENT_DETAILS`；只在现有 `cz-hosttohost.cfg.xml` 追加一条注册。BCO 的实体、映射、`cz-crm-mapping.cfg.xml`、Repository、业务入口和 batch 不修改；共用 `module-cfg.properties`、`persistence.xml` 也不修改。

保留一个有意的事务差异：BCO 的 `super.create()` 使用线程当前 session；本期 HTH 必须使用 Writer 打开的独立 NONXA session。项目框架的 `Session.save()` 也会读取线程当前 session 缓存，因此 HTH Adapter 先在独立 session 检查 EVENT_ID 不存在，再调用公开的 `saveOrUpdate(entity)`，由该 session 的 EntityManager 执行 ORM persist。查询已存在的 EVENT_ID 直接拒绝；各持久化字段另设 `updatable=false`、实体缓存设 `ISOLATED`，确保即使并发窗口内出现相同 ID，ORM merge 也不改写原行或污染共享缓存。该并发窗口可能保留已有行并正常返回，不保证一定抛重复异常；正常入口使用新 UUID。不同 EVENT_ID 的重复业务动作仍由 DEDUP_KEY 唯一索引阻止。此处不修改框架、不使用反射、不替换线程业务 session。主键查询使用实体名重载 `get("HthCRMEvent3DomainDTO", key)`，避免框架 Class 重载内部读取线程 MetaModel。

新增 `HthCRMDescriptorCustomizer` 只绑定 HTH CRM 实体，将该实体的 ORM 查询/写入超时保留为 5 秒；不修改共享 customizer，不改变 BCO 实体的超时。

配置分组 `HTHMtbConfiguration`、`HTH_MTB_ADAPTER_FACTORY`/`HTH_MTB_ADAPTER` key 保留既有标识；ORM 调整不重置开关和活动码映射。

## 文件及执行顺序

| 顺序 | 文件 | 连接 | 内容 |
| --- | --- | --- | --- |
| 1 | `1_HTH_CRM_849_Schema.sql` | HTH_BEA | 新建/检查独立 CRM 表、索引及 25 项英文注释 |
| 2 | `2_HTH_CRM_849_DIGX_Config.sql` | DIGX 配置 schema | HTH Factory 注册、缺失时插入默认关闭开关；保留已有开关/映射 |
| 3 | `3_HTH_CRM_849_HTH_Verify.sql` | HTH_BEA | 只读检查新表、24 列、注释、主键、索引、权限及事件 |
| 4 | `4_HTH_CRM_849_DIGX_Verify.sql` | DIGX 配置 schema | 只读检查开关、Factory 和 BCO 参考配置 |
| 可选 | `5_HTH_CRM_849_Comments.sql` | HTH_BEA | 为已存在的新 CRM 表单独补注释，末尾直接查询结果 |

1、2 在各自连接中打开整个文件，使用 DBeaver **Execute SQL Script / Alt+X**；也可选中完整 `DECLARE ... END;` 使用 Ctrl+Enter。文件无局部 procedure/function、无独立 `/`，避免客户端提前拆分。3、4 可整份执行或逐条执行；5 为普通 COMMENT/SELECT，整份 Alt+X 执行。不要继续执行以前指向 MTB 表的本地旧副本。

部署步骤：

0. 若原采集已经启用，先安排切换窗口：暂停 HTH 相关业务流量并等待在途请求结束，或暂时关闭采集且确认所有实例已刷新配置。暂停采集期间的操作不会补录；需连续记录时应暂停业务流量。完成所有实例的新包部署后再恢复，避免新旧实例分别写入两张表。安装脚本不会自动操作开关。
1. 用 HTH_BEA 连接运行脚本 1，再执行脚本 3 的结构检查。新建时应有 24 列、表注释和全部列注释、有效主键及 3 个业务索引，事件数为 0。
2. 按实际 NONXA datasource 账号配置新表的 SELECT/INSERT 权限。旧表的授权不会自动应用到新表；如果 datasource 本身是 HTH_BEA，则无需另授对象权限。脚本不猜测账号、不授予 PUBLIC。
3. 用 DIGX 配置连接执行脚本 2、4。ENABLED 缺失/N/空值都会关闭采集；脚本不自动改为 Y。Factory 初次注册需刷新应用缓存/重启。
4. 干净编译并同时部署 hosttohost 新包和 config_cz 中以下两个文件，再重启应用加载 ORM 映射：`orm/eclipselink/cfg/cz-hosttohost.cfg.xml`、`orm/eclipselink/mappings/cz/hosttohost/crm/HthCRMEvent3DomainDTO.orm.xml`。新的 Entity/Key/Assembler/Repository/Adapter 必须全部在同批 hosttohost 包中；不能只替换 Java 或只发布 XML，也不能仅更新数据库后认定已切换。Jenkins 构建会打包 hosttohost，并把 consulting/config 复制到 dist/config_cz，发布时不能漏掉该配置产物。本次 ORM 调整没有表结构变更；SQL 1/3/5 仅把 CREATED_AT 注释统一为 Record write time。已完成新 CRM 表安装的环境可只运行脚本 5 更新注释，再执行脚本 3 核对；不需重建表。若此前 849 的 common、SMS、approval 包尚未部署，仍须按完整 849 发布清单一起部署。
5. 确认应用读到所需开关后，再做真实 HTH User Access submit/approve，按实际时间和业务引用查询新表，并对应 `HTH_CRM stage=WRITE`。普通 BCO 继续原流程。

Oracle DDL（包括 COMMENT）自动提交，请使用专用部署连接。新表上线不自动补录旧表中的任何历史事件。回退应用或关闭开关不会搬移新旧表数据。

## 重复执行和注释

脚本 1 对新 CRM 表不存在时创建，已存在时检查并按实际元数据补列/扩容，保留数据、额外列及原有约束。不会收缩字段、删除行或伪造历史元数据；不兼容的类型/约束/重复键等先报错。正常重跑应输出 `DDL statements applied=0`。

新表 24 列和表本身均有英文注释。脚本 1 只更新缺失或不同的注释；结构预检查/ALTER 失败时，后续注释可能尚未执行。此时不能把失败的安装当成完成。

脚本 5 可在目标表及 24 列均已存在时独立补注释；每次设置同样的 25 项注释，不改表结构或业务数据。缺列时报错，应先完成脚本 1。以末尾 `ALL_TAB_COMMENTS` / `ALL_COL_COMMENTS` 查询为准；若查询已有注释但客户端界面为空，再刷新表元数据，无需重启应用。

## Verify 如何判读

脚本 3 仅访问新 CRM 表及 Oracle 数据字典：

- `TABLE_CHECK`：必须 PASS；如果缺表或当前连接无权看到它，应先处理再执行后面的数据查询。
- 24 行字段结果：`DEFINITION_CHECK` 与 `COMMENT_CHECK` 必须 PASS。即使某字段缺失，也会返回该字段的 FAIL，而不是遗漏这一行。容量/类型/可空检查与安装脚本的应用写入兼容规则一致；注释逐项比对本版英文文本，非空但错误的注释也会 FAIL。
- `EXTRA_COLUMN_CHECK`：新建表正常应无结果；额外必填字段为 FAIL，因为应用未给它提供值。其他额外字段提示 REVIEW。
- `TABLE_COMMENT_CHECK`、`PRIMARY_KEY_CHECK`、3 行 `INDEX_CHECK`：必须 PASS。表注释同样比对本版文本；索引按目标表、列顺序、唯一性、类型和有效状态核对，允许安装脚本接受的等价索引名。保留名称被其他对象占用或同时存在冲突定义时，不能因另有正确索引就 PASS。
- 授权列表：与真实 NONXA datasource 账号核对。部署连接能查表不代表应用账号能 INSERT；表拥有者没有显式对象授权也是正常情况。
- 重复 DEDUP_KEY 查询：应无结果。最近事件、ACCESS 活动查询只是业务核对依据；查询为空不等于结构错误，更不能据此生成假数据。

脚本 4 的 `FACTORY_CHECK` 必须 PASS，缺失、重复或类名错误会给出 FAIL。`COLLECTION_GATE` 说明数据库中的开关状态；Y 不证明运行中的应用已刷新缓存。正式外部活动码仍由 `ACTIVITY_<ACTIVITY_KEY>` 配置，未配置时允许外部码列为空，不虚构编码。

**Verify 的 PASS 证明所列元数据符合检查条件，不证明应用已部署新包、事务回调已执行或权限/数据源已完成联调。** 最终应由一笔真实操作及 WRITE 日志、新表对应事件共同确认。

## 数据和日志含义

`SOURCE_SYSTEM=HTH`；`CHANNEL_TYPE` 区分 CM/BM；`ACTIVITY_KEY` 区分业务。USER_ID 是操作人，TARGET_USER_ID 是目标用户；ACCT_NBR 保存维护操作的公司标识。授权保留 Related/Associated，一次操作不按账户/API 数量自行拆行。

`EVENT_DTE/EVENT_TIME` 继续取原操作时间并转换香港时间。`CREATED_AT` 在 Writer 保存前取应用当前香港时间，表示本次入库尝试时刻；不再使用原生 INSERT 内的数据库 SYSTIMESTAMP，数据库与应用服务器应保持时钟同步。

`PHASE=SUBMIT` 代表提交，`APPROVAL_APPROVE/APPROVAL_REJECT/APPROVAL_ACTION` 为审批动作，`APPLY` 为有效变更，`GENERATE` 为 Code 生成，`EXECUTE` 为 Setup/Reset 执行。`EVENT_STATUS_CODE=A/R` 是该阶段结果；审批拒绝动作成功可为 A，不能解释为业务获批。只有可靠动作标识才生成 DEDUP_KEY。

查询、REVEAL、通知没有新增本期 CRM 活动。密码、Code、密文、Hash、认证材料和原始请求不入表、不入日志。已有旧表记录来源未知，不自动判为假数据，也不自动迁入新表。

| 日志阶段 | 含义与排查方向 |
| --- | --- |
| `DISABLED` | 应用读到 ENABLED 非 Y，不会写库；核对开关和缓存 |
| `COLLECT` | 已生成事件，继续检查事务状态 |
| `WAITING_FOR_JTA` | 已注册事务完成回调，继续找对应 eventId 的 WRITE/失败 |
| `LOCAL_TX_PENDING` | 存在活动 resource-local 事务且无可用回调，本次跳过；需查具体调用时点 |
| `TX_OUTCOME_UNKNOWN` / `WRITE_TX_ACTIVE` | 无法可靠确定事务结果，本次未写库 |
| `WRITE` | 独立 CRM 写入已提交；按 eventId 查新表，查不到则核对应用版本和 NONXA 数据源 |
| `COLLECT_FAILED` / `APPROVAL_CAPTURE_FAILED` | 采集/Adapter 异常，核对包及 Factory 配置 |
| `WRITE_FAILED` | 写库失败，核对新表权限、结构、约束、数据源及 ORM XML/实体是否同批部署 |

在 JTA transaction 存在时使用完成回调，无活动事务时同步保存，不新增线程/队列。Writer 使用独立 NONXA session，不 commit/rollback 调用方事务。失败不重试、不补录。此次 ORM 调整不改变这些已知边界；原 User Access 不落库的具体原因仍需 UAT 的开关及日志判断。

## 本地验证与边界

```sh
JAVA_HOME=<JDK21> H2_JAR=<local-h2.jar> python3 devtools/backend-compile/tests/verify_hth_849.py
JAVA_HOME=<JDK21> DBEAVER_HOME=<DBeaver26.2.1安装目录> python3 devtools/backend-compile/tests/verify_hth_849_sql_script.py
```

当前 Java 回归使用项目 EclipseLink、真实 Session wrapper、生产 ORM XML 和 H2，验证实体实际入库及原业务事务隔离；不以原生 SQL 代理测试代替 ORM 验证。测试命令必须提供 H2_JAR，不能把跳过数据库测试当作通过。

本次 69 项单元/JTA/日志检查、450 项真实 ORM 检查及既有 791、审批和 BCO 隔离回归通过。真实 ORM 检查包含 24 列、embedded key、TIMESTAMP(6)、JVM 为 UTC 时仍保存香港时间、有/无原业务 Session、生产 Writer 默认资源路径、重复主键不覆盖、绕过 guard 的 merge 不覆盖、查询后并发插入同 ID 保留原行及缓存、重复业务键提交失败整笔回滚、原业务独立提交/回滚、旧 MTB 表不变及缺新表不回退。HTH 与原 BCO 映射同时加载，HTH timeout=5、BCO timeout=0；正常持久化路径未读取线程当前 Session。1216、1288、Audit 的编译验证也通过。DataAccessManager 初始化、配置、JTA 边界使用测试替身；ORM 实体、XML、Session wrapper、SessionPersister 和 Transaction wrapper 使用项目实际实现，仍不等于真实 WebLogic/UAT 已验收。

DBeaver Community 26.2.1 原解析器对安装脚本的六组分段设置通过；脚本 3/4/5 也分别在六组设置下完整解析为 13/6/28 条语句，包含文本中的分号没有被误拆。解析器实际输出原文通过项目 JDBC 19.8 驱动在隔离 Oracle 26ai Free 23.26.3.0.0 上执行。

此前独立表版本的 Oracle 44 项基础步骤和 20 项增强步骤全部通过（本次仅调整 CREATED_AT 注释文案，1/3/5 的相同文案已静态核对，DBeaver 分段回归重新通过；未重跑 Oracle）：旧 MTB 表及旧 PK/索引/API_CONFIG 同存时成功新建 CRM 表；新表 24 列、4 个索引（含主键）、25 项精确英文注释及 0 行；重复安装为 0 DDL。旧对象的 DDL、字段、约束、索引、注释、数据和 DDL 时间快照前后一致。脚本 5 单独补注释成功；Verify 能识别缺字段、缺失/错误注释、非唯一去重索引、等价索引与错误标准名称共存、标准名称被其他对象/表占用、Factory 缺失/错误/重复，修复后均 PASS。已有 ENABLED=Y 在配置重跑后保留。实际验证了 ALL_TAB_PRIVS 查询语法。

所有测试使用合成数据，没有连接或修改 UAT。测试 Oracle 版本与 UAT 可能不同；真实 WebLogic 事务、datasource 权限/目标库、配置缓存及 CM/BM 操作仍需部署后验证。
