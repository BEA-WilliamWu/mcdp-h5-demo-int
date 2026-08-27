# BCOH2H-538 / BCOH2H-595 Database Changes

| 项目 | 内容 |
| --- | --- |
| Stories | BCOH2H-538、BCOH2H-595 |
| 文档状态 | As Implemented |
| 业务 Schema | `HTH_BEA` |
| 更新时间 | 2026-08-28 |

## 1. 变更结论

- BCOH2H-538 不新增独立业务表。它读取既有 `HTH_USER_PROFILE`，并从 595 的生效表和审批请求表生成 Related/Associated Summary。
- BCOH2H-595 新增 5 张业务表：2 张生效表、3 张 Maker Request Snapshot 表。
- 没有 `HTH_USER_ACCESS` Header 表。User/Company Context 直接保存在每个 Effective Account Grant 中，避免重复建模。
- Account Type 只允许 `CSA`（Current and Savings）与 `TD`（Time Deposit）。
- 5 张 Feature Table 均不包含 `OBJECT_VERSION_NUMBER`；平台公共配置表原有的 `OBJECT_VERSION_NUMBER` 继续保留并正常写入。
- 每张 Feature Table 和每个 Field 都有 `COMMENT ON`。

## 2. 前置对象

以下对象必须已存在于 `HTH_BEA`：

| 对象 | 用途 |
| --- | --- |
| `HTH_USER_PROFILE` | 以 `(PARTY_ID, CLOSE_ID)` 标识 HTH 用户。 |
| `HTH_MANAGEMENT` | 企业 HTH Enable/Disable 状态。 |
| `HTH_MANAGEMENT_API` | 企业已启用 API Mapping。 |
| `HTH_API_MASTER` | API Code、Name、Display Order 与 Active 状态。 |

新 Schema Script 不修改这些平台/前置表的 Column，也不会删除它们的版本字段。

## 3. Table 结构

```text
Effective Grants
├── HTH_USER_ACCESS_ACCOUNT
└── HTH_USER_ACCESS_ACCOUNT_API

Maker Request Snapshot
├── HTH_USER_ACCESS_REQUEST
├── HTH_USER_ACCESS_REQ_ACCOUNT
└── HTH_USER_ACCESS_REQ_API
```

### 3.1 `HTH_USER_ACCESS_ACCOUNT`

一行代表某个 HTH User 在一个 Related/Associated Company Context 下的一项已审批 Account Grant。

| Field | Type | 作用 |
| --- | --- | --- |
| `ID` | `VARCHAR2(36)` | UUID Primary Key。 |
| `PARTY_ID` | `VARCHAR2(64)` | 拥有 HTH User 的主 Party。 |
| `CLOSE_ID` | `VARCHAR2(255)` | HTH User CloseID。 |
| `ACCESS_PARTY_ID` | `VARCHAR2(64)` | 拥有被授权 Account 的 Party。 |
| `LINKAGE_TYPE` | `VARCHAR2(16)` | `RELATED` 或 `ASSOCIATED`。 |
| `ACCOUNT_NUMBER` | `VARCHAR2(64)` | Canonical Account Number；授权维护页面可按 BCO 方式显示，日志不得直接打印。 |
| `ACCOUNT_TYPE` | `VARCHAR2(8)` | `CSA` 或 `TD`。 |
| `CURRENCY` | `VARCHAR2(3)` | 显示快照，不作为 Ownership 校验依据。 |
| `OBJECT_STATUS` | `VARCHAR2(1)` | `A` Active、`I` Inactive。 |
| `CREATED_BY` | `VARCHAR2(255)` | 创建用户。 |
| `CREATION_DATE` | `DATE` | 创建时间。 |
| `LAST_UPDATED_BY` | `VARCHAR2(255)` | 最后更新用户。 |
| `LAST_UPDATE_DATE` | `DATE` | 最后更新时间。 |

关键约束：

```text
PK (ID)
UNIQUE (PARTY_ID, CLOSE_ID, ACCESS_PARTY_ID, LINKAGE_TYPE, ACCOUNT_TYPE, ACCOUNT_NUMBER)
FK (PARTY_ID, CLOSE_ID) -> HTH_BEA.HTH_USER_PROFILE
CHECK LINKAGE_TYPE IN ('RELATED','ASSOCIATED')
CHECK ACCOUNT_TYPE IN ('CSA','TD')
CHECK OBJECT_STATUS IN ('A','I')
```

Index：

- `IX_HTH_UAA_ACCOUNT_NO`：Runtime Account Authorization Lookup。
- `IX_HTH_UAA_CONTEXT`：Summary、Detail、Soft Replace。

### 3.2 `HTH_USER_ACCESS_ACCOUNT_API`

一行代表某个 Effective Account Grant 下的一项已审批 API Grant。

