# BCOH2H-596 Technical Design

## Associated Account Summary — Create

| 项目 | 内容 |
| --- | --- |
| Story | `BCOH2H-596` Associated Account Summary create |
| 文档状态 | Proposed（基于当前 BCO-aligned HTH User Access 基线） |
| 日期 | 2026-09-01 |
| 前置设计 | `BCOH2H-595-TECHNICAL-DESIGN.md` |
| 影响范围 | HTH User Accounts & Services Access；Associated Account；Create |
| 不影响范围 | BCO 原有账户权限管理、Certificate/API Master Maintenance、Runtime HTH API 授权协议 |

## 1. 目标

当 HTH 用户尚未在某个已关联公司下配置账户/API 权限时，管理员可从 HTH Summary 的
Associated Account Summary 发起 Create，选择 Current and Savings 或 Time Deposit 账户，
为每个账户配置企业已启用的 HTH API，提交 Maker/Checker 审批；只有 Checker 批准后，
配置才成为 Effective Access，并在 Summary 中显示账户数量。

本 Story 不创建第二套 Associated Company、Account Inventory 或审批数据模型，而是复用：

- BCO User Account Access 返回的用户级关联公司及账户顺序；
- HTH 企业 API 配置；
- HTH Effective Account/API 表；
- OBDX 平台 Transaction Snapshot、Pending Approval、Activity Log 与 OTP/Checker 流程。

## 2. Story 解读与边界

### 2.1 前置条件

- 操作者已进入 CM Portal，并拥有 HTH User Accounts & Services Access 维护权限。
- 目标用户属于当前主公司 `partyId` / `closeId`。
- Associated Company 必须来自该用户的 BCO `USERLINKAGE` 数据；不得展示只有企业关系、
  但未分配给该用户的 `LINKAGE` 公司。尚未配置的公司不逐条铺在 Summary 上，而是在
  点击单一 `To link` 后的 BCO 风格 Company Selector 中选择。
- 目标 Context 尚无 Active HTH Effective Access；若已有配置，应进入 Edit，而不是 Create。
- 企业 HTH Profile、Management/API Master 配置有效。

### 2.2 Story 原文歧义处理

验收条件第 2 点中的 “approved related account in CSA” 与 Story 标题、入口和操作上下文不一致。
本设计按 **approved associated account** 处理：批准后更新所选 Associated Company 的
Current and Savings / Time Deposits 数量，不更新 Related Context。

### 2.3 明确不做

- 不允许在 HTH 页面自行创建 Party-to-Party Relationship。
- 不把所有企业级 Associated Company 展示给用户。
- 不建立 `HTH_USER_ACCESS_REQ*` 请求表。
- 不在 Maker 提交时提前写 Effective Access。
- 不修改 BCO Summary、BCO Task Code 或 BCO Service Contract。

## 3. 设计原则

1. **与 BCO 同源**：Associated Company 和 Account Inventory 复用 BCO User Account Access。
2. **完整 Context 隔离**：任何读取、重复判断、审批和落库都使用完整四字段 Context。
3. **审批快照为主**：Pending/Checker Detail/Activity Log 使用平台 Transaction Snapshot。
4. **后端不信任 UI**：写入及批准时均重新验证 Relationship、Account Ownership 和 API Eligibility。
5. **批准后生效**：Maker 阶段 Effective 表不改变；Reject 对 Effective 表无副作用。

## 4. Context 定义

Associated Create 的唯一业务边界为：

```text
(partyId, closeId, accessPartyId, linkageType = ASSOCIATED)
```

| 字段 | 含义 | 规则 |
| --- | --- | --- |
| `partyId` | 主公司 Party ID | 必须匹配 HTH Profile |
| `closeId` | 目标用户 Close ID | 必须属于当前主公司 |
| `accessPartyId` | Associated Company Party ID | 必须是目标用户当前有效的 BCO `USERLINKAGE` |
| `linkageType` | 权限上下文类型 | 固定为 `ASSOCIATED` |

`companyName`、`username`、`fullName` 仅用于展示，不参与授权、唯一键或重复判断。

## 5. 用户流程

```text
HTH Summary
  -> Associated Account Summary
  -> 未配置：单一 No associated account(s) linked to the user. + To link
  -> Associated Company Selector（仅用户级、尚未配置的 USERLINKAGE）
  -> 选择 Company -> To link
  -> HTH Account Linkage（默认 CSA；可切换 TD）
  -> 选择账户 -> Next
  -> HTH API Service Mapping
  -> 展开账户 -> 选择至少一个 API -> Save
  -> Review
  -> Confirm
  -> 平台建立 Pending Approval Transaction
  -> Confirmation（Reference No. + Pending Approval）
  -> Checker Approve/Reject
  -> Approve：创建 Effective Account/API
  -> Summary 显示 CSA/TD 数量
```

