# BCOH2H-788 Technical Design

> 2026-09-09 合并更新：Code 表、密文校验、787 生成/审批接入及部署顺序以 [787/788/790/1204 合并说明](BCOH2H-787-788-790-1204-INTEGRATION.md) 为准。以下保留原始设计记录。

## HTH API Password — First-time Setup

| 项目 | 内容 |
| --- | --- |
| Story | `BCOH2H-788` HTH API password - First time Setup |
| 文档状态 | Proposed（当前代码库尚无 API Password 实现） |
| 日期 | 2026-09-07 |
| 依赖 Story | API Password Code 生成 Story `BCOH2H-789`；成功通知 `BCOH2H-1204` |
| 影响范围 | HTH API User 首次登录提示、API Password Setup 页面、UAM 凭证写入、Password Code 消耗、审计与通知事件 |
| 不影响范围 | BCO Login Password/PIN、Signer PIN、HTH User Access Maker/Checker、HTH API Account/API Authorization |

## 1. 目标

已成功创建并批准、`userChannelType=HTH` 且从未设置过 HTH API Password 的用户，在首次成功登录
BCO 后收到设置提示。用户输入新 API Password、确认密码及 Authorized Person 生成的有效
HTH API Password Code，系统同步完成密码创建、Code 一次性消耗和安全审计。

本 Story 的 API Password 是 **HTH API 调用使用的独立凭证**，不是 BCO 登录密码、Login PIN、
Signer PIN，也不是 OAuth `client_secret`。设计只复用 BCO 现有的 UI、密码策略、加密传输、Task/
Entitlement、Alert 和错误处理模式，不共享凭证值或生命周期。

## 2. Story 解读与范围

### 2.1 前置条件

- HTH API User 已创建并通过审批，用户状态允许登录。
- `UserExtensionData.userChannelType` 规范化后为 `HTH`。
- 当前用户存在一个由 `BCOH2H-789` 生成、未使用且未过期的 HTH API Password Code。
- UAM 中该用户尚无有效 HTH API Password。
- 用户已使用 BCO Login Password/PIN 完成正常登录和现有登录认证。

### 2.2 Story 字段歧义处理

Figma 将第三个输入框标为 `One Time Password (OTP)`，但验收条件说明它是 Authorized Person 在
CM Portal 生成的 `HTH API Password Code`。本设计统一按以下含义实现：

```text
页面 Label：HTH API Password Code
业务含义：一次性、短期有效的 API Password Setup/Reset Code
不是：OBDX 登录 OTP、交易 OTP 或 OMB/2FA challenge
```

若 Product 最终坚持保留 `One Time Password (OTP)` 文案，只替换 NLS 文案，不改变 API 和后端
语义。是否在此高风险操作之外再叠加平台 Step-up 2FA，应作为安全评审决定，不与 Password Code
混为同一字段。

### 2.3 明确不做

- 不修改普通 BCO 用户首次登录和 Force Change Password 流程。
- 不将 API Password 写入 `DIGX_CZ_UM_OPENAPI`、`HTH_MANAGEMENT`、
  `HTH_UAM_CLIENT_REGISTRY` 或 HTH User Access 表。
- 不保存明文 API Password 或明文 Password Code。
- 不进入 Maker/Checker；本人首次设置成功后立即生效。
- 不在 URL、query string、日志、Activity Log detail 或通知内容中携带密码/Code。
- 不负责生成 Password Code；Code 的生成、通知及有效期来源属于 `BCOH2H-789`。

## 3. 现有能力与新增边界

