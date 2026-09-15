# HTH Notification & Audit Log — Technical Design Review

**更新：2026-09-16。** 851 已按用户确认的联系人规则实现；1216 Link/Edit 通知也已实现，按当前规则假设运行、独立开关默认关闭，收件矩阵仍待确认。两者本地编译、运行、SQL 检查通过；791 仍是技术设计。SQL 尚未在 UAT 执行，真实收件尚未验收。成套模块、SQL、状态和测试分别见 [851 部署说明](</Users/devs/CProj/hth-application/consulting/db/branch_change_history/20260915_HTH_Profile_Contact_851/README.md>)、[1216 部署说明](</Users/devs/CProj/hth-application/consulting/db/branch_change_history/20260916_HTH_User_Access_1216/README.md>)。

## 1. 三个 Story 的结论

| Story / TD | 设计文档 | 核心改动 | 评审重点 |
| --- | --- | --- | --- |
| 851 / 1295 | [Profile Contact Update Notification](</Users/devs/CProj/hth-application/story/notification & audit log/BCOH2H-851-TECHNICAL-DESIGN.md>) | 最终批准后的新旧联系方式快照、按变更类型通知、一次 Email fallback | 联系人来源、#7/#8/#9 收件规则、模板语言 |
| 1216 / 1303 | [User Accounts & Service Access Notification](</Users/devs/CProj/hth-application/story/notification & audit log/BCOH2H-1216-TECHNICAL-DESIGN.md>) | 在 HTH 独立权限生效路径接入通知，复用 BCO 派送 | 不能直接认定零开发；用户权限与公司 HTH 开关是不同服务 |
| 791 / 1299 | [HTH Onboarding Audit Log](</Users/devs/CProj/hth-application/story/notification & audit log/BCOH2H-791-TECHNICAL-DESIGN.md>) | 任务映射、安全业务摘要、现有查询及导出增强 | request 与 response 都要脱敏；操作人和目标用户不能混淆 |

851/1216 TD 已更新为实际实现、验证结果与待确认项；791 保留设计方案。三份 TD 包含 AC 映射、数据来源、时序、代码/SQL、异常处理、BCO 影响及 SIT/UAT 用例。下文集中描述共用部分，避免两个通知 Story 各自实现一套 fallback 或重复修改公共类。

## 2. 公共代码改动及 BCO 影响

下表覆盖三个 Story，类型沿用评审时的“必改/条件修改”分类。851/1216 的实际交付以各自实现版 TD 和部署说明为准；791 为拟改清单。两个通知 Story 已使用独立同事务 ledger，共用派送状态机，并在原 BatchExecutionScheduler 之后分别消费；原因是现有 ActivityLog 无法保证提交前不派送及业务去重。