## 6. 前端设计

### 6.1 Summary 页面

复用组件：

- `consulting/channel/extensions/components/account-access-management/summary/summary.js`
- `consulting/channel/extensions/components/account-access-management/summary/summary.html`
- `consulting/channel/extensions/components/account-access-management/summary/model.js`

加载时并行读取：

1. HTH `/search?partyId={partyId}&closeId={closeId}`，取得 Effective Count；
2. BCO User Account Access `readAllUserAccountDetails(partyId, userId)`，取得用户级公司/账户模型。

Associated Company 的候选全集必须以 BCO 返回的 `USERLINKAGE` 行为主集合，并保持其顺序。
HTH Summary 只补充各 Context 的 CSA/TD Effective Count，不可回退到 `LINKAGE`，否则会把未分配
给目标用户的公司暴露出来。

展示需拆成两个集合：

- **Configured Summaries**：`USERLINKAGE` 与 HTH Active Context 的交集；按公司显示账户数量，
  供后续 Edit。
- **Create Candidates**：`USERLINKAGE` 减去 Active/Pending Context；不逐条显示 Company Card，
  只作为 `To link` 弹窗下拉选项。

当前实现若通过 `userScopedAssociatedSummaries()` 为每个 `USERLINKAGE` 物化一个 `NOT_SETUP`
Company Card，会造成大量“未配置公司”逐条显示，不符合本 Story 与 BCO 交互。实现本 Story 时应
改为上述两个集合，但仍共用同一次 BCO Account Access 响应。

存在任一 Create Candidate 时，只显示一条 BCO 风格提示：

```text
No associated account(s) linked to the user.                         [To link]
```

文字和按钮同一行。已配置时显示用户实际拥有且已建立 HTH Effective Access 的 Associated
Company 行及 CSA/TD 数量；未配置公司不逐条显示。

点击 `To link` 打开 `Associated Account Summary` Company Selector：

- 下拉选项只取 Create Candidates；
- Label 使用 BCO Company Display Name，Value 使用 Party ID；
- 未选择 Company 时弹窗内 `To link` 禁用；
- Cancel/Close 不修改 Summary State；
- 选择后点击弹窗 `To link` 才进入 `hth-account-linkage`；
- 如果候选已被另一会话提交为 Pending，后端 Duplicate Check 仍是最终保护。

Company 选择完成后构造：

```javascript
{
  partyId,
  closeId,
  accessPartyId: associatedPartyId,
  linkageType: "ASSOCIATED",
  action: "CREATE",
  initialAccountType: "CSA" // 或用户点击的 TD
}
```

### 6.2 Account Linkage 页面

组件：`hth-account-linkage`。

- 只显示 `Current and Savings`（`CSA`）和 `Time Deposit`（`TD`）两个 Tab。
- 默认 CSA；若从某 Account Type 链接进入，则使用 `initialAccountType`。
- 账户列表顺序、账号显示格式、币种和 Product Description 与 BCO 同一 Account Access
  返回保持一致。
- `Link All Accounts` 只作用于当前 Tab。
- 至少选择一个账户后 `Next` 才可用。
- Back/Cancel 与 BCO 一致；有未保存修改时提示确认。

### 6.3 API Service Mapping 页面

组件：`hth-api-service-mapping`。

- 只显示上一页选中的账户。
- 展开账户后显示该企业 Active HTH API Tree。
- 每个选中账户必须至少勾选一个 API。
- “Apply first account APIs to all” 按 `apiCode` 复制，不按前端对象引用复制。
- Save 将完整 CSA + TD 选择带入 Review；切换 Tab 不得丢失另一 Tab 的内存状态。

### 6.4 Review 与 Confirmation

组件：`review-hth-user-access`、`confirm-screen`。

- Review 为只读，按 CSA/TD 分 Tab 展示账户及 API。
- Confirm 调用 `POST /cz/v1/hostToHostUserAccess/submit`。
- HTTP 400 + `DIGX_APPROVAL_REQUIRED` 是旧 OBDX 对“已接受并需审批”的传输表达；前端只可在
  能解析到当前 Context 对应 Transaction Reference 时规范化为接受结果，其他 400 必须显示错误。
- Confirmation 必须显示 Transaction Reference、`Pending Approval` 和 Quick Approve（有权限时）。
- Quick Approve 只能使用平台 Transaction ID，不可使用自生成 Reference。

## 7. API Contract

### 7.1 读取账户

