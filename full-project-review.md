# HTH 全项目功能盘点与 BCO 影响评审

日期：2026-09-11；代码基准：`0795e1bc`。本材料替代此前仅聚焦 API Password 的会议主材料；原 `meeting-review.md` 保留为密码专题及发布策略附录。

## 评审范围和判断标准

本次从当前 `consulting/` 与 `story/` 的前端、Java Service/REST/Adapter、SQL、ORM、配置和公共调用点查找 HTH/H2H 功能，不限于近期提交。发现的是当前仓库已有实现/配置，不代表已部署、已通过 AC，也不能仅凭整批 sync 记录归责某位同事。要确认“谁做的”和“是否已随 BCO 上线”，仍需对应负责人及 BCO 生产 tag。

不把同一功能的页面、DTO、Adapter、SQL分别算成 Story。API 目录里出现的 URI 也不代表对应外部微服务由本仓库实现。关键词命中明细另见 `hth-source-index.md` 和 `hth-source-inventory.json`。

## 全量功能地图（本仓库已发现）

| 功能域 | 实现内容/边界 | 当前证据 | Story 对应情况 | BCO 影响 |
|---|---|---|---|---|
| 1. 公司 HTH 管理 | 查询、创建提交、修改、停用；公司选用 API 配置 | `HostToHostManagement` 的 search/submit/edit/disable；`host-to-host-management/search/details/review` 页面；4 月管理 SQL | 未在本地 story 文件名中找到独立 AC PDF，需 BA 补编号 | 公共审批、公司状态、菜单与用户资格依赖；停用后的联动需要验证 |
| 2. UAM Client ID 与收费账户配置 | 校验 UAM Client ID；候选收费账户；UAM registry 与公司管理资料 | `validateUamClientId`、`eligibleChargeAccounts`；`HTH_UAM_CLIENT_REGISTRY` | 公司管理子能力，不另造 Story 编号 | UAM 外部依赖、公司/账户查询与注册一致性；不是 API Password 的 UAM 存储开关 |
| 3. API 服务目录与 URI 映射 | API master、URI、公司可用 API 的映射 | `HTH_API_MASTER`、`HTH_API_URI`、`HTH_MANAGEMENT_API`；6 月 URI 脚本 | 未确认独立编号 | 目录变更会影响授权与可选服务，需确认是否对外生效 |
| 4. 用户 HTH 身份及用户维护增强 | HTH_USER_PROFILE、CloseID、userChannelType、账户权限设置状态；标准用户列表/详情增强 | `CZUserExt`、`CZVoidUserExt`、`LocalUserExtensionDataRepositoryAdapter`、UserExtensionData DTO/entity/service；user-management 页面 | 与 538 及 HTH 用户维护关联，独立创建 Story 编号待核对 | **公共 BCO 用户查询、创建修改和登录资料链路**；HTH DB/Adapter 依赖不能忽略 |
| 5. 用户账户与服务权限设置 | 搜索用户、账户目录、API 选择、提交、修改、删除；权限摘要/审核页面 | `HostToHostUserAccess`；`account-access-management`；`HTH_USER_ACCESS_ACCOUNT`、`HTH_USER_ACCESS_ACCOUNT_API` | 本地有 538 PDF/技术设计 | **明确为 HTH & BCO 增强**；公共账户选择、审批、Maker/Checker 回归必需 |
| 6. Related Account Summary | Related 账户创建和修改流程、选中状态及展示 | `hth-account-linkage`、summary/review 及 UserAccess 服务；对应设计文档 | 本地有 595 创建、782 编辑 PDF/设计 | 共享账户资料、公司关系、审批快照；不能跨公司或覆盖其他用户授权 |
| 7. Associated Account Summary | Associated 账户创建/修改、关联公司摘要和审批展示 | `linked-party-access-*`、`review-linked-party-access-management`、`HthUserAccessTransactionName` | 本地有 596 创建、785 编辑 PDF/设计 | 关联公司资料、审批列表名称及重放逻辑涉及公共流程 |
| 8. 账户/API 运行时授权 | 校验用户账户授权、账户 API 授权、公司 API 启用关系 | `HostToHostRuntimeAuthorizer` 与 Repository 判定 | 权限设置的执行边界，未确认独立编号 | **当前项目源码只找到 helper 定义，未找到调用点**；需确认外部 ingress 是否接入，不能把“能保存权限”当成“接口已强制权限” |
| 9. HTH 证书管理 | 证书查询、上传提交、下载；证书及请求记录 | `HostToHostCertificateManagement`；`corporate-host-to-host/host-to-host-certificate-management`；6 月证书 SQL | 未在本地 story 文件名中找到对应独立 PDF | 上传/下载权限、公司资格、公共认证资料、文件与证书存储；不能仅隐藏菜单 |
| 10. API Password Code 与密码 | Code 生成/查看/审批激活、首次设置、Profile 重置；DATABASE/UAM 可选 | `HostToHostApiPassword` 及相关页面、ORM/SQL | 787 由脚本/集成文档对应；788、790 有 PDF | 公共 Dashboard/Profile/登录/审批/加密依赖；有效加解密尚待端到端验证 |
| 11. HTH 通知 | Code 审批通知、首次设置成功通知、重置成功通知 | 789 用户/公司邮件；1204/1205个人邮件及短信；4事件、18模板/关联配置 | 本地有 789、1204、1205 PDF | 共享事件引擎及邮件/SMS网关；正式文案/地址和真实投递待确认 |
| 12. 公共账户余额查询接入 H2H 微服务 | `CZDemandDepositAdapter.fetchBalance` 可由 EBK 切换到 H2H MS；失败可回退 EBK | `fetchBalanceH2H`、`getBalanceInquiryFromH2HMSApi`、`CZDemandDepositReadAssembler`、`H2HApiClientFactory` | **本地未找到直接对应 Story**；可能为独立 BCO 基建改造，需负责人确认，不能仅因名称 H2H 判断上线节奏 | **最高优先：位于公共账户余额 Adapter，不是只在 HTH 页面调用**；独立开关默认值为 Y |

