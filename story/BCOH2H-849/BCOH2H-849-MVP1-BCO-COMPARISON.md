# BCOH2H-849 MVP1 与 BCO CRM 对照

> 2026-09-23：用户已确认本期同步保存。实施以 [最终技术设计](BCOH2H-849-TECH-DESIGN.md) 为准；下文早期异步建议及待确认状态仅保留作历史分析。

2026-09-22。静态代码检查结果，未连接 UAT，未执行真实交易，未修改生产代码或 SQL。

## 结论

不需要重新设计全部 CRM 基础设施，也不需要让 BA 重新定义每项 BCO 规则。先复用用户维护的已有路径，针对 HTH 独立服务补映射及配置。不能把 Audit Log、Notification 或 checkResponsePolicy 调用当作 CRM 已完整实现。

本清单中的“需要新增”指：如果该操作纳入 849，需要新增 CRM 支持；不是擅自扩展需求。spike/sample 不用来认定范围。

## 分类定义

- **已覆盖**：本地代码已经存在的通用能力，或原功能可继续保留。不是 UAT 已验证 HTH 全业务落库。
- **可沿用**：有明确 BCO 对应实现，需要开发核对配置、输入类型及实际落库，不要求 BA 重定义相同规则。
- **需要新增**：HTH 独立操作/结构未找到完整 CRM 映射，需要补配置、字段转换或接入。以需求纳入为前提。

对不属于独立交易的展示、通知、迁移等条目单列“范围判断”，不硬凑进三类新增事件。

## 已覆盖的基础能力

| 能力 | 依据 | 限制 |
|---|---|---|
| 业务响应/失败进入统一记录框架 | CRMAsserter 的响应及失败入口；框架直接调用 | HTH 有调用入口不代表符合允许列表及活动码条件 |
| 公共字段、结果、标识生成 | fetchCommonInfo、generateEventID | 仍需核对具体业务覆盖字段、用户语义及 UAT 配置 |
| 对象到数据库保存 | CRMInputData → CRMRequestAssembler → Entity → Repository → ORM | 固定写 BCO 表；若决定 HTH 独立表，应复用模式而非直接调用原保存方法 |
| 保存异常日志，无自动补录 | crmInsertData/sendCRMData 异常处理 | 不能由 catch 推断事务已隔离；按已确认规则不新增重试/补偿 |

## MVP1 每项对照

下表保留原文档行号和 Jira。原文档没有细分描述的项目不从 story 编号猜测具体动作。

