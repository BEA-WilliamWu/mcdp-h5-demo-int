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
| 6 | `6_HTH_API_Password_Notification.sql` | OBDX 配置账号；Activity、事件、Action、收件人及模板 |
| 7 | `7_HTH_API_Password_Verification.sql` | 能查询 OBDX 配置表及 HTH_BEA 的账号；部署结果校验 |

“OBDX 配置账号”指应用实际数据源使用的 schema；不要把未限定 schema 的配置写入 HTH_BEA。
脚本不会发送邮件、短信，也不会补发已经失败的历史通知。

## 重复执行时保留什么

- 不删除或清空 Code、密码哈希、状态表及操作历史，不修改 Code 的状态、Purpose、有效期或使用时间。
- 1 只创建缺少的表、字段和索引；已匹配的 Code 状态约束不重复删除重建。旧状态约束升级前先检查已有数据。
- 2 原位更新已有 HTH 任务，保留任务身份；重新建立本功能服务映射和 audit 配置。
- 3 根据当前 BCO Change Password 权限集合重新建立 HTH 权限，不改 BCO 源配置。
- 4 的输入全部保留 NULL 时，复用现有密钥、存储方式、开关、UAM 地址和路径；密码策略及超时只在缺失时初始化。Repository 的 OBDX_BU 注册更新不删除其他业务单元配置。
- 5 将本功能 001～010 错误文案更新为本版本；6 将本功能事件、Action、收件人和 OBDX_BU 模板更新为本版本。环境中自行修改的这些 HTH 文案会被本版本覆盖，应先合并正式文案。
- 6 原位注册三个 Activity：`setup`、`reset`、`activateOnUserApproval`。Activity 使用现有 BCO Login PIN 通知的 `MODULE_TYPE=PC`、`OBJECT_STATUS=A`、`DOMAIN_OBJECT_EXTN=CZ`。
- 5、6 任一配置语句失败，整块回滚，避免仅删除旧配置后就留下半套数据。

## 4 号脚本的输入

已有配置的 UAT **保留变量为 NULL 即可**，不要重新生成、截短或替换 Code 密钥。

| 变量 | NULL 时的行为 | 需要变更时 |
| --- | --- | --- |
| `V_ORIGINAL_KEY` | 复用已配置密钥；首次部署缺密钥则报错 | 首次部署在客户端填写原 Base64 AES-256 密钥，不保存到 Git |
| `V_STORAGE_BACKEND` | 保留现值；首次部署默认 DATABASE | 显式填写 DATABASE 或 UAM；切换不迁移旧密码 |
| `V_ENABLED` | 保留现值；首次部署默认 true | 显式填写 true / false |
| `V_SERVICE_URL`、`V_STATUS_PATH`、`V_SETUP_PATH`、`V_RESET_PATH` | 保留现值 | UAM 模式填写经过确认的 HTTPS 地址及三个接口路径 |

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
- 4 个通知事件，每行 `CONFIG_STATUS=OK`：Activity、事件、Activity-Event 映射及 Action 各 1 条；Setup/Reset 各 6 个收件人与模板，Code 用户/公司邮件各 3 个。
- `ACTIVITY_COUNT` 真正检查 `DIGX_EP_ACT_B` 父记录，不会把 `DIGX_EP_ACT_EVT_B` 映射当成主表已存在。
- 密钥配置数量为 1；存储模式符合当前环境，需使用功能时 ENABLED=true。
- 无孤立操作记录、重复有效 Code；`IN_PROGRESS` / `UNKNOWN` 需要单独核查。过期但尚未退休的 ACTIVE Code 仅作为提示，不要为部署延长有效期或改成可用。

7 不查询密钥值、Code 密文或密码哈希。按用户检查状态、通知发送记录及网关配置时，使用
`devtools/backend-compile/tests/diagnose_hth_notifications.sql`；其中可选用户查询需要绑定参数。

## 执行后的操作与通知验证

配置提交后，按 UAT 既有方式刷新配置/ORM 缓存，或安排应用所有节点重启，然后重新登录。
本次代码库的修改仅涉及 SQL、部署说明和验证工具，不要求为了这次配置修正另改前后端业务代码。

1. 由获授权人士在用户维护流程生成并批准新的 Code；未设置密码生成 SETUP，已设置且 ACTIVE 生成 RESET。
2. Code 审批触发用户、公司邮件，邮箱归一后去重。
3. 使用新 Code 完成一次 Setup 或 Reset；成功后分别发布 `HTH_API_PASSWORD_SETUP_SUCCESS` 或 `HTH_API_PASSWORD_RESET_SUCCESS`，使用用户已登记邮箱及手机。
4. 确认不再出现 `Could not find entity ... COD_ACT_ID=...HostToHostApiPassword...` 或 `Unable to publish HTH notification event`。
5. 查询 MNG 发送记录并核对实际收件。MNG 的 Success 是网关处理结果，不等同于手机/邮箱最终送达回执。

已成功的 Reset、已经 USED 的 Code，以及相同 requestId 的重放不会因为重跑 SQL 而重新发送通知。
发布失败不会回滚已成功保存的密码；修正配置后需用新的合法操作验证通知，不能用旧请求反复重置密码。
短信实际发送还依赖联系人、Dispatcher、`isDispatchMocked` 和网关配置。

## 存储与部署边界

DATABASE 使用 `HTH_BEA.HTH_API_PASSWORD_CREDENTIAL` 保存 PBKDF2-HMAC-SHA256 哈希；
Code 单独使用 AES-GCM 密文保存。UAM 模式不写本地密码哈希，失败不自动回退 DATABASE。
Code 占用与失败次数使用独立事务；成功保存密码、Code USED、操作 SUCCESS、状态 ACTIVE 在凭据完成事务中处理。

Entity / Key / ORM / Repository / Adapter 映射在 `consulting/config/orm/eclipselink/cfg/cz-hosttohost.cfg.xml` 注册。
此前传输协议、Java、ORM、Preferences 和前端的部署依赖仍须保留；重跑 SQL 不能替代这些部署。
当前应用内会话 RSA 传输不依赖 `AUTH_PUBLICKEY_PROVIDER` 或 `AUTH_DECRYPT_PROVIDER`。
当前仓库的 HTH API 入站密码认证入口仍需按实际接入系统确认；保存成功不等同于外部 API 已完成认证对接。

## 本地验证及限制

```text
python3 devtools/backend-compile/tests/verify_hth_deployment_sql.py
JAVA_HOME=<JDK> python3 devtools/backend-compile/tests/verify_hth_notifications.py
```

第一项将实际通知/错误配置 DML 适配日期和用户表达式后在 SQLite 执行，检查两次部署、父记录约束、
外部引用、其他业务单元隔离、中途失败回滚、Adapter 属性写入及实际通知校验查询。
第二项执行生产通知方法体，用替身隔离 OBDX 框架，检查收件人、去重及模板语言/通道。
这些检查不等同于 Oracle PL/SQL 编译、真实数据源缓存或 MNG 网关联调；本地未连接 UAT 数据库执行脚本。
