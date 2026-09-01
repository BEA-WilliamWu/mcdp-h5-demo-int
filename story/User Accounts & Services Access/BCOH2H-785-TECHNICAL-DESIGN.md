# BCOH2H-785 Technical Design

## Associated Account Summary — Edit

| 项目 | 内容 |
| --- | --- |
| Story | `BCOH2H-785` Associated Account Summary edit |
| 文档状态 | Proposed（基于当前 BCO-aligned HTH User Access 基线） |
| 日期 | 2026-09-01 |
| 前置设计 | `BCOH2H-595-TECHNICAL-DESIGN.md`、`BCOH2H-596-TECHNICAL-DESIGN.md` |
| 影响范围 | HTH User Accounts & Services Access；Associated Account；Edit |
| 不影响范围 | BCO 原有编辑流程、Related Context、其他 Associated Company、Certificate/API Master Maintenance |

## 1. 目标

为已有 HTH Associated Account 配置的用户提供 Edit 能力。管理员从 Associated Account Summary
选择某个用户级关联公司及 Account Type，查看并修改该公司下的 CSA/TD 账户和每账户 API 权限，
经与 BCO 一致的 Maker/Checker 审批后替换该 Associated Context 的 Effective Access。

本 Story 的核心风险是跨公司数据泄露或误更新，因此所有读取、审批、重复判断和落库必须同时包含
`partyId`、`closeId`、`accessPartyId`、`linkageType=ASSOCIATED`，不能只凭 `closeId` 或公司名称。

## 2. Scope 与前置条件

### 2.1 前置条件

- 操作者拥有 HTH User Accounts & Services Access Edit 权限。
- 目标用户属于主公司 `partyId` / `closeId`。
- 目标 Associated Company 是该用户当前有效的 BCO `USERLINKAGE`。
- 精确 Associated Context 已有 Active HTH Effective Access。
- 企业 HTH Profile 和 API Catalogue 有效。

### 2.2 Context

```text
(partyId, closeId, accessPartyId, linkageType = ASSOCIATED)
```

| 字段 | 含义 | Edit 约束 |
| --- | --- | --- |
| `partyId` | 主公司 | 不能由页面切换 |
| `closeId` | 被维护用户 | 不能由页面切换 |
| `accessPartyId` | 当前被编辑的 Associated Company | 必须精确匹配用户级关系与账户归属 |
| `linkageType` | 关联类型 | 固定为 `ASSOCIATED` |

### 2.3 不在范围

- 不创建/修改 Party Relationship。
- 不把所有企业关联公司作为下拉选项。
- 不在一次 Edit 中同时修改多个 Associated Company。
- 不修改 Related Account Effective Access。
- 不新增 Feature Request 表或另一套 Pending 状态。
- 不更改 BCO Task、BCO Summary 或 BCO Approval Detail。

## 3. 与 BCO/其他 Story 的关系

- 页面布局与交互参考 BCO Associated Account Edit。
- Create 入口、用户级公司筛选和基础数据来源继承 `BCOH2H-596`。
- View/Edit/完整替换、Maker/Checker 和 CSA/TD 状态管理继承 `BCOH2H-782`。
- 共用底层接口、Task、Entity 和审批配置继承 `BCOH2H-595`。

设计上复用同一组件和 `UAT_N_HUA_EDT` Task，但由完整 Context 保证 Related 与每个 Associated
Company 互相隔离。

## 4. 用户流程

```text
HTH Summary
  -> Associated Account Summary（仅用户级 USERLINKAGE 公司）
  -> 点击目标公司下 Current and Savings / Time Deposits
  -> HTH Account Linkage（VIEW，定位目标 Tab）
  -> Edit
  -> 调整目标公司的账户 -> Next
  -> 调整每账户 API -> Save
  -> Review（目标公司完整 CSA + TD）
  -> Confirm
  -> UAT_N_HUA_EDT Pending Transaction
  -> Confirmation / Quick Approve
  -> Checker Detail
  -> Approve：只替换目标 Associated Context
  -> Summary Count / Activity Log 更新
```

Maker 提交显示成功时，文案应为 “request has been sent for approval”，状态为 Pending Approval；
Effective Count 在 Checker Approve 前不变。

## 5. 前端设计

### 5.1 Associated Summary

`summary` 组件并行读取 HTH Summary 与 BCO User Account Access：

1. HTH `/search` 返回 Related/Associated Effective Count；
2. BCO `readAllUserAccountDetails(partyId, userId)` 返回目标用户的账户/公司模型。

Associated Edit 行集合必须是 BCO `USERLINKAGE` 与 HTH Active Context 的交集，并保持 BCO 顺序。
未配置公司属于 596 Create Candidate，只进入单一 `To link` Company Selector，不在 Edit Summary
逐条物化 `NOT_SETUP` Company Card。不得：

