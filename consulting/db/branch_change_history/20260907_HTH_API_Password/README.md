# 788 / 790 / 1204 最终部署 SQL

脚本使用 Oracle SQL 和 PL/SQL，不依赖 SQL*Plus 的 DEFINE、UNDEFINE、WHENEVER 或单独的 `/`。

- `DECLARE ... END;` / `BEGIN ... END;` 是一个完整语句；在客户端选中整个块执行，不要按块内分号拆开。
- 1 为一个完整 PL/SQL 块；2、3、4 的配置块执行成功后，再执行其后的 SELECT 校验语句。
- 5、6、7 为普通 SQL，按语句执行。文件整体执行需要客户端正确识别 Oracle PL/SQL 块。
- 4 默认 DATABASE，无需 UAM URL；仅选择 UAM 时填写真实 URL 和三个接口路径。两种模式都需先配置 Code 加密密钥。
- 2、3、4 将前置校验和配置写入放在同一块；失败回滚到块内保存点并抛出异常，成功保持原有 COMMIT 行为。
- 1 的 DDL 会隐式提交，不能整体回滚；发生错误立即停止，不继续执行后续脚本。
- 最终版本已把凭证表和 STORAGE_BACKEND 合入 1，无需额外增量补丁。具体顺序及账号见下表。
- 787 原始交付脚本不在此次转换范围内。未连接数据库执行或验证这些脚本。

## 最终执行顺序

前提：已部署 787 原有 Code 基表及其业务配置；保留现有 Code 数据。

| 顺序 | 文件 | 执行账号 / 内容 |
| --- | --- | --- |
| 1 | `1_HTH_API_Password_Schema.sql` | HTH_BEA 或有直接 DDL 权限的账号：Code 扩展、状态表、操作表、密码凭证表 |
| 2 | `2_HTH_API_Password_Process.sql` | OBDX 配置账号：先创建任务和流程配置 |
| 3 | `3_HTH_API_Password_Permission.sql` | OBDX 配置账号：权限配置，依赖上一步任务 |
| 4 | `4_HTH_API_Password_Adapters.sql` | OBDX 配置账号：迁移原 Code 密钥，默认 DATABASE 并启用功能；不需填写 UAM 地址 |
| 5 | `5_HTH_API_Password_Error_Messages.sql` | OBDX 配置账号：错误文案 |
| 6 | `6_HTH_API_Password_Notification.sql` | OBDX 配置账号：通知配置 |
| 7 | `7_HTH_API_Password_Verification.sql` | 有配置表及 HTH_BEA 查询权限的账号：按脚本填写测试用户绑定参数后校验 |

按文件编号 **1 → 2 → 3 → 4 → 5 → 6 → 7** 执行即可。Schema 脚本首次创建操作表时默认 DATABASE；若重跑时遇到旧操作表，会保留历史操作的 UAM 标识，新操作默认 DATABASE。任何一步失败都先处理错误，再继续。

## Code 密钥配置

Code 密钥与存储设置统一在 `4_HTH_API_Password_Adapters.sql` 配置，在同一事务提交；任意校验或写入失败，整块配置回滚。

1. 首次部署时，从实际生成现有 Code 的旧版 `HostToHostApiPassword.java` 取得原 `CIPHER_KEY`，在客户端将 `V_ORIGINAL_KEY` 的 NULL 改成该 Base64 字符串。不要生成替代密钥，不要保存或提交含密钥的脚本。
2. 已有有效配置时，可保留 `V_ORIGINAL_KEY := NULL`，脚本复用现有值；填写相同值也可重跑。不同值、空配置或重复行均拒绝执行。
3. 当前 Java 通过 `codeCipherKey()` 读取配置；生成、查看和消费 Code 使用同一项，没有硬编码密钥或默认密钥。
4. 配置视图为 `DIGX_FW_CONFIG_ADAPTER_PROP_V`，CATEGORY_ID 为 `HthApiCredentialAdapterConfig`，PROP_ID 为 `HTH_API_PASSWORD.CODE_CIPHER_KEY`，PROP_VALUE 为原 Base64 密钥（解码后 32 字节）。
5. 执行完整配置块后，按环境既有方式刷新缓存或重启应用，并验证旧 Code reveal、新 Code generate 和审批激活。脚本后的查询隐藏密钥值。