| 原行号 / Story | 文档功能 | 分类或范围判断 | BCO / HTH 代码检查结果 | 开发处理及仅需确认的差异 |
|---|---|---|---|---|
| 1 / BCOH2H-97 | HTH Entry point | 范围判断：展示入口 | 入口展示本身不等于业务交易，不能自动推导一个 CRM 事件。 | 默认不另记菜单点击；若需要使用量记录，BA 明确。 |
| 2 / 无 Jira 编号 | 原文未重复填写功能名 | 范围判断：空白条目 | 原文无功能描述。 | 不据此增加开发。 |
| 3 / BCOH2H-98 | Corporate HTH Management & Activity Log | 需要新增（若纳入） | HostToHostManagement 的 submit/edit/disable 有策略检查入口；未找到对应 HTH CRM evaluator/活动码映射完整链路。原文第 4–7、9 行未细分动作。 | 已确认 BM 管理纳入 849；新增公司 HTH 活动映射。Enable/Edit/Disable 与 Activity Log 不混作一个事件。 |
| 4 / BCOH2H-176 | 原文未重复填写功能名 | 需要新增（若纳入） | HostToHostManagement 的 submit/edit/disable 有策略检查入口；未找到对应 HTH CRM evaluator/活动码映射完整链路。原文第 4–7、9 行未细分动作。 | 已确认 BM 管理纳入 849；新增公司 HTH 活动映射。Enable/Edit/Disable 与 Activity Log 不混作一个事件。 |
| 5 / BCOH2H-195 | 原文未重复填写功能名 | 需要新增（若纳入） | HostToHostManagement 的 submit/edit/disable 有策略检查入口；未找到对应 HTH CRM evaluator/活动码映射完整链路。原文第 4–7、9 行未细分动作。 | 已确认 BM 管理纳入 849；新增公司 HTH 活动映射。Enable/Edit/Disable 与 Activity Log 不混作一个事件。 |
| 6 / BCOH2H-411 | 原文未重复填写功能名 | 需要新增（若纳入） | HostToHostManagement 的 submit/edit/disable 有策略检查入口；未找到对应 HTH CRM evaluator/活动码映射完整链路。原文第 4–7、9 行未细分动作。 | 已确认 BM 管理纳入 849；新增公司 HTH 活动映射。Enable/Edit/Disable 与 Activity Log 不混作一个事件。 |
| 7 / BCOH2H-1255 | 原文未重复填写功能名 | 需要新增（若纳入） | HostToHostManagement 的 submit/edit/disable 有策略检查入口；未找到对应 HTH CRM evaluator/活动码映射完整链路。原文第 4–7、9 行未细分动作。 | 已确认 BM 管理纳入 849；新增公司 HTH 活动映射。Enable/Edit/Disable 与 Activity Log 不混作一个事件。 |
| 8 / BCOH2H-425 | Corporate HTH disable (maker & checker) | 需要新增（若纳入） | HostToHostManagement 的 submit/edit/disable 有策略检查入口；未找到对应 HTH CRM evaluator/活动码映射完整链路。原文第 4–7、9 行未细分动作。 | 已确认 BM 管理纳入 849；新增公司 HTH 活动映射。Enable/Edit/Disable 与 Activity Log 不混作一个事件。 |
| 9 / BCOH2H-967 | 原文未重复填写功能名 | 需要新增（若纳入） | HostToHostManagement 的 submit/edit/disable 有策略检查入口；未找到对应 HTH CRM evaluator/活动码映射完整链路。原文第 4–7、9 行未细分动作。 | 已确认 BM 管理纳入 849；新增公司 HTH 活动映射。Enable/Edit/Disable 与 Activity Log 不混作一个事件。 |
| 10 / BCOH2H-597、BCOH2H-1288 | Notification? | 范围判断：独立通知/审计/FMO功能 | 相关功能本身不证明业务事件已写 CRM。Audit Log 与 CRM 的存储及用途不同。 | 建议只记录其源业务，不把发送通知/写审计再算一笔 MTB 交易；若需这些功能的访问/投递统计，BA 单独列明。 |
| 11 / BCOH2H-852 | Audit Log | 范围判断：独立通知/审计/FMO功能 | 相关功能本身不证明业务事件已写 CRM。Audit Log 与 CRM 的存储及用途不同。 | 建议只记录其源业务，不把发送通知/写审计再算一笔 MTB 交易；若需这些功能的访问/投递统计，BA 单独列明。 |
| 12 / 无 Jira 编号 | User Management enhance | 范围判断：描述待细化 | 原文只有功能标题，无 Jira 和操作细节；不能可靠对应新增 CRM 事件。 | 若属于已列用户/授权功能则合并，不重复新增；若是独立 BM 操作需说明。 |
| 13 / 无 Jira 编号 | User Accounts & Services Access enhance | 范围判断：描述待细化 | 原文只有功能标题，无 Jira 和操作细节；不能可靠对应新增 CRM 事件。 | 若属于已列用户/授权功能则合并，不重复新增；若是独立 BM 操作需说明。 |
| 14 / 无 Jira 编号 | Non- high risk payment notification setting | 范围判断：描述待细化 | 原文只有功能标题，无 Jira 和操作细节；不能可靠对应新增 CRM 事件。 | 若属于已列用户/授权功能则合并，不重复新增；若是独立 BM 操作需说明。 |
| 15 / BCOH2H-590 | User management - User List - User channel type | 可沿用（列表业务）；展示列不另记 | BCO 有 UserManagementViewCRMEvaluator；用户服务已有 list 响应检查。不能仅凭名称保证新 HTH 查询 DTO 与旧 evaluator 相容。 | 先核对实际列表请求是否仍走原服务；只增加渠道类型显示不另建 CRM 事件。是否需要记录 HTH 列表访问或区分渠道，列为差异。 |
| 16 / BCOH2H-591 | User Create - add user channel type | 可沿用 | UserExtensionData create/update 保留策略检查；UserExtensionDataDTO 已含 userChannelType。UserManagementCRMEvaluator 识别该 DTO，但只补 cdcNo，未把 HTH 渠道写到 CRM。 | 核对 MT_N_CUS/MT_N_UUS 活动配置后沿用原用户维护记录；已确认需要区分 HTH，补标识/活动映射。不得宣称当前记录已含全部 HTH 信息。 |
| 17 / BCOH2H-593 | User Edit - user channel type cannot be edited | 可沿用 | UserExtensionData create/update 保留策略检查；UserExtensionDataDTO 已含 userChannelType。UserManagementCRMEvaluator 识别该 DTO，但只补 cdcNo，未把 HTH 渠道写到 CRM。 | 核对 MT_N_CUS/MT_N_UUS 活动配置后沿用原用户维护记录；已确认需要区分 HTH，补标识/活动映射。不得宣称当前记录已含全部 HTH 信息。 |
| 18 / BCOH2H-780 | Data migration - Update existing BCO user "user channel type " to "BCO" | 范围判断：数据迁移 | 迁移既有用户类型是数据初始化，不等于客户发起用户维护。 | 建议不为每条迁移数据生成客户 CRM 交易；若需迁移报告另归迁移方案。 |
| 19 / BCOH2H-592 | User List add "User Channel Type" | 可沿用（列表业务）；展示列不另记 | BCO 有 UserManagementViewCRMEvaluator；用户服务已有 list 响应检查。不能仅凭名称保证新 HTH 查询 DTO 与旧 evaluator 相容。 | 先核对实际列表请求是否仍走原服务；只增加渠道类型显示不另建 CRM 事件。是否需要记录 HTH 列表访问或区分渠道，列为差异。 |
| 20 / BCOH2H-538 | User (HTH & BCO )Accounts & Services Access Setting enhance | 需要新增 HTH 映射；BCO 路径可沿用 | HTH 用 HostToHostUserAccessDTO；旧 UserAccountAccessCRMEvaluator 识别 BCO UserAccountAccess/LinkedUserAccountAccess DTO，未识别 HTH DTO。HTH SQL 已加入 UAT_N_HUA_NEW/EDT/DEL 允许列表，但未找到配套 CRM evaluator/活动码。 | 新增 HTH 字段转换并补映射；不能只复制旧 evaluator 配置。动作区分、字段及记录条数按 BCO function 授权核对后映射。原 BCO 授权保持原流程。 |
| 21 / BCOH2H-595 | Related Account Summary create | 需要新增 HTH 映射；BCO 路径可沿用 | HTH 用 HostToHostUserAccessDTO；旧 UserAccountAccessCRMEvaluator 识别 BCO UserAccountAccess/LinkedUserAccountAccess DTO，未识别 HTH DTO。HTH SQL 已加入 UAT_N_HUA_NEW/EDT/DEL 允许列表，但未找到配套 CRM evaluator/活动码。 | 新增 HTH 字段转换并补映射；不能只复制旧 evaluator 配置。动作区分、字段及记录条数按 BCO function 授权核对后映射。原 BCO 授权保持原流程。 |
| 22 / BCOH2H-782 | Related Account Summary edit - | 需要新增 HTH 映射；BCO 路径可沿用 | HTH 用 HostToHostUserAccessDTO；旧 UserAccountAccessCRMEvaluator 识别 BCO UserAccountAccess/LinkedUserAccountAccess DTO，未识别 HTH DTO。HTH SQL 已加入 UAT_N_HUA_NEW/EDT/DEL 允许列表，但未找到配套 CRM evaluator/活动码。 | 新增 HTH 字段转换并补映射；不能只复制旧 evaluator 配置。动作区分、字段及记录条数按 BCO function 授权核对后映射。原 BCO 授权保持原流程。 |
| 23 / BCOH2H-596 | Associated Account Summary create | 需要新增 HTH 映射；BCO 路径可沿用 | HTH 用 HostToHostUserAccessDTO；旧 UserAccountAccessCRMEvaluator 识别 BCO UserAccountAccess/LinkedUserAccountAccess DTO，未识别 HTH DTO。HTH SQL 已加入 UAT_N_HUA_NEW/EDT/DEL 允许列表，但未找到配套 CRM evaluator/活动码。 | 新增 HTH 字段转换并补映射；不能只复制旧 evaluator 配置。动作区分、字段及记录条数按 BCO function 授权核对后映射。原 BCO 授权保持原流程。 |
| 24 / BCOH2H-785 | Associated Account Summary edit | 需要新增 HTH 映射；BCO 路径可沿用 | HTH 用 HostToHostUserAccessDTO；旧 UserAccountAccessCRMEvaluator 识别 BCO UserAccountAccess/LinkedUserAccountAccess DTO，未识别 HTH DTO。HTH SQL 已加入 UAT_N_HUA_NEW/EDT/DEL 允许列表，但未找到配套 CRM evaluator/活动码。 | 新增 HTH 字段转换并补映射；不能只复制旧 evaluator 配置。动作区分、字段及记录条数按 BCO function 授权核对后映射。原 BCO 授权保持原流程。 |
| 25 / BCOH2H-787 | User Management - User Create - HTH API Password Code Generate | 需要新增（若单独记录 Code 生成） | API Password generate 有策略检查与审计；未找到完整 HTH CRM 映射。Code 最终激活还与用户创建/编辑审批相关。 | 确认生成是否独立于用户创建事件；明确最终批准后的记录时点。只记录动作/目标等信息，不记录 Code、密码或密文。重新生成非本行明确范围，若纳入需列明。 |
| 26 / BCOH2H-788 | HTH API password - First time Setup | 需要新增 | HostToHostApiPassword setup/reset 有统一策略检查，但使用自己的请求 DTO；Signer PIN evaluator 是不同 DTO 和不同业务编码，不能直接套用。 | 复用公共记录方式，新增 API Password 映射/活动码；已确认首次设置与重设分别记录；公共字段参考 BCO。 |
| 27 / BCOH2H-790 | HTH API password - Reset | 需要新增 | HostToHostApiPassword setup/reset 有统一策略检查，但使用自己的请求 DTO；Signer PIN evaluator 是不同 DTO 和不同业务编码，不能直接套用。 | 复用公共记录方式，新增 API Password 映射/活动码；已确认首次设置与重设分别记录；公共字段参考 BCO。 |
| 28 / BCOH2H-789 | HTH API password - HTH Password Code  - Notification | 范围判断：独立通知/审计/FMO功能 | 相关功能本身不证明业务事件已写 CRM。Audit Log 与 CRM 的存储及用途不同。 | 建议只记录其源业务，不把发送通知/写审计再算一笔 MTB 交易；若需这些功能的访问/投递统计，BA 单独列明。 |
| 29 / BCOH2H-1204 | HTH API password - First time set up  - Notification | 范围判断：独立通知/审计/FMO功能 | 相关功能本身不证明业务事件已写 CRM。Audit Log 与 CRM 的存储及用途不同。 | 建议只记录其源业务，不把发送通知/写审计再算一笔 MTB 交易；若需这些功能的访问/投递统计，BA 单独列明。 |
| 30 / BCOH2H-1205 | HTH API password - API Password Reset  - Notification | 范围判断：独立通知/审计/FMO功能 | 相关功能本身不证明业务事件已写 CRM。Audit Log 与 CRM 的存储及用途不同。 | 建议只记录其源业务，不把发送通知/写审计再算一笔 MTB 交易；若需这些功能的访问/投递统计，BA 单独列明。 |
| 31 / 无 Jira 编号 | Notification - auto mail for expired(after 365 days)  HTH password reset reminder | 范围判断：无 Jira 的提醒 | 原文列出 365 天邮件/弹窗提醒，未由该清单证明已实现或需独立 CRM 事件。 | 默认不因发邮件/弹窗再新增 MTB 交易；是否纳入提醒统计需明确。 |
| 32 / 无 Jira 编号 | BCO pop up reminder - HTH password  expired(after 365 days)  HTH password reset reminder | 范围判断：无 Jira 的提醒 | 原文列出 365 天邮件/弹窗提醒，未由该清单证明已实现或需独立 CRM 事件。 | 默认不因发邮件/弹窗再新增 MTB 交易；是否纳入提醒统计需明确。 |
| 33 / BCOH2H-791 | CM - Audit Log | 范围判断：独立通知/审计/FMO功能 | 相关功能本身不证明业务事件已写 CRM。Audit Log 与 CRM 的存储及用途不同。 | 建议只记录其源业务，不把发送通知/写审计再算一笔 MTB 交易；若需这些功能的访问/投递统计，BA 单独列明。 |
| 34 / BCOH2H-849 | BCO - CRM | 本次 849 工作项 | BCO - CRM 是本次对照目标，不应再生成“执行 CRM”的 CRM 事件。 | 承接确认后的业务范围、映射和接入。 |
| 35 / BCOH2H-850 | BCO - FMO | 范围判断：独立通知/审计/FMO功能 | 相关功能本身不证明业务事件已写 CRM。Audit Log 与 CRM 的存储及用途不同。 | 建议只记录其源业务，不把发送通知/写审计再算一笔 MTB 交易；若需这些功能的访问/投递统计，BA 单独列明。 |
| 36 / BCOH2H-851 | BCO - Notification | 范围判断：独立通知/审计/FMO功能 | 相关功能本身不证明业务事件已写 CRM。Audit Log 与 CRM 的存储及用途不同。 | 建议只记录其源业务，不把发送通知/写审计再算一笔 MTB 交易；若需这些功能的访问/投递统计，BA 单独列明。 |
| 37 / BCOH2H-1212 | 原文未重复填写功能名 | 范围判断：独立通知/审计/FMO功能 | 相关功能本身不证明业务事件已写 CRM。Audit Log 与 CRM 的存储及用途不同。 | 建议只记录其源业务，不把发送通知/写审计再算一笔 MTB 交易；若需这些功能的访问/投递统计，BA 单独列明。 |
| 38 / BCOH2H-1216 | 原文未重复填写功能名 | 范围判断：独立通知/审计/FMO功能 | 相关功能本身不证明业务事件已写 CRM。Audit Log 与 CRM 的存储及用途不同。 | 建议只记录其源业务，不把发送通知/写审计再算一笔 MTB 交易；若需这些功能的访问/投递统计，BA 单独列明。 |
| 39 / 无 Jira 编号 | BCO revamp- Batch job reset limit without 18 months transaction | 范围判断：未指定 Jira 的 revamp | 18 个月无交易重置限额、API Transaction History Interface 是独立增强，当前清单未提供详细 AC。 | 建议本次不自动纳入；若包含由 BA 明确并提供具体行为。 |
| 40 / 无 Jira 编号 | BCO revamp- API Transaction History Interface in BCO API Profile | 范围判断：未指定 Jira 的 revamp | 18 个月无交易重置限额、API Transaction History Interface 是独立增强，当前清单未提供详细 AC。 | 建议本次不自动纳入；若包含由 BA 明确并提供具体行为。 |