- 回退使用企业级 `LINKAGE`；
- 展示与目标用户无关的 Associated Company；
- 用 HTH Effective 表中残留的历史 Company 绕过当前 Relationship；
- 把不同 Company 的 Count 合并。

已配置的 Account Type 为链接；点击时传递目标公司 `accessPartyId`、`action=EDIT` 和
`initialAccountType`。未配置公司按 596 Create 流程显示 `To link`。

### 5.2 Linkage VIEW/EDIT

组件：`hth-account-linkage`。

- `setupStatus=ACTIVE` 初始化为 VIEW；点击 Edit 进入 EDIT。
- 只有 CSA、TD 两个 Tab，数据源独立。
- 账户顺序和展示字段与 BCO 对同一用户、同一 Associated Company 的结果一致。
- 前端合并时必须精确过滤 `party == accessPartyId`；不能接受其他 Associated Company 的账户。
- VIEW Checkbox 只读；EDIT 可修改。
- `Link All Accounts` 只影响当前 Tab。
- Cancel 恢复 Deep Copy；Back 对未保存修改提示确认。
- 清空整个 Context 应使用 Delete；Edit 至少保留一个 Account。

### 5.3 Account/API 合并规则

账户层：

- BCO Account Access 决定 Eligible Account、Display Order、Formatted Number 和 Product Metadata；
- HTH `/accounts` 决定 Effective Selection 和企业 HTH API Catalogue；
- 合并键为标准化 Account Type + Canonical Account Number；
- `ACCOUNT_NUMBER_FORMATTED` 只用于显示/兼容，不作为唯一业务身份；
- `PRODUCT_CODE` 原样保留，不能把 CSA/6805 混写到 `ACCOUNT_TYPE`。

API 层：

- 只显示企业 Active HTH API；
- Existing Effective API 预选；
- 每个选中 Account 至少一个 API；
- Apply-to-all 通过 `apiCode` 匹配。

### 5.4 完整 Working State

Edit 必须维护目标 Associated Context 的完整 CSA + TD Working State。只编辑当前 Tab 时，另一
Tab 的既有选择也必须进入 Review 和 Payload，防止完整替换时被误停用。

Working State 不得包含：

- Related Context Accounts；
- 其他 Associated Company Accounts；
- 当前用户无权访问的 Company/Account；
- 仅存在于旧 Snapshot 但当前已失效的账户。

### 5.5 Review、Confirmation、Quick Approve

- Review Header 显示目标用户及 Associated Company，账户/API 为只读。
- Checker Detail 优先从平台 `transactionSnapshot` 还原，不重新拼接 Maker 内存对象。
- Task Mapping 将 `UAT_N_HUA_EDT` 映射到 `review-hth-user-access`。
- Confirmation 使用 Service 返回的平台 Status/Reference。
- 旧 OBDX 的 HTTP 400 + `DIGX_APPROVAL_REQUIRED` 只有在当前 Context Transaction Reference
  已解析后才可规范化为已接受；否则应显示错误。
- Quick Approve 通过 Transaction ID 跳 Checker Detail，不能只传页面名。

## 6. API Contract

### 6.1 Detail

```http
GET /cz/v1/hostToHostUserAccess/accounts
    ?partyId={partyId}
    &closeId={closeId}
    &accessPartyId={associatedPartyId}
    &linkageType=ASSOCIATED
```

服务端必须精确过滤返回账户的 Party 为 `accessPartyId`。如果当前 Relationship 已失效，返回
Context Invalid，而不是空列表加可编辑页面。

### 6.2 Edit

```http
POST /cz/v1/hostToHostUserAccess/edit
Content-Type: application/json
```

逻辑 Payload：

```json
{
  "partyId": "PRIMARY_PARTY",
  "closeId": "TARGET_CLOSE_ID",
  "accessPartyId": "ASSOCIATED_PARTY",
  "linkageType": "ASSOCIATED",
  "action": "EDIT",
  "accounts": [
    {
      "accountNumber": "canonical-account-number",
      "accountNumberFormatted": "display-account-number",
      "productCode": "6805",
      "accountType": "CSA",
      "currency": "HKD",
      "apiServices": [
        { "apiCode": "ACCOUNT_ACTIVITY" },
        { "apiCode": "FX_TRANSACTION_INQUIRY" }
      ]
    },
    {
      "accountNumber": "canonical-td-number",
      "productCode": "TD01",
      "accountType": "TD",
      "currency": "HKD",
      "apiServices": [{ "apiCode": "ACCOUNT_ACTIVITY" }]
    }
  ]
}
```

`accounts` 是该精确 Associated Context 批准后的完整目标状态，不是当前 Tab Delta。

## 7. 后端设计

入口：`HostToHostUserAccess.edit`，Task `UAT_N_HUA_EDT`。

