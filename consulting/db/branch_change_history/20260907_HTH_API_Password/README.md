# HTH API Password 最终部署 SQL（787 扩展 / 788 / 790 / 1204 / 789 / 1205）

## 本次重新部署

使用本目录的完整版本，按 **1 → 2 → 3 → 4 → 5 → 6 → 7** 执行。
已有 787 基表及业务配置是前提；本次不需要重跑其他目录的 787 原始脚本。

- 1～6 各包含一个完整 PL/SQL 主块。在 DBeaver 等客户端选择从最外层 `DECLARE` / `BEGIN` 到最后的 `END;` 一起执行；不要按块内分号拆开。
- 2、3、4、6 的主块后有查询，主块成功后再执行。7 全部是只读查询，无需填写用户参数。
- 不使用 SQL*Plus 的 `DEFINE`、`UNDEFINE`、`WHENEVER` 或独立 `/`。
- 2～6 成功时提交；失败时回滚到本块保存点并重新抛出异常。不要点“跳过错误”继续部署。
- 1 包含 DDL，Oracle 会隐式提交；部署前备份，失败时停止并检查已完成的步骤。
- 使用独立的部署连接，先处理该连接原有的未提交事务。脚本的 COMMIT 会提交该连接的事务。
- 部署期间暂停 HTH 密码及用户维护相关操作；多个部署人员不要同时执行配置脚本。

| 顺序 | 文件 | 执行账号与用途 |
| --- | --- | --- |
| 1 | `1_HTH_API_Password_Schema.sql` | HTH_BEA 或有直接 DDL 权限的账号；扩展 Code、STATE、OPERATION、CREDENTIAL 表 |
| 2 | `2_HTH_API_Password_Process.sql` | OBDX 配置账号；任务、审计及服务映射 |
| 3 | `3_HTH_API_Password_Permission.sql` | OBDX 配置账号；继承现有 BCO Change Password 权限集合 |
| 4 | `4_HTH_API_Password_Adapters.sql` | OBDX 配置账号；Code 密钥、存储设置及 Repository 注册 |
| 5 | `5_HTH_API_Password_Error_Messages.sql` | OBDX 配置账号；001～010 三语言错误文案 |
| 6 | `6_HTH_API_Password_Notification.sql` | OBDX 配置账号；Activity、事件、Action、收件人、模板及变量映射 |
| 7 | `7_HTH_API_Password_Verification.sql` | 能查询 OBDX 配置表及 HTH_BEA 的账号；部署结果校验 |

“OBDX 配置账号”指应用实际数据源使用的 schema；不要把未限定 schema 的配置写入 HTH_BEA。
脚本不会发送邮件、短信，也不会补发已经失败的历史通知。

## 重复执行时保留什么

- 不删除或清空 Code、密码哈希、状态表及操作历史，不修改 Code 的状态、Purpose、有效期或使用时间。
- 1 创建缺少的表、字段和索引，并升级本功能的 OPERATION backend 约束以允许 DSP；已匹配的 Code 状态约束不重复删除重建。旧状态约束升级前先检查已有数据。
- 2 原位更新已有 HTH 任务，保留任务身份；重新建立本功能服务映射和 audit 配置。
- 3 根据当前 BCO Change Password 权限集合重新建立 HTH 权限，不改 BCO 源配置。
- 4 的输入全部保留 NULL 时，复用现有密钥、存储方式、开关、UAM/DSP 地址和凭证；密码策略及超时只在缺失时初始化。Repository 的 OBDX_BU 注册更新不删除其他业务单元配置。
- 5 将本功能 001～010 错误文案更新为本版本；6 将本功能事件、Action、收件人和 OBDX_BU 模板更新为本版本。环境中自行修改的这些 HTH 文案会被本版本覆盖，应先合并正式文案。
- 6 原位注册三个 Activity：`setup`、`reset`、`activateOnUserApproval`。Activity 使用现有 BCO Login PIN 通知的 `MODULE_TYPE=PC`、`OBJECT_STATUS=A`、`DOMAIN_OBJECT_EXTN=CZ`。
- 5、6 任一配置语句失败，整块回滚，避免仅删除旧配置后就留下半套数据。