## 两个关键代码差异

1. 用户创建/修改仍是 BCO UserExtensionDataDTO，具备复用条件；现有 UserManagementCRMEvaluator 没有读取 userChannelType，不能说明 MTB 已能区分 HTH。
2. HTH 账户授权虽然已列入 CRM_ALLOWED_TASK_CODES，但公共 CRMAsserter 还要求活动码非空才进行正常插入。现有 BCO evaluator 又不识别 HTH DTO。因此“允许 task”不等于“正确记录”。没有 UAT 配置，不能断言 UAT 一定缺配置；这里只能确认源码交付未找到完整配置。

## 用户确认结果（2026-09-22 更新）

以下结论替代本文件逐项表中对应的“若纳入／先确认／是否区分”条件；逐项表保留代码检查依据。

| 确认项 | 已确认要求 | 开发处理 |
|---|---|---|
| CM / BM 范围 | 849 包含 BM 公司 HTH 管理。 | 纳入公司 Enable/Edit/Disable，并核对各服务及审批执行入口，补 HTH CRM 映射。 |
| HTH 与 BCO 区分 | MTB 必须能区分 HTH 与 BCO 用户操作。 | 保留原 BCO 记录语义，为 HTH 增加可识别映射；具体活动编码/字段承载方式由开发与 TL、MTB 接收方核对，不能直接覆盖原访问渠道字段。 |
| HTH 授权动作及额外信息 | 参考 BCO function 授权。 | 逐项对照 BCO function 授权的动作、字段、条数及记录阶段，再映射 HTH DTO；不另要求 BA 重定义 BCO 规则。不能只因类名相近就把账户授权 evaluator 当作 function 授权依据。 |
| API Password 活动 | Code Generate、Setup、Reset 分别记录。 | 分别配置活动标识。审批记录时点需要在设计中写清：先核对 BCO 同类业务和 HTH 实际审批路径，区分提交、审批执行及直接生效；本次回复未指定具体记录阶段，不能把“最终审批后才记”宣称为已确认。 |
| 查询、通知、提醒等 | 无额外例外，参考 BCO。 | 沿用对应 BCO 行为，不自行增加 HTH 特例事件；是否已有 BCO 查询/通知记录，以代码及配置为准，不能一概当作全部不记录。 |
| 同步 / 异步 | 2026-09-23 已确认本期同步。 | 采用同步保存，Jira AC2 需同步记录调整；不实现异步队列。 |