| 能力 | 当前代码依据 | 复用方式 |
| --- | --- | --- |
| 登录后提醒 | `framework/elements/core/dashboard/dashboard.js` 已处理 Login/Signer PIN reminder | 增加仅 HTH 用户执行的 API Password Setup Gate，不改变 BCO 分支 |
| 登录强制改密 | `components/login/login-form-web/login-form-web.js` 处理 `FORCE_CHANGE` | 不复用该异常，因为 API Password 与 BCO 登录认证独立 |
| 密码策略 | `extensions/components/change-password/change-password/model.js` 调用 `me/passwordPolicy` | UI/校验模式复用；后端仍需再次执行 HTH API Password Policy |
| 密码传输保护 | `change-password/hooks.js` 使用 `customer-pin-encrypt` 和 RSA header | 新页面复用同一加密能力，不发送明文 |
| 用户渠道识别 | `UserExtensionDataDTO.userChannelType`、`IHthUserProfileAdapter.normalizeUserChannelType()` | 只有规范化结果为 `HTH` 才可访问 |
| 企业 UAM Client | `HTH_MANAGEMENT.UAM_CLIENT_ID` | 仅用于解析 UAM tenant/client context，不保存用户密码 |
| UAM Client 历史注册 | `HTH_UAM_CLIENT_REGISTRY` | 不参与用户密码状态及 Code 校验 |
| 通知 | `IModuleToAlertAdapter.registerActivityAndGenerateEvent` + `DIGX_EP_*` | 成功后触发 `BCOH2H-1204` 事件 |

### 3.1 推荐组件边界

```text
BCO Channel
  -> HTH API Password Status REST
  -> HTH API Password Setup REST
      -> HostToHostApiPassword Service
          -> Password Code Repository（一次性 Code 生命周期）
          -> HTH Management Repository（只解析有效 UAM clientId）
          -> HTH API Credential Adapter（创建独立 API Password）
          -> Alert Adapter（成功通知）
          -> Audit/Activity Framework
```

UAM 是 API Password 凭证的 Source of Truth。应用数据库只保存 Code 的安全摘要、状态及必要的
业务/审计元数据；不得保存可还原的 API Password。

## 4. 用户流程

```text
用户完成 BCO 登录
  -> 读取 userChannelType 与 HTH API Password status
  -> 非 HTH / 已设置：继续原 Home 流程
  -> HTH + NOT_SETUP + 有效 Setup Code：弹出 Create HTH Login Password
  -> 用户选择 Set now / Next
  -> Create H2H API Password 页面
  -> 输入 New Password、Re-enter Password、HTH API Password Code
  -> 前端一致性及必填校验
  -> 加密 password/code 后 POST setup
  -> 后端鉴权、Code 校验、密码策略校验
  -> 原子保留 Code -> 调用 UAM 创建凭证
  -> 标记 Code USED、状态 ACTIVE、写审计
  -> 触发 BCOH2H-1204 成功通知
  -> 显示成功确认 -> 返回 Home/Profile
```

### 4.1 首次提示规则

Setup Gate 的判定条件必须全部成立：

1. 已认证 Session，且用户不是匿名/预登录状态；
2. `userChannelType=HTH`；
3. HTH corporate profile 有效且 `HTH_MANAGEMENT.HTH_STATUS=ENABLE`；
4. UAM credential status 为 `NOT_SETUP`；
5. 用户存在可用的 Setup Code，或产品决定即使无 Code 也展示并引导联系 AP。

推荐后端返回明确状态，而不是由前端拼接多个接口推断：

| `setupState` | 前端行为 |
| --- | --- |
| `NOT_APPLICABLE` | 不展示入口 |
| `REQUIRED` | 登录后弹窗，允许进入 Setup |
| `CODE_REQUIRED` | 弹窗提示联系 Authorized Person 生成 Code |
| `ACTIVE` | 不弹窗，Profile 中展示 Change 功能 |
| `LOCKED` | 不允许提交，显示联系银行/Authorized Person |
| `UNKNOWN` | 不误判成功；记录技术错误并允许用户继续使用 BCO 非 HTH 功能 |

同一浏览器 Session 内弹窗只打开一次，但关闭弹窗不能将服务端状态标记为已设置。下次登录或刷新
状态后仍应按服务端状态决定是否提示。

## 5. 前端设计

### 5.1 新组件

建议新增：

```text
consulting/channel/extensions/components/hth-api-password/
  hth-api-password.js
  hth-api-password.html
  hth-api-password.scss
  hth-api-password.css
  model.js
  loader.js
  hth-api-password.json
consulting/channel/extensions/resources/nls/hth-api-password.js
consulting/channel/extensions/partials/hth-api-password-setup-reminder.html
```