## 4 号脚本的输入

已有配置的 UAT **保留变量为 NULL 即可**，不要重新生成、截短或替换 Code 密钥。

| 变量 | NULL 时的行为 | 需要变更时 |
| --- | --- | --- |
| `V_ORIGINAL_KEY` | 复用已配置密钥；首次部署缺密钥则报错 | 首次部署在客户端填写原 Base64 AES-256 密钥，不保存到 Git |
| `V_STORAGE_BACKEND` | 保留现值；首次部署默认 DATABASE | 显式填写 DATABASE、UAM 或 DSP；切换不迁移旧密码 |
| `V_ENABLED` | 保留现值；首次部署默认 true | 显式填写 true / false |
| `V_SERVICE_URL`、`V_STATUS_PATH`、`V_SETUP_PATH`、`V_RESET_PATH` | 保留现值 | UAM 模式填写经过确认的 HTTPS 地址及三个接口路径 |
| `V_DSP_PERSIST_URL` | 保留已配置的 DSP URL | DSP 模式填完整的 HTTPS persist URL |
| `V_DSP_APIC_CLIENT_ID`、`V_DSP_APIC_CLIENT_SECRET` | 保留已配置的专属 DSP 网关凭证 | 首次启用 DSP 在本地部署副本填写，不保存到 Git |

检测到重复配置、空密钥、密钥不同或非法存储模式时，脚本报错并回滚。DATABASE 不要求 UAM 地址或 APIC 参数。
现有 `ENABLED=false` 会被保留；如果本次要启用，明确把 `V_ENABLED` 设置为 `'true'`。

配置写入 `DIGX_FW_CONFIG_ADAPTER_PROP_B`：

- `HOST_ID = HthApiCredentialAdapterConfig`
- `TRANSACTION_TYPE = HTH_API_PASSWORD`
- 密钥底表 `PROP_ID = CODE_CIPHER_KEY`，视图读取名 `HTH_API_PASSWORD.CODE_CIPHER_KEY`
- 存储底表 `PROP_ID = STORAGE_BACKEND`，视图读取名 `HTH_API_PASSWORD.STORAGE_BACKEND`

`DIGX_FW_CONFIG_ADAPTER_PROP_V` 的 PROP_ID 是拼接列，仅用于读取，不对视图进行 INSERT/UPDATE。
Code 密钥是 Base64 字符串，解码后必须为 32 字节。生成、查看、消费 Code 都使用同一配置项。
现有 `Preferences.xml` 必须在 `<Nodes>` 内包含下列节点；已有时不要重复添加：

```xml
<Preference name="HthApiCredentialAdapterConfig" PreferencesProvider="com.ofss.digx.infra.config.impl.MultiEntityDBBasedPropProvider" parent="jdbcpreference" propertyFileName="select prop_id, prop_value from DIGX_FW_CONFIG_ADAPTER_PROP_V where category_id = 'HthApiCredentialAdapterConfig'" syncTimeInterval="36000000" />
```

## 7 号脚本的结果

- 4 张业务表可见，约束启用，4 个索引 VALID。787 的两个索引名称是 `IX_HTH_API_PWD_CODE_OWN`、`IX_HTH_API_PWD_CODE_TXN`。
- 4 个资源及资源动作；3 个 entitlement 均有 policy/group；2 个任务的 `BCO_CLASSIFICATION=MATCH` 且 audit=Y。
- 10 个错误码，各 3 种语言，`CONFIG_STATUS=OK`。
- Code 邮件变量映射：6 个多语言模板 × 2 个属性，12 行全部 `CONFIG_STATUS=OK`；脚本 6 维护 `DIGX_EP_MSG_ATTR_B`、`DIGX_EP_MSG_SRC_B`、`DIGX_MD_SERVICE_ATTR` 和 `DIGX_MD_GEN_ATTR_LEGACY_B`。
- 4 个通知事件，每行 `CONFIG_STATUS=OK`：Activity、事件、Activity-Event 映射及 Action 各 1 条；Setup/Reset 各 6 个收件人与模板，Code 用户/公司邮件各 3 个。
- `ACTIVITY_COUNT` 真正检查 `DIGX_EP_ACT_B` 父记录，不会把 `DIGX_EP_ACT_EVT_B` 映射当成主表已存在。
- 密钥配置数量为 1；存储模式符合当前环境，需使用功能时 ENABLED=true。
- 无孤立操作记录、重复有效 Code；`IN_PROGRESS` / `UNKNOWN` 需要单独核查。过期但尚未退休的 ACTIVE Code 仅作为提示，不要为部署延长有效期或改成可用。