已确认的保存失败策略保持不变：记录日志，不自动重试、不补录、不补偿。这不同于“业务操作失败是否也记录”，后者参考对应 BCO 行为。

不再把上面已经确认的范围问题交 BA 重复确认。下一步由开发补齐 BCO function 授权的具体对照及各操作记录时点；只有查到 BCO 无对应规则或 HTH 存在无法直接沿用的业务差异，才再提出具体问题。同步方式已于 2026-09-23 确认，详见最终设计。

## 开发 / TL 自行核对，不再全部交给 BA

- 查 UAT 的实际允许 task、evaluator、活动码及资源 task 映射；先验证用户 create/update 的原记录再决定是否加代码。
- 核对 BCO DTO 字段语义、事件身份和结果判断，对同类业务优先沿用。
- 新旧表由架构结合下游隔离要求决定。若复用旧表，评估原 BCO 文件会不会抽到新增事件；如独立表，复用分层而非直接调固定表的旧 Repository。
- 如果 AC2 保留，另设计最小异步执行；不凭业务本身有审批 worker 就宣称 MTB 已异步。
- 同步或异步都验证异常事务影响；失败只留日志，不自动重试/补录。
- 核对时区、真实 DDL、容量和保留期，能沿用 BCO 的不重新提业务问题。
- 每个纳入业务验证真实数据库行、字段、条数及成功/失败路径；保护 BCO 既有数据和流程。