| Field | Type | 作用 |
| --- | --- | --- |
| `ID` | `VARCHAR2(36)` | UUID Primary Key。 |
| `HTH_USER_ACCESS_ACCOUNT_ID` | `VARCHAR2(36)` | FK 到 Effective Account Grant。 |
| `API_MASTER_ID` | `VARCHAR2(36)` | FK 到 `HTH_BEA.HTH_API_MASTER`。 |
| `OBJECT_STATUS` | `VARCHAR2(1)` | `A`/`I` Soft Status。 |
| `CREATED_BY` / `CREATION_DATE` |  | 创建审计字段。 |
| `LAST_UPDATED_BY` / `LAST_UPDATE_DATE` |  | 更新审计字段。 |

Unique Key 为 `(HTH_USER_ACCESS_ACCOUNT_ID, API_MASTER_ID)`；`IX_HTH_UAAA_API` 支持 API Disable 影响分析。

### 3.3 `HTH_USER_ACCESS_REQUEST`

Maker Request Header Snapshot。它保存审批所需的完整 Context，但不保存另一份 Approval Status；当前状态由 `TRANSACTION_ID` 关联 `DIGX_AP_TRANSACTION` 得到。

| Field | Type | 作用 |
| --- | --- | --- |
| `ID` | `VARCHAR2(36)` | UUID Primary Key。 |
| `TRANSACTION_ID` | `VARCHAR2(64)` | OBDX Approval Transaction ID，Unique。 |
| `REFERENCE_NO` | `VARCHAR2(64)` | UI、Approval List、Activity Log 使用的业务 Reference，Unique。 |
| `ACTION_TYPE` | `VARCHAR2(16)` | `CREATE`、`EDIT`、`DELETE`。 |
| `PARTY_ID` / `CLOSE_ID` |  | HTH User Context Snapshot。 |
| `ACCESS_PARTY_ID` / `LINKAGE_TYPE` |  | Account-owning Company Context Snapshot。 |
| `USER_NAME` / `FULL_NAME` |  | Checker Review 显示快照。 |
| `ACCESS_PARTY_NAME` | `VARCHAR2(255)` | Company Name 显示快照。 |
| `OBJECT_STATUS` 与审计字段 |  | Soft Status 与创建/更新审计。 |

`IX_HTH_UAR_CONTEXT` 支持同一 Context Pending Request 检查。

### 3.4 `HTH_USER_ACCESS_REQ_ACCOUNT`

Maker 提交 Create/Edit 时的 Account Snapshot；Delete Request 没有 Account Child。

| Field | Type | 作用 |
| --- | --- | --- |
| `ID` | `VARCHAR2(36)` | UUID Primary Key。 |
| `HTH_USER_ACCESS_REQUEST_ID` | `VARCHAR2(36)` | FK 到 Request Header。 |
| `ACCOUNT_NUMBER` | `VARCHAR2(64)` | 提交时 Canonical Account。 |
| `ACCOUNT_TYPE` | `VARCHAR2(8)` | `CSA` 或 `TD`。 |
| `CURRENCY` | `VARCHAR2(3)` | 提交时显示快照。 |
| `DISPLAY_ORDER` | `NUMBER` | Checker Review 顺序。 |
| `OBJECT_STATUS` 与审计字段 |  | Soft Status 与创建/更新审计。 |

Unique Key 为 `(HTH_USER_ACCESS_REQUEST_ID, ACCOUNT_TYPE, ACCOUNT_NUMBER)`，Account Type Check Constraint 为 `IN ('CSA','TD')`。Account Type 必须进入唯一键，因为 BCO 可把同一个 Account Number 同时归入 CSA 和 TD。

### 3.5 `HTH_USER_ACCESS_REQ_API`

Maker 提交时每个 Request Account 下的 API Snapshot。

| Field | Type | 作用 |
| --- | --- | --- |
| `ID` | `VARCHAR2(36)` | UUID Primary Key。 |
| `HTH_USER_ACCESS_REQ_ACC_ID` | `VARCHAR2(36)` | FK 到 Request Account。 |
| `API_MASTER_ID` | `VARCHAR2(36)` | FK 到当前 API Master，Checker Approval 时重新验证。 |
| `API_CODE` | `VARCHAR2(64)` | Maker 提交时 API Code 快照。 |
| `API_NAME` | `VARCHAR2(255)` | Maker 提交时 API Name 快照。 |
| `DISPLAY_ORDER` | `NUMBER` | Checker Review 顺序。 |
| `OBJECT_STATUS` 与审计字段 |  | Soft Status 与创建/更新审计。 |

Unique Key 为 `(HTH_USER_ACCESS_REQ_ACC_ID, API_MASTER_ID)`；`IX_HTH_UARAPI_API` 支持 API 影响分析。

## 4. Maker/Checker 数据变化

### Maker Submit

