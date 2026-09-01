# BCOH2H-782 Technical Design

## Related Account Summary — Edit

| 项目 | 内容 |
| --- | --- |
| Story | `BCOH2H-782` Related Account Summary edit |
| 文档状态 | Proposed（基于当前 BCO-aligned HTH User Access 基线） |
| 日期 | 2026-09-01 |
| 前置设计 | `BCOH2H-595-TECHNICAL-DESIGN.md` |
| 影响范围 | HTH User Accounts & Services Access；Related Account；Edit |
| 不影响范围 | BCO 原有编辑流程、Associated Context、Certificate/API Master Maintenance |

## 1. 目标

为已有 HTH Related Account 配置的用户提供 Edit 能力。管理员可从 HTH Summary 的
Related Account Summary 进入 Current and Savings 或 Time Deposits，查看现有账户/API 权限，
切换到 Edit 后增加、移除账户或调整 API；提交后进入与 BCO 一致的 Maker/Checker 审批。
批准前 Effective Access 保持不变，批准后以审批快照完整替换该 Related Context。

## 2. 业务范围与关键解释

### 2.1 适用 Context

```text
(partyId, closeId, accessPartyId = partyId, linkageType = RELATED)
```

Related 表示目标用户在主公司本身的账户权限，因此：

- `accessPartyId` 必须等于 `partyId`；
- Summary 下不显示 Company Name；
- Account Inventory 与 BCO 对同一 `partyId + userId` 的 Related 账户查询同源；
- Edit 只能修改这一 Related Context，不得影响任何 Associated Company。

### 2.2 Story 中 “successful update” 的生命周期

启用 Maker/Checker 时，Maker 提交后的成功含义是 **request sent for approval**，状态为
`Pending Approval`；不是 Effective Access 已更新。只有 Checker Approve 后才是业务配置更新成功。

### 2.3 完整替换而非局部 Patch

Edit Payload 是该 Related Context 的完整目标状态，涵盖 CSA 和 TD。即使操作者只在一个 Tab
做修改，另一 Tab 的既有选择也必须原样保留在 Payload 中；后端不能只按当前 Tab 做 Patch，
否则会误删未打开 Tab 的权限。

## 3. 与 BCO 对齐的设计原则

1. Summary 布局、Account Type Link、View/Edit/Cancel/Back、Review、Confirmation 与 BCO 一致。
2. 账户集合和顺序复用 BCO Account Access 返回；HTH 只叠加 API Catalogue 和 HTH Effective State。
3. Maker/Checker、Pending Approval、Quick Approve、Activity Log、OTP 使用平台公共机制。
4. BCO 与 HTH 通过独立入口、Task 和 Service 隔离；不得在 BCO Branch 注入 HTH 判断。
5. 读取、提交、重复判断和批准均以完整 Related Context 为边界。

## 4. 用户流程

```text
HTH Summary
  -> Related Account Summary
  -> 点击 Current and Savings / Time Deposits
  -> HTH Account Linkage（VIEW，定位对应 Tab）
  -> Edit
  -> 修改账户选择 -> Next
  -> 修改每个账户的 API -> Save
  -> Review（完整 CSA + TD）
  -> Confirm
  -> UAT_N_HUA_EDT Pending Transaction
  -> Confirmation（Reference No. / Pending Approval / Quick Approve）
  -> Checker Detail
  -> Approve 或 Reject
  -> Approve：完整替换 Related Effective Access
  -> Summary / Activity Log 更新
```

## 5. 前端设计

### 5.1 Summary

复用 `summary` 组件的 HTH Branch：

- `GET /hostToHostUserAccess/search` 提供 HTH Effective Count；
- BCO `readAllUserAccountDetails(partyId, userId)` 提供同一用户的账户模型；
- Related Summary 只显示 Account Type 与 Number of Account(s)，不重复显示主公司名称；
- `Current and Savings`、`Time Deposits` 为可点击链接；
- 点击链接传递完整 Context、`action=EDIT` 和 `initialAccountType=CSA|TD`。

若已有 Active Related Access，则进入 VIEW/EDIT；若无 Active Access，应走 Create Story，不能
把空配置伪装成 Edit。

### 5.2 Account Linkage

`hth-account-linkage` 初始化规则：

```text
setupStatus = ACTIVE -> mode = VIEW
点击 Edit              -> mode = EDIT
initialAccountType=TD   -> 默认 TD Tab
其他                    -> 默认 CSA Tab
```

VIEW：

- 账户 Checkbox 只读并反映当前 Effective Selection；
- 显示 Edit/Delete、Back/Cancel；
- CSA/TD 数据独立，禁止两个 Tab 绑定同一数组或同一 DataProvider。

EDIT：

- 账户可选；`Link All Accounts` 仅作用于当前 Tab；
- 切 Tab 时保留两个 Tab 的 Working Copy；
- Cancel 恢复进入 Edit 前的 Deep Copy；
- Back 检测未保存变化；
- 至少保留一个账户才能进入 Next；若用户要清空整个 Context，应使用 Delete 流程。

### 5.3 Account Inventory 合并