组件支持 `mode=SETUP|RESET`，788 只使用 `SETUP`。共享组件可以减少 788/790 重复，但必须在
model 层调用不同 endpoint 并显示不同标题/成功文案。

### 5.2 登录后接入

- 在 Dashboard 已完成 `userInfoPromise`、已取得 `userProfile` 后调用 status endpoint。
- 仅在 `userChannelType=HTH` 时发起；普通 BCO 用户零额外请求。
- Gate 应在既有 BCO Login PIN、Signer PIN reminder 之外排队展示，禁止多个 modal 重叠。
- 推荐优先级：平台强制改登录密码 > 登录/Signer PIN 强制流程 > HTH API Password Setup > 普通提醒。
- 用户点 `Set HTH Login password now` 后，通过 `dashboard.loadComponent` 进入新组件；不把 Code、
  密码或 userId 放入路由 query。

### 5.3 Setup 页面

字段：

| 字段 | 类型 | 前端校验 |
| --- | --- | --- |
| New Password | `oj-input-password` | required；按服务端返回的 HTH policy 展示即时规则 |
| Re-enter Password | `oj-input-password` | required；必须与 New Password 完全一致 |
| HTH API Password Code | password/masked input | required；不 trim 内部字符；格式只作基本长度/字符校验 |

按钮和交互：

- `Submit`：Validation Group 全部通过且未在提交时才启用。
- `Cancel/Back`：回到 Home，但不得把 Setup 状态改为完成。
- 双击 Submit 只发送一次；提交期间按钮 disabled 并显示 busy context。
- 成功后立即清空 observable、DOM value、加密临时变量和 payload reference。
- 失败时清空 New/Re-enter Password；是否保留 Code 由安全评审决定，推荐一并清空。

### 5.4 密码规则展示

Story/Figma 给出的最低规则为：

- 8–16 个字母或数字；
- 同时包含字母与数字；
- 至少两个数字；
- 不包含空格或特殊字符。

实现不得只在 JavaScript 中硬编码。后端配置/原系统策略是唯一规则来源，status/policy endpoint
返回可展示的 policy DTO；前端沿用 change-password 的 rule rendering 模式。若原系统策略与 Figma
不同，以 Product 确认并配置后的后端策略为准，前后端同版发布。

### 5.5 敏感数据传输

- 复用 `customer-pin-encrypt`/平台 RSA public key 机制；密码和 Code 均不得明文进入 JSON。
- 使用独立字段，例如 `encryptedPassword`、`encryptedPasswordCode`，并携带现有 RSA Key Indicator/
  token header。
- 禁止 console log、analytics、前端 error reporting 或 Redux/KO debug dump 记录 payload。
- 浏览器 autocomplete 建议为 `new-password`；Code 使用 `one-time-code` 仅用于语义，不允许浏览器
  长期保存。

## 6. API Contract（建议新增）

以下 URI 和 Task ID 是 Tech Design 建议，当前仓库中尚不存在，实施前需纳入 OpenAPI/API Review。

### 6.1 状态与策略

```http
GET /cz/v1/hostToHostApiPassword/status?locale=en
```

身份只从当前 Session 获取，不接受调用方传入任意 `partyId`/`userId`。