| 代码 / 配置 | Story | 类型 | 拟改内容 | BCO 影响与必要回归 |
| --- | --- | --- | --- | --- |
| SMS `UserExtensionData` | 851、791 | 必改，共用入口 | HTH Contact before/after、批准后事件；安全审计上下文 | 普通用户及 Merchant 原通知不变；回归 User Create/Edit、审批、Email/Mobile 通知 |
| BCO `UserAccountAccessExt` | 1216 | 参考复用，原则上不改 | 参考现有通知 DTO 和文案；不把整个 postCreate 接到 HTH | 避免重复 eAdvice / BCO 权限副作用；回归原账户服务权限通知 |
| HTH `HostToHostUserAccess` | 1216、791 | 必改，HTH 服务 | 最终生效差异和 reference 一次计算，分别供通知与审计使用 | 业务授权、Related/Associated 校验及审批规则不变 |
| HTH `HostToHostApiPassword` | 791 | 必改，HTH 服务 | 生成、重新生成、setup/reset 的安全结果摘要 | 回归真实 setup/reset、幂等、获准查看旧 Code；不改传输/存储协议 |
| xface DTO / 小范围 helper | 三个 | 新增 | 版本化通知上下文与审计投影 | 通知需要的地址不进入通用审计；不改变原公共 DTO 的业务响应 |
| `EmailDispatcher` / `SMSDispatcher` | 851、1216 | 条件修改，共用类 | HTH event 的收件角色、地址来源及关联；保留旧值快照 | HTH allowlist 内适配；BCO 原事件不改变收件人或派送次数 |
| Bounce Batch 实际部署源码 | 851、1216 | 条件修改，共用批处理 | HTH API Email 一次 fallback、失败终态及原 reference 关联 | 不能让原通用 fallback 再次接管并扩散到 AP/SYSADM/Webmail |
| `CZAsyncAuditHandler` | 791 | 必改，共用类 | 在日志、Audit Stack 和 JMS 前构造 HTH 安全 payload，覆盖 REST/SERVICE/HOST | 保持原 BCO 路由、过滤、状态；回归 BCO 审计完整性与性能 |
| `CZVoidAuditExt` / `LocalAuditRepositoryAdapter` | 791 | 前者条件适配，后者原则上不改 | task 名称、安全详情；仅在明确查询需求不足时扩展条件 | 保留 FMO、Party/targetUnit 隔离、现有 User ID 条件语义 |
| Audit Log 前端 / 实际 Export DTO、XSL | 791 | 条件展示增强 | 原页面增加 HTH 详情、任务文案，导出同一安全摘要 | 普通 BCO 版式/分页/导出不变；JS ESLint，CSS/SCSS 同步 |
| Activity/Event/Recipient/Template/Task SQL、nls、Preferences | 三个 | 增量配置 | HTH 独立事件、完整父子映射、标签及控制项 | 不覆盖旧模板、整份 event 列表、权限或已有运行流水 |

仅新增 HTH 命名的类不能证明 BCO 无影响。真正风险集中在 UserExtensionData、Dispatcher/Bounce、Audit Handler、Audit UI/Export 和共享配置；这些单元必须纳入每月 BCO 回归。

## 3. 共用通知设计

### 3.1 业务服务与通知平台边界

业务 Service 负责最终审批、生效状态、before/after、目标用户和 reference。通知 helper 负责角色解析、模板上下文和同事务意图写入；后台发布者注册已提交的活动，Repository 管理派送状态。不另建用户权限或密码存储。

通知 DTO 放入现有 xface 模块对应包。共享策略只依赖稳定的基础 DTO / Alert 接口，避免引入 SMS 与 HostToHost 模块互相依赖；部署清单要包含实际新增的 DTO、helper JAR 和相关配置。

851、1216 共用以下规则：

- 只有最终批准并生效的变化产生成功通知；拒绝、取消、待批不发。
- 收件人由服务器解析为角色、通道、地址、模板，携带原业务 reference；不信任浏览器传入的目的地址。
- API Contact、Company Contact、Final Approver 分开解析；同地址的去重还要考虑模板语义。
- 收件地址属于受控派送数据，不进入普通日志/审计，不把某角色的地址通过模板泄漏给其他角色。
- 显式旧地址必须保持快照。Dispatcher 当前有按 event 重新取用户 Email/Mobile 的逻辑，实施需确认其不会将旧地址覆盖为更新后的地址；有差距时只在 HTH 事件分支处理。
- MNG submission、最终 delivery receipt、fallback 是三个阶段；收到邮件不能推导短信成功，MNG 表无记录也不能单独证明没有其他派送路径。

### 3.2 事务与去重：实施结果及环境验收

现有 ActivityData ORM 映射到 `DIGX_EP_ACT_LOG_B`，包含 ActivityLog BLOB 和 `TXN_REF_NO`；它证明有可复用的持久化载体，**未证明所有派送与业务事务原子提交，也未证明业务 reference 唯一**。