### 已有 Story 证据汇总

- User Accounts & Services Access：538、595、596、782、785，共 5 份本地需求 PDF。
- API Password：788、789、790、1204、1205，共 5 份本地需求 PDF。
- 787：本地有独立部署脚本目录及集成设计引用，本次未找到独立 AC PDF。
- 公司管理、证书管理、目录映射、UAM 注册、公共余额微服务接入：当前有代码/SQL，Story 编号和正式验收范围需要 BA/原实现负责人补齐。
- 这是“已发现的需求证据”，不是完整 Jira backlog；外部系统、未同步分支或未保存的需求不能从此仓库恢复。

## 会上应优先讨论的 BCO 风险

### A. 公共余额接口已出现独立 H2H 微服务路由

`CZDemandDepositAdapter.fetchBalance()` 读取 `ENABLE_INQ01_MSAPI`，默认值 `Y`；为 Y 时调用 H2H MS，否则走 EBK。失败分支读取 `ENABLE_INQ01_FAIL_AUTO_ROUTE_TO_EBK`，默认也是 `Y`。

影响：即便 HTH API Password 关闭，公共余额路径仍可能调用新服务。需要确认 BCO 当前生产配置、服务地址、SDK版本、超时、部分成功/错误映射及回退一致性。代码默认不等于生产有效值，会议不能宣称生产已走新接口；也不能为停用 HTH 而直接改这个开关，可能影响已批准的 BCO 改造。

### B. H2H 客户端存在明确安全实现问题

- `H2HClient.getOkHttpClient()` 使用空证书校验方法，并把 hostname verifier 设为始终 true。
- `H2HApiClientFactory` 把 `getApicClientSecret()` 拼接到日志。

