# HTH API Password — 2026-09-15 补充审查

状态：三项修复已在本地实现并通过针对性验证，尚未部署 UAT。DSP 的 passwordHash 格式及 POST 请求已由用户确认；请求体、HTTP 传输及 setup/reset 主流程已接入独立 DSP 模式，尚未在银行环境部署或验证 Token。以下第 1–4 节保留修复前的审查依据；本次实现和验证见第 7 节。截图中的网关凭证及样例密码摘要未写入文档、SQL 或代码。

## 1. 结论与范围

| 项目 | 已确认事实 | 后续处理 |
| --- | --- | --- |
| Password 显示按钮 | 截图原型要求 New Password、Re-enter Password 右侧有眼睛按钮；现有页面使用 oj-input-password，未显式配置切换按钮 | 核对当前 Oracle JET 版本并复用项目同类实现；不要另引组件库或改 Code 验证 |
| Code 审批通知变量 | DTO 已赋 UserName、ExpiryDateTime；当前 SQL 只有模板和收件等配置，缺少变量绑定链路 | 补模板属性、数据源与 ActivityLog 元数据映射，并运行真实模板渲染验证 |
| Code 审批通知收件人 | HTH 方法只处理 user/company；BCO 参考方法会遍历审批签署人，再处理公司 | 按截图规则补全部实际审批人，统一邮箱去重，用户/审批人先于公司 |
| DSP 凭证持久化 | 截图提供 credentials/v1/persist 的 clientId、closeId、passwordHash 契约片段；现有 UAM adapter 契约不同 | 已接入独立 DSP 模式；成功响应匹配和真实 Token 仍需 UAT 核对 |

本次 Code 审批通知补充对应既有 API Password 功能。不能直接把“全部审批人”视为 851/1216 全部通知场景的已批准规则；这两个 Story 的收件矩阵仍需明确审批人集合，已有实现可以作为复用依据。

## 2. 邮件占位符未替换

截图仍出现 `#hthApiPasswordUserName#` 和 `#hthApiPasswordExpiryDateTime#`。源码中 `notifyHthApiPasswordApproved()` 已给这两个字段赋值，`HthApiPasswordActivityLogDTO` 也有 getter，不能简单归因于 DTO 少字段。

本地框架 `MessageTemplateService` 读取模板 dataAttributes/dataSources，按 serviceAttributeId 通过元数据取得 ActivityLog 的值。ORM 与 BCO Login PIN 的既有部署/回退脚本共同显示如下配置链路：

| 配置 | 用途 |
| --- | --- |
| `DIGX_EP_MSG_TMPL_B` | 模板正文、标题、模板 ID 和 determinant |
| `DIGX_EP_MSG_ATTR_B` | 模板使用的属性/占位符及 masking |
| `DIGX_EP_MSG_SRC_B` | 同一模板、属性、Activity 对应的 service attribute |
| `DIGX_MD_SERVICE_ATTR` | 服务属性的数据来源、Activity、字段映射 |
| `DIGX_MD_GEN_ATTR_LEGACY_B` | 与现有 BCO 元数据机制一致的通用属性定义；按部署版本确认实际维护方式 |

当前 `20260907_HTH_API_Password` SQL 未包含后四项的 HTH 属性配置。这是可以从仓库确认的交付缺口；UAT 是否另有人工配置、是否已刷新缓存仍需运行环境确认。

修复要求：

1. 每个含占位符的 user/company、多语言模板均补齐属性及数据源；精确绑定 `activateOnUserApproval` 的真实 Activity。
2. 核对字段路径、类型、通用属性 ID 与 service attribute ID；不能因为同名 getter 存在就认为引擎会自动遍历 DTO。
3. SQL 可重跑且仅维护 HTH 自有映射，模板替换时处理子记录依赖，保留 BCO 原元数据。
4. 使用真实框架渲染后的 Email 正文验收，确认用户名、HKT 到期时间正确，所有语言均无残留占位符。
5. 增强现有验证脚本：目前它检查 getter 存在后输出“placeholders resolve”，并未执行模板引擎或验证元数据配置；该结论超出了测试实际覆盖。

