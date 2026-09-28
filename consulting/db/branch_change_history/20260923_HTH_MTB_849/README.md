# BCOH2H-849 部署和验证

## 本次字段扩展

落库目标仍是独立的 **`HTH_BEA.HTH_CRM_EVENT_DETAILS`**，由现有 24 列扩展为 **113 列：BCO ORM 的 99 个字段 + 14 个 HTH 专用字段**。实体、XML 和 SQL 使用同一份字段清单：[hth_crm_849_fields.json](../../../../devtools/backend-compile/tests/fixtures/hth_crm_849_fields.json)。

这里的基线是仓库中的 `CustomerRelationshipManagement_Event3.orm.xml`，不是声称已复制 UAT 的整张 BCO 物理表。BCO batch 另行使用的字段和文件抽取不在本次范围。新增 VARCHAR2 容量是明确的 HTH 定义；参考 BCO assembler 截断宽度的字段已在清单中注明，不能把它理解成已验证的 BCO 数据库列宽。

- 保留现有 24 列及已有值，新增 89 列可空，不伪造历史元数据。
- 补充适用的公共 CRM 资料，例如记录类型、CRM 渠道、国家、用户角色、语言、设备信息、交易备注。`CHNL_ID` / `CHNL_TYPE_CODE` 与 HTH 的 `CHANNEL_TYPE=CM/BM` 分别保存。
- 金额、支付、EDDA、商户等非本期场景沿用 BCO 字段/Java 类型，当前 HTH 不采集，保留 NULL。BigDecimal 对应不限定精度/小数位的 Oracle `NUMBER`，避免列定义自行四舍五入。
- `TOKEN_ID`、`AR_TOKEN` 作为模型字段保留，但 HTH 不采集认证材料；密码、Code、密文、Hash、原始请求不入库或日志。
- 每列都有英文注释；表注释 + 113 列注释共 114 项。

本次只调整 HTH 表、实体、映射及采集实现。**BCO 原表、实体、配置、业务流程和 batch 不改。** `HTH_MTB_EVENT_DETAILS`、`HTH_MTB_API_CONFIG` 不创建、不读取、不复制、不修改。安装脚本不插入事件样例或 API 配置数据。

## SQL 分工和执行顺序

HTH 与 DIGX 不在同一 schema，必须使用各自连接。

| 顺序 | 文件 | 连接 | 用途 |
| --- | --- | --- | --- |
| 1 | `1_HTH_CRM_849_Schema.sql` | HTH_BEA | 新建 113 列表，或把原 24 列表增量升级；包含全部注释 |
| 2 | `2_HTH_CRM_849_DIGX_Config.sql` | DIGX 配置 schema | 注册 HTH Factory；已有 ENABLED、活动码等操作配置保留；本次文件未修改 |
| 3 | `3_HTH_CRM_849_HTH_Verify.sql` | HTH_BEA | 只读检查 113 列、精确注释、主键、索引及运行数据 |
| 4 | `4_HTH_CRM_849_DIGX_Verify.sql` | DIGX 配置 schema | 只读检查开关、Factory；本次文件未修改 |
| 可选 | `5_HTH_CRM_849_Comments.sql` | HTH_BEA | 已有 113 列表单独补注释，不替代结构升级 |

1、2 在 DBeaver 中打开整份文件，用 **Execute SQL Script / Alt+X**。也可选中完整 `DECLARE ... END;` 后 Ctrl+Enter。脚本不含局部 procedure/function 或单独的 `/`，避免旧问题中的客户端提前拆分。3、4、5 可整份 Alt+X 执行。

已部署原 24 列 CRM 表的环境，执行顺序为：

1. 用 HTH_BEA 运行脚本 1，再运行脚本 3。无需删除、重建表；已有行保留，新 89 列在历史行中为 NULL。
2. 先完成数据库扩展，再同批部署 hosttohost Java 包及 `config_cz` 内 `orm/eclipselink/mappings/cz/hosttohost/crm/HthCRMEvent3DomainDTO.orm.xml`，重启应用加载映射。`cz-hosttohost.cfg.xml` 中原有 HTH 注册继续使用。不能只发布 Java 或只发布 XML。
3. 首次部署 849 时还需用 DIGX 连接执行 2、4，并核对 NONXA datasource 账号对 HTH 新表的 SELECT/INSERT 权限。已部署且 Factory/开关正确时无需为了本次加列重新修改配置。脚本不猜测 datasource 账号、不授权 PUBLIC、不自动启用采集。
4. 做真实 HTH User Access submit/approve，再用脚本 3 的时间范围、业务引用和 `HTH_CRM stage=WRITE` 核对。确认新事件的适用字段有值；历史行新字段为空是正常的。