这两项有当前源码证据，应列为上线前阻断项：使用受信任证书配置，禁止记录 secret；由安全/平台负责人确认现有日志是否含真实凭据，以及是否需要轮换。此材料不包含任何密钥值，本次仅盘点，未改业务实现。

### C. 公共用户资料与审批不是 HTH 专用模块

`CZVoidUserExt` 查询 HTH profile 以生成 `/me.userChannelType`；标准用户列表也补充 HTH 资料；共享审批代码调用 HTH 激活及账户权限处理。前端不显示 HTH 菜单不能证明这些后端依赖不执行。要逐个明确 BCO 生产是否必须具备 HTH 表、ORM、Adapter、依赖 JAR，或先完成可选模块隔离。

### D. 运行时授权接入证据缺口

`HostToHostRuntimeAuthorizer` 明确提供判定方法，但本次检索 `consulting/middleware/projects` 仅找到其定义，未找到源码调用。外部 ingress 可能在另一仓库或构建依赖里，需要接口负责人提供调用证据/测试。不能据此断言所有线上 API 没有授权，也不能据此宣称权限设置已被实际执行。

### E. SQL 版本与 Schema 名称不统一

当前同时存在：
- `20260416_H2H_Management` 与 `20260416_HTH_Management` 两套命名目录。
- 4 月管理和 6 月证书 schema 脚本使用 `HTH_BEAUAT`；当前部分 ORM/后续权限表使用 `HTH_BEA`。
- 6 月 URI 脚本写 `DIGX_CZ_HTH_API_URI`；当前管理结构使用 `HTH_API_URI`。
- User Access 同时有原始升级脚本和 `final/`；密码有 787 原始目录和后续扩展目录。

这些可能是历史迁移/环境别名，但部署清单必须明确目标，不应全目录按文件名重复执行。原始 DDL、升级脚本和最终新装脚本不能混用。以上是版本并存证据，不等于当前 UAT 一定配错。

## 对 BCO 月版与共用 UAT 的调整建议

1. 将“HTH 业务功能”和“使用 H2H SDK 的 BCO 公共改造”分开立项确认，分别决定上线时间；不能一刀切延期半年。
2. 为上述 12 个功能域指定 Owner、Story、代码路径、开关、DB/依赖、已上线版本与验收状态。未知项必须保留“待确认”，不能按没有近期 commit 认定无影响。
3. BCO 月版冻结期间不替换 HTH 公共目录。HTH 基于每月 BCO 发布基线持续合并，UAT 使用明确的集成测试窗口。
4. 共用 UAT 分账号仅隔离数据，不隔离公共 class、JS、DB和缓存。公共余额、用户维护、审批、证书、通知应纳入每次发布后的 BCO 冒烟。
5. 暗部署必须验证所有功能域的开关与依赖，而不是只验 `HTH_API_PASSWORD.ENABLED`。余额路由有独立开关，公司管理/证书/用户权限也有独立入口。
6. 指定 BCO 生产 tag 后生成真实 baseline diff，排除整批 sync 中无关改动；mcdp 变更交付包不得当作完整生产版本。

## 会议议程建议（35 分钟）

- 5 分钟：确认全项目 12 个功能域，补齐缺失 Story/Owner。
- 10 分钟：公共余额路由、TLS/日志、公共用户/审批及运行时授权接入。
- 10 分钟：每月 BCO 与半年 HTH 的包边界、开关、DB和SDK兼容。
- 5 分钟：共用 UAT 冻结/集成窗口、通知收件人和缓存刷新责任。
- 5 分钟：记录行动项、阻断项与每项证据交付日期。

## 本轮交付与限制

- `hth-source-index.md`：候选源码/配置/历史脚本的可点击索引。
- `hth-source-inventory.json`：机器可读路径与命中行号；仅含路径/行号，不复制配置值。
- `meeting-review.md`：密码专题、公共改动矩阵及发布/回归建议附录。
- 本轮完成的是仓库功能盘点与风险定位，不是所有功能的逐行审计、生产基线差异或端到端验收。没有擅自修改业务代码、部署、开关或凭据。