### 7.1 Maker 校验

1. 验证 Edit Perform Entitlement。
2. 验证 `(partyId, closeId)` HTH Profile/用户有效。
3. 验证 `linkageType=ASSOCIATED`。
4. 验证主公司与 `accessPartyId` 的当前 Party Relationship。
5. 验证该精确 Context 有 Active Effective Access；没有则应使用 Create。
6. 复用 BCO Account Inventory，并对返回行执行 `party == accessPartyId` 精确匹配。
7. 验证账户只为 CSA/TD、Ownership 正确、Canonical/Formatted/Product Code 元数据合法。
8. 验证每账户至少一个企业 Active API。
9. Account/API 去重后将完整 DTO 提交 Approval Framework。
10. Maker 阶段不写 `HTH_USER_ACCESS_ACCOUNT*`。

### 7.2 Duplicate/Pending

Approval Assembler 的 Entity Identifier 包含完整四字段 Context。因此：

- 同一用户、同一 Associated Company 的并发 Create/Edit/Delete 互斥；
- 同一用户的不同 Associated Company 可独立审批；
- Related Context 不被 Associated Pending 阻塞；
- 相同 Context 已 Pending 返回平台 `DIGX_AP_0062`。

Pending List、Checker Detail、Activity Log 使用同一个 `DIGX_AP_TRANSACTION` 与 Snapshot，不引入
`HTH_USER_ACCESS_REQ*` 表。

### 7.3 Checker Approve

Checker Approve 重入时，服务端重新验证：

- 当前 Profile/Close ID；
- Associated Relationship；
- 每个 Account 的当前 Ownership 和状态；
- 每个 API 的当前企业授权。

验证通过后调用完整替换：

1. 只查询目标四字段 Context 的旧 Effective Account/API；
2. 先软停用旧 Account API，再软停用旧 Account；
3. 按 Snapshot 复用/重新激活或创建 Account；
4. 复用/重新激活或创建 Account API；
5. 完成平台 Transaction；
6. Summary 下一次读取按目标 Context 聚合新 CSA/TD Count。

任何 Related 或其他 `accessPartyId` 的行都不在替换查询范围内。Reject 不改 Effective 数据。

## 8. 数据模型

### 8.1 Effective Account

`HTH_BEA.HTH_USER_ACCESS_ACCOUNT` 保存：

- `PARTY_ID`、`CLOSE_ID`、`ACCESS_PARTY_ID`、`LINKAGE_TYPE`；
- `ACCOUNT_NUMBER`（Canonical）；
- `ACCOUNT_NUMBER_FORMATTED`（展示/兼容）；
- `PRODUCT_CODE`（产品原始代码）；
- `ACCOUNT_TYPE`（仅 CSA/TD）、`CURRENCY`；
- `OBJECT_STATUS` 和审计列。

Unique Key 覆盖完整 Context、Account Type 和 Canonical Account Number，防止跨公司错误复用。

### 8.2 Effective Account API

`HTH_BEA.HTH_USER_ACCESS_ACCOUNT_API` 保存 Account/API Master 映射，并通过 Active/Inactive 软状态
支持替换和历史追踪。

### 8.3 Approval/Audit

| 目的 | 来源 |
| --- | --- |
| Pending Request | `DIGX_AP_TRANSACTION.transactionSnapshot` |
| Checker Detail | 同一 Snapshot |
| Duplicate | 平台 Entity Identifier |
| Approval Status | 平台 Transaction Workflow State |
| Activity Log | 平台 Audit/Transaction，Task=`UAT_N_HUA_EDT` |
| Current Effective | 两张 HTH User Access Effective 表 |

## 9. 安全和数据隔离

- Associated Company 选择同时受前端 `USERLINKAGE` 过滤和后端 Relationship/Ownership 校验。
- 后端不可接受仅由 Company Name 解析的 `accessPartyId`。
- URL/Context 被篡改时必须 Fail Closed，不能回退到主公司账户。
- Checker 时 Relationship 失效必须拒绝批准。
- 日志屏蔽完整 Account Number、Snapshot、OTP 和 Token。
- UI 权限仅控制显示；REST Service Entitlement 是最终安全边界。

## 10. 错误处理

| 场景 | 结果 |
| --- | --- |
| Profile/CloseID 无效 | `DIGX_CZ_HTH_UA_001` |
| 企业 HTH 未启用 | `DIGX_CZ_HTH_UA_002` |
| Relationship/Associated Context 无效 | `DIGX_CZ_HTH_UA_003` |
| 非 CSA/TD | `DIGX_CZ_HTH_UA_004` |
| Account 属于其他公司/用户 | `DIGX_CZ_HTH_UA_005` |
| API 已停用或企业未授权 | `DIGX_CZ_HTH_UA_006` |
| 未选择账户或某账户无 API | `DIGX_CZ_HTH_UA_009` |
| Context 没有 Active 配置 | `DIGX_CZ_HTH_UA_010` |
| Checker 时 Account 关闭 | `DIGX_CZ_HTH_UA_012` |
| 相同 Context 已 Pending | 平台 `DIGX_AP_0062` |

