# BCOH2H-791 - HTH Onboarding Audit Log（简版技术设计）

2026-09-17 · 代码基线：`8add860d` · **设计已确认，已完成本地实现；UAT 验收待执行**

依据：同目录 791 AC PDF（2026-09-16 更新）及用户提供的 CM Audit Log 截图。PDF 的 Scenario 4、5 重复编号，下面按场景名称对照。

**建议复用已有 BCO 审计机制和 HTH Activity，补齐记录内容、Code 脱敏和查询展示。无需另建一套 HTH Audit Log。**

## 1. 截图已有的 Activity，能覆盖到哪里

| 截图中的 Activity | 当前 Task | 覆盖关系 |
| --- | --- | --- |
| HTH API Password - Generate | `UAT_N_HAP_GEN` | 生成和重新生成 Code 都调用这个接口；已有审计配置，但还没有明确区分两种操作 |
| Set Up HTH API Password | `CM_N_HAP_SETUP` | 首次设置 API Password，已有审计配置 |
| Reset HTH API Password | `CM_N_HAP_RESET` | 重设 API Password，已有审计配置 |
| HTH API Password - Reveal | `UAT_N_HAP_RVL` | 查看 Code 的已有审计；保留，但它不能代替“重新生成”记录 |

下拉框来自 `resourceTasks?aspects=audit&view=list`，说明当前环境返回了这些 Task。**它不能证明操作后已经写入记录，也不能证明内容、结果及脱敏符合 AC。**本次没有连接 UAT 数据库验证实际记录。

## 2. 各 AC 的现状与调整

| AC 场景 | 当前代码情况 | 建议处理 |
| --- | --- | --- |
| 创建 HTH 用户 | `UserExtensionData.create` 已有 `MT_N_CUS` 审计；请求 DTO 已有 `userChannelType` | 沿用 BCO 创建用户 Activity；确保详情有目标用户、公司、HTH Channel，以及操作人、时间、动作和结果，不重复新增一条 HTH 创建记录 |
| 修改 HTH 用户 | `UserExtensionData.update` 已有 `MT_N_UUS` 审计 | 沿用 BCO 修改用户 Activity；补充 HTH 相关修改的目标用户及修改前后 Channel。识别被修改用户，不能按当前管理员自己的 Channel 判断 |
| 创建用户时生成 Code | 已有 Generate Task；生成后先是 `PENDING`，原用户维护审批完成才激活 | 复用 Generate 记录，补充目标用户、公司、Code ID、用途、生成动作及状态；生成成功不表示用户已批准或 Code 已可用 |
| 编辑用户时重新生成 Code | 仍调用同一个 Generate 接口；已有替换旧 Code 的业务逻辑 | 在同一 Activity 的详情中区分 `GENERATE` / `REGENERATE`，保留新旧 Code ID，不记录 Code 内容；不额外建立 Re-Generate Task |
| API Accounts & Services Access 新增、删除、修改 | 已有 `UAT_N_HUA_NEW / EDT / DEL` 三个 Task，Java 声明了审计；SQL 复制对应 BCO 权限 Task 的配置 | 复用三个 Task；补充目标用户、关联公司、账户及 API Service 的实际新增/移除变化，不能只记录“调用了 Edit” |
| API Password 首次设置、重设 | Setup / Reset 已有审计 Task；现有请求是加密输入 | 保留两项 Activity；明确记录目标用户、操作、时间、业务结果及安全的错误码，不保存密码、Code 或传输凭证 |
| 原 Audit Log 查询、展示、导出 | 已有查询接口、Task 名称转换、列表及详情代码 | 沿用原日期、Activity、User ID、公司、动作、结果等条件及权限；补齐 HTH 记录的可查、可看、可导出验证 |

**为什么搜 `hth` 不一定能看到全部场景：**创建/修改用户继续使用 BCO 原 Activity 名称，名称中可能没有 HTH。应通过原创建/修改用户 Activity 查到记录，再看详情中的 Channel。这符合 AC 中沿用 BCO 原逻辑的要求。

## 3. 怎么补，尽量保持小范围

### A. 沿用原记录，只加必要信息

