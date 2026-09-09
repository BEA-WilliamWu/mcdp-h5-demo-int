# BCOH2H-790 Technical Design

> 2026-09-09 合并更新：Code 表、密文校验、787 生成/审批接入及部署顺序以 [787/788/790/1204 合并说明](BCOH2H-787-788-790-1204-INTEGRATION.md) 为准。以下保留原始设计记录。

## HTH API Password — Reset

| 项目 | 内容 |
| --- | --- |
| Story | `BCOH2H-790` HTH API password - Reset |
| 文档状态 | Proposed（当前代码库尚无 API Password 实现） |
| 日期 | 2026-09-07 |
| 依赖设计 | `BCOH2H-788-TECHNICAL-DESIGN.md`；API Password Code 生成 Story `BCOH2H-789` |
| 影响范围 | Profile/Security 入口、HTH API Password Reset 页面、UAM credential replace、Reset Code 消耗、审计 |
| 不影响范围 | BCO Login Password/PIN、Signer PIN、首次 Setup、HTH User Access Maker/Checker、企业 UAM Client 绑定 |

## 1. 目标

已设置 HTH API Password 的 HTH API User，可从 BCO Profile 的 Security/Login 区域进入
`Change HTH API Password`，使用 Authorized Person 新生成的有效 HTH API Password Code 设置新密码。
成功后新密码立即生效、旧密码立即失效、Code 立即作废；任一校验或下游更新失败时，现有密码必须
继续有效，不能把用户留在没有可用 API Password 的状态。

Reset 是用户本人已登录后的即时安全操作，不走 Maker/Checker。它复用 788 的组件、中央密码策略、
Code Repository、UAM Credential Adapter、审计和加密传输，但使用独立的 `RESET` purpose、Task、
endpoint、错误及通知事件。

## 2. Story 范围与业务规则

### 2.1 前置条件

- 用户已完成 BCO 登录，Session 有效。
- `userChannelType` 规范化后为 `HTH`。
- HTH corporate profile 及 UAM client binding 有效。
- UAM 中该用户已有 `ACTIVE` HTH API Password。
- Authorized Person 已为该用户生成新的、未使用、未过期的 `RESET` Password Code。

### 2.2 Reset 与 Setup 的区别

| 项目 | First-time Setup（788） | Reset（790） |
| --- | --- | --- |
| 当前 credential | 不存在 | 必须存在且 ACTIVE |
| Code purpose | `SETUP` | `RESET` |
| 入口 | 首次登录后的 modal | Profile / Security and Login |
| UAM 操作 | create | atomic replace/update |
| 成功结果 | 创建第一版 password | 新版生效，旧版立即失效 |
| 不满足前置状态 | 引导 Reset | 引导 Setup/联系 AP |

Setup Code 与 Reset Code 不可互换。仅以 Code 字符串相同不能越过 `purpose`、party、user、status、
expiry 的绑定校验。

### 2.3 Story 字段解释

Figma 页面中的 `One Time Password (OTP)` 按业务验收条件解释为 `HTH API Password Code`，不是
OBDX 登录/交易 OTP。页面推荐使用准确 Label；若最终 UI 文案仍使用 OTP，后端 contract 和校验
语义不变。

### 2.4 明确不做

- 不要求输入当前/旧 API Password；Reset Code 承担本 Story 规定的重置授权证明。
- 不修改 BCO 登录密码、Login PIN、Signer PIN 或登录 Session。
- 不保存明文 password、旧 password、Code 或可还原密文。
- 不将 password 写入 `HTH_MANAGEMENT`、`HTH_UAM_CLIENT_REGISTRY`、
  `DIGX_CZ_UM_OPENAPI`、HTH User Access 表。
- 不先删除旧 credential 再创建新 credential。
- 不由前端直接访问 UAM。

## 3. 设计原则