| 平台验证项 | 实施所需结果 | 若不满足 |
| --- | --- | --- |
| Activity/Event 与外层批准事务 | 回滚不发成功通知；commit 后存在可恢复的通知意图 | 在现有 Alert 扩展点补提交联动；不能在 finally 或仅内层 close 后直接发 |
| 并发重入 | 同 reference + event + 业务主键只有一份意图 | 使用平台唯一约束/原子注册等价能力；不能仅做 SELECT 后 INSERT |
| 进程重启 / MNG 暂停 | 可重试未完成意图，无需重放业务修改 | 补平台持久重试消费，不能仅用内存线程或 catch 后丢弃 |
| 收件人持久上下文 | 原地址、角色、模板版本、reference 可恢复 | 扩展 ActivityLog DTO 并验证序列化兼容，不写进普通日志 |
| 回执关联与 fallback claim | 同一失败派送只产生一次 fallback，多节点批处理不重复 | 补平台派送关联及原子 claim，不靠邮箱/时间模糊匹配 |

实际实现没有新增权限/审批业务表，已新增两个独立派送 ledger：851 `DIGX_CZ_HTH_CONTACT_NOTIFY`、1216 `DIGX_CZ_HTH_ACCESS_NOTIFY`。意图用当前 DIGX ORM 写入，后台共用 Repository 状态机和原 BCO MNG builder；本地已验证 commit/rollback、并发 claim、MNG-before-IO 和一次 fallback。上述平台验证项仍需真实 WebLogic/JTA/MDB/Oracle 证据。外部网关超时结果归 UNKNOWN，不自动重发，不声称端到端 exactly-once。

### 3.3 Bounce Back 状态

实际 ledger 状态为 READY、PUBLISHING/PUBLISHED、SENDING、SUBMITTED、REJECTED、UNKNOWN、DELIVERY_FAILED、FALLBACK_QUEUED、FAILED_FINAL。SUBMITTED 是网关接受，不代表最终送达；负回执以完整 HC/HA reference 关联。具体可恢复和终止条件见部署说明，不要直接改 READY 来重发结果未知的通知。

851：BCO fallback Email 再失败就停止，属于 AC 明确规则。1216：当前已实现一次终止，仍待该 Story 规则确认；公司已在同次原收件中时不会再次发送相同内容。两个 Story 均不因 Company Contact 或 Approver 原通知失败而向全公司扩散。

回执批处理现有查询会使用当前 User Profile 匹配 MNG CUSTOMERID / RECIPIENTID。851 的旧 Email 已不在当前 Profile 时，仅靠该匹配可能丢失身份；需优先按派送 reference 找原始角色与活动上下文。配置高风险 event 列表必须与 fallback 策略一起发布，不能单独追加 event 就认定 AC4 完成。

## 4. 共享 UAT 与每月 BCO 发布

### 4.1 发布控制建议

H2H 上线较晚、BCO 每月上线且共用 UAT，已实现两个独立控制项：`HTH_PROFILE_CONTACT_NOTIFICATION_ENABLED`、`HTH_USER_ACCESS_NOTIFICATION_ENABLED`，首次 SQL 部署默认 false。应用读取、Preferences provider、底表 SQL 与验证查询均已配套；只配置数据库而未部署同版模块仍不能生效。

控制项使用项目现有 Preferences/配置提供者机制；实施必须成套提供读取代码、`Preferences.xml` 类别绑定、底表配置 SQL 和值校验，不只新增数据库一行。类别名及 provider 沿用确认后的部署契约，不猜测配置视图可写性。

| 环境 / 开关 | 预期 |
| --- | --- |
| BCO 月度版本，新通知关闭 | 保留上线前的通知路径，包括既有 HTH/BCO 通用通知；不改变普通 BCO 行为 |
| 共享 UAT，指定 HTH 测试范围启用 | HTH 按新矩阵验证，普通 BCO 仍走原规则；使用受控测试联系人 |
| HTH 正式启用 | 三方确认模板、收件人、fallback；通过并发/失败/对账验证后开启 |
| 临时停用新通知 | 停止该 Story 的新意图和派送；未完成记录保留，恢复开关后继续，在途网关请求不能撤回 |