7 不查询密钥值、Code 密文或密码哈希。按用户检查状态、通知发送记录及网关配置时，使用
`devtools/backend-compile/tests/diagnose_hth_notifications.sql`；其中可选用户查询需要绑定参数。

## 执行后的操作与通知验证

配置提交后，按 UAT 既有方式刷新配置/ORM 缓存，或安排应用所有节点重启，然后重新登录。
2026-09-15 修复需同时部署 HTH 后端 `HostToHostApiPassword`（补审批人派送）和前端 API Password 组件及三语资源（补密码显示按钮）。仅修复邮件占位符时，重跑脚本 6 即可补齐映射；无需重建密码表或重新设置密钥。

1. 由获授权人士在用户维护流程生成并批准新的 Code；未设置密码生成 SETUP，已设置且 ACTIVE 生成 RESET。
2. Code 审批触发目标用户、全部实际签署人、公司邮件。签署人来自当前生效交易的 `approvalDetails.signedBy`，OMB 无签署列表时采用当前已认证操作人；不查询整个公司的 AP。按去首尾空格、不区分大小写的邮箱去重，先 user/AP，最后 company。
3. 使用新 Code 完成一次 Setup 或 Reset；成功后分别发布 `HTH_API_PASSWORD_SETUP_SUCCESS` 或 `HTH_API_PASSWORD_RESET_SUCCESS`，使用用户已登记邮箱及手机。
4. 确认不再出现 `Could not find entity ... COD_ACT_ID=...HostToHostApiPassword...` 或 `Unable to publish HTH notification event`。
5. 查询 MNG 发送记录并核对实际收件。MNG 的 Success 是网关处理结果，不等同于手机/邮箱最终送达回执。

已成功的 Reset、已经 USED 的 Code，以及相同 requestId 的重放不会因为重跑 SQL 而重新发送通知。
发布失败不会回滚已成功保存的密码；修正配置后需用新的合法操作验证通知，不能用旧请求反复重置密码。
短信实际发送还依赖联系人、Dispatcher、`isDispatchMocked` 和网关配置。

## 启用 DSP 保存（2026-09-15）

setup/reset 已接入独立 `DSP` 模式，二者均 POST 到 `credentials/v1/persist`，发送：

- `clientId`：当前有效 HTH Management 的 `UAM_CLIENT_ID`，不是网关 Header ID。
- `closeId`：由当前会话身份生成的完整 `USER@party`，即使旧 profile 使用短用户名也不会发送短 ID。
- `passwordHash`：后端会话 RSA 解密后的原始密码按 UTF-8 → SHA-256 → 标准 Base64，44 字符、末尾 `=`；不发送明文。

部署步骤：