1. **旧密码保护**：只有 UAM 确认新 credential 已原子替换成功，操作才成功。
2. **Code 一次性**：成功 Reset 后立即 `USED`；失败/未知状态按明确状态机处理。
3. **身份由 Session 决定**：前端不得指定要重置的其他 `partyId/userId`。
4. **中央策略为准**：规则展示可复用前端，但最终合规判断必须在后端/UAM。
5. **幂等可恢复**：网络超时重试不能重复切换 credential 或发送多次通知。
6. **与 BCO 隔离**：只在 HTH 用户 Profile 增加入口；不改变现有 Change Password。

## 4. 用户流程

```text
BCO Profile
  -> Security and Login
  -> H2H API Password / Change HTH API Password
  -> 后端确认 HTH + credential ACTIVE
  -> 输入 New Password、Re-enter Password、HTH API Password Code
  -> Submit
  -> 前端校验 + 敏感字段加密
  -> PUT reset
  -> 后端鉴权、状态、Code、密码策略校验
  -> 保留 Reset Code
  -> UAM atomic replace password
  -> 确认新 credential ACTIVE、旧 credential version invalid
  -> Code USED + 状态/审计更新
  -> 成功确认
```

失败分支：

- Code 无效/过期：不调用 UAM，旧密码不变。
- 密码不合规：不调用 UAM，旧密码不变。
- UAM 明确拒绝：Code 不标记 USED，旧密码不变。
- UAM timeout/结果未知：Code 进入 `IN_PROGRESS/UNKNOWN`，页面禁止重复提交；后台对账完成前不得
  宣称成功或恢复 Code。

## 5. 前端设计

### 5.1 Profile 入口

现有 `components/security/security-menu/security-menu.js` 为 Security 页面装配 Change Password 等
选项。建议增加 HTH-only item：

```javascript
{
  id: "changeHthApiPassword",
  module: "hth-api-password",
  parentModule: "security",
  data: { mode: "RESET" }
}
```

显示条件：

- `userChannelType=HTH`；
- `/hostToHostApiPassword/status` 返回 `credentialState=ACTIVE`；
- 用户具备 Reset task entitlement；
- 企业 HTH/UAM 配置有效。

不要仅凭前端 `userChannelType` 显示后就认为可执行；后端重复鉴权。普通 BCO 用户的 Security Menu、
既有 `changePassword` 与组件排序保持不变。

### 5.2 页面复用

复用 788 建议的 `extensions/components/hth-api-password`，以 `mode=RESET` 切换：

- 标题：`Change HTH API Password`；
- 字段：New Password、Re-enter Password、HTH API Password Code；
- 操作：Submit、Cancel/Back；
- 成功文案：HTH API Password changed successfully；
- API：调用 Reset endpoint，不调用 Setup endpoint。

不能简单把 `extensions/components/change-password` 的 model endpoint 指向 HTH；现有组件的
`me/credentials` 会更新 BCO Login Credential，业务对象不同。可复用 form/policy rendering、
`customer-pin-encrypt` 和交互模式，但必须有独立 model/service。

### 5.3 校验与状态

| 检查 | 前端行为 |
| --- | --- |
| 必填 | 三个字段均 required |
| Confirm mismatch | 阻止 API，显示 `The passwords do not match.` |
| Policy | 根据服务端 HTH policy 即时显示，但只作 UX 校验 |
| Submitting | 禁用 Submit/Cancel，避免重复请求 |
| Session timeout | 清空敏感字段并返回 Login |
| Server error | 清空 password；Code 是否清空按安全策略，推荐全部清空 |

页面卸载、Cancel、Back、成功/失败完成后必须清除 KO observable、DOM value、payload、RSA 临时 token。
禁止将字段写入 session/local storage。

### 5.4 密码规则

沿用 788/原系统规则配置，Story/Figma 当前规则为：

- 8–16 个 alphanumeric；
- 同时含字母与数字；
- 至少两个数字；
- 无空格、无特殊字符。

Reset 还应执行 UAM 的 Password History/Reuse 策略；新密码与旧密码相同应由后端/UAM拒绝，前端
无需取得旧密码作比较。

## 6. API Contract（建议新增）