```json
{
  "status": { "result": "SUCCESSFUL" },
  "setupState": "REQUIRED",
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

响应不得暴露 Code 值、Code hash、具体失败次数、UAM credential id 或 UAM client secret。

### 6.2 首次设置

```http
POST /cz/v1/hostToHostApiPassword/setup?locale=en
Content-Type: application/json
```

逻辑 Payload（线上为密文）：

```json
{
  "encryptedPassword": "BASE64_CIPHER_TEXT",
  "encryptedPasswordCode": "BASE64_CIPHER_TEXT",
  "rsaKeyIndicator": "KEY_VERSION",
  "requestId": "UUID"
}
```

成功：

```http
200 OK
```

```json
{
  "status": {
    "result": "SUCCESSFUL",
    "referenceNumber": "HTH_API_PWD_SETUP_REFERENCE",
    "message": { "code": "HTH_API_PASSWORD_SETUP_SUCCESS", "type": "INFO" }
  }
}
```

此接口不返回 password、Code、hash 或 UAM token。重复发送同一 `requestId` 时必须返回第一次已完成
结果，不重复消耗 Code、不重复创建 credential、不重复发通知。

## 7. 后端设计

### 7.1 新 Service 与 Task

建议新增：

- REST：`com.ofss.digx.cz.bea.appx.hosttohost.service.HostToHostApiPassword`
- Service：`com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostApiPassword`
- Interface/DTO：放入相同 hosttohost module/xface 结构。
- Task：`CM_N_HAP_SETUP`（名称最终由 Task Governance 确认）。
- Entitlement：`Set HTH API Password`，仅本人且 `userChannelType=HTH` 可执行。

本操作是 self-service immediate update，不配置 `APPROVAL` aspect。是否配置
`TWO_FACTOR_AUTHENTICATION` 需由 Security 评审；即使配置，平台 2FA challenge 也与 API Password Code
分开处理。

### 7.2 Setup 处理顺序

1. 从 `SessionContext` 获取 authenticated subject、partyId、userId/closeId；忽略请求中的身份字段。
2. 校验当前用户状态、`userChannelType=HTH`、HTH Profile Enabled。
3. 读取当前 `HTH_MANAGEMENT`，取得有效 `UAM_CLIENT_ID`；无绑定或 Disabled 时拒绝。
4. 解密 Password/Code；解密失败返回统一输入错误，不回显原值。
5. 对 Password 执行中央 HTH API Password Policy；前端校验不作为安全依据。
6. 对目标用户的 Active Setup Code 加行锁/乐观锁，检查用途、状态、过期时间和失败次数。
7. 使用常量时间比较输入 Code 与存储摘要；失败时原子增加失败计数并执行锁定策略。
8. 调用 UAM 查询 credential，确认仍为 `NOT_SETUP`；若已存在，拒绝 Setup 并引导 Reset。
9. 将 Code 状态从 `ACTIVE` 原子切换为 `IN_PROGRESS`，记录 `requestId` 和开始时间。
10. 调用 HTH API Credential Adapter 创建独立 password，向下游传递同一 idempotency key。
11. UAM 成功后，将 Code 标记 `USED`、写 `USED_AT`，更新本地状态投影为 `ACTIVE`。
12. 写安全审计/Activity，并触发 `HTH_API_PASSWORD_SETUP_SUCCESS` 通知事件。
13. 清除服务内存中的 char array/临时密文 reference，返回业务 reference。

### 7.3 UAM Adapter

新增接口示例：

```java
public interface IHthApiCredentialAdapter {
    HthApiCredentialStatus getStatus(HthApiCredentialContext context);
    HthApiCredentialResult createPassword(
        HthApiCredentialContext context,
        char[] password,
        String idempotencyKey);
}
```

`HthApiCredentialContext` 至少包含：

- 主公司 `partyId`；
- 当前用户 canonical `userId/closeId`；
- 从 Active `HTH_MANAGEMENT` 解析出的 `uamClientId`；
- correlation/context id。

Adapter 不把 `client_secret` 返回 Service；secret 从受控 Credential Vault/现有 UAM connector config
读取。任何 UAM 请求/响应 logging 均需字段脱敏。

## 8. 数据设计

### 8.1 Password Code（依赖 BCOH2H-789）

建议由 789 提供专用表/Entity，例如 `HTH_API_PASSWORD_CODE`：

| 字段 | 用途 | 约束 |
| --- | --- | --- |
| `ID` | 技术主键 | UUID/sequence，PK |
| `PARTY_ID` | 主公司 | not null；参与用户边界 |
| `USER_ID` | canonical HTH user/closeId | not null；与 party 组成查询键 |
| `PURPOSE` | `SETUP` / `RESET` | 788 仅接受 `SETUP` |
| `CODE_HASH` | Code 的不可逆摘要 | not null；禁止明文/可逆密文 |
| `STATUS` | `ACTIVE/IN_PROGRESS/USED/EXPIRED/REVOKED/LOCKED` | 状态机约束 |
| `EXPIRES_AT` | 失效时间 | 使用 DB/UTC 时间判断 |
| `FAILED_ATTEMPTS` | 错误次数 | 默认 0；原子更新 |
| `MAX_ATTEMPTS` | 最大次数快照 | 生成时固化配置 |
| `REQUEST_ID` | 当前提交幂等键 | ACTIVE 可为空；处理中/成功唯一 |
| `USED_AT` | 使用时间 | USED 时必填 |
| `GENERATION_REFERENCE` | 生成操作 reference | 可审计，不暴露 Code |
| 审计字段 | created/updated by/time/version | 标准字段 |

推荐唯一约束保证同一 `(PARTY_ID, USER_ID, PURPOSE)` 最多一个 Active Code；Oracle 可使用
function-based unique index 或在 Service 中配合锁实现。新 Code 生成时必须撤销旧 Active Code。

### 8.2 Credential 状态投影

UAM 为最终 Source of Truth。若首次登录每次直查 UAM 成本可接受，不新增本地状态表；若必须缓存，
建议新增最小状态投影 `HTH_API_PASSWORD_STATE`：

| 字段 | 用途 |
| --- | --- |
| `PARTY_ID`, `USER_ID` | 业务唯一键 |
| `CREDENTIAL_STATUS` | `NOT_SETUP/ACTIVE/LOCKED/UNKNOWN` |
| `CREDENTIAL_VERSION` | UAM version/etag（若提供） |
| `SETUP_AT`, `LAST_RESET_AT` | 展示/审计 |
| `LAST_RECONCILED_AT` | 与 UAM 对账时间 |
| `OBJECT_VERSION_NUMBER` | 并发控制 |

状态投影不得包含 password、password hash、Code 或 UAM client secret。状态不一致时以 UAM 为准并
修复投影，不允许本地 `ACTIVE` 直接证明密码存在。

### 8.3 明确禁止复用的表

- `HTH_MANAGEMENT`：企业级 HTH 开关与当前 UAM Client ID，不是用户凭证表。
- `HTH_UAM_CLIENT_REGISTRY`：UAM Client ID 永久使用历史，不是 password registry。
- `DIGX_CZ_UM_OPENAPI`：Open API channel/account consent，不是 credential store。
- `DIGX_CZ_UM_EXTENSIONDATA.BYPASS_CODE`：现有 Login PIN Reset Code，与 HTH API Password Code
  用途、生命周期及并发边界不同，不应共用同一列。

## 9. 一致性、失败恢复与并发

UAM 与本地数据库无法假设为同一 XA transaction，采用保留 + 幂等 + 对账：

| 失败点 | 处理 |
| --- | --- |
| Code 校验前失败 | 不改变 Code；返回技术错误 |
| Code 不正确 | 增加失败次数；未到阈值仍 ACTIVE，到阈值 LOCKED |
| Code 保留后 UAM 明确失败 | 将 `IN_PROGRESS` 恢复 ACTIVE 或按错误类别 LOCKED；不标记 USED |
| UAM 超时、结果未知 | 保持 `IN_PROGRESS/UNKNOWN`；禁止 Code 重用；使用 requestId 查询/对账 |
| UAM 成功、本地 finalize 失败 | 重试相同 requestId；Adapter 识别已创建，不生成第二份 credential |
| 本地成功、通知失败 | Setup 仍成功；通知框架按 1204 重试/失败处理，不回滚密码 |
| 两个页面并发提交同一 Code | 锁/版本更新只允许一个取得 `IN_PROGRESS`，另一个返回 Code 已使用/处理中 |

`IN_PROGRESS` 超时记录由定时 reconciliation job 查询 UAM 并落到 `USED/ACTIVE` 或可重试状态；
禁止简单按时间自动恢复 ACTIVE，因为 UAM 可能已成功。

## 10. 安全与审计

### 10.1 密码安全

- API Password 只在 UAM credential service 中以其标准安全方式保存。
- 本系统不落库、不缓存、不写 log、不进入 notification template。
- Service 使用 `char[]`/可清理容器优于长期 `String`；finally 中清理敏感对象。
- Password history、复杂度、重用、锁定策略由原系统/中央 HTH policy 执行。
- TLS、CSRF/nonce、same-origin、session timeout 继续沿用 BCO Channel 标准。

### 10.2 Code 安全

- 仅存 salted hash/HMAC，不存明文；比较采用 constant-time。
- Code 绑定 `partyId + userId + purpose`，不可跨用户或 Setup/Reset 混用。
- Code 有短期有效期、最大尝试次数、一次性消费和生成后旧 Code 撤销。
- 错误响应避免区分“用户不存在”和“Code 不存在”，防止枚举。

### 10.3 审计字段

安全审计只记录：

- action：`HTH_API_PASSWORD_SETUP`；
- party/user、result、reason code、request/reference/context id；
- UAM client id 可记录脱敏值或内部 management id；
- timestamp、source channel/IP/device（按现有审计标准）。

不得记录 password、Code、hash、密文、RSA token、完整下游请求/响应。

## 11. 错误处理

| 场景 | 建议错误码 | 用户文案 |
| --- | --- | --- |
| Code 无效 | `HTH_API_PASSWORD_CODE_INVALID` | The HTH API password code you entered is invalid, please enter it again. |
| Code 过期 | `HTH_API_PASSWORD_CODE_EXPIRED` | The HTH API Password Code has expired. Please contact your Authorized Person to generate a new code. |
| Code 已使用/撤销 | `HTH_API_PASSWORD_CODE_UNAVAILABLE` | The HTH API Password Code is no longer valid. Please request a new code. |
| 两次密码不一致 | 前端 `HTH_API_PASSWORD_MISMATCH` | The passwords do not match. |
| 密码策略不满足 | `HTH_API_PASSWORD_POLICY_VIOLATION` | 返回安全、可展示的规则列表，不回显密码 |
| API Password 已存在 | `HTH_API_PASSWORD_ALREADY_SETUP` | Password is already set. Please use Change HTH API Password. |
| 非 HTH 用户 | `HTH_API_PASSWORD_NOT_ELIGIBLE` | Function is not available. |
| 企业 HTH/UAM 未配置 | `HTH_API_PASSWORD_CLIENT_UNAVAILABLE` | Service is temporarily unavailable. Please contact the bank. |
| 下游状态未知 | `HTH_API_PASSWORD_RESULT_PENDING` | Request is being processed. Do not submit again. |

HTTP 建议：输入/状态错误 `400/409/422`，未认证 `401`，无 entitlement `403`，下游暂时不可用
`503`。不得用 HTTP 200 + `message.type=ERROR` 表示失败。

## 12. 通知与 Activity

首次设置成功后发布 `HTH_API_PASSWORD_SETUP_SUCCESS` 事件，由 `BCOH2H-1204` 配置模板、收件人、
渠道及失败处理。Service 只发布一次业务事件，不直接拼装邮件。

- Event payload 不含 password 或 Code。
- 通知失败不回滚已生效 credential。
- 使用 `requestId/referenceNumber` 做事件去重。
- Activity Log 可显示成功/失败结果，但不使用 Approval/Pending Approval 分类。

## 13. 测试设计

### 13.1 单元测试

- `userChannelType` 大小写/空格规范化，只允许 HTH。
- Setup Code 的 valid/invalid/expired/used/revoked/locked/purpose mismatch。
- Password policy：长度、字母、至少两个数字、空格、特殊字符、历史重用。
- Session identity 覆盖请求伪造身份。
- 幂等 requestId、Code 状态迁移、失败次数原子增加。
- password/Code 不出现在 `toString()`、exception 和 audit payload。

### 13.2 API/集成测试

- 有效 Code 首次设置：UAM credential 创建、Code USED、状态 ACTIVE、事件一次。
- 错误 Code：UAM 不被调用、credential 不创建。
- 过期 Code：准确错误文案，状态保持 EXPIRED。
- 两次密码不一致：前端不调用 API；直接构造 API 时由后端 policy 仍保护。
- UAM timeout/unknown：Code 不可重用；对账后恢复正确最终状态。
- 同 requestId 重试：同 reference，无重复通知。
- 两个并发请求：只有一个成功。
- BCO 用户回归：无弹窗、无 status 调用或快速返回 NOT_APPLICABLE。

### 13.3 UI/E2E

- 首次登录 modal 文案、Next、Cancel、刷新/重新登录行为。
- 三语言 NLS、键盘导航、screen reader label、focus trap。
- 密码规则与服务端 policy 一致。
- 输入错误、过期、mismatch 及成功确认。
- 移动端/桌面端、浏览器 back、session expiry。

### 13.4 安全测试

- Cross-user/party Code 重放、Setup Code 用于 Reset、重复使用。
- 暴力尝试限速/锁定、账户枚举、CSRF、session fixation。
- Network/log/APM/DB dump 中无敏感值。
- UAM adapter secret 不出现在配置明文和异常。

## 14. Acceptance Criteria 映射

| AC | 设计覆盖 |
| --- | --- |
| AC1 首次访问弹窗并跳 Setup | 第 4、5.2 节 |
| AC2 有效 Code + 合规密码成功创建并立即失效 Code | 第 6.2、7.2、8、9 节 |
| AC3 无效 Code 拒绝且不创建密码 | 第 7.2、11、13 节 |
| AC4 过期 Code 返回指定提示 | 第 11 节 |
| AC5 两次密码不一致 | 第 5.3、11、13 节 |

## 15. 部署与回滚

### 15.1 建议上线顺序

1. BCOH2H-789 Code schema/repository/config；
2. UAM credential adapter 与 vault/config；
3. 788 backend endpoint/service/task/permission/error messages；
4. 1204 notification event/template；
5. Channel component/NLS/setup gate；
6. 清理 OBDX/Task/Configuration/Alert cache 并重启受影响 managed server；
7. 运行 verification SQL、API smoke test、首次登录 E2E。

### 15.2 Feature Toggle

建议增加 `ENABLE_HTH_API_PASSWORD_SETUP`（默认 `N`），同时要求 UAM adapter health 正常后才开启。
可按 determinant/环境控制，不按单用户硬编码。

### 15.3 回滚

- 先关闭 feature toggle，停止新 Setup；不删除已创建的 UAM credential。
- 回滚 Channel/REST/Service 配置与 Task mapping。
- Notification template 可停用，不删除历史审计。
- Schema 仅在确认无数据后才执行 drop；生产回滚通常保留表并禁用功能。

## 16. 关键文件（实施建议）

| 类型 | 文件/目录 | 动作 |
| --- | --- | --- |
| Channel | `consulting/channel/extensions/components/hth-api-password/` | 新增 Setup/Reset 共用组件 |
| Channel | `consulting/channel/framework/elements/core/dashboard/dashboard.js` | 最小化增加 HTH-only post-login gate；不改变 BCO 分支 |
| NLS | `consulting/channel/extensions/resources/nls/hth-api-password.js` | 新增三语言 key |
| REST | `...appx.service.rest/.../hosttohost/service/HostToHostApiPassword.java` | 新增 status/setup endpoint |
| Service | `...module.hosttohost/.../app/hosttohost/service/HostToHostApiPassword.java` | 新增业务服务 |
| Adapter | `...app.xface/.../hosttohost/adapter/IHthApiCredentialAdapter.java` | 新增 UAM 边界 |
| Entity/Repo | `...module.hosttohost/.../domain/hosttohost/entity/` | 使用 789 Code entity；可选 State 投影 |
| DB | `consulting/db/branch_change_history/<API_PASSWORD>/` | Schema、Permission、Process、Adapters、Error_Messages、Notification、Verification 分包 |

## 17. 实施前待确认项

1. `BCOH2H-789` 的 Code 长度、有效期、最大失败次数和实际表/API contract。
2. UAM 是否提供 create credential 的幂等键、credential status 查询及密码策略 API。
3. Figma 的 `OTP` 是否正式改名为 `HTH API Password Code`。
4. 首次提示允许 `Later/Cancel`，还是安全要求强制完成后才能继续使用 BCO。
5. HTH API Password 是否需要额外平台 2FA；Story 当前未要求。
6. Task ID、notification event ID 和 message code 的最终命名需通过治理审批。