1. 暂停 HTH 密码操作，运行新版脚本 1：允许 OPERATION 的 DSP backend，并新增 `REMOTE_CLIENT_ID`。保留旧操作历史，不能把 DATABASE/UAM 历史行直接改成 DSP。
2. 同时部署 HTH module（Service、Operation Entity/Repository/Adapter）、common extxface（异常类）、ext-xface app.impl（DSP Adapter/Client/请求 DTO/响应校验）以及 `HthApiPasswordOperation.orm.xml`。本次连接不要求改前端或 BCO 公共接口。
3. 在脚本 4 的本地副本设置 `V_STORAGE_BACKEND := 'DSP'`、`V_ENABLED := 'true'`，填写完整 URL 和上述两项网关凭证；原 Code 密钥留 NULL 复用。已提供的 SIT URL 是 `https://eam-intgw-test.intranet.hkbea.com:443/hkbea/sit-int/dsp/api/auth/credentials/v1/persist`，其他环境用对应的已批准地址。
4. 执行脚本 4、7，确认 STORAGE_BACKEND=DSP、ENABLED=true、DSP URL/凭证存在、`INVALID_DSP_BINDING_COUNT=0`。确认默认信任库信任网关证书；不禁用 TLS 校验。
5. 按 UAT 既有方式刷新配置和 ORM，或重启所有应用节点，重新登录。首次在 DSP 模式使用的用户需要新生成并批准 SETUP Code；原 DATABASE 密码不会自动同步，已用 Code 不能复用。DSP setup 成功后，新生成的 Code 自动为 RESET。

脚本 4 保留现有存储模式：**变量全留 NULL 不会把 DATABASE 自动切换到 DSP**。没有部署环境访问记录前，不能把代码接入等同于 UAT 已启用。

### DSP 成功响应规则

尚未取得银行接口的实际响应样例。为避免把未知 JSON 当作成功，默认只确认 HTTP 200/201/204 且响应体为空；202、重定向、4xx/5xx 均不确认。这个默认是客户端的同步 HTTP 规则，须与 DSP 实际返回核对。

如果 DSP 成功时返回 JSON，先按正式响应配置下列属性（同一 HthApiCredentialAdapterConfig 节点）：

| 底表 PROP_ID | 用法 |
| --- | --- |
| `DSP_SUCCESS_HTTP_STATUSES` | 逗号分隔的同步成功状态码，默认 `200,201,204`，可收窄；不允许 202 |
| `DSP_SUCCESS_JSON_POINTER` | 成功标识的 JSON Pointer 路径 |
| `DSP_SUCCESS_JSON_VALUE` | 期望的 JSON 标量，保留类型；字符串包含双引号，布尔值为 `true` |
| `DSP_CONNECT_TIMEOUT_MS` / `DSP_READ_TIMEOUT_MS` | 正整数，默认 5000 / 15000 |

例如**仅当正式返回确认为** `{"result":{"saved":true}}` 时才将 Pointer 配置为 `/result/saved`、Value 配置为 `true`；这不是已知的 DSP 返回格式。配置 JSON 匹配后，缺字段、值不符、非 JSON、空体和无法解析的响应均不确认。不要为了绕过错误随意填写匹配规则。

### 结果与失败处理

- 成功次序：身份/密码/Code 校验 → 独立事务预留 Code/操作 → DSP POST → 响应确认 → 本地 Code USED、Operation SUCCESS、STATE ACTIVE → 既有成功通知。
- DSP 模式不写本地 PASSWORD_HASH，也不会失败后回退到 DATABASE。旧本地哈希保留原样。
- DSP workflow 状态仅由当前 party、实际 profile key、业务 clientId 对应的 DSP 操作判断；无成功记录为 NOT_SETUP，有 SUCCESS 为 ACTIVE，存在 IN_PROGRESS/UNKNOWN 优先显示 UNKNOWN。未调用不存在的 DSP status API，也不能检测外部系统单独修改/锁定的状态。
- 确定在 POST 前失败（例如缺配置）会将操作记为 FAILED，并释放 Code 供新请求重试。
- POST 超时、未确认响应、远端成功但本地提交失败：保留 UNKNOWN，数据库也不可用时可能仍为 IN_PROGRESS。不消费 Code、不发成功通知、不自动重发；核对 DSP 结果后再处理记录。
- 操作 requestId 与 backend、业务 clientId 绑定；同一个成功请求重放不再次 POST、不重复发通知。

UAT 验收：正确 SETUP/RESET 各一次、错误/过期 Code、不合法密码、同 requestId 重放、网关失败/超时；检查 `DSP_WRITE`、`PERSIST_CONFIRMED`、`DSP_COMPLETE` 日志和操作表。最后必须用 DSP Token 接口验证新密码生效、reset 后旧密码失效，本地测试不能替代这一项。