前端 `hth-account-linkage/model.js` 同时读取：

1. HTH `/accounts`：企业 API Catalogue、Effective Selected Accounts/APIs；
2. BCO Account Access：同一 `partyId + userId` 的 Related 账户及展示顺序。

合并键使用标准化的 Account Type + Canonical Account Number；展示字段以 BCO 为主，HTH 补充
`apiServices` 和 Selected State。不得使用全企业账户列表，也不得用账号长度或格式化账号直接关联。

### 5.4 API Mapping

- 只显示在 Linkage 页选中的账户。
- 每个账户展开后显示企业 Active HTH API。
- 进入 Edit 时预选 Effective API。
- 每个选中账户至少保留一个 API。
- Save 生成完整 Related Context 的 Working Snapshot，包括未修改 Tab。

### 5.5 Review、Confirmation 与 Checker Detail

- Maker Review 使用内存中的完整 Working Snapshot。
- Checker Detail 优先读取平台 `transactionSnapshot`，并兼容平台包装字段。
- Task `UAT_N_HUA_EDT` 决定 Action=EDIT，不依赖页面 URL 猜测。
- Confirmation 使用平台 Status/Reference；Quick Approve 以 Transaction ID 打开
  `review-hth-user-access`，不可直接跳空白的通用 Detail。
- 老版本返回 HTTP 400 + `DIGX_APPROVAL_REQUIRED` 时，只有成功解析本次 Transaction 后才显示成功。

## 6. API Contract

### 6.1 Detail

```http
GET /cz/v1/hostToHostUserAccess/accounts
    ?partyId={partyId}
    &closeId={closeId}
    &accessPartyId={partyId}
    &linkageType=RELATED
```

返回 CSA/TD Account Inventory、企业 API Catalogue、Effective Selection 和 Context Status。

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
  "accessPartyId": "PRIMARY_PARTY",
  "linkageType": "RELATED",
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

Payload 中 `accounts` 表示批准后的完整目标集合，不是 Delta。

## 7. 后端设计

入口：`HostToHostUserAccess.edit`，Task `UAT_N_HUA_EDT`。

### 7.1 Maker 校验

1. 验证 Edit Perform Entitlement。
2. 验证 HTH Profile 与目标 Close ID。
3. 强制 `linkageType=RELATED` 且 `accessPartyId=partyId`。
4. 验证该 Context 存在 Active Effective Access；不存在时拒绝 Edit。
5. 通过 BCO `IAccountAccess.listAccounts()` 获取当前可用账户并验证 Ownership。
6. 只允许 CSA/TD；同时校验 Canonical Number、Formatted Number、Product Code 的一致性。
7. 每个选中账户至少一个 API，API 必须仍为企业 Active。
8. 对 Account 和 API Business Key 去重。
9. 将完整 DTO 交给 Approval Framework；不更新 Effective 表。

### 7.2 审批与重复处理

- Approval Assembler 按完整 Related Context 生成 Entity Identifier。
- 相同 Context 已有 Pending Create/Edit/Delete 时，平台 Generic Duplicate Check 返回
  `DIGX_AP_0062`。
- 不同用户或不同 Associated Context 不应互相阻塞。
- Pending Approval、Checker Detail、Activity Log 均读取同一个平台 Transaction/Snapshot。

### 7.3 Checker 批准重入

批准时不能盲信 Maker Snapshot，必须按当前数据重新验证：

- Profile/用户仍有效；
- Related Context 仍合法；
- Account 仍属于目标用户/公司且未关闭；
- API 仍由企业启用。

验证成功后执行 `replaceEffectiveAccounts`：

1. 软停用该完整 Related Context 下旧的 Account API；
2. 软停用旧 Account 行；
3. 按批准 Snapshot 复用/重新激活或创建 Account 行；
4. 复用/重新激活或创建 Account API 行；
5. 完成平台 Transaction。

该替换范围只限：

```text
partyId + closeId + accessPartyId=partyId + linkageType=RELATED
```

Associated Context 不参与查询或停用。Reject 不改 Effective 数据。

## 8. 数据与审批模型

### 8.1 Effective 表

- `HTH_USER_ACCESS_ACCOUNT`：完整 Context、Canonical/Formatted Account Number、Product Code、
  CSA/TD、Currency、Status/Audit。
- `HTH_USER_ACCESS_ACCOUNT_API`：Account 与 API Master 的 Active Mapping。

不新增 Edit History 表；Effective 表表达当前生效状态，历史与 Maker/Checker 证据由平台 Transaction
和 Activity Log 保存。

### 8.2 Platform Transaction

| 项目 | 值 |
| --- | --- |
| Task | `UAT_N_HUA_EDT` |
| Parent | `UAT` |
| Action | Edit |
| Snapshot | 完整 Related Context + CSA/TD Accounts/APIs |
| Approval/OTP | 复制 BCO `UAT_N_UA` 当前环境配置 |
| Duplicate | 完整 Context Entity Identifier |

## 9. 权限、安全与审计