## 3. 收件人对齐 BCO

截图转述的确认规则为：用户、全部审批人、公司三类收件人，同一个 Email 仅发一次；先处理 user/AP，再处理 company。

现有 BCO `CZUserExtensionDataExt.postUpdate()` 从平台 Transaction 的 `approvalDetails.signedBy` 读取签署人，按 `~` 拆分并加入目标用户；OMB 有独立执行人路径。`sendNotifications()` 依次读取用户邮箱，使用 Set 去重，最后检查公司 officeEmail。

HTH `notifyHthApiPasswordApproved()` 当前没有读取该审批人集合，仅调用 user/company 两类派送。因此“少发审批人”可在源码复现；不能只改 SQL 收件配置解决全部业务身份解析。

建议修复方式：

- 使用同一已生效审批 reference 读取真实签署人；不查询整个公司的所有 AP 角色用户，也不把 Maker 自动列为审批人。
- 在最终生效触发点只组装一次有序收件集合：target user → 实际审批人 → company；去除重复身份及相同邮箱。
- 在同一 Code 审批通知内按归一化后的邮箱去重，company 与 user/AP 相同则跳过 company；不同事件的正常独立通知不受影响。
- 复用 BCO 业务规则，但不直接调用其 private 方法或发 Login PIN 事件；保留 HTH 专属 Activity/Event。
- 每个派送使用独立 ActivityLog/NotificationDetails 快照，避免复用可变对象导致异步消费者读到最后一个地址。
- 验证多级审批、重复签署人、相同邮箱、空邮箱、OMB、审批记录缺失及单个派送失败。不得把参考代码已有的空值处理缺陷或日志输出一并复制。

“只向实际签署人发送”与 851/1216 文档中的“最终审批人”存在范围差异。851/1216 在评审中应明确最终一位还是所有实际签署人，签定后统一设计 DTO 和配置；当前截图直接证明的补充范围是 Code 审批通知。

## 4. UI 验证范围

截图只明确两个密码输入框右侧的眼睛图标，OTP/Code 没有该图标。实现应保持两个密码各自独立的显示状态、键盘可访问性、可本地化的显示/隐藏提示和原输入校验。提交中禁用策略、成功后清空输入及现有 reset 布局保持一致。

先核对当前 JET 是否支持原生显示控制，否则沿用项目 Login PIN / Login Password 同类图标和交互。涉及样式时同步 SCSS/CSS，不压缩成与同类文件不同的格式；修改 JS 后运行项目自定义 ESLint。不能仅以截图认定浏览器实际组件已支持原生眼睛功能。

## 5. DSP 接入实现与部署状态

**代码已接入 setup/reset；UAT 是否启用取决于部署及 STORAGE_BACKEND=DSP 配置。** 原有 DATABASE/UAM 模式保持各自行为，SQL 重跑不会自动替换存储模式。

用户确认的请求是 POST `credentials/v1/persist`，字段仅 `clientId`、`closeId`、`passwordHash`：

- 业务 clientId 从当前有效 HTH Management 的 UAM_CLIENT_ID 取得，与 Header 的网关身份分开。
- closeId 使用完整的已认证 `USER@party`；本地表仍使用真实 profile key，兼容旧短用户名。
- passwordHash 为 `Base64(SHA-256(UTF-8(password)))`，直接编码 32 字节摘要，输出 44 字符并含末尾 `=`。不 trim、不改变大小写、不先转 hex。用户后来贴的 64 位 hex 样例不覆盖此前明确确认的 Base64 规则。

新增 `DspApiPasswordPersistRequest`、`DspApiPasswordPersistClient`、`DspApiPasswordResponsePolicy`、`HthDspApiCredentialAdapter`。应用默认信任库与主机名校验保持启用；不会跟随重定向或自动重试密码 POST。网关凭证只从环境配置读取，日志仅包含阶段、状态码及异常类型。

主流程顺序为：身份/密码/Code 校验 → Code 和操作预留 → DSP POST → 响应确认 → Code USED、Operation SUCCESS、STATE ACTIVE → 既有通知。DSP 模式不写本地密码哈希，旧 DATABASE 密码不会自动同步。