### 6.1 状态/策略

复用 788：

```http
GET /cz/v1/hostToHostApiPassword/status?locale=en
```

Reset 场景示例：

```json
{
  "status": { "result": "SUCCESSFUL" },
  "setupState": "ACTIVE",
  "resetAllowed": true,
  "passwordPolicy": {
    "minLength": 8,
    "maxLength": 16,
    "numericRequired": 2,
    "alphabetRequired": 1,
    "specialCharsAllowed": false,
    "spacesAllowed": false
  }
}
```

不返回旧 credential id、password metadata、Code 是否存在的过细信息或失败次数。

### 6.2 Reset

```http
PUT /cz/v1/hostToHostApiPassword/reset?locale=en
Content-Type: application/json
```

逻辑 Payload（实际值需加密）：

```json
{
  "encryptedPassword": "BASE64_CIPHER_TEXT",
  "encryptedPasswordCode": "BASE64_CIPHER_TEXT",
  "rsaKeyIndicator": "KEY_VERSION",
  "requestId": "UUID"
}
```

身份从 authenticated Session 提取；禁止在 body/query 中接受可覆盖的 userId/partyId/uamClientId。

成功：

```http
200 OK
```

```json
{
  "status": {
    "result": "SUCCESSFUL",
    "referenceNumber": "HTH_API_PWD_RESET_REFERENCE",
    "message": { "code": "HTH_API_PASSWORD_RESET_SUCCESS", "type": "INFO" }
  }
}
```

相同 `requestId` 重试返回相同最终结果；不能第二次消费 Code 或产生第二次 credential rotation。

## 7. 后端设计

### 7.1 Service 与 Task

与 788 共用类：

- REST：`com.ofss.digx.cz.bea.appx.hosttohost.service.HostToHostApiPassword`
- Service：`com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostApiPassword`
- Adapter：`IHthApiCredentialAdapter`
- Task 建议：`CM_N_HAP_RESET`（最终名称由 Task Governance 确认）
- Entitlement：`Change HTH API Password`，只允许用户本人执行。

不配置 Approval aspect。若 Security 要求 Step-up 2FA，可配置平台
`TaskAspect.TWO_FACTOR_AUTHENTICATION`，但必须确保 OMB/OTP 回跳保留 requestId 和加密 payload，且
HTH API Password Code 仍需单独校验。

### 7.2 Reset 处理顺序

1. 从 SessionContext 解析 subject、partyId、canonical userId/closeId。
2. 校验用户 Active、`userChannelType=HTH`、Reset entitlement。
3. 校验 HTH corporate profile Enabled；读取 Active `HTH_MANAGEMENT.UAM_CLIENT_ID`。
4. 解密 password/code；失败返回统一错误并终止。
5. 从 UAM/可信状态投影确认当前 credential 为 `ACTIVE`；不存在时拒绝 Reset。
6. 执行中央 HTH API Password Policy 和 history/reuse 校验。
7. 对当前用户 `purpose=RESET` 的 Active Code 加锁并验证 hash、expiry、attempt、status。
8. Code 失败则原子增加 attempts；不调用 UAM，不影响旧 credential。
9. 将 Code 切换 `ACTIVE -> IN_PROGRESS`，绑定 requestId。
10. 调用 Adapter 的原子 `replacePassword`，传递 credential version/etag（若 UAM 支持）。
11. UAM 返回成功后查询/校验新 version 已 Active、旧 version 已 invalid。
12. 本地事务将 Code 置 `USED`、记录 usedAt，更新状态投影 `LAST_RESET_AT/version`。
13. 写 audit/activity；若后续 Reset 通知 Story 已启用，发布一次 reset-success event。
14. 清除敏感变量并返回 reference。

### 7.3 Adapter Contract

在 788 接口上增加：

```java
HthApiCredentialResult replacePassword(
    HthApiCredentialContext context,
    char[] newPassword,
    String currentVersion,
    String idempotencyKey);
```

Adapter 必须满足：