- 主记录继续使用原来的操作人、时间、公司、Task、动作、结果、交易参考号。`User ID` 查询仍表示操作人；目标用户单独放在详情，避免把 Maker/Checker 改写成目标用户。
- HTH 详情只保留白名单字段：目标用户、公司、Channel、业务操作、业务阶段/结果、参考号；Code 操作另外保留 Code ID、前一 Code ID、`SETUP/RESET` 用途和状态。
- `GENERATE/REGENERATE` 根据服务端生成前的 Code 记录判断；`SETUP/RESET` 是用途，不能用它代替“是否重新生成”的判断。Code ID 与用户审批记录关联，沿用审批参考号，不凭空填写尚未产生的参考号。
- 用户维护沿用 BCO 的提交、审批和结果记录。待审批只表示提交成功，不标记为业务已生效；最终成功以原业务处理和事务结果为准。
- 账户/服务修改在最终批准执行时，对比变更前的有效授权与校验后的新授权，记录增加和移除项；删除记录被移除的授权。必须在当前“先停用再重建”逻辑之前取得旧值，否则无法正确判断差异。账户展示沿用 BCO 脱敏规则，服务保留可识别的 ID/名称。
- Setup / Reset 按实际业务结果记成功/失败；不能只看 HTTP 状态或顶层 `result=SUCCESSFUL`。同一 requestId 重试可以保留请求审计，但不能伪造成又发生一次密码修改。

### B. Code 脱敏是必须补的地方

当前 Generate / Reveal 都是 POST，会返回含明文 `code` 的 DTO；`CZAsyncAuditHandler` 会复制非 GET 响应，并在交给异步审计前输出整个 Audit DTO。当前代码没有看到 HTH 专用脱敏，**存在明文 Code 进入审计及应用日志的路径**，实际 UAT 是否已有此数据仍需核查。

拟在公共审计入口加一个范围明确的 HTH 处理分支：

1. 仅处理上述 HTH 服务及用户维护中的 HTH 数据；普通 BCO 原分支继续执行。
2. 在审计对象打印、序列化及异步写入之前，处理 REST / SERVICE / HOST 明细副本，按白名单形成安全内容；不能只在前端隐藏。
3. 排除密码、明文 Code、密码 hash、密文、密钥和传输凭证；保留业务错误码，例如 `DIGX_CZ_HTH_API_PASSWORD_...`，不能把所有名为 `code` 的字段一律删除。
4. 不修改返回浏览器的业务 DTO，保证已有 Generate / Reveal 查看 Code 功能正常。
5. 脱敏处理异常时，不回退写原始敏感内容；保留可用的安全元数据，并只记录处理阶段和异常类型。

这部分需要接触公共审计类，不能承诺“完全不改公共代码”。但不改异步消费者、batch、审计表结构或 BCO 的写入机制。

### C. 查询与页面沿用 BCO

- 核对三个 HTH User Access Task 的环境配置。当前 CM 页面会过滤 `ADMINISTRATION` 类型，而 Java 中三个 HUA Task 声明了此类型；实际返回类型还取决于环境 Task 配置，因此截图不足以判定它们缺失的原因。
- 若服务已返回这些 Task、但页面把它们过滤，只对这三个 CM HTH Task 补充显示条件；不全量开放 `ADMINISTRATION`，不为显示菜单修改审批 Task 分类。若服务未返回，先补缺失的 HTH audit/resource 配置。
- 当前公共详情组件残留旧版 `HTH_ONBOARDING_791` 摘要和独立 CSV 代码，还强制依赖仓库中已不存在的 `extensions/resources/nls/hth-audit.js`。本版清理这段不完整扩展，复用 BCO 原 JSON 详情，安全摘要放入其可显示的 REST 明细。
- 查询结果 JS 已有 `openJSON`，但当前扩展 HTML 未见点击绑定。实施时核对实际加载组件，补接已有详情查看方法及原权限校验，不另建详情页面。此处属于公共页面变化，需同时回归 BCO。
- 导出按现有 BCO 报表字段及入口处理 HTH 记录，不新增 HTH 独立 CSV。仓库中的标准 XSL 是摘要报表，不能据此声称已支持账户/服务逐项明细导出；UAT 要验证实际使用的标准导出入口。如需要新增逐项明细导出，应另行确认范围。

## 4. 预计修改范围、BCO 影响