开关仅控制这两个 Story 的新通知，不是绕过原 BCO 通知的总开关。791 的审计脱敏必须持续生效，不能因为 HTH 通知开关关闭就恢复记录秘密；正在开放的 HTH 功能也不能因“尚未正式上线”而免审计。

如需要 UAT 按指定公司灰度，复用项目已确认的环境/公司范围机制；若没有，另行定义显式 allowlist，不在代码里写死测试 User ID。不把生产配置复制为 UAT 的真实收件地址。

### 4.2 建议实施与交付顺序

1. 固定联系人定义、通知矩阵及模板文案；确认审批/派送/审计的真实平台事务和回执能力。
2. 提交独立的共用 helper、DTO 及审计安全投影；做 BCO 公共链路回归。
3. 分别接入 851、1216 的 HTH 生效事件和 791 的业务摘要，保持可单独 review 的提交。
4. 部署增量 SQL、nls、模板及实际 Batch 版本；核对新增文件都在构建产物中。UAT/PRD Batch 源码有多份时按实际构建来源同步本次变更，不覆盖环境差异。
5. 在新通知关闭和启用两种状态下运行 BCO/HTH 回归；覆盖多级审批、并发、回滚、网关失败、fallback、审计与导出。
6. 发布清单记录代码 SHA、JAR/前端/Batch 版本、SQL 顺序、配置值、模板版本和验证 reference。BCO 每月合并公共改动时复查同一清单。

回滚先停新增 HTH 通知注册，再按依赖恢复应用/配置。保留已批准业务、通知流水、回执和审计；禁止以清空数据作为回滚。审核公共包回滚不会移除仍在运行的 HTH DTO 或恢复敏感审计 payload。

### 4.3 BCO 最小回归集合

| 领域 | 必测场景 |
| --- | --- |
| 用户管理 | Create/Edit、原 User Channel、Email only/Mobile only/both、Merchant 路径、Maker/Checker |
| 权限维护 | BCO To link/Edit/Delete、Related/Linked 场景、eAdvice 等原扩展行为 |
| 通知 | 原 Email/SMS 模板及目的地址、单次/多级审批触发次数、Bounce Back 原行为 |
| Login PIN / API Password | BCO Login PIN 通知不变；HTH setup/reset 正常、Code reveal 正常 |
| Audit | BCO 查询、分页、过滤、详情、导出、权限隔离；无重复/遗漏；HTH 敏感字段全链路排除 |
| 稳定性 | JMS/批处理暂停恢复、多节点重入、网关超时、模板缺失、配置缓存刷新 |

## 5. 评审决策清单