当前仓库不包含原密钥。尚无配置时，NULL 占位会使脚本报错并回滚；需填写原密钥才能完成部署。Code 密钥与用户输入的 API 密码存储位置相互独立，DATABASE 和 UAM 都需要该 Code 密钥。

现有 Login PIN / Signer PIN 的 HostCredentialsAssembler 使用 pinIndicator 1/2，通过主机接口变更对应凭证。没有确认 HTH 独立凭证类型和认证方之前，不能将 HTH 密码操作映射到这两种 PIN。

## 密码存储选择

在 `4_HTH_API_Password_Adapters.sql` 中设置 `V_STORAGE_BACKEND := 'DATABASE'`（默认）或 `'UAM'`，再执行完整配置块。它写入配置底表 `DIGX_FW_CONFIG_ADAPTER_PROP_B`，应用通过视图 `DIGX_FW_CONFIG_ADAPTER_PROP_V` 读取：

- CATEGORY_ID：`HthApiCredentialAdapterConfig`
- PROP_ID：`HTH_API_PASSWORD.STORAGE_BACKEND`
- PROP_VALUE：`DATABASE` 或 `UAM`

选择 UAM 时，还需填写脚本中的 V_SERVICE_URL、V_STATUS_PATH、V_SETUP_PATH、V_RESET_PATH，并配置环境实际需要的 APIC 凭据。配置变更后按环境既有方式刷新缓存或重启应用。

存储行为：

- `DATABASE`（默认）：HTH_BEA.HTH_API_PASSWORD_CREDENTIAL 存储密码哈希。不要求 UAM Client ID、URL 或 APIC 参数，不创建 UAM Adapter。
- `UAM`：仅通过显式配置的 UAM Adapter 写入密码，本地保留 Code、操作记录和状态投影，不写本地 PASSWORD_HASH。UAM 故障不回退数据库。
- 缺失配置默认 DATABASE；空白或无效枚举报错。`HTH_API_PASSWORD.ENABLED` 仍是独立功能开关，默认关闭，4 脚本成功后开启。

数据库模式使用 PBKDF2-HMAC-SHA256，600000 次，随机 16-byte salt，32-byte hash；格式为
`pbkdf2-sha256-v1$600000$Base64(salt)$Base64(hash)`。密码不是 Code，不使用 787 的 AES 可逆加密。
算法参数参考 https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html 。

首次设置只插入不存在的用户凭证；重置只更新 ACTIVE 凭证。写入哈希、Code USED、操作 SUCCESS 和状态投影在一个独立数据库事务内完成。Code 占用/失败次数沿用独立事务。提交结果不确定时保留 UNKNOWN，不能自动释放并重试。
操作记录写 STORAGE_BACKEND；跨存储重放同一 requestId 拒绝。主 Schema 脚本兼容旧操作表的 UAM 历史记录，新操作显式写入当前模式。

切换存储不是数据迁移，也不会删除旧后端凭证；部署时必须停止相关流量，安排密码重新设置/迁移及旧后端失效，再让认证端与管理端使用同一后端。各节点配置需一致，不能在线反复切换。

本次提供哈希校验工具 `HthApiPasswordHash.verify`；当前仓库未找到已确认的 HTH API 入站认证入口，未将其接到 Login PIN/Signer PIN 或新增公开认证接口。认证端需要按公司/用户读取 ACTIVE 凭证、调用 verify，并实施限流、失败次数/锁定和重置并发处理。只完成保存不能等同于外部 HTH API 已可认证。

验证：6 个变更/新增生产 Java 文件以 Java 8 目标编译通过；24 项存储枚举和密码哈希检查通过，含独立 Python PBKDF2 向量、随机 salt、旧/新密码校验和非法格式拒绝。未执行 Oracle SQL、真实 JTA、UAM 或外部认证联调。

## 持久化部署