- Accounts View；Edit Perform/Approve 权限必须同时配置 Service Policy 与 UI Authorization。
- Checker Detail 只显示 Snapshot 中的业务数据；审批动作仍由平台 Role/Level/OTP 控制。
- 不允许通过前端修改 `accessPartyId` 将 Related Edit 变成 Associated Edit。
- 日志只记录 Context/Reference/数量，不打印完整 Account Number 或整个 Snapshot。
- Activity Log 按 `UAT_N_HUA_EDT` 和平台 Transaction Status 查询，不查 Effective 表猜测历史。

## 10. 错误处理

| 场景 | 结果 |
| --- | --- |
| Related Context 字段不一致 | `DIGX_CZ_HTH_UA_003` |
| Account 类型非 CSA/TD | `DIGX_CZ_HTH_UA_004` |
| Account 不属于用户/公司 | `DIGX_CZ_HTH_UA_005` |
| API 已停用或未授权 | `DIGX_CZ_HTH_UA_006` |
| 空选择或某账户无 API | `DIGX_CZ_HTH_UA_009` |
| Context 不存在 Active 配置 | `DIGX_CZ_HTH_UA_010` |
| Checker 时账户失效 | `DIGX_CZ_HTH_UA_012` |
| 相同 Context 有 Pending | 平台 `DIGX_AP_0062` |

所有非 Approval-required 的 HTTP 4xx 必须进入错误页面/Toast，不得显示 Confirmation Success。

## 11. 一致性与并发

- Maker Snapshot 是候选状态，Effective 表是已批准状态；两者在 Pending 期间不同是预期行为。
- Summary Count 始终读取 Effective 表，不应在 Maker 提交后提前变化。
- Pending List 读取平台 Transaction，不应通过 Effective Count 推导。
- 无 Feature Request 表，因此不会产生两套 Pending 状态。
- Unique Constraint 防止重复 Effective Account；平台 Entity Identifier 防止并发 Pending 修改。
- Checker 重新验证处理 Pending 期间账户关闭、API 停用或 Profile 变化。

## 12. 测试设计

### 12.1 UI

- Related Summary 不显示 Company Name。
- CSA/TD 链接打开正确 Tab，两 Tab 使用不同账户集合。
- VIEW 只读；Edit 后可选；Cancel 恢复原值；Back 提示未保存修改。
- 只编辑 CSA 时，TD 原配置仍包含在 Review/Payload；反向同样成立。
- Quick Approve 能加载完整 Checker Detail，不出现空白页或 `transactionId undefined`。

### 12.2 Service/Repository

- 无 Active Context 时 Edit 被拒绝。
- Maker 提交不改 Effective；Approve 执行完整 Replace；Reject 不变。
- 删除一个 Account/API 后批准，旧行变 Inactive；保留项复用/Active。
- Associated Context 数据在 Related Edit 前后完全一致。
- Account Number 不同表示格式能正确映射同一 Canonical Account。

### 12.3 Approval/Regression

- Pending Approval、My Approval List、Activity Log 显示同一 Reference。
- Checker Detail 与 Maker Review 的 CSA/TD/Account/API 一致。
- Duplicate Edit 返回平台提示且 Pending List 可定位原 Transaction。
- BCO User Account Access Edit/Approve/Reject 全流程回归。
- HTH Associated Create/Edit/Delete 回归。

## 13. 验收条件映射

| Story AC | 设计实现 | 验证 |
| --- | --- | --- |
| AC1 Edit Account and Service Access | Summary Account Type Link -> VIEW -> Edit | UI E2E |
| AC1 CSA/TD 权限调整 | 两个独立 Tab + 完整 Context Snapshot | Component/API Test |
| AC1 Maker/Checker | `UAT_N_HUA_EDT` + Platform Snapshot | Approval Test |
| AC1 Pending/Approval List/Activity Log | 平台 Transaction 单一来源 | Integration Test |
| AC2 与 Create 一致 | 共用 linkage/mapping/review 组件 | Regression |
| AC3 CSA/TD 都可编辑审批 | 完整替换包含两类账户 | E2E Matrix |

## 14. 部署与回滚

本 Story 复用 595 已建立的 `/accounts`、`/edit`、`UAT_N_HUA_EDT`、Approval Assembler、
Task Mapping 和两张 Effective 表；不新增数据库对象。

部署顺序：后端 Service/REST -> Channel Components/Task Mapping -> 刷新 Authorization/Framework Cache
-> Related Edit Maker/Checker Smoke Test。

代码回滚不得删除已批准 Effective 数据；需撤销业务配置时应通过正常 Edit/Delete 审批或获批数据迁移。

## 15. 关键实现文件

- `account-access-management/summary/summary.js|html|model.js`
- `account-access-management/hth-account-linkage/hth-account-linkage.js|html|model.js`
- `account-access-management/hth-api-service-mapping/hth-api-service-mapping.js|html`
- `account-access-management/review-hth-user-access/review-hth-user-access.js|html`
- `override/task-component-mapping.js`
- Application/REST `HostToHostUserAccess.java`
- `SubmitHostToHostUserAccessApprovalAssembler.java`
- HTH User Access Account/API Entity、Repository 与 Adapter