非 Approval-required 的 400/4xx 必须显示 Error，不得进入成功 Confirmation。

## 11. 一致性、并发与性能

### 11.1 一致性

- Summary Count 读取 Effective 表；Pending List 读取平台 Transaction，职责分离但 Reference/Context
  来自同一审批快照。
- Maker 后 Count 不变；Approve 后 Count 更新；Reject 后 Count 不变。
- 无 Feature Request 表，不存在请求表和平台 Transaction 的双写/异步同步问题。

### 11.2 并发

- 平台 Context Entity Identifier 防止同一 Associated Company 并发修改。
- Effective Business Unique Key 防止重复 Account。
- Checker 重验证解决 Pending 期间关系、账户或 API 变化。

### 11.3 性能

- Account Inventory 复用一次 BCO Account Access 查询。
- HTH `/accounts` 按完整 Context 批量读 Effective Accounts/APIs。
- API Catalogue 按企业一次读取。
- Summary 只返回 Context/Account Type 聚合，不返回全部 Account/API 明细。

## 12. 测试设计

### 12.1 UI/Component

- 只显示目标用户 `USERLINKAGE` 中已有 Active HTH Context 的公司，顺序与 BCO 一致。
- 未配置公司不逐条显示；它们只在 596 的单一 `To link` Company Selector 中出现。
- 点击 Company A 的 CSA/TD 只加载 Company A 账户。
- CSA/TD Tab 数据独立，未修改 Tab 在 Review/Payload 中保留。
- VIEW/EDIT/Cancel/Back 行为与 BCO 一致。
- Confirmation 有 Reference；Quick Approve 正确加载 Checker Detail。

### 12.2 隔离与负面测试

- 篡改 Company A URL 为 Company B，但用户无 B `USERLINKAGE`：拒绝。
- 在 Company A Payload 放入 Company B Account：拒绝。
- Edit Company A 后 Company B Effective 数据不变。
- Edit Associated 后 Related Effective 数据不变。
- Relationship 在 Pending 期间失效：Approve 失败且 Effective 不变。

### 12.3 Maker/Checker

- Maker 提交生成 `UAT_N_HUA_EDT`，Effective 不变。
- Pending Approval、My Approval List、Activity Log 显示同一 Reference/Company。
- Approve 完整替换目标 Context；Reject 不变。
- Duplicate 只阻塞同一 Context；另一 Associated Company 可正常提交。

### 12.4 Regression

- HTH Associated Create/Delete。
- HTH Related Create/Edit/Delete。
- BCO Related/Associated Account Access 全流程。
- Certificate、API Master、Management API Maintenance。

## 13. 验收条件映射

| Story AC | 设计实现 | 验证 |
| --- | --- | --- |
| AC1 Edit Account and Service Access | Associated Account Type Link -> VIEW -> Edit | UI E2E |
| AC1 调整 CSA/TD API | 独立 Tab + 完整 Working Snapshot | Component/API Test |
| AC1 Save/Success | Review -> `/edit` -> Pending Confirmation | E2E |
| AC1 BCO Maker/Checker 行为 | `UAT_N_HUA_EDT` + Platform Snapshot/OTP | Approval Test |
| AC2 与 Create 一致 | 共用 linkage/mapping/review 组件 | Regression |
| AC3 CSA/TD 均可编辑审批 | 完整目标集合替换 | E2E Matrix |

## 14. 部署与回滚

本 Story 不新增表、Endpoint 或 Task；复用 595 的 HTH User Access 基础设施和 596 的 Associated
Summary/Context 入口。

部署顺序：后端 Service/REST -> Channel Components/Task Mapping -> 刷新 Framework/Authorization Cache
-> 每公司隔离 Maker/Checker Smoke Test。

回滚代码时保留已批准 Effective 数据。业务配置回退通过正常 Edit/Delete 审批或获批 Migration，
不得直接 Drop/Truncate HTH User Access 表。

## 15. 关键实现文件

- `account-access-management/summary/summary.js|html|model.js`
- `account-access-management/hth-account-linkage/hth-account-linkage.js|html|model.js`
- `account-access-management/hth-api-service-mapping/hth-api-service-mapping.js|html`
- `account-access-management/review-hth-user-access/review-hth-user-access.js|html`
- `override/task-component-mapping.js`
- Application/REST `HostToHostUserAccess.java`
- `SubmitHostToHostUserAccessApprovalAssembler.java`
- HTH User Access Account/API Entity、Repository 与 Adapter