OPERATION 新增 REMOTE_CLIENT_ID，记录当前业务 client 与操作的绑定。DSP 模式的状态来自同一 party、profile、client 下已确认的 DSP 操作：无记录为 NOT_SETUP，SUCCESS 为 ACTIVE，IN_PROGRESS/UNKNOWN 优先返回 UNKNOWN。没有假设远程 status API 存在；当前做法不能发现外部系统独立修改或锁定密码。

已确认成功的 requestId 重放不重复 POST 或发送通知；换 backend/client 后不复用旧请求结果。POST 前确定失败会释放 Code；超时、未知响应、远端成功后本地提交失败保留待核对记录。没有成功确认时不会发送成功通知。

**响应样例仍未取得。** 默认只确认 HTTP 200/201/204 且空响应体；202、未知 JSON 和错误响应不确认。返回 JSON 时，通过 `DSP_SUCCESS_HTTP_STATUSES`、`DSP_SUCCESS_JSON_POINTER`、`DSP_SUCCESS_JSON_VALUE` 配置正式成功规则，不能猜测业务字段。这个默认是客户端约定，UAT 必须与 DSP 实际契约核对。

部署时运行新版脚本 1，部署 module、common extxface、ext-xface app.impl 与 Operation ORM；脚本 4 设置 DSP 模式、完整 URL、专属 APIC 凭证，原 Code 密钥保留；运行脚本 7，再刷新所有节点配置/ORM。首次 DSP 使用应重新生成并批准 SETUP Code，成功后自动选择 RESET。具体变量、响应配置及验收步骤见 [部署 README](</Users/devs/CProj/hth-application/consulting/db/branch_change_history/20260907_HTH_API_Password/README.md>)。

本地验证已通过：独立 SHA-256/Base64 向量；实际 Jersey HTTPS 的 POST、Header、JSON、响应匹配、证书拒绝、超时、重定向及错误分支；实际 `change()` + OBDX/EclipseLink/H2 的 setup/reset 成功、错误 Code、重复请求、client 切换、未发送失败释放、结果未知阻断和本地提交失败。事务测试隔离了会话解密、远程服务和实际通知；另有真实 RSA/HTTPS 测试。尚未连接银行 DSP 或验证 Token，新旧密码的实际认证效果需要 UAT 验收。

## 6. 源码证据

| 来源 | 内容 |
| --- | --- |
| [BCO postUpdate 与 signedBy](</Users/devs/CProj/hth-application/consulting/middleware/projects/module/com.ofss.digx.cz.bea.module.sms/src/com/ofss/digx/cz/bea/app/sms/service/user/ext/CZUserExtensionDataExt.java:138>) | 实际审批人收集及 OMB 路径 |
| [BCO sendNotifications](</Users/devs/CProj/hth-application/consulting/middleware/projects/module/com.ofss.digx.cz.bea.module.sms/src/com/ofss/digx/cz/bea/app/sms/service/user/ext/CZUserExtensionDataExt.java:685>) | user/AP、company 顺序与邮箱去重 |
| [HTH Code 审批通知](</Users/devs/CProj/hth-application/consulting/middleware/projects/module/com.ofss.digx.cz.bea.module.hosttohost/src/com/ofss/digx/cz/bea/app/hosttohost/service/HostToHostApiPassword.java:887>) | 已赋模板值、目前仅处理 user/company |
| [HTH 通知 SQL](</Users/devs/CProj/hth-application/consulting/db/branch_change_history/20260907_HTH_API_Password/6_HTH_API_Password_Notification.sql:318>) | 含变量的 Email 模板 |
| [模板 ORM](</Users/devs/CProj/hth-application/consulting/config_core/orm/eclipselink/mappings/alert/messageTemplate/MessageTemplate.orm.xml:69>) | 模板属性及数据源表 |
| [服务元数据 ORM](</Users/devs/CProj/hth-application/consulting/config_core/orm/eclipselink/mappings/commonsms/sms.MetaDataDefinition.orm.xml:63>) | ServiceAttribute 映射 |
| [BCO Login PIN 元数据清单](</Users/devs/CProj/hth-application/consulting/db/branch_change_history/251209_Self_Rest_Pin_WO112611/251209_Self_Rest_Pin_WO112611_fallback.sql:47>) | 既有回退脚本证明使用相应属性配置；不能把回退脚本用于部署 |
| [现有通知检查](</Users/devs/CProj/hth-application/devtools/backend-compile/tests/verify_hth_notifications.py>) | 当前使用 doubles，变量验证仅检查 getter |
| [Password 页面](</Users/devs/CProj/hth-application/consulting/channel/extensions/components/host-to-host/api-password/api-password.html:10>) | 两个密码输入和 Code 输入 |
| [当前远端 adapter](</Users/devs/CProj/hth-application/consulting/middleware/projects/ext-xface/com.ofss.digx.cz.bea.extxface.app.impl/src/com/ofss/digx/cz/bea/extxface/hosttohost/adapter/impl/HthApiCredentialAdapter.java:77>) | 当前 write 请求契约 |
| [本地密码散列](</Users/devs/CProj/hth-application/consulting/middleware/projects/module/com.ofss.digx.cz.bea.module.hosttohost/src/com/ofss/digx/cz/bea/app/hosttohost/util/HthApiPasswordHash.java:79>) | PBKDF2，不是 DSP 单次 SHA-256 |
| [HTH Management ORM](</Users/devs/CProj/hth-application/consulting/config/orm/eclipselink/mappings/cz/hosttohost/HthManagement.orm.xml:16>) | UAM_CLIENT_ID |