```http
GET /cz/v1/hostToHostUserAccess/accounts
    ?partyId={partyId}
    &closeId={closeId}
    &accessPartyId={associatedPartyId}
    &linkageType=ASSOCIATED
```

后端返回：用户在目标 Associated Company 下可配置的 CSA/TD 账户、账户展示元数据、企业 API
Catalogue 及现有选择。Create 场景现有选择为空。

### 7.2 Create 请求

```http
POST /cz/v1/hostToHostUserAccess/submit
Content-Type: application/json
```

逻辑 Payload：

```json
{
  "partyId": "PRIMARY_PARTY",
  "closeId": "TARGET_CLOSE_ID",
  "accessPartyId": "ASSOCIATED_PARTY",
  "linkageType": "ASSOCIATED",
  "action": "CREATE",
  "accounts": [
    {
      "accountNumber": "canonical-account-number",
      "accountNumberFormatted": "display-account-number",
      "productCode": "6805",
      "accountType": "CSA",
      "currency": "HKD",
      "apiServices": [{ "apiCode": "ACCOUNT_ACTIVITY" }]
    }
  ]
}
```

服务端不接受前端提交的 Company Name、Account Description 或 API Description 作为授权依据。

## 8. 后端处理

入口：`HostToHostUserAccess.submit`，Task `UAT_N_HUA_NEW`。

### 8.1 Maker 提交校验

1. 验证当前操作者的 Service Entitlement。
2. 验证 `(partyId, closeId)` 对应有效 HTH Profile/用户。
3. 验证 `accessPartyId` 是当前有效 Party Relationship，并与用户级账户返回精确匹配。
4. 验证 Context 当前没有 Active Effective Access；有则返回状态不允许。
5. 复用 BCO Account Access Inventory，验证每个账户属于目标 `accessPartyId`。
6. 账户类型只允许 `CSA`、`TD`。
7. 验证每个账户至少一个 API；API 必须在企业 Active HTH API Catalogue 中。
8. 对 Account/API 做业务键去重。
9. 将完整 DTO 交给 Approval Framework，不写 Effective 表。

### 8.2 Pending 与重复判断

`SubmitHostToHostUserAccessApprovalAssembler` 使用完整 Context 生成 Entity Identifier Hash。
平台发现相同 Context 已有 Pending Transaction 时返回 `DIGX_AP_0062`。

Pending 来源唯一为 `DIGX_AP_TRANSACTION.transactionSnapshot`。不得同时写 Feature Request 表，
否则会出现“重复提交但 Approval List 不可见”的双状态问题。

### 8.3 Checker Approve

批准重入时再次执行 Profile、Relationship、Account Inventory、Account Type、API Entitlement 校验。
验证通过后在当前业务事务中：

1. 按完整 Context 查找或创建 `HTH_USER_ACCESS_ACCOUNT`；
2. 保存 Canonical/Formatted Account Number、Product Code、CSA/TD、Currency；
3. 创建/重新激活 `HTH_USER_ACCESS_ACCOUNT_API`；
4. 把本 Context 未在批准快照中的旧行保持为非 Active；Create 正常情况下不存在旧 Active 行；
5. 完成平台 Transaction，Pending List 移除，Activity Log 可查询。

Reject 不写 Effective 表。

## 9. 数据模型

### 9.1 Effective Account

`HTH_BEA.HTH_USER_ACCESS_ACCOUNT`

- Context：`PARTY_ID`、`CLOSE_ID`、`ACCESS_PARTY_ID`、`LINKAGE_TYPE`；
- Account Identity：`ACCOUNT_NUMBER`（Canonical）、`ACCOUNT_NUMBER_FORMATTED`、`PRODUCT_CODE`；
- Classification：`ACCOUNT_TYPE`（CSA/TD）、`CURRENCY`；
- Lifecycle：`OBJECT_STATUS` 及审计列。

Business Unique Key：

```text
(PARTY_ID, CLOSE_ID, ACCESS_PARTY_ID, LINKAGE_TYPE, ACCOUNT_TYPE, ACCOUNT_NUMBER)
```

### 9.2 Effective Account API

`HTH_BEA.HTH_USER_ACCESS_ACCOUNT_API`

- 通过 `HTH_USER_ACCESS_ACCOUNT_ID` 关联账户；
- 通过 `API_MASTER_ID` 关联 HTH API Master；
- 同一 Account/API 组合唯一；
- 使用 `OBJECT_STATUS` 软启停。

### 9.3 Approval 数据

请求和审批状态只存平台 Transaction：

- Task：`UAT_N_HUA_NEW`；
- Action：Create；
- Snapshot：完整 `HostToHostUserAccessDTO`；
- Reference：平台 Transaction ID。

## 10. 权限与安全