扩展后的新字段都可空，因此原 24 列版本写入时不要求提供新字段。旧实例仍可能产生缺少新元数据的行，需确认所有实例已部署新包。关闭采集期间的操作不会补录；应用回退也不会删除已扩展字段或历史数据。

Oracle DDL（包括 COMMENT）会隐式提交，使用专用部署连接。此处没有 UAT 自动执行或数据库回滚承诺。

## 重复执行和兼容性

脚本 1 在同名 CRM 表存在时按实际元数据预检查，再补列或安全扩容。不会收缩列、删除行、改变历史值或放宽既有业务约束。

- 缺少新字段：补为可空列。
- VARCHAR2 容量较小：扩容；已有更宽列保留。
- 原 BYTE 列：转为足够宽的 CHAR 语义，保留原字节容量。
- 普通 TIMESTAMP 精度不足 6：扩展精度。
- 错误类型、带精度/小数位限制的 NUMBER、应用可能写 NULL 的非空列、额外必填列、重复主键/去重键、冲突索引：报清楚的预检查错误，保留原定义和数据，不猜测转换。
- 预检查通过后按计划执行 DDL。若执行阶段受权限或并发 DDL 等影响而失败，已完成的 Oracle DDL 不能事务回滚；排除问题后可重跑。

正常重复执行输出 **`DDL statements applied=0`**。脚本 1 已包含完整注释；完成脚本 1 后无需再执行 5。脚本 5 只用于已经有 113 列时单独恢复注释，可重复执行。客户端显示空注释时以 SQL 查得的 `ALL_TAB_COMMENTS` / `ALL_COL_COMMENTS` 为准，再刷新客户端元数据。

独立索引继续沿用：

| 名称 | 定义 |
| --- | --- |
| `PK_HTH_CRM_EVENT` | EVENT_ID 主键 |
| `UX_HTH_CRM_DEDUP` | DEDUP_KEY 唯一索引 |
| `IX_HTH_CRM_DATE_ACT` | EVENT_DTE、ACTIVITY_KEY |
| `IX_HTH_CRM_SOURCE` | SOURCE_TRX_REF_NBR |

## Verify 的结果

脚本 3 只访问新 CRM 表和 Oracle 字典。

- `TABLE_CHECK` 必须 PASS。缺表或不可见时先处理，后面的业务查询依赖该表。
- 113 行字段检查中的 `DEFINITION_CHECK`、`COMMENT_CHECK` 必须 PASS。缺列也会返回 FAIL；错误但非空的注释同样 FAIL。NUMBER 校验精度和小数位，VARCHAR2 校验 CHAR 语义及足够容量。
- `EXTRA_COLUMN_CHECK`：新表正常无结果；保留的额外可空字段提示 REVIEW，额外必填字段 FAIL。
- `TABLE_COMMENT_CHECK`、`PRIMARY_KEY_CHECK` 和 3 行 `INDEX_CHECK` 必须 PASS。允许等价正确索引名称；冲突的保留名称不会被另一条正确索引掩盖。
- 授权列表需与实际 NONXA datasource 账号核对；表 owner 无显式对象授权正常。部署账号能查询不代表应用账号能写入。
- 去重键查询应无重复。最近事件查询为空不代表元数据错误，更不能据此插入假事件。

脚本 4 的 `FACTORY_CHECK` 必须 PASS；`COLLECTION_GATE` 为数据库配置状态，仍需确认应用配置缓存。正式外部活动码取 `ACTIVITY_<ACTIVITY_KEY>`，缺少配置时保持 NULL，不伪造编码。

**Verify 的 PASS 证明所列元数据符合要求，不代表真实应用操作已完成验收。** 需要真实操作、对应 WRITE 日志和新表记录一并确认。

## 数据和日志

`SOURCE_SYSTEM=HTH`；`CHANNEL_TYPE` 区分 CM/BM；`ACTIVITY_KEY` 区分业务。`USER_ID` 是操作人，`TARGET_USER_ID` 是目标用户；`ACCT_NBR` 保存维护操作的公司标识。授权保留 Related/Associated，一次操作不按账户/API 数量自行拆行。

`EVENT_DTE/EVENT_TIME` 取原操作时间并转换香港时间；`CREATED_AT` 在 Writer 保存前取应用当前香港时间。`EVENT_REM` 对齐 BCO 的业务引用备注，完整关联信息仍保留在 HTH 原有引用字段。`ERROR_CODE` 是业务错误，BCO 模型的 `ERR_CODE` 优先取首条审批 ProcessingError 的安全代码，其次取 TFA 错误代码，不能混为一项。