## 7. 本次实现与验证

已修复：

1. API Password 两个密码框增加独立眼睛按钮，复用 BCO 的 icon-eye/icon-eye-slash。当前 JET 8.3 不支持原生 mask-icon 属性，采用明确的 button 控件；三语提示、键盘和 aria-pressed 均已覆盖，OTP 无显示按钮。
2. 脚本 6 补六个 Code 邮件模板的 12 组属性/数据源映射及 2 组通用/服务属性，精确指向 `HthApiPasswordActivityLogDTO`。脚本 7 可查出缺失/错误映射。重跑先清理本功能模板子记录，再替换模板，保留服务属性主记录及其他业务单元配置。
3. HTH Code 审批读取实际交易 signedBy，按 target user → all actual signers → company 派送；邮箱去空格并不区分大小写去重。OMB 无签署人时采用已认证操作人；每个事件有独立日志 DTO，联系人/发布失败不终止其他收件人。

BCO 公共服务和配置未修改。Setup/Reset 的用户 Email + SMS 规则、RSA 协议和 Code 验证保持原样。新增 DSP 存储流程见第 5 节，原 DATABASE/UAM 行为保持。851/1216 的全部签署人范围仍须独立确认。

已通过的本地验证：

- 修改的 HTH 后端服务及当前 DTO 使用 Java 8 目标编译；依赖采用项目现有类库。
- `verify_hth_notifications.py`：生产方法体路由、所有实际签署人、重复邮箱、OMB、异常隔离、DTO 独立性。
- `verify_hth_deployment_sql.py`：实际通知 DML 的两次执行、12 组映射、缺失映射修复、外键顺序、其他业务单元隔离和回滚；SQLite 仅适配 Oracle 日期/用户表达式，不代替 Oracle 执行。
- `verify_hth_template_metadata.py`：实际 OBDX 元数据读取器按 SQL 字段路径调用生产 DTO getter；仅基类银行配置引导使用替身，不发送邮件。
- `hth-api-password-visibility.test.js`：本机 Edge 中实际 Oracle JET/Knockout 页面，验证 setup/reset 的眼睛、键盘、输入值、清空、OTP 隐藏及移动布局。
- 原有真实前端 → JCA → 后端解析的 setup/reset 传输测试、组件打包测试及 60 个多语言错误场景通过；四个修改的 JS/NLS 文件通过项目自定义 ESLint。

部署：前端组件及三语资源、HTH 后端服务、完整脚本 6，然后执行脚本 7 并按既有方式刷新各节点配置缓存。用新 Code 审批检查实际所有收件人和三语邮件内容；本地测试不证明 UAT 已投递。详见部署目录 README。