- replace 成功是原子的：新密码生效与旧密码失效为同一语义操作；
- 明确失败不得破坏旧密码；
- 超时后可按 idempotency key 查询最终结果；
- 返回 credential version/status，不返回 password/hash；
- UAM client secret 由 vault/安全配置管理。

若下游只提供“delete old + create new”，不能直接用于本 Story；需在 UAM 层补原子 rotate API，或使用
支持双版本短事务切换的协议，并通过安全评审。

## 8. 数据与状态机

### 8.1 Reset Code

复用 `BCOH2H-789` 的专用 `HTH_API_PASSWORD_CODE`，本 Story 使用：

```text
PARTY_ID + USER_ID + PURPOSE=RESET + STATUS=ACTIVE
```

状态机：

```text
ACTIVE -> IN_PROGRESS -> USED
   |           |          (terminal)
   |           +-> ACTIVE       （仅下游明确未执行且可安全重试）
   |           +-> UNKNOWN      （下游结果未知，待对账）
   +-> EXPIRED / REVOKED / LOCKED
```

新的 Reset Code 生成时，同一用户旧的 Active Reset Code 必须被 `REVOKED`；Setup Code 不受影响，
但不能被 Reset endpoint 使用。

### 8.2 Credential 状态

UAM 是 Source of Truth。若使用 788 建议的 `HTH_API_PASSWORD_STATE` 投影，Reset 成功只更新：

- `CREDENTIAL_STATUS=ACTIVE`；
- `CREDENTIAL_VERSION=newVersion`；
- `LAST_RESET_AT`；
- `LAST_RECONCILED_AT`；
- 标准审计/版本字段。

不保存旧 version 的 secret/password。旧 version id 如为排查需要，只允许进入受限安全审计且需脱敏，
不进入业务表。

### 8.3 与企业 UAM Client 切换

Reset 使用操作开始时读取的 Active `HTH_MANAGEMENT.UAM_CLIENT_ID`。如果请求期间 corporate client
binding 被更换/禁用：

- 调用前版本检查失败则拒绝，要求刷新；
- 调用结果未知则按当次 management/client context 对账；
- 不允许自动改用新 client 重放同一 Code，以免在两个 realm 创建 credential。

## 9. 一致性与失败恢复

| 场景 | Code 状态 | 旧密码 | 新密码 | 返回/恢复 |
| --- | --- | --- | --- | --- |
| 输入/Policy 失败 | ACTIVE/attempt update | 有效 | 未创建 | 明确业务错误 |
| UAM 明确拒绝且未变更 | 恢复 ACTIVE 或按原因 LOCKED | 有效 | 无效 | 可安全重试/申请新 Code |
| UAM 成功 | USED | 立即无效 | 有效 | Success |
| UAM 超时未知 | UNKNOWN/IN_PROGRESS | 未知 | 未知 | Pending；按 requestId 对账 |
| UAM 成功但本地 finalize 失败 | IN_PROGRESS | 无效 | 有效 | 同 requestId 重试并 finalize |
| 通知失败 | USED | 无效 | 有效 | Reset 保持成功，通知独立重试 |

对账 Job 在 UNKNOWN 时查询 UAM credential version：

- 新 version Active：补 Code USED、状态投影和审计事件；
- 旧 version 仍 Active且下游确认未执行：恢复 Code ACTIVE；
- 无法判断：保持阻断并告警人工处理，不能猜测。

## 10. 安全设计

- password/code 全程使用 TLS + 平台 RSA 加密机制，服务端解密后尽快清理内存。
- API Password 不出现在 URL、日志、异常、audit detail、通知、APM tag。
- Code 使用 salted hash/HMAC、constant-time compare、有效期、最大尝试次数、用户/用途绑定。
- Reset endpoint 增加 user/device/IP 维度 rate limit，连续失败触发安全事件。
- 不通过不同错误文案泄露用户、credential 或 Code 是否存在；登录后的过期/无效可按 Story 展示。
- 成功 Reset 应撤销现有 HTH API session/token，或至少使下一次 API authentication 强制使用新凭证；
  否则“旧密码失效”不等同于已签发 token 失效，需 Product/Security 明确。