| ID | 决策 | 当前证据 / 缺口 | 建议负责人 | 决策影响 |
| --- | --- | --- | --- | --- |
| D1 | API Contact 是否为 HTH User 联系方式；BCO Account Profile 是否为公司 officeEmail | 851 已确认；1216 当前采用相同来源，待确认 | BA/PO + User Management 开发 | 两个通知 Story 的数据来源 |
| D2 | 851 #8/#9 是否通知一次未变通道；Approver 是否双通道 | 851 已确认：未变通道一次；最终 Approver Email + SMS | BA/PO | 收件次数和模板 |
| D3 | 1216 每个角色 Email + SMS 还是 Email or Mobile | 当前按双通道实现；#3/#4/#5 编辑痕迹冲突，启用前需确认 | BA/PO | 不能提前承诺“三方都收到邮件短信” |
| D4 | #3 公司 disable 与用户整组 access delete 的归属 | 两套独立 Service；名称混用 | BA/PO | 1216 开发与测试范围 |
| D5 | #3 正确 Email、#9 标题、语言组合及占位符 | #3 仍为 PIN Activation，#9 标题/正文不一致，&1..&5 未定义 | BA/PO + 通知模板负责人 | 最终 SQL/模板不能直接照抄 PDF |
| D6 | 1216 是否采用一次 fallback 后终止；“may”具体启用规则 | 851 明确终止；1216 已按一次补发/公司原收件去重实现，待确认 | BA/PO | Bounce 策略及收件边界 |
| D7 | 活动注册事务、持久去重、派送回执关联 | 851/1216 已实现同事务 ledger、并发领取及 MNG-before-IO，本地运行通过；UAT 待验证 | Alert/平台开发 + DBA | 是否只需适配，是否需额外持久化设计 |
| D8 | 791 详情原型、targetUser 检索、导出链路 | PDF 提到 Figma 未给内容；现有查询按 actor | BA/PO + 前端/平台开发 | UI/接口改动范围 |
| D9 | 共用 UAT 启用范围、版本清单与 Batch 构建来源 | HTH/BCO 发布节奏不同 | Release/UAT 负责人 | 防止月度发布漏文件或提前启用 |
| D10 | 851/1216 通知最终一位还是全部实际审批人 | 851 已确认；1216 当前只通知最终一位、待确认，Code 通知全部签署人规则不外推 | BA/PO | approverIds、收件顺序与去重范围 |

已确认项仅按表中 Story 范围生效；其余继续作为评审待办。851/1216 三语正式文案与真实 UAT 派送证据仍需补齐。

## 6. 本次检查范围与限制

2026-09-15 补充审查见 [API Password 页面、通知与 DSP 核对](</Users/devs/CProj/hth-application/story/API Password/20260915-API-PASSWORD-REVIEW-NOTES.md>)。已将模板属性/数据源/元数据配置链补入 851、1216 的 SQL 设计。Code 审批通知的全部审批人规则作为新增证据记录；DSP 凭证持久化属于独立对接范围，不并入这三个 Story。

已对照本目录全部三个 Story、七页补充矩阵、相关 HTH/BCO Service、Dispatcher/Bounce、审计 Handler/ORM/UI/SQL。补充矩阵保留的红字、绿色替换文字和删除线已按待评审内容处理。

没有访问 UAT 数据库、网关或部署服务器，没有验证通知实际送达或运行时导出链路。851/1216 已用生产源码配合 H2、真实 OBDX ORM/metadata 完成提交/回滚、并发、派送失败及 SQL 测试；1216 全部 12 个模板已用真实 DTO getter 取值后替换验证。银行仓库、Event 与网络使用 fixture，仍需 UAT。791 实施、通知矩阵签定和真实送达验收属于后续工作。

## 7. 源文件索引

| 需求来源 | 使用范围 |
| --- | --- |
| [851 PDF](</Users/devs/CProj/hth-application/story/notification & audit log/[%23BCOH2H-851] Customer onboarding - CM - Profile Contract Update Notification.pdf>) | Profile 联系方式通知 |
| [1216 PDF](</Users/devs/CProj/hth-application/story/notification & audit log/[BCOH2H-1216] CM - User Accounts & Service access - Notification - Jira.pdf>) | User Account & Service Access 通知 |
| [791 PDF](</Users/devs/CProj/hth-application/story/notification & audit log/[%23BCOH2H-791] CM - Audit Log - Enhance BCO Audit Log to Support HTH API Onboarding Activities.pdf>) | HTH 业务审计 |
| [matrix PDF](</Users/devs/CProj/hth-application/story/notification & audit log/292819003_42a73221555b491c9269d186f430d7bc-140926-0744-3.pdf>) | 收件人与模板矩阵，Pending Review |

