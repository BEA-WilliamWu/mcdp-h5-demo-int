# 788 / 790 / 1204 / 789 / 1205 最终部署 SQL

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

登录 Profile 的 `CZVoidUserExt` 通过现有 dictionary 返回 `userChannelType`，复用已读取的用户扩展数据。Dashboard 和 Profile 页面仅在该值为 `HTH` 时查询 API Password 状态；BCO 或缺少该字段时不发起查询。后端 HTH 身份及权限校验保持有效。

此次需要同步部署 `CZVoidUserExt`、Dashboard、Profile 页面、安全菜单及新增 `api-password/user-context.js`；只部署前端而未更新 Profile 会使 HTH 入口不可见。HTH 登录提醒等待最多 5 秒，失败或超时继续原 Bounce Back 提醒，忽略迟到响应；页面销毁会取消提醒定时器。原有 Login PIN/Signer PIN 提醒顺序保持不变。

可运行 `node devtools/frontend-tests/hth-api-password-isolation.test.js` 验证普通 BCO 零 HTH 请求、原安全菜单保留、HTH 入口、重复查询、失败/超时及页面销毁分支。该测试使用隔离的 UI/网络替身，不替代 UAT 登录联调。

## 790 Profile 修改密码入口

入口位于右上角用户菜单 → Profile 页面。仅渠道为 HTH 且 status 返回 ACTIVE 的用户显示 Change HTH API Password；是否有 RESET Code 不影响入口显示。点击进入现有 API Password 页面 RESET 模式，提交时仍校验 Code 的归属、用途、有效期和使用状态。未设置密码、普通 BCO 用户或状态查询失败时不显示。Security Settings 已移除该入口及 HTH 状态请求，原有安全菜单保留。复用现有按钮样式和 API Password 三语言文案，不修改 CSS/SCSS。

## 首次登录向导与 API Password 提示

HTH 用户的登录 Profile 返回 `firstLoginFlowDone=true` 后才查询 API Password 状态。在原登录密码、安全问题向导期间不查询、不设置 HTH 提示已显示标记；最后一步的 `me/loginFlow` 保存成功后，原向导刷新页面，在新的 Dashboard 提醒链中检查并显示 API Password 设置提示。已完成设置则不提示；无有效 SETUP Code 时显示取码提示。测试时需同时确认登录 Profile 中 `userChannelType=HTH`、`firstLoginFlowDone=true`，以及 status 返回 REQUIRED/CODE_REQUIRED。

每次 Web/移动端登录会与既有 PIN 提醒一起重置 `hthApiPasswordSetupPromptLoaded`。尚未设置 API Password 的 HTH 用户每次登录最多提示一次；关闭提示或刷新页面不重复弹出，重新登录后重新查询状态。首次登录向导完成前不查询、不消耗提示标记；ACTIVE 用户不提示。

## 通知部署与回归（789 / 1204 / 1205）

继续按 1→7 执行，不另加插队脚本。已部署 1～5 且结构/配置未变化的环境，只需重新执行本次 6、7 并部署相应 Java。

- 789：Code 审批从 PENDING 变为 ACTIVE 后，发送用户及公司邮件；邮箱大小写/首尾空格归一后去重。不使用手机号/公司电话作为回退。复用 BCO `CZUserExtensionDataExt.sendNotifications` 的 Code 邮件收件人模式。
- 1204 / 1205：凭据设置/重置成功后，分别使用 SETUP_SUCCESS / RESET_SUCCESS 事件；发送用户已登记邮箱和手机。不是邮件与短信二选一。通道使用 BCO `CZCredentialsExt.postChangeCredentials` 的成功重置通知模式。
- 用户联系人通过所属公司范围内的 UserExtensionData 记录解析真实 OBDX 主键，兼容短 ID 和 `USER@partyId`。不得直接用 HTH Code 的短用户名读取 OBDX User，也不得跨公司猜测用户。
- 6 配置 4 个事件、18 个语言/通道收件人及模板：Setup 6、Reset 6、Code 用户邮件 3、Code 公司邮件 3。7 输出每个事件的配置数量和模板匹配数量。
- 模板不含密码、实际 Code 或联系人地址。邮件/SMS 文案为本仓库 HTH 配置，正式文案如另有业务审核版本，应合并到这些模板；PDF 没有附完整正式模板。
- 保持 BCO 的 Event/Action/Recipient 分发机制及既有重试策略，不另建通知队列或重试机制。发布异常记录日志；密码成功不会因通知失败而回滚。实际网关退信/重试依赖环境已有分发配置。

部署验证：个人/公司邮箱不同及相同；仅邮箱、仅手机、两者都没有；Code 审批重复执行；同 requestId 重放 Setup/Reset；通知发布失败；错误归属 Code 审批失败；三语言模板及实际邮箱/手机收件结果。SQL 静态检查不替代真实 Oracle 和网关验证。

本地验证命令：`JAVA_HOME=<JDK> python3 devtools/backend-compile/tests/verify_hth_notifications.py`。
该测试执行生产通知方法体，用内存替身隔离 OBDX 框架，并检查 SQL 的事件/模板字段。它不模拟真实事务提交或消息投递。

### HTH setup/reset 输入传输

前端 `transport.js` 通过已授权的 `GET hostToHostApiPassword/status?transport=true` 获取短期 RSA 公钥，
再提交 `encryptedCredentials`、`transportKeyId` 和 `requestId`。私钥只保存在当前已认证的 HTTP 会话中，
公钥有效期为 10 分钟；应用直接解密并继续原有密码策略、Code 消费及数据库哈希流程。
此传输不依赖 `AUTH_PUBLICKEY_PROVIDER`、`AUTH_DECRYPT_PROVIDER` 或 UAM；Code 的 AES 配置继续用于保存和查看 Code。

部署需同时更新前端两个 JS、公共 DTO 模块、REST endpoint 模块、hosttohost 模块（包含 `HthApiPasswordTransport`）。
本次传输调整不新增表或 SQL 配置。部署后清理前端缓存并重新登录，旧请求密文不能用于新的登录会话。
集群必须保持现有登录会话的粘性或复制；公钥获取与提交应使用同一登录会话。生产部署继续要求 HTTPS。

验证时观察 `status?transport=true` 返回 `transportKey`，然后执行 setup/reset；正常流程不再请求通用 `v1/publicKey`。
验证正确 Code 成功落库、错误/过期 Code 不改密码、过期或不同会话的密钥返回 `_010`。
本地真实 RSA 互通测试：`JAVA_HOME=<JDK目录> node devtools/frontend-tests/hth-api-password-transport.test.js`。
该测试覆盖前端加密、Java 解密、后端解析、会话密钥序列化及错误分支，不替代 UAT 的实际落库和通知测试。

生产前端构建把组件依赖合并进 `api-password/loader.js`，随后清理组件内的独立文件。
`transport` 必须是 ViewModel 的显式 AMD 依赖，随组件打包；提交时不应再单独请求 `transport.js`。
服务器发布应使用同一次前端构建产物，包含组件 loader 及构建生成的完整性配置和指纹，不能只替换源码 JS。
可运行 `node devtools/frontend-tests/hth-api-password-bundle.test.js` 验证压缩、组件打包及独立文件清理后的加载行为。