## 存储与部署边界

DATABASE 使用 `HTH_BEA.HTH_API_PASSWORD_CREDENTIAL` 保存 PBKDF2-HMAC-SHA256 哈希；
Code 单独使用 AES-GCM 密文保存。UAM/DSP 模式不写本地密码哈希，失败不自动回退 DATABASE。
Code 占用与失败次数使用独立事务；成功保存密码、Code USED、操作 SUCCESS、状态 ACTIVE 在凭据完成事务中处理。

Entity / Key / ORM / Repository / Adapter 映射在 `consulting/config/orm/eclipselink/cfg/cz-hosttohost.cfg.xml` 注册。
此前传输协议、Java、ORM、Preferences 和前端的部署依赖仍须保留；重跑 SQL 不能替代这些部署。
当前应用内会话 RSA 传输不依赖 `AUTH_PUBLICKEY_PROVIDER` 或 `AUTH_DECRYPT_PROVIDER`。
当前仓库的 HTH API 入站密码认证入口仍需按实际接入系统确认；保存成功不等同于外部 API 已完成认证对接。

## 本地验证及限制

```text
python3 devtools/backend-compile/tests/verify_hth_deployment_sql.py
JAVA_HOME=<JDK> python3 devtools/backend-compile/tests/verify_hth_notifications.py
JAVA_HOME=<JDK> python3 devtools/backend-compile/tests/verify_hth_template_metadata.py
JAVA_HOME=<JDK> python3 devtools/backend-compile/tests/verify_hth_dsp_payload.py
JAVA_HOME=<JDK> python3 devtools/backend-compile/tests/verify_hth_dsp_transport.py
JAVA_HOME=<JDK> H2_JAR=<h2-1.4.200.jar> python3 devtools/backend-compile/tests/verify_hth_password_transactions.py
```

第一项将实际通知/错误配置 DML 适配日期和用户表达式后在 SQLite 执行，检查两次部署、父记录约束、
外部引用、其他业务单元隔离、中途失败回滚、Adapter 属性写入及实际通知校验查询。
第二项执行生产通知方法体，用替身隔离 OBDX 框架，检查全部签署人、OMB、联系人缺失、邮箱去重、派送隔离及每个事件独立的 DTO。第三项使用实际 `HostServiceMetadataService` 与 HTH DTO getter 读取 SQL 中的字段路径，仅替换基类的银行配置引导；不等同 UAT 邮件发送。
这些检查不等同于 Oracle PL/SQL 编译、真实数据源缓存或 MNG 网关联调；本地未连接 UAT 数据库执行脚本。

### 2026-09-15 补充验收

- 重跑完整脚本 6，再运行脚本 7；变量映射应返回 12 行 OK。已在本地关系数据库替身验证重复运行、缺失映射修复、子记录约束、其他业务单元隔离和失败回滚，Oracle UAT 仍须执行检查。
- 新建并批准一笔 Code：两个不同邮箱的实际签署人都应收到邮件；user/AP/company 共用邮箱时只发布一封，使用 user 事件模板；公司独立邮箱最后派送。
- 检查三种语言的邮件用户名及 HKT 到期时间，无 `#hthApiPassword...#` 残留。配置失败的历史邮件不会因重跑 SQL 自动补发。
- Code 审批仍是 Email；Setup/Reset 成功通知保持原来的用户 Email + SMS。上述“全部审批人”规则没有扩大到 Setup/Reset 成功通知或其他 Story。
- 密码页面眼睛按钮可用鼠标、Enter 和空格切换，两个字段互不影响，OTP 无按钮。取消、失败、成功和页面销毁均清空输入并恢复隐藏。

DSP 本地测试包括独立 SHA-256/Base64 向量、真实 Jersey HTTPS 调用/响应校验，以及实际 change() 方法与 OBDX/EclipseLink/H2 事务。change() 的会话解密、远程 DSP、通知投递使用替身隔离，另有真实 RSA/HTTPS 传输测试；没有连银行 DSP 或验证真实 Token。