- UI Authorization：Search/Accounts 为 View；Submit 为 Perform/Approve。
- Checker OTP/iToken、审批层级、Role/Determinant 复制 BCO Create Task 配置。
- Summary 的 Associated Company 过滤和后端 Relationship 校验必须同时存在。
- 日志不得打印完整 Account Number、Snapshot 或 OTP。
- Checker 重入 Fail Closed；Relationship 或 Account 在 Pending 期间失效时不得落库。

## 11. 错误处理

| 场景 | 结果 |
| --- | --- |
| Profile/CloseID 无效 | `DIGX_CZ_HTH_UA_001` |
| 企业 HTH 未启用 | `DIGX_CZ_HTH_UA_002` |
| Associated Context/Relationship 无效 | `DIGX_CZ_HTH_UA_003` |
| 非 CSA/TD | `DIGX_CZ_HTH_UA_004` |
| Account 不属于目标公司/用户 | `DIGX_CZ_HTH_UA_005` |
| API 不可用 | `DIGX_CZ_HTH_UA_006` |
| 未选择账户或 API | `DIGX_CZ_HTH_UA_009` |
| Context 已有 Active 配置 | `DIGX_CZ_HTH_UA_010` |
| Pending 期间账户失效 | `DIGX_CZ_HTH_UA_012` |
| 相同 Context 已 Pending | 平台 `DIGX_AP_0062` |

## 12. 测试设计

### 12.1 UI/Component

- Company 候选只来自 BCO `USERLINKAGE`，顺序与 BCO 一致。
- 从未配置或仍有未配置公司时只显示一条提示与同一行 `To link`，不逐条显示未配置公司。
- `To link` 弹窗只列尚未配置的用户级公司；选择后 Context 的 `accessPartyId` 正确。
- CSA/TD Tab 数据不同，切换不串数据。
- Account Type 深链正确设置初始 Tab。
- Copy、Back、Cancel 不污染目标 Associated Context。
- Confirmation 有平台 Reference；Quick Approve 能打开 Checker Detail。

### 12.2 API/Service

- 正常 Associated Create 生成 `UAT_N_HUA_NEW` Pending Transaction。
- 伪造 `accessPartyId`、跨公司账户、非 CSA/TD、未授权 API 均拒绝。
- 相同 Context 重复提交由平台拒绝；不同 Associated Company 可独立提交。
- Maker 后 Effective 表不变；Approve 后才写入；Reject 后不写入。

### 12.3 Approval/Regression

- Pending Approval、My Approval List、Activity Log 可见且使用同一 Transaction Reference。
- Checker Detail 从 Snapshot 还原相同公司、账户、API。
- Approve 后 Summary CSA/TD Count 正确。
- BCO Related/Associated Create/Edit/Delete 全回归不受影响。
- HTH RELATED Context 不受本 Associated Create 影响。

## 13. 验收条件映射

| Story AC | 设计实现 | 验证 |
| --- | --- | --- |
| AC1 To link 并配置账户/API | Summary `To link` -> linkage -> API mapping -> review | UI E2E |
| AC1 Current/Savings 默认 Tab | `initialAccountType` 默认 CSA | Component Test |
| AC1 展开账户显示企业 API Tree | 企业 Active API Catalogue | UI/API Test |
| AC1 Confirm 后 Pending Approval | `POST /submit` + `UAT_N_HUA_NEW` Snapshot | Maker/Checker Test |
| AC2 Approve 后 Summary 更新 | Approved Re-entry 写 Effective；Summary 聚合 CSA/TD | Integration Test |

## 14. 部署与回滚

本 Story 不新增表或 Task；依赖 595 的 HTH User Access Schema、Permission、Task、Assembler、
Repository Adapter 和 Error Message 已部署。

部署顺序：后端 common/module/REST -> Channel 组件/NLS/UI Authorization -> 刷新 Framework Cache ->
执行 Maker/Checker Smoke Test。

回滚只回退本 Story 的 Channel/Service 代码；不得直接删除已批准的 Effective 数据。若生产已产生
业务记录，数据撤销必须走获批的维护/迁移流程。

## 15. 关键实现文件

- `summary/summary.js`、`summary/summary.html`、`summary/model.js`
- `hth-account-linkage/hth-account-linkage.js`、`.html`、`model.js`
- `hth-api-service-mapping/hth-api-service-mapping.js`、`.html`
- `review-hth-user-access/review-hth-user-access.js`、`.html`
- `HostToHostUserAccess.java`（Application Service 与 REST）
- `SubmitHostToHostUserAccessApprovalAssembler.java`
- `HTH_USER_ACCESS_ACCOUNT` / `HTH_USER_ACCESS_ACCOUNT_API` Repository 与 Entity