| 位置 | 本次用途 | BCO 影响控制 |
| --- | --- | --- |
| `HostToHostApiPassword.java` | 补生成/重新生成、Setup / Reset 的安全审计信息 | HTH 专用服务，不修改密码校验、通知或 DSP 业务流程 |
| `HostToHostUserAccess.java` | 取得批准前后授权差异，形成审计明细 | HTH 专用服务，不修改审批或授权规则 |
| `UserExtensionData.java` | 仅在涉及 HTH 的创建/修改中补目标用户及 Channel 信息 | 公共用户维护类；普通 BCO 不增加 HTH 查询，不修改原 Task 和收件通知逻辑 |
| `CZAsyncAuditHandler.java` 及一个 HTH 专用辅助类 | 安全摘要并入原审计，写日志/审计前脱敏 | 公共入口小分支；辅助类只处理 HTH，不重写原 Handler，不另写一套审计 |
| Audit Log 搜索/结果/详情组件 | 按需补 HUA Activity 显示及详情入口，清理旧版缺失依赖 | 公共页面；原查询条件、BCO Task 显示和列表字段保持，回归详情加载与导航 |
| HTH 审计配置 SQL（有缺项才补） | 补 HTH Task 的 audit aspect/resource 关联，提供核对查询 | 可重复执行；只操作本功能配置，不覆盖 BCO Task，也不重跑整套审批脚本 |

复用 `DIGX_AL_AUDIT_LOGGING`、`DIGX_AL_AUDIT_LOGGING_DETAILS` 的现有主记录和明细。**不新增表/列、不改 batch、scheduler、通知公共包、审计查询 Repository 或跨公司权限规则。**

历史审计不自动删除或重写。若 UAT 抽查发现旧记录含明文 Code，需单独评审历史数据处理及读取防护；新写入脱敏不能代表历史数据已被清理。

## 5. 验收重点

1. 创建/修改 HTH 用户：原 Activity 可查；操作人、目标用户、公司、Channel、时间和结果正确；普通 BCO 创建/修改记录保持原行为。
2. 首次生成、重新生成 Code：同一 Generate Activity 能在详情中区分；审批前为 Pending，批准后能通过 Code ID/交易记录关联，取消/拒绝不显示为已生效。
3. 权限新增、Edit 中增删服务、整笔 Delete：记录正确的账户/服务变化；Maker 提交、中间审批和最终落地不混淆。
4. Setup / Reset 成功与失败：至少覆盖正确 Code、错误 Code、过期 Code、解密失败，以及相同 requestId 重试；结果与真实业务一致。
5. Generate / Reveal 的浏览器仍能按原权限查看 Code；审计明细、应用日志及导出均不能出现测试密码或 Code。覆盖嵌套响应、异常路径及审计处理失败。
6. 原 CM 页面可按日期、Activity、公司、操作人和参考号查询；详情正常加载，标准导出含对应记录；不能查看其他公司的记录。
7. BCO 回归：用户维护、账户权限、Login PIN、原审计搜索/分页/详情/导出，以及无权限角色访问。

先做真实业务操作，再核对审计主表、明细、CM 页面及导出；只有 Task 下拉框、编译或单元测试通过，不算 AC 完成。

## 6. 代码核对依据

- [UserExtensionData：创建/修改的原审计 Task](/Users/devs/CProj/hth-application/consulting/middleware/projects/module/com.ofss.digx.cz.bea.module.sms/src/com/ofss/digx/cz/bea/app/sms/service/user/UserExtensionData.java:544)
- [HostToHostApiPassword：Setup / Reset](/Users/devs/CProj/hth-application/consulting/middleware/projects/module/com.ofss.digx.cz.bea.module.hosttohost/src/com/ofss/digx/cz/bea/app/hosttohost/service/HostToHostApiPassword.java:190)
- [HostToHostApiPassword：Generate 与明文返回](/Users/devs/CProj/hth-application/consulting/middleware/projects/module/com.ofss.digx.cz.bea.module.hosttohost/src/com/ofss/digx/cz/bea/app/hosttohost/service/HostToHostApiPassword.java:670)
- [HostToHostUserAccess：三种维护 Task](/Users/devs/CProj/hth-application/consulting/middleware/projects/module/com.ofss.digx.cz.bea.module.hosttohost/src/com/ofss/digx/cz/bea/app/hosttohost/service/HostToHostUserAccess.java:267)
- [CZAsyncAuditHandler：复制响应](/Users/devs/CProj/hth-application/consulting/middleware/projects/module/com.ofss.digx.cz.bea.module.common/src/com/ofss/digx/cz/bea/app/audit/handler/CZAsyncAuditHandler.java:441)
- [CM Activity 筛选](/Users/devs/CProj/hth-application/consulting/channel/extensions/components/audit/audit-log/audit-log.js:361)
- [详情中的旧版 HTH 依赖及扩展](/Users/devs/CProj/hth-application/consulting/channel/components/audit/audit-log-results/audit-log-results.js:1)