1. Framework 生成 Approval Transaction。
2. Service 重新验证 Profile、Company Relationship、Account Ownership 和 Enterprise API Catalogue。
3. 在一个独立 NONXA Transaction 中写 Request Header、Selected Accounts 和 Selected APIs。
4. Snapshot 全部成功才 Commit；失败全部 Rollback。
5. Effective Tables 不变，因此 Pending 状态不会提前获得权限。

### Checker Approve

1. 按 Framework Transaction ID 重新读取数据库 Snapshot。
2. 使用审批时的当前 Profile、Relationship、Portfolio 和 API Catalogue 再验证。
3. Create/Edit 先把旧 Effective Grants 设为 `I`，再复用或创建所选 CSA/TD Account/API Row 并设为 `A`。
4. Delete 把 Context 下 API Row 和 Account Row 依次设为 `I`。
5. Summary 只统计 `OBJECT_STATUS='A'`。

### Checker Reject

Reject 不进入 `APPROVED` Re-entry，Effective Tables 不变。Approval List 与 Activity Log 由三个 Task 的 Approval/Audit Aspect 记录。

## 5. Runtime Authorization Query

Runtime Allow 需要同一查询链全部 Active：

- `HTH_USER_ACCESS_ACCOUNT`
- `HTH_USER_ACCESS_ACCOUNT_API`
- `HTH_API_MASTER`
- `HTH_MANAGEMENT` 且 `HTH_STATUS='ENABLE'`
- `HTH_MANAGEMENT_API`

查询 Key 为 `(PARTY_ID, CLOSE_ID, ACCOUNT_NUMBER, API_CODE)`。任何缺失、Inactive 或查询失败都 Deny。实际 HTH Gateway/Dispatcher 必须在解析出这四项后调用 `HostToHostRuntimeAuthorizer`；该外部 Ingress 不在当前 Repository 中。

## 6. Configuration SQL

除 5 张业务表外，交付 SQL 还维护：

- Search/Accounts/Submit/Edit/Delete Resource 与 Entitlement。
- Maker/Checker Role Group Mapping。
- `UAT_N_HUA_NEW`、`UAT_N_HUA_EDT`、`UAT_N_HUA_DEL` Task。
- Approval、Audit、Blackout Task Aspects。
- Approval Assembler Base/Override Configuration。
- 5 个 Repository Adapter Base/Override Configuration。
- 12 个 Error Code，每个包含 English、Simplified Chinese、Traditional Chinese。

这些平台表已有 `OBJECT_VERSION_NUMBER`，SQL 必须继续提供该 Column；不得删除公共表字段。

## 7. SQL 文件与执行顺序

目录：

```text
consulting/db/branch_change_history/20260825_HTH_User_Access/
```

全新安装：

1. `1_HTH_User_Access_Schema.sql`
2. `2_HTH_User_Access_Permission.sql`
3. `2_HTH_User_Access_Permission_1_Maker.sql`
4. `2_HTH_User_Access_Permission_2_Checker.sql`
5. `3_HTH_User_Access_Process.sql`
6. `4_HTH_User_Access_Repository_Adapters.sql`
7. `5_HTH_User_Access_Error_Messages.sql`
8. `6_HTH_User_Access_Verification.sql`

已执行旧版 Schema 的环境：

1. 不重跑 Schema，不删表。
2. 执行 `7_HTH_User_Access_Time_Deposit_Upgrade.sql`，扩展两项 Account Type Constraint，并把两项 Account Unique Key 改为 `Account Type + Account Number`。
3. 重跑可重复执行的 Error Message SQL以更新 004 文案。
4. 执行 Verification SQL。

## 8. Verification

部署后至少检查：

- 5 张 Table 全部位于 `HTH_BEA`。
- 2 项 Account Type Constraint 为 Enabled，并同时包含 `CSA`、`TD`。
- 2 项 Account Unique Key 都同时包含 `ACCOUNT_TYPE` 和 `ACCOUNT_NUMBER`。
- 所有 Foreign Key 为 Enabled。
- 8 个 Entitlement 与 8 个 Group Mapping。
- 5 个 Service Resource、16 个 Resource Action Mapping。
- 3 个 Task 都包含 Approval/Audit/Blackout。
- 5 组 Repository Adapter Base/Override Configuration。
- 12 个 Error Code，每个 3 个 Locale。

## 9. 回滚原则

- Configuration DML 使用 Feature-owned Key，可使用对应 Delete 条件回退。
- Oracle DDL 自动提交；已有业务数据后不得直接 Drop 5 张表。
- TD Upgrade 不删除或转换旧 CSA 数据。若代码回滚，保留允许 TD 的 Constraint 不会破坏旧 CSA Row；但包含 TD 生效数据时，旧代码无法维护这些 Row，必须先制定业务数据回退方案。
- 不得删除或修改平台公共表的 Column，包括 `OBJECT_VERSION_NUMBER`。