- 如果外部 API 使用 Basic/password grant，旧密码验证在 UAM authentication endpoint 上回归；
  BCO 登录仍使用原 Login PIN，不受影响。

## 11. 错误处理

| 场景 | 建议错误码 | 用户行为 |
| --- | --- | --- |
| Code 无效 | `HTH_API_PASSWORD_CODE_INVALID` | 显示 Story 文案，允许重输 |
| Code 过期 | `HTH_API_PASSWORD_CODE_EXPIRED` | 联系 Authorized Person 生成新 Code |
| Code 已使用/撤销 | `HTH_API_PASSWORD_CODE_UNAVAILABLE` | 申请新 Code |
| Code purpose 不符 | 对外仍使用 INVALID/UNAVAILABLE | 不泄露具体用途 |
| 密码不一致 | `HTH_API_PASSWORD_MISMATCH`（前端） | 阻止调用 |
| 密码策略/历史冲突 | `HTH_API_PASSWORD_POLICY_VIOLATION` | 展示安全规则 |
| credential 不存在 | `HTH_API_PASSWORD_NOT_SETUP` | 引导首次 Setup |
| credential 锁定 | `HTH_API_PASSWORD_LOCKED` | 联系银行/AP |
| corporate client 不可用 | `HTH_API_PASSWORD_CLIENT_UNAVAILABLE` | 暂不可用 |
| 结果未知 | `HTH_API_PASSWORD_RESULT_PENDING` | 禁止重复，以 reference 查询 |

Story 指定文案：

- Invalid：`The HTH API password code you entered is invalid, please enter it again.`
- Expired：`The HTH API Password Code has expired. Please contact your Authorized Person to generate a new code.`
- Mismatch：`The passwords do not match.`

HTTP 不得使用 200 + ERROR；建议状态错误 `409`、校验 `400/422`、鉴权 `401/403`、下游暂时失败
`503`、已受理但待对账 `202`。

## 12. 审计、Activity 与通知

### 12.1 审计

记录：action `HTH_API_PASSWORD_RESET`、party/user、result/reason、request/reference/context id、
credential version 的非敏感标识、timestamp、source channel/device/IP。

不记录 password、Code/hash、RSA token、UAM client secret、下游 raw payload。

### 12.2 通知

本 Story 未直接要求 Reset 成功通知；仓库 Story `BCOH2H-1205` 若负责 Reset notification，Reset
Service 成功后应发布独立 `HTH_API_PASSWORD_RESET_SUCCESS` 事件供其消费。未上线 1205 时不应
复用 1204 的 First-time Setup 模板伪装成 Reset 通知。

通知失败不回滚 password reset，并按既有 BCO Logon PIN/Password 告警失败流程处理。

## 13. 测试设计

### 13.1 单元测试

- HTH/BCO 用户入口和后端 eligibility。
- ACTIVE/NOT_SETUP/LOCKED/UNKNOWN credential 状态。
- RESET Code 的 valid/invalid/expired/used/revoked/locked/purpose mismatch。
- 密码长度、字符、数字、空格、特殊字符、history/reuse。
- 幂等 requestId、状态机、失败计数、并发锁。
- Session identity 覆盖 body/query 伪造身份。

### 13.2 API/集成测试

- 正常 Reset：新密码成功、旧密码失败、Code USED。
- invalid/expired/mismatch/policy failure：旧密码仍成功，新密码失败，UAM mutation 未调用。
- UAM 明确失败：旧 password 不变。
- UAM timeout + reconciliation：最终状态只有一个版本 Active。
- 相同 requestId 重试：同 reference、一次 rotate、一次事件。
- 两个并发 Reset：只有一个 Code/credential version 成功。
- corporate client binding 在处理中变化的版本冲突。

### 13.3 E2E/UI