| 共用源码证据 | 支持内容 |
| --- | --- |
| [UserExtensionData — 原通知](</Users/devs/CProj/hth-application/consulting/middleware/projects/module/com.ofss.digx.cz.bea.module.sms/src/com/ofss/digx/cz/bea/app/sms/service/user/UserExtensionData.java:1911>) | BCO 通用用户维护及联系方式通知实现。 |
| [BCO UserAccountAccessExt](</Users/devs/CProj/hth-application/consulting/middleware/projects/module/com.ofss.digx.cz.bea.module.access/src/com/ofss/digx/cz/bea/app/access/service/account/party/user/ext/UserAccountAccessExt.java:207>) | isAdmin=false 分支、DTO 字段及 USER_ACCOUNT_ACCESS_UPDATE 注册。 |
| [EmailDispatcher](</Users/devs/CProj/hth-application/consulting/middleware/projects/module/com.ofss.digx.cz.bea.domain.service.dispatch/src/com/ofss/digx/cz/bea/domain/service/dispatch/EmailDispatcher.java:1051>) | 既有派送入口、收件人处理及 MNG 路径。 |
| [SMSDispatcher](</Users/devs/CProj/hth-application/consulting/middleware/projects/module/com.ofss.digx.cz.bea.domain.service.dispatch/src/com/ofss/digx/cz/bea/domain/service/dispatch/SMSDispatcher.java:327>) | 事件对应的用户/手机号码解析配置。 |
| [ActivityData ORM](</Users/devs/CProj/hth-application/consulting/config_core/orm/eclipselink/mappings/alert/eventGeneration/ActivityData.orm.xml:4>) | DIGX_EP_ACT_LOG_B、ActivityLog BLOB 及 TXN_REF_NO。 |
| [EmailMNG ORM](</Users/devs/CProj/hth-application/consulting/config/orm/eclipselink/mappings/cz/mng/EmailMNG.xml:4>) | 邮件/短信 MNG 提交记录的共用数据映射。 |
| [UAT Bounce Batch](</Users/devs/CProj/hth-application/consulting/middleware/batchJobs/UAT/inboundbatchprocessor/ValidateAndSendBounceNotify.java:122>) | 高风险事件和 MNG 回执关联；实施需确认真实部署源码。 |
| [Email 回执导入](</Users/devs/CProj/hth-application/consulting/middleware/batchJobs/UAT/inboundbatchprocessor/LoadMNGmsgCCBEMAIL.java:14>) | 与网关提交结果分开的回执处理。 |
| [SMS 回执导入](</Users/devs/CProj/hth-application/consulting/middleware/batchJobs/UAT/inboundbatchprocessor/LoadMNGmsgCCBSMS.java:14>) | 短信最终回执关联的检查入口。 |
| [Audit Handler](</Users/devs/CProj/hth-application/consulting/middleware/projects/module/com.ofss.digx.cz.bea.module.common/src/com/ofss/digx/cz/bea/app/audit/handler/CZAsyncAuditHandler.java:376>) | HOST/SERVICE/REST 审计细节采集及非 GET response 路径。 |
| [Audit Extension](</Users/devs/CProj/hth-application/consulting/middleware/projects/module/com.ofss.digx.cz.bea.module.access/src/com/ofss/digx/cz/bea/app/audit/service/ext/CZVoidAuditExt.java:63>) | 任务名称增强、过滤及 FMO 控制。 |
| [Audit 页面 Model](</Users/devs/CProj/hth-application/consulting/channel/extensions/components/audit/audit-log/model.js:111>) | 现有 activity 下拉及审计查询 API。 |
| [Audit Export XSL](</Users/devs/CProj/hth-application/consulting/config_core/resources/com/ofss/digx/app/audit/dto/AuditListResponseDTO.xsl:86>) | 源码中的现有审计报告转换；实际 UAT 调用链待验证。 |
| [Preferences.xml](</Users/devs/CProj/hth-application/consulting/config/Preferences.xml:1>) | 851/1216 通知配置类别已绑定；791 尚未实现。 |