## “已覆盖”验收口径

当前能标“已覆盖”的是通用采集/存储框架，不是整项 HTH 新业务已在 UAT 验证。用户维护先标“可沿用”，待实际配置和一次完整提交/审批/查询记录验证后，才升级为“已覆盖”。不能为了分满三组而给尚未验证的功能贴已完成标签。

## 代码依据

- [CRMAsserter.java](/Users/devs/CProj/hth-application/consulting/middleware/projects/adapter/com.ofss.digx.cz.bea.adapter.impl/src/com/ofss/digx/cz/bea/app/generic/asserter/impl/CRMAsserter.java)
- [UserManagementCRMEvaluator.java](/Users/devs/CProj/hth-application/consulting/middleware/projects/adapter/com.ofss.digx.cz.bea.adapter.impl/src/com/ofss/digx/cz/bea/app/crm/evaluator/impl/UserManagementCRMEvaluator.java)
- [UserManagementViewCRMEvaluator.java](/Users/devs/CProj/hth-application/consulting/middleware/projects/adapter/com.ofss.digx.cz.bea.adapter.impl/src/com/ofss/digx/cz/bea/app/crm/evaluator/impl/UserManagementViewCRMEvaluator.java)
- [UserAccountAccessCRMEvaluator.java](/Users/devs/CProj/hth-application/consulting/middleware/projects/adapter/com.ofss.digx.cz.bea.adapter.impl/src/com/ofss/digx/cz/bea/app/crm/evaluator/impl/UserAccountAccessCRMEvaluator.java)
- [PartyAccountAccessCRMEvaluator.java](/Users/devs/CProj/hth-application/consulting/middleware/projects/adapter/com.ofss.digx.cz.bea.adapter.impl/src/com/ofss/digx/cz/bea/app/crm/evaluator/impl/PartyAccountAccessCRMEvaluator.java)
- [SelfServeResetSignerPinCRMEvaluator.java](/Users/devs/CProj/hth-application/consulting/middleware/projects/adapter/com.ofss.digx.cz.bea.adapter.impl/src/com/ofss/digx/cz/bea/app/crm/evaluator/impl/SelfServeResetSignerPinCRMEvaluator.java)
- [HostToHostUserAccessDTO.java](/Users/devs/CProj/hth-application/consulting/middleware/projects/common/com.ofss.digx.cz.bea.app.xface/src/com/ofss/digx/cz/bea/app/hosttohost/dto/HostToHostUserAccessDTO.java)
- [HostToHostManagement.java](/Users/devs/CProj/hth-application/consulting/middleware/projects/module/com.ofss.digx.cz.bea.module.hosttohost/src/com/ofss/digx/cz/bea/app/hosttohost/service/HostToHostManagement.java)
- [HostToHostManagement.java](/Users/devs/CProj/hth-application/consulting/middleware/projects/endpoint/com.ofss.digx.cz.bea.appx.service.rest/src/com/ofss/digx/cz/bea/appx/hosttohost/service/HostToHostManagement.java)
- [HostToHostUserAccess.java](/Users/devs/CProj/hth-application/consulting/middleware/projects/module/com.ofss.digx.cz.bea.module.hosttohost/src/com/ofss/digx/cz/bea/app/hosttohost/service/HostToHostUserAccess.java)
- [HostToHostUserAccess.java](/Users/devs/CProj/hth-application/consulting/middleware/projects/endpoint/com.ofss.digx.cz.bea.appx.service.rest/src/com/ofss/digx/cz/bea/appx/hosttohost/service/HostToHostUserAccess.java)
- [HostToHostApiPassword.java](/Users/devs/CProj/hth-application/consulting/middleware/projects/module/com.ofss.digx.cz.bea.module.hosttohost/src/com/ofss/digx/cz/bea/app/hosttohost/service/HostToHostApiPassword.java)
- [HostToHostApiPassword.java](/Users/devs/CProj/hth-application/consulting/middleware/projects/endpoint/com.ofss.digx.cz.bea.appx.service.rest/src/com/ofss/digx/cz/bea/appx/hosttohost/service/HostToHostApiPassword.java)
- [UserExtensionData.java](/Users/devs/CProj/hth-application/consulting/middleware/projects/module/com.ofss.digx.cz.bea.module.sms/src/com/ofss/digx/cz/bea/app/sms/service/user/UserExtensionData.java)
- [UserExtensionData.java](/Users/devs/CProj/hth-application/consulting/middleware/projects/module/com.ofss.digx.cz.bea.module.sms/src/com/ofss/digx/cz/bea/domain/sms/entity/user/UserExtensionData.java)
- [UserExtensionData.java](/Users/devs/CProj/hth-application/consulting/middleware/projects/endpoint/com.ofss.digx.cz.bea.appx.service.rest/src/com/ofss/digx/cz/bea/appx/sms/service/user/UserExtensionData.java)
- [HTH 授权 CRM 允许列表配置](/Users/devs/CProj/hth-application/consulting/db/branch_change_history/20260825_HTH_User_Access/final/3_HTH_User_Access_Process.sql:213)