CREDENTIAL、STATE、OPERATION 各自提供 Entity、Key、ORM XML、Repository 和本地 RepositoryAdapter。
映射统一注册在 `consulting/config/orm/eclipselink/cfg/cz-hosttohost.cfg.xml`。
Credential/State 主键为 PARTY_ID + USER_ID；Operation 主键仍为 REQUEST_ID。
`4_HTH_API_Password_Adapters.sql` 同时注册三组 repositoryadapterconfig / RepositoryAdapterFactories 配置；
已执行过配置脚本的环境也需重跑 4，并部署新的 Java 和 ORM 文件。
本次不改变表结构，不能仅更新 Java 而遗漏 Repository 注册和 ORM 配置。

HostToHostApiPassword 负责 Code 校验和事务编排，各 Repository 使用同一个传入的 Session；
本地 Adapter 的条件更新方法不自行提交、关闭 Session 或开启其他事务。
密码哈希、Code USED、状态投影和操作 SUCCESS 仍在一次提交中完成；错误次数及占用使用独立事务。

持久化分层验证：19 个新增/变更 Java 文件编译通过；6 个 ORM XML 通过项目内 EclipseLink 2.5 XSD 校验，字段覆盖与 Schema 一致。19 条原 SQL 保留，事务管理实现未变。
`devtools/backend-compile/tests/verify_hth_password_repositories.py` 用临时类隔离 OBDX 启动依赖，验证 19 次 Session 方法调用的绑定完整性及事务所有权；这不替代实际 Repository 工厂、Oracle 或 JTA 联调。

## Adapter 配置底表与读取

配置写入 `DIGX_FW_CONFIG_ADAPTER_PROP_B`；`DIGX_FW_CONFIG_ADAPTER_PROP_V` 仅用于读取，PROP_ID 是 TRANSACTION_TYPE 与底表 PROP_ID 拼接得到的计算列。
本功能 HOST_ID 为 `HthApiCredentialAdapterConfig`，TRANSACTION_TYPE 为 `HTH_API_PASSWORD`。
例如：底表 PROP_ID 为 `STORAGE_BACKEND`，应用读取 `HTH_API_PASSWORD.STORAGE_BACKEND`；底表 PROP_ID 为 `CODE_CIPHER_KEY`，应用读取 `HTH_API_PASSWORD.CODE_CIPHER_KEY`。
原密钥的值不变。填写原密钥后执行 4，并同步部署 `Preferences.xml` 中同名配置节点及 `HthApiPasswordCrypto.java`，刷新配置缓存或重启应用。仅更新 SQL 不足以切换读取链路。

部署环境的 `Preferences.xml` 必须在 `<Nodes>` 内包含以下节点（已有同名节点时核对配置，不要重复添加）：

```xml
<Preference name="HthApiCredentialAdapterConfig" PreferencesProvider="com.ofss.digx.infra.config.impl.MultiEntityDBBasedPropProvider" parent="jdbcpreference" propertyFileName="select prop_id, prop_value from DIGX_FW_CONFIG_ADAPTER_PROP_V where category_id = 'HthApiCredentialAdapterConfig'" syncTimeInterval="36000000" />
```

## BCO 页面隔离

登录 Profile 的 `CZVoidUserExt` 通过现有 dictionary 返回 `userChannelType`，复用已读取的用户扩展数据。Dashboard 和 Security Settings 仅在该值为 `HTH` 时查询 API Password 状态；BCO 或缺少该字段时不发起查询。后端 HTH 身份及权限校验保持有效。

此次需要同步部署 `CZVoidUserExt`、Dashboard、安全菜单及新增 `api-password/user-context.js`；只部署前端而未更新 Profile 会使 HTH 入口不可见。HTH 登录提醒等待最多 5 秒，失败或超时继续原 Bounce Back 提醒，忽略迟到响应；页面销毁会取消提醒定时器。原有 Login PIN/Signer PIN 提醒顺序保持不变。

可运行 `node devtools/frontend-tests/hth-api-password-isolation.test.js` 验证普通 BCO 零 HTH 请求、原安全菜单保留、HTH 入口、重复查询、失败/超时及页面销毁分支。该测试使用隔离的 UI/网络替身，不替代 UAT 登录联调。