本次按以下已确认范围实施：**复用现有 Activity，在详情区分 Generate / Re-Generate；账户/服务覆盖新增、修改和移除；统一保护密码及 Code；公共审计和页面只做上述必要调整。**


## 7. 实现与验证记录（2026-09-17）

已按上述范围实现。新增 `HthOnboardingAudit` 处理 HTH 审计副本及安全摘要，新增 `HthUserAccessAudit` 比较批准前后有效授权。公共审计入口只增加 HTH 分支；用户维护只为 HTH 生成摘要，沿用原 Task、审批和通知。

用户维护以实际写入和框架返回状态记录 `SUCCESS / NO_CHANGE / PENDING_APPROVAL / FAILURE`。Generate 成功另外标记 `codeStatus=PENDING`、`processingStage=PENDING_APPROVAL`，不表示 Code 已生效。Setup/Reset 记录 `idempotentReplay`，失败记录安全错误码和处理阶段。Reveal 原业务响应保留明文 Code，但新写入的审计副本排除该值。

**本地已通过：**

- 791 全部生产接入类对当前 SDK、Java 8 目标的编译。
- 两个修改的前端 JS 的项目 ESLint（含 OBDX 自定义规则）：0 error、0 warning。
- 实际审计 DTO 的 HOST/SERVICE/REST 安全投影、JMS 序列化、异常回退、操作人/目标用户区分、待审批和失败状态、普通 BCO 不改写；业务 Generate/Reveal 响应对象不被修改。
- 账户/服务增删、无变化及相同数量的服务替换，1000 条变更不截断；账户号脱敏。
- 执行实际页面 JS：原 JSON 详情、返回条件、false/0 值、大量 HTH 明细、CM 三个 HUA Task 筛选、BM 原筛选、已有详情接口跳转。
- 原 setup/reset 业务方法及 OBDX/EclipseLink + H2 事务回归：成功、Code 错误/过期/已用、解密失败、重试、提交失败及回滚。银行远端 DSP/通知在本地用 fixture，不能视为真实 DSP 联调。
- 851、1216 原通知回归，覆盖收件去重、最终审批人、原 BCO 通知基线、失败处理和通知调用顺序。1216 测试只把存储/网络/交易边界替换为 fixture。

**部署和 UAT：**

1. 同批发布 common、module.common、module.sms、module.hosttohost 的相关产物，以及四个 Audit Log 前端文件，避免只部署调用方而缺少新增 helper。
2. 执行 [1_HTH_Audit_Config.sql](/Users/devs/CProj/hth-application/consulting/db/branch_change_history/20260917_BCOH2H-791_HTH_Audit/1_HTH_Audit_Config.sql)。仅修补七个现有 HTH Task 的 audit/resource 配置；重复执行不重复新增。缺少前置 Task 或发现冲突/重复配置会报错并回滚本脚本。不修改普通 BCO Task、权限、审批或 2FA。
3. 执行 [2_HTH_Audit_Verify.sql](/Users/devs/CProj/hth-application/consulting/db/branch_change_history/20260917_BCOH2H-791_HTH_Audit/2_HTH_Audit_Verify.sql) 核对配置及测试公司的最新记录；配置缓存按现有发布流程刷新。
4. 按第 5 节做真实 CM 操作，并核对 JMS 落库、CM 查询/详情、标准导出、跨公司权限和 BCO 回归。这些依赖 WebLogic/Oracle/UAT，本地尚未执行；Oracle 修补脚本也尚未在数据库执行。

没有修改 batch、异步消费者、通知公共包、审计查询 Repository、表结构或跨公司权限。历史审计未清理，标准导出仍为 BCO 原摘要字段。