- HTH ACTIVE 用户 Profile 显示入口；BCO/NOT_SETUP 用户不显示或正确引导。
- Desktop/mobile、三语言、无障碍、Back/Cancel/session timeout。
- 指定三条 Story 错误文案。
- 成功后用旧/新 API Password 分别调用受保护 HTH test endpoint。
- BCO Login Password 仍可登录，证明 credential 隔离。

### 13.4 安全测试

- Reset Code 跨用户、跨 corporate、跨 purpose、重放和暴力尝试。
- CSRF/nonce/session fixation、越权调用、rate limit。
- 浏览器、server、APM、DB、notification 中无敏感值。
- 已签发 HTH API token 的撤销策略符合安全决定。

## 14. Acceptance Criteria 映射

| AC | 设计覆盖 |
| --- | --- |
| AC1 Profile 显示 Change HTH API Password | 第 5.1 节 |
| AC2 打开 Change 页面 | 第 4、5.2 节 |
| AC3 有效 Code 更新、Code 立即失效 | 第 6.2、7.2、8、9 节 |
| AC4 Invalid Code，旧密码不变 | 第 9、11、13 节 |
| AC5 Expired Code 提示 | 第 11 节 |
| AC6 Password mismatch | 第 5.3、11 节 |
| AC7 旧密码失效、新密码成功 | 第 7.3、9、13.2 节 |

## 15. 部署与回滚

### 15.1 上线顺序

1. 789 Password Code 基础能力与 788 UAM Adapter/State；
2. Reset Service/REST/Task/Entitlement/Error Messages；
3. 可选 1205 Reset notification 配置；
4. Channel Profile entry、共用组件和 NLS；
5. 清理 Task/Permission/Configuration cache 并重启受影响 managed server；
6. Verification、UAM rotate smoke test、旧/新密码 E2E。

### 15.2 Feature Toggle

建议 `ENABLE_HTH_API_PASSWORD_RESET` 默认 `N`；只有 Setup/UAM Adapter、Code 生成和安全监控准备好
后开启。Reset toggle 与 Setup toggle 分开，便于紧急关闭 rotate 而不影响状态查询。

### 15.3 回滚

- 关闭 Reset toggle 并隐藏 Profile 入口。
- 不自动恢复任何旧密码；已成功 rotate 的 credential 保持当前 UAM 状态。
- 回滚 Channel/REST/Service/Task mapping，保留审计和 Code 状态。
- UNKNOWN 请求必须先对账后处理，不能因应用回滚而将 Code 直接恢复 ACTIVE。

## 16. 关键文件（实施建议）

| 类型 | 文件/目录 | 动作 |
| --- | --- | --- |
| Channel | `consulting/channel/components/security/security-menu/security-menu.js` | 增加 HTH-only Profile entry |
| Channel | `consulting/channel/extensions/components/hth-api-password/` | 复用 788 组件，增加 RESET mode |
| NLS | `consulting/channel/extensions/resources/nls/hth-api-password.js` | Reset 标题、错误、成功文案 |
| REST | `...appx.service.rest/.../hosttohost/service/HostToHostApiPassword.java` | 增加 `PUT reset` |
| Service | `...module.hosttohost/.../app/hosttohost/service/HostToHostApiPassword.java` | 增加 reset business flow |
| Adapter | `...app.xface/.../hosttohost/adapter/IHthApiCredentialAdapter.java` | 增加 atomic replace/query result |
| DB | `consulting/db/branch_change_history/<API_PASSWORD>/` | Task、Permission、Process、Adapter、Error Message、可选 Notification、Verification |

## 17. 实施前待确认项

1. UAM 是否原生支持 atomic password replace、idempotency 和 result query。
2. Reset 成功是否必须撤销已签发 access token/API session。
3. Reset 是否需要额外平台 2FA；Story 当前只要求 Password Code。
4. Figma `OTP` 是否正式改为 `HTH API Password Code`。
5. `BCOH2H-1205` 是否负责 Reset 成功通知及其收件人。
6. Task ID、endpoint URI、错误码和 feature toggle 最终命名。