`PHASE=SUBMIT` 表示提交；`APPROVAL_APPROVE/APPROVAL_REJECT/APPROVAL_ACTION` 表示审批动作；`APPLY` 为有效变更；`GENERATE` 为 Code 生成；`EXECUTE` 为 Setup/Reset。`EVENT_STATUS_CODE=A/R` 表示该阶段结果，审批拒绝动作成功可为 A，不能解释成业务获批。可靠动作标识存在时才产生 DEDUP_KEY。

| 日志阶段 | 含义 |
| --- | --- |
| `DISABLED` | 应用读到开关非 Y，不采集 |
| `COLLECT` | 已组装事件，继续看事务状态 |
| `WAITING_FOR_JTA` | 等事务完成回调 |
| `LOCAL_TX_PENDING` | 活动 resource-local 事务无回调，本次跳过 |
| `TX_OUTCOME_UNKNOWN` / `WRITE_TX_ACTIVE` | 无法安全确定事务结果，本次未写 |
| `WRITE` | 独立 CRM 写入提交；按 eventId 查表 |
| `CONTEXT_CONFIG_FAILED` / `CONTEXT_LOCALE_FAILED` / `CONTEXT_DEVICE_FAILED` | 选填元数据取得失败，其他事件信息仍继续保存 |
| `COLLECT_FAILED` / `APPROVAL_CAPTURE_FAILED` | 采集/Adapter 失败 |
| `WRITE_FAILED` | 核对新表权限、结构、约束、datasource、实体与 XML 部署版本 |

持久化继续使用 BCO 风格的实体/Key、Assembler、Repository 和 EclipseLink。HTH Writer 使用独立 NONXA Session，在 JTA 完成回调后或无活动事务时同步保存，不新增线程/队列，不重试补录，不提交或回滚调用方事务。实体映射仍为追加记录：字段不可更新，HTH 独立缓存，查询超时 5 秒。BCO 映射和事务保持原样。

## 验证方法

```sh
JAVA_HOME=<JDK21> H2_JAR=<local-h2.jar> python3 devtools/backend-compile/tests/verify_hth_849.py
JAVA_HOME=<JDK21> DBEAVER_HOME=<DBeaver26.2.1安装目录> python3 devtools/backend-compile/tests/verify_hth_849_sql_script.py
```

Java 回归使用项目 EclipseLink、真实 Session wrapper、生产 ORM XML 和 H2，验证 113 字段映射/类型、BigDecimal、实际入库、重复保护、事务隔离与 BCO 原映射同时存在。字段清单同时对照 BCO XML，不能只检查“字段个数相同”。

SQL 分段使用原版 DBeaver 26.2.1 Oracle parser，六组设置下 1/2 各为一个完整匿名块，3/4 分别为 13/6 条查询，5 为 114 条 COMMENT + 3 条查询。解析结果原文通过项目 JDBC 19.8 在隔离 Oracle Free 23.26.3 上测试新建、原 24 列带数据升级、重复执行、注释、VARCHAR2 扩容、NUMBER 精度和不兼容定义预检查；测试证据与最终结果以本次提交说明为准。

上述验证均为本地合成数据，不连接 UAT。真实 WebLogic 事务、datasource 权限/目标库、配置缓存和 CM/BM 操作仍需部署后验收。

### 2026-09-28 本次验证结果

- Java 8 目标编译通过；201 项请求上下文/元数据检查、2,938 项真实 EclipseLink/OBDX ORM 检查、69 项原有 CRM 检查及既有 791/审批隔离回归通过。覆盖实际审批 ProcessingError 到 ORM、全部 113 列读写、BigDecimal、空格 filler、NULL、复制/回滚及并发不可覆盖。
- Oracle 原 24 列带数据升级及 45 项结构/数据/重复执行检查通过；随后只修改 OMB_FLAG、ERR_CODE 注释，各跑 8 项 Oracle 针对性验证，最终安装、注释和 Verify 均通过，重复安装为 0 DDL。最终 SQL 与该 45 项版本的结构和安装逻辑相同，只有这两处注释变化，未把 45 项声称为再次全量运行。
- 五份最终 SQL 均通过原版 DBeaver 六组解析设置；最终实际 payload 在隔离 Oracle Free 23.26.3 / 项目 JDBC 19.8 上运行。原 BCO/旧 MTB 合成对象及数据保持一致。
- 791、1216、1288 编译检查通过。本次 diff 检查确认 BCO CRM、公共 common、SMS、共享 approval、adapter、batch 源码与修改前相同。

本地证据：`/tmp/hth849-expanded-tests.log`、`/tmp/hth849-expanded-oracle-evidence/err-final/`。以上均使用本地合成数据，未连接 UAT；仍须按上文顺序部署后做实际 CM/BM 操作验收。
