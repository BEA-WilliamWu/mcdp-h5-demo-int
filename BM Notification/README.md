# BM Email / SMS 通知开发参考

整理日期：2026-09-16。源码基线：`hth-application`，commit `b55652b0`。

本文按 **Bank Manager 后台业务操作触发 Email / SMS** 理解 BM，例如 BM 审批通过后通知客户。它不是“给 BM 员工发站内信”的实现；后者还需要明确员工收件人、站内消息通道及权限规则。

本包是给 developer 的接入参考，依据当前工程实际 Java、配置 SQL 和框架 JAR 整理。示例没有注册 REST API、定时任务或业务调用点，不会自行发信。SQL 没有在 UAT 执行，示例文案也不是业务批准的通知模板。

## 1. 先抓住三个关键点

1. **业务服务发布 Activity + Event，并提供通知 DTO、收件通道和地址。** 模板解析、Dispatcher、MNG Adapter 负责后续处理。通常不用业务服务自己写 HTTP 调 MNG。
2. **BM 路由由执行 MNG assembler 的 JVM 判断。** 当前代码读取 `System.getProperty("isAdmin")`；值为 `BM` 时 Email 走 `BMMNG`，SMS 走 `BMMNGSMS`。
3. **发布通知、MNG 接受请求、客户实际收到，是三个不同阶段。** `registerActivityAndGenerateEvent()` 返回不代表送达；`DIGX_CZ_EMAIL_MNG.RESPONSE_STATUS='Success'` 也不是邮件/手机最终送达回执。

### 文件清单

| 文件 | 用途 |
|---|---|
| [BmNotificationExample.java](BmNotificationExample.java) | 构造 EMAIL / SMS 收件详情、组装 DTO、调用事件发布器、恢复上下文 |
| [BmNotificationActivityLogDTO.java](BmNotificationActivityLogDTO.java) | 三个模板变量的 public getter / setter |
| [BmNotificationExampleApplication.java](BmNotificationExampleApplication.java) | 演示如何在 `AbstractApplication` 子类调用 protected 框架 API |
| [config-example.sql](config-example.sql) | 独立示例事件、英文邮件/短信模板、完整属性映射；需绑定实际 targetUnit |
| [diagnose.sql](diagnose.sql) | 只读查询：事件配置、模板映射、Dispatcher、mock、MNG 记录 |
| [verify.py](verify.py) | 本地实际依赖编译、隔离测试和框架 getter 解析检查；不联网、不发送 |
| [SOURCE-INDEX.md](SOURCE-INDEX.md) | 当前工程的真实代码位置和阅读顺序 |

## 2. 实际调用链

```text
BM 业务最终审批成功
  └─ 在业务事务内保存通知意图，事务提交
      └─ Worker / 已有通知发布服务读取已提交意图 [推荐生产接法]
          └─ registerActivityAndGenerateEvent(context, activityId, eventId, Date, DTO)
              ├─ Activity / Event / Action 配置
              ├─ Recipient + Locale + Target unit -> Template
              ├─ Message attribute -> Service attribute -> DTO getter
              └─ 配置中的 Dispatcher
                  ├─ EmailDispatcher -> MNGEmailAdapter
                  │   └─ MNGEmailAdapterAssembler -> BMMNG / MNG
                  └─ SMSDispatcher   -> MNGSmsAdapter
                      └─ MNGSmsAdapterAssembler   -> BMMNGSMS / MNGSMS
                          └─ EndpointFactory.getEndpoint(interfaceId)
                              └─ endpoint.processRequest(...) -> MNG
```

图中“提交后读取意图”是推荐的可靠接入方式，不是 `registerActivityAndGenerateEvent()` 自动提供的保证。本文最小示例只覆盖发布器这一层；生产环境需要把它接到现有的审批、事务和持久化机制。

### BM 与其他运行环境的区别

| 判定/参数 | 当前作用 | 不应混淆的地方 |
|---|---|---|
| JVM `System.getProperty("isAdmin") == "BM"`，忽略大小写 | 选择 `BMMNG` / `BMMNGSMS` | 不是 request header，也不是用户 role |
| JVM `isAdmin` 为其他值或未设置 | 选择 `MNG` / `MNGSMS` | BM 发起的事件也可能在另一个 JVM 派送 |
| `SessionContext.targetUnit` | 业务单元、配置/模板等查找范围 | 不能根据名称猜为 `OBDX_BU` 或 `ADMIN_BU` |
| `SessionContext.userId` | 当前真实操作人/框架上下文身份 | 不是通知目标用户，不能为了短信改成客户账号 |
| ThreadAttribute 内同名属性 | 请求线程范围的框架/业务数据 | 不等于 JVM 的 `System.getProperty("isAdmin")` |

如果事件在 BM 服务生成，但由独立 MDB / 批处理 JVM 派送，应检查**真正执行 assembler 的 JVM**。部署上常见的参数形式是 `-DisAdmin=BM`，但是否应配置在该服务器必须由实际部署拓扑决定。不要在每个请求中 `System.setProperty("isAdmin", "BM")`，它是进程级状态，会影响并发业务。

工程中能够确认接口 ID 和选择代码，但没有找到足以确认当前 UAT `BMMNG` / `BMMNGSMS` 实际 URL 的已跟踪配置。因此本文不提供猜测 URL。应沿 `EndpointFactory` 和部署后的 provider 配置确认地址、协议、证书和路由。浏览器访问 BM/CM 的 URL 不是 MNG endpoint。

## 3. 最小 Java 例子怎么接

### 3.1 框架 API 的真实签名

已根据工程 JAR 核对：

```java
protected com.ofss.fc.service.response.TransactionStatus registerActivityAndGenerateEvent(
    com.ofss.fc.app.context.SessionContext sessionContext,
    String activityId,
    String eventId,
    com.ofss.fc.datatype.Date eventDate,
    com.ofss.digx.app.alerts.dto.eventgen.ActivityLog activityLog
) throws com.ofss.digx.infra.exceptions.Exception;
```

它是 `AbstractApplication` 的 protected 方法，所以本包提供一个薄的 `BmNotificationExampleApplication` 桥接类。已有业务服务如果继承了 `AbstractApplication`，也可以直接在该服务内构造发布器：

```java
BmNotificationExample notifications = new BmNotificationExample(
    (ctx, activity, event, when, payload) ->
        super.registerActivityAndGenerateEvent(ctx, activity, event, when, payload)
);
```

上面这一段必须放在 `AbstractApplication` 子类的实例方法中，不是任意工具类都能访问 `super.registerActivityAndGenerateEvent()`。

### 3.2 一个已批准并提交的业务结果，发一封邮件

下面演示调用方式。`context` 应来自已有 application/worker 的合法运行上下文；联系人与业务数据由服务端已提交记录取得。

```java
BmNotificationExample notifications =
    new BmNotificationExampleApplication().publisher();

TransactionStatus publicationStatus = notifications.publishEmail(
    context,                         // 实际 BM/通知 worker 上下文
    "PARTY_DEMO",                    // 业务所属客户/公司
    "USER_DEMO@PARTY_DEMO",          // 被操作的用户，不是 BM 操作人
    "BMREF001",                      // 业务 reference
    Instant.parse("2030-01-01T01:00:00Z"), // 已提交审批时间；示例值
    "recipient@example.invalid"      // 仅示例地址，不能用于真实送达测试
);
```

真实接入时这些值不能来自可任意编辑的前端邮件/电话参数。由后端根据已授权业务对象读取收件资料，并在审批/通知意图中保留必要快照。上面的未来时间仅用于演示时区转换；实际要传最终审批时间。

调用者应按当前应用惯例处理返回的 `TransactionStatus` 和异常，并记录发布结果。不要把“方法没抛异常”直接映射成 UI 的“邮件已送达”。示例发布器不吞异常，也不自动重试。

### 3.3 同一业务结果再发短信

```java
TransactionStatus smsPublicationStatus = notifications.publishSms(
    context,
    "PARTY_DEMO",
    "USER_DEMO@PARTY_DEMO",
    "BMREF001",
    Instant.parse("2030-01-01T01:00:00Z"),
    serverResolvedLocalMobileNumber, // 本地号码，不带 +852/852
    serverResolvedCountryCode        // 例如 852，不带 +
);
```

示例会组装 `localNumber + "~" + countryCode`，这是当前 legacy `SMSDispatcher` 能解析的格式。但国家码仍可能被 Dispatcher 查到的用户资料覆盖，详见第 7 节；不能只复制这行就认为 BM SMS 已验证正确。

邮件和短信在示例中分别发布。生产上建议分别保存通知意图及结果，避免邮件发布抛异常后整段方法结束，连短信都没有机会处理。缺邮件、缺手机号、无效联系人、是否补发给公司等规则必须由具体 story 明确，不能由工具方法擅自补收件人。

### 3.4 核心对象和字段

邮件路径中实际构造的核心代码是：

```java
NotificationDetail detail = new NotificationDetail();
detail.setRecipientId(ownerPartyId);
detail.setRecipientType(SubscriberType.EXTERNAL.toString());
detail.setDestination(DestinationType.EMAIL);
detail.setDispatchAddress(serverResolvedEmail);

BmNotificationActivityLogDTO log = new BmNotificationActivityLogDTO();
log.setCustomerId(ownerPartyId);
log.setBmDemoUserId(targetUserId);
log.setBmDemoReference(businessReference);
log.setBmDemoApprovedAt(approvedAtHkt);
log.setNotificationDetails(new NotificationDetail[] { detail });
```

| 字段 | 示例含义 | 常见错误 |
|---|---|---|
| `context.userId` | BM 操作人/真实执行身份 | 改成被通知客户，污染审计或授权上下文 |
| `context.targetUnit` | 实际业务单元 | 随便写 `OBDX_BU`，导致模板/配置查错范围 |
| `context.transactingPartyCode` | 此条通知所属公司 | 沿用上一条通知的公司 |
| `NotificationDetail.recipientId` | 当前 EXTERNAL 路径使用公司/partyId | 把 email 填到这里 |
| `recipientType` | `EXTERNAL` | 与 SQL 的 subscriber 类型不一致 |
| `destination` | `EMAIL` 或 `SMS` | 选了 EMAIL 地址，后面又硬编码 SMS |
| `dispatchAddress` | 实际 email / 本地号码及国家码 | 只有 userId，没有地址 |
| `ActivityLog.customerId` | 业务所属公司 | 使用操作人的银行内部组织标识 |
| `bmDemoUserId` | 被操作的业务用户，供模板展示 | 错把当前 BM operator 展示成客户 |
| `bmDemoReference` | 业务参考号 | 与 MNG REFNUMBER 混为同一个值 |

通用 `ActivityLog` 没有可直接照抄使用的 `setUserId()`；某些业务专用 DTO 才定义这个字段。本包使用自己的 `bmDemoUserId`，并通过 SQL 映射到 getter。

示例临时设置 `transactingPartyCode` 和英文 locale，在 `finally` 恢复，保留真实 actor 和 targetUnit。不要把同一个可变 `SessionContext` 跨并发线程共享。英文只是本例的范围；多语言要同时增加语言选择及对应 SQL。

## 4. 收件人规则放在哪一层

**业务服务/通知计划层决定“发给谁”，Dispatcher 负责“怎样送”。**

公司邮箱可以参考现有 BM `HostToHostManagement` 使用的：

```java
CZPartyPreferenceDTO partyDetails = userExtensionAdapter.getPartyPreferences(partyId);
String companyEmail = partyDetails.getOfficeEmailId();
String companyPhone = partyDetails.getOfficeTelNo();
```

这是公司资料，不等于被操作用户的 email/mobile，也不等于审批人的联系方式。公司电话字段是否具备可收 SMS 的号码和国家码，需要另行核对，不能把任意办公电话号码直接当手机。

若 story 要求用户、审批人、公司均接收，应先明确：最终审批人还是所有审批人；审批人名单从实际审批记录还是角色成员取得；改联系资料时用旧地址还是新地址；渠道缺失是否跳过；重复地址怎样处理。BM 示例不沿用某个 CM story 的收件人规则作为所有业务的默认规则。

生产建议为一个业务事件先生成收件计划：

```text
业务 reference + event + recipient category + channel + normalized destination
```

按批准的规则去重。Email 和 SMS 分别去重；公司、用户、审批人使用同一邮箱时是否只发一次，要按 story 的明确规则决定。手机号先标准化国家码和本地号码，避免 `+852…`、`852…`、`…~852` 被当成不同收件人。地址标准化策略应与现有 BCO 一致，不要临时重新定义一套。

不要为了给审批人通知，把所有 BM role 成员都加入收件人。两者不是同一范围。

## 5. Event、模板和属性配置：不能只插模板

完整示例在 `config-example.sql`。绑定 `:BM_TARGET_UNIT`，执行整段 PL/SQL；脚本不自动 COMMIT。可以先在同一会话运行 `diagnose.sql` 验证，再按部署流程提交。

脚本只拥有以下示例命名空间：

```text
Activity: com.ofss.digx.cz.bea.app.notifications.example.BmNotificationExample.publish
Event:    BM_EXAMPLE_APPROVED
Action:   A
Email:    BM_EXAMPLE_EMAIL_EN
SMS:      BM_EXAMPLE_SMS_EN
Locale:   en
```

这些 ID 和 Java 常量要完全一致。不要借用现有 `PIN_RESET_SUCCESS`、HTH API Password 或 851/1216 的 event ID 来绕过配置。

### 5.1 配置表的职责

| 表 | 作用 | 本例期望 |
|---|---|---|
| `DIGX_EP_ACT_B` | Activity 注册 | 一个 active 的示例 Activity |
| `DIGX_PM_EVENT_ALL_B` | Event 主配置 | 示例 Event，`ALERTS_FLAG='Y'` |
| `DIGX_EP_ACT_EVT_B` | Activity 与 Event 关系 | 一个对应关系 |
| `DIGX_EP_ACT_EVT_ACN_B` | Action、处理方式、有效期、重试参数 | Action A，active，未过期 |
| `DIGX_EP_EVT_REC_B` | Event/Action 到通道、语言、模板的关系 | EMAIL/en、SMS/en 各一条 |
| `DIGX_EP_MSG_TMPL_B` | 模板正文、主题、通道、业务单元 | 当前 unit 两条 active 模板 |
| `DIGX_EP_MSG_ATTR_B` | 模板需要哪些变量 | 两个模板，各三个变量 |
| `DIGX_EP_MSG_SRC_B` | 模板变量来自哪个 service attribute | 六条映射 |
| `DIGX_MD_SERVICE_ATTR` | service attribute 如何从 DTO 取值 | 三个 DTO 属性映射 |
| `DIGX_MD_GEN_ATTR_LEGACY_B` | 属性名称与数据类型 | 三个 String 属性 |

`SUBSCRIBER_TYPE='EXTERNAL'` 与 Java 一致；示例中的 `SUBSCRIBER_VALUE='USER'` 沿用工程现有配置方式，不表示自动替你查出 BM 员工邮箱。

本例沿用工程已有的 Action template `1`、decision `0` 等前置配置。因此 SQL 是现有 OBDX 环境的增量接入例子，不是从空数据库建立完整通知平台的安装脚本。

### 5.2 一个模板变量完整对应关系

以 `bmDemoReference` 为例：

```text
正文：#bmDemoReference#
  -> MSG_ATTR.COD_ATTR_ID = bmDemoReference
  -> MSG_SRC.COD_SERVICE_ATTR_ID = <activity>.bmDemoReference.DTO
  -> SERVICE_ATTR:
       TYP_DATA_AVAIL = INDIRECT
       TYP_DATA_SRC   = DTO
       COD_ATTR_ID    = bmDemoReference
       COD_SERVICE_ID = <activity>
       REF_FIELD_DEFN_ID =
         com.ofss.digx.cz.bea.app.notifications.example.BmNotificationActivityLogDTO.BmDemoReference
  -> DTO.getBmDemoReference()
```

注意路径末尾是 `BmDemoReference`，getter 是 `getBmDemoReference()`；不要随意改大小写。只在模板里写 `#变量#`，并不会让框架自动知道从哪个 DTO 获取。

如果邮件中还出现 `#bmDemoReference#`，按链条逐级查：SQL 是否提交到正确 schema、determinant/locale 是否一致、DTO 是否部署、public getter 是否存在、元数据缓存是否仍是旧值。不要只替换邮件模板文字。

### 5.3 样例文案与期望结果

邮件正文模板：

```html
<p>Request approved.</p>
<p>User: #bmDemoUserId#</p>
<p>Reference: #bmDemoReference#</p>
<p>Approved at: #bmDemoApprovedAt# HKT</p>
```

上述调用的期望显示为：

```text
Request approved.
User: USER_DEMO@PARTY_DEMO
Reference: BMREF001
Approved at: 01/01/2030 09:00:00 HKT
```

时间从 UTC 转为香港时区；重试应使用原始审批时间，而不是每次重发的当前时间。DTO 中若新增自由文本，例如公司名称，要在生成 HTML 模板值时正确转义；本例标识符限制了可用字符，不是通用 HTML 文本处理工具。

短信长度按**替换变量后的最终文案**检查。当前工程有配置化长度检查，不应固定假设所有语言都是 160 字符。本例最长标识符组合可能超过实际环境限额，UAT 必须覆盖最大实际业务值。

### 5.4 重跑和生效范围

脚本对自己拥有的 event/action/recipient bindings 重建，对选定 targetUnit 的两个模板和属性映射重建，对属性元数据做插入/更新。不会更改 BCO 的现有 Event、模板、网关地址、数据源、JVM 参数或共享 SMS 列表。

这表示**同一个示例定义重跑不会累加同样的模板关系**，不表示实际 Oracle 已验证无约束/版本差异。Activity/Event 和元数据中有全局 ID，不能让另一个功能共用这组示例 ID。多业务单元复用时也需要统一维护全局关系。

`FLG_TRANSACTIONAL='N'`、`ALERT_DISPATCH_TYPE='I'` 是参考现有工程的动作配置；它们不等于“业务一定已提交”或“严格只发一次”。可靠时序要由调用点和通知意图/派送记录实现。

## 6. 审批、事务和重复发送

不要在用户打开页面、保存草稿、Maker submit、每一个中间审批步骤里发送“已批准”通知。

建议的时序：

1. 确认本次是最终审批执行，业务变更真正成立。
2. 同一业务事务内写入通知意图，保存业务 reference、事件、批准时间、收件计划等。
3. 业务回滚时通知意图也回滚。
4. 事务提交后，由 worker 读取并 claim 待发布意图。
5. 发布 event，记录发布结果。
6. 派送前建立通道级幂等记录，再调用 MNG，保存确定或不确定的结果。

唯一性通常至少包含业务 reference / event / channel / 规范化收件地址，并按业务需要加入版本或具体变更 ID。同一 reference 下有多个合法事件时，不能只按 reference 去重。

MNG timeout 可能发生在“对方已接受、响应丢失”之后。此时应保存不确定状态并核对网关记录，不要无条件自动重发。启用框架 retry flag 本身不能解决重复投递。

工程已有 851/1216 的持久化参考：

- `HthUserAccessNotification`：审批时捕获通知意图。
- `HthUserAccessNotificationService`：读取已提交记录，发布事件并恢复上下文。
- `HthContactNotificationDispatch` / `HthContactNotificationRepository`：按 ledger claim 派送、保存 `SUBMITTED` / `REJECTED` / `UNKNOWN` 等状态。

这些类有 **HTH event allowlist、功能开关、DTO/ledger ID 要求和 `OBDX_BU` 范围限制**。不能直接传入通用 BM event 就认为它会走同样的可靠分支。应复用设计方式，按新 story 建立受控接入；不要去掉 HTH 的限制来兼容 BM。

本文 demo event 走正常配置的通用通知路径，不自动拥有上述 HTH 专用 ledger 的保证。

## 7. BM 短信特别容易出错：国家码和运行时用户

当前 `SMSDispatcher.dispatchMNGSms()` 的关键步骤是：

1. 地址含 `~` 时，拆为本地号码和备用国家码。
2. 根据事件配置及运行时用户，可能替换收件号码或用于查国家码的用户。
3. 除了 `SMS_DISPATCHER_SKIP_COUNTRY_CODE_EVENTID_LIST` 内的事件，会查询用户扩展资料的 `MOBILE_CODE`。
4. 查到非 null 的数据库国家码时优先使用它；否则使用备用值，其中地址提供的 `~国家码` 会覆盖 thread 的 `NEW_MOB_NO_CODE` 备用值。
5. 组装 `countryCode + localNumber` 作为最终 MNG SMS 号码。

因此，**BM 操作人国家码与客户国家码不同**时，即使传了客户本地号码，也可能拼成错误目的号码。`bmDemoUserId` 是模板变量，不会自动改变 Dispatcher 的 runtime user 选择。

接入本例短信前必须决定并验证以下一种方式：

- 若沿现有运行时用户查国家码，确认被查询的确实是目标收件用户，并验证 BM、worker、重试上下文都一致；保留真实审计 actor。
- 若业务已提供明确的收件国家码，评审后将**新 event** 加入现有 skip-country-code 配置，保留所有原有条目；同时确认它不会被其他事件列表重新替换号码。本例 `~国家码` 才会进入对应 fallback。
- 若需要更强的事件专用地址契约，可实现受限的专用派送分支，显式验证完整目的号码。不要全局修改 BCO SMS 行为。

`SMS_DISPATCHER_ALERT_EVENTID_LIST` 等列表也会影响地址处理。不要从某个配置示例复制一串值覆盖 UAT 现有列表。

验收至少要有：BM 员工与客户国家码不同、香港/非香港手机号、缺国家码、输入已有国家码的号码、Email 成功但 SMS 失败、mock 打开时没有真实短信。

## 8. MNG 层实际做什么

正常路径使用工程现有的 `MNGEmailAdapter` / `MNGSmsAdapter`，由 assembler 选 interfaceId，再通过 `EndpointFactory.getInstance().getEndpoint(interfaceId)` 调外部系统。

| 项目 | Email builder | SMS builder |
|---|---|---|
| Header `AppID` | 当前代码为 `89L` | 当前代码为 `89L` |
| Header `messageMode` | `o` | `o` |
| MessageType | `6` | `1` |
| 目的地址 | `email.toAddr` | `sms.distNo`，最终国家码+号码 |
| 内容 | subject + message | message |
| 关联号 | `sourceSysRefNumber` | `sourceSysRefNumber` |
| 业务标签 | `activityTag` | `activityTag` |

这是当前代码观测到的值，不是要求新业务手工重复硬编码。通常复用 Dispatcher/Adapter 即可。两个 `buildMNGrequest()` 都是 package-private，不能在任意业务 package 中直接调用；不要只复制一个调用语句就宣称可编译。

现有 EmailDispatcher 还先经过 `IDispatchAdapter`。只有其结果指示默认邮件处理时才继续通用 MNG 分支；某些特别事件还走 SMTP。排查时需要确认实际加载的 dispatcher / adapter / 分支，不能认为所有 Email 都必经 MNG 表。

不建议让前端调用网关，也不要把 APIC/MNG 的凭据放到 JavaScript、模板或示例源码中。

## 9. 配置、缓存与部署

### 9.1 配置名称要看实际映射

当前 `Preferences.xml`：

| Preference 名 | 实际配置来源 | 本地声明的刷新间隔 |
|---|---|---|
| `Dispatchers` | `DIGX_FW_CONFIG_ALL_B`，category=`AlertDispatcher` | 60,000 ms |
| `DispatchDetails` | 多业务单元 provider，基础 category=`DispatchDetails` | 3,600,000 ms |

特别注意 `Dispatchers` 不是数据库中的 `CATEGORY_ID`。多单元环境还要检查 `DIGX_FW_CONFIG_ALL_O` 的有效覆盖值。

这些间隔是本地配置声明，不证明 UAT 已部署相同文件，也不代表 Activity/Event/template/metadata 所有缓存都按相同间隔刷新。SQL 提交后应使用环境支持的配置刷新方式；如果该元数据只能靠重启生效，按部署规范处理相应节点，不能只靠浏览器刷新。

### 9.2 安装到项目时哪些东西要改

| 范围 | 内容 | 对 BCO 的控制方式 |
|---|---|---|
| 新业务服务或 worker | 在最终已提交结果之后构造/发布通知 | 仅新业务调用，不挂公共登录/首页 |
| common xface / 可被 sender 加载的模块 | 新通知 DTO | emitter 和异步 sender 都可加载同一版本 |
| 新事件 SQL | activity/event/action/template/attribute | 独立 ID，不覆盖现有 BCO 文案 |
| BM sender 部署 | 确认 JVM `isAdmin`、provider、网络/证书 | 不在请求内改进程全局配置 |
| 新业务的可靠性层 | 通知意图、claim、状态及受控重试 | 单独作用域，不接管 BCO 事件 |
| SMS 事件规则（如需要） | 新事件的国家码策略 | 仅追加评审过的新事件，保留旧列表 |

**不要因为加一个通知，就顺便改公共 datasource 的 XA/NONXA、关闭通用校验或移除共享代码。** 本例不要求这些改动。若新业务采用独立通知 ledger，事务边界要针对它设计和验证。

### 9.3 最短接入顺序

1. 确认通知发生的业务事件、最终审批条件、收件人、渠道、文案和语言。
2. 确认实际 sender JVM、targetUnit、配置 schema 和 BM MNG provider。
3. 按本例建立独立 DTO、Activity/Event 名称及完整 SQL。
4. 把 Java 发布器接入已有已提交结果处理链；补业务级幂等/失败处理。
5. 核对 SMS 国家码规则；检查配置层实际是否启用 mock。
6. 部署相关 JAR，执行并校验 SQL，刷新相应缓存。
7. 用专用 UAT 收件人验证模板、Email/SMS、MNG 记录和最终收件证据。

## 10. MNG 表怎么看，为什么邮件收到却查不到

当前 inspected Email/SMS Dispatcher 都使用 `DIGX_CZ_EMAIL_MNG` 保存网关调用审计，靠 `ALERT_TYPE` 区分通道。

```sql
SELECT REFNUMBER, EVENTID, ACTIVITYID, ALERT_TYPE,
       RESPONSE_STATUS, COD_ACT_DATA_ID, LAST_UPDATED_DATE
FROM DIGX_CZ_EMAIL_MNG
WHERE EVENTID = 'BM_EXAMPLE_APPROVED'
  AND LAST_UPDATED_DATE >= SYSDATE - 7
ORDER BY LAST_UPDATED_DATE DESC;
```

| 观察 | 能说明什么 | 不能直接说明什么 |
|---|---|---|
| `Pending` | 有调用审计起点，最终状态尚未成功保存 | 网关一定没收到 |
| `Success` | 当前 dispatcher 把 MNG 响应判断为成功 | 客户邮箱/手机一定已收到 |
| `Failed` | 返回结果被判为失败 | 可以无条件自动重试 |
| `Exception` | 派送/结果处理出现异常 | 对端一定未接受请求 |
| 没有记录 | 此次查询未命中审计记录 | 没有发生外部发送 |

“确实收到邮件但表为空”在这份代码里有明确可能：**通用 `sendMNGMail()` 对审计 insert/update 的异常有捕获处理，审计保存失败之后仍可能继续调用网关。** 所以要查写库异常、datasource/schema/synonym、实际 dispatcher 分支，不能只重新发送邮件。

另一些常见原因：查错环境、eventId 与实际不符、时间范围不符、走自定义 adapter/SMTP、通知还未进入 dispatcher、mock、地址或模板校验提前失败。

MNG `REFNUMBER` 在通用 dispatcher 中会新生成，通常以 `CDC` 开头，不保证等于业务 reference。至少一起保留：业务 reference、eventId、activityId、时间、`COD_ACT_DATA_ID`、MNG REFNUMBER、sender 节点。HTH ledger 专用路径还有自己的关联规则，不能混用。

`MOCK_EMAIL_SEND_IF` 在查看到的通用 Email 分支中存在读取/日志，但不能仅凭这个名字推断它一定阻止真实发送。SMS 的 `DispatchDetails.isDispatchMocked=true` 则有实际跳过发送的分支。必须检查当前执行路径的代码与运行值。

## 11. 排查路线与日志建议

### 11.1 按阶段确认，不要只搜索 `/setup`

```text
业务最终审批/提交成功？
  -> 通知计划有记录、渠道和地址正确？
  -> event 发布执行过？返回或异常是什么？
  -> activity/event/action 配置命中？
  -> locale + targetUnit + destination 对应模板存在？
  -> 变量全部解析？
  -> dispatcher/adapter 确实走 MNG？是否 mock？
  -> sender JVM 选中 BMMNG/BMMNGSMS？
  -> MNG 请求关联号、返回/timeout、审计写库结果？
  -> 邮件/短信最终送达或退信/失败回执？
```

异步派送可能发生在另一台 managed server，HTTP 路径未必出现在 sender 日志中。按通知 reference、event、时间和节点查，而不是只搜索浏览器 URL。

建议新代码只记录必要诊断字段，例如以下**建议格式，并非现有统一日志 API**：

```text
BM_NOTIFICATION reference=BMREF001 event=BM_EXAMPLE_APPROVED channel=EMAIL stage=PUBLICATION result=ACCEPTED
BM_NOTIFICATION reference=BMREF001 stage=PUBLICATION exception=SomeExceptionClass
BM_NOTIFICATION notificationId=<id> channel=SMS stage=DISPATCH interface=BMMNGSMS result=UNKNOWN
```

`ACCEPTED` 在这里仅表示发布层接受，不是最终送达。需要审计时把稳定的通知 ID 与业务 reference 保存关联。不要记录完整收件地址、密码、OTP/Code、网关 secret、完整 DTO/request/response；旧代码若已经打印这些内容，共享日志前先脱敏。

### 11.2 常见现象对应检查

| 现象 | 优先检查 |
|---|---|
| 完全没有通知 | 最终审批调用点、功能开关、事务提交、意图/事件发布 |
| Email 有、SMS 无 | 是否 `if EMAIL else SMS`；号码与国家码；SMS模板/locale；长度；mock；MNGSMS路由 |
| 邮件显示 `#变量#` | 五层属性映射、DTO getter、部署版本、元数据缓存 |
| BM 走了 CM 网关 | 实际 sender JVM 的 `System.getProperty("isAdmin")` |
| SMS 送到错误国家/号码 | 国家码来源、runtime user、事件地址替换列表、号码是否重复带国家码 |
| UAT 某一节点有效、另一个无效 | JAR、模板缓存、JVM 属性、provider 配置是否所有节点一致 |
| 重复邮件 | 多个审批阶段都发、发布重试、队列重复消费、相同地址跨身份未去重 |
| 收到邮件但 MNG 表为空 | 实际 schema/synonym、审计 insert/update 异常、自定义adapter/SMTP分支 |
| 网关有 Success、客户没有收到 | 真实目的地址、隔离/退信/下游运营商、最终送达回执 |

## 12. 现有 BM 代码：哪些可参考，哪些不能照抄

`HostToHostManagement.notifyHostToHostManagement()` 是一个已有业务调用例子，但当前实现存在以下不一致：

1. `buildNotification()` 先选公司 Email，Email 不存在才选电话/SMS。
2. 调用方只检查选出的 destination 是否为空，随后重新创建 `NotificationDetail`，固定为 SMS，并使用 `officeTelNo`，没有沿用前面选出的 EMAIL 地址。
3. 实际调用分支目前只有 `ACTION_ENABLE`；不能因为 `getActivityId()` / `getEventId()` 出现 EDIT/DISABLE 常量，就认为这些通知都已完成。
4. 实际传入的是 `CORPORATEPLUS_WELCOME_MAIL` 常量对应的事件，而生成专用事件 ID 的调用被注释；要核对运行 event/template，不能根据方法名猜。
5. `processSave()` 在 `approvedExecution` 分支、`executeApprovedSave()` 之后调用通知，但 Java 方法返回本身不是数据库最终 COMMIT 的证据。
6. 现有日志含电话/DTO 等内容；不要把它当成新代码的日志范例。

可以从这里参考 adapter 取得公司资料，以及 `registerActivityAndGenerateEvent` 的调用形状。收件通道、事件选择、事务时机和日志写法应按新的业务要求落实。本次只整理材料，没有修改这段生产代码。

## 13. Developer / UAT 验收清单

| 场景 | 需要看到的结果/证据 |
|---|---|
| 最终批准正常结果 | 对应业务提交后才发布；event、模板、收件目标正确 |
| 草稿/中间审批/拒绝/撤回 | 不发送“已批准”通知 |
| 业务事务回滚 | 没有可派送的已提交通知意图 |
| 同一通知重复消费 | 幂等记录阻止重复外发，或按已定义策略处理 |
| 邮件成功、短信失败 | 两个渠道分别有结果，不把整体笼统记成全成功 |
| 同一邮箱属于多个角色 | 按批准的去重规则发送 |
| 缺少一个通道资料 | 按 story 跳过/报错/补发，不能随意使用其他人的联系方式 |
| BM 与收件人国家码不同 | 最终 distNo 是目标收件人的完整号码 |
| en / zh-Hant / zh-Hans-CN | 每种已支持语言命中正确模板；本例仅 en，未支持的语言不得冒充已覆盖 |
| 模板变量与长文本 | 无未解析占位符，时间正确，HTML转义正确，SMS不过长 |
| MNG 明确拒绝 | 留可诊断失败状态，不显示送达成功 |
| MNG timeout / 空或异常响应 | 留不确定状态，核对后再决定是否重发 |
| MNG 审计写库失败 | 不把“表空”当“没发”，能够从关联日志核对 |
| sender 切换/集群节点 | JVM路由、DTO版本、配置和缓存一致 |
| BCO 回归 | 既有邮箱/短信、国家码列表、模板和默认网关路由不受新事件影响 |

最终成功证据应包括：已提交业务结果、通知/事件关联记录、模板替换结果、MNG 调用及返回、UAT 收件邮箱/手机或可信下游回执。测试截图中的联系人应脱敏。

## 14. 本包已经验证和仍需环境验证的内容

在仓库根目录执行：

```sh
python3 devtools/examples/bm-notification/verify.py
```

也可以解压后显式指定项目路径：

```sh
python3 verify.py /path/to/hth-application
```

Windows PowerShell 示例：

```powershell
$env:JAVA_HOME = 'C:\Java\jdk-21'
python .\verify.py 'D:\work\hth-application'
```

前置条件为 Python 3、JDK 9+、工程原有 `consulting/middleware/lib` 依赖；实际源码以 Java 8 target 编译。脚本使用平台路径分隔符，可在对应平台运行，但本次只在 macOS 执行过。

本次本地结果：

- 三个 Java 文件对实际项目依赖编译通过，包括 protected API 桥接。
- 隔离测试验证 EMAIL/SMS 的对象契约、无效地址不发布、actor 保持、正常/异常路径恢复上下文。
- 使用实际 `HostServiceMetadataService` 验证三个 DTO getter 的解析与值，包含香港时间转换。
- SQL 中占位符名、getter 属性名及显式 targetUnit bind 的静态检查通过。

运行测试时，为绕开 application-server 配置初始化，替换了 `ActivityLog` 的银行名称初始化父层、`SessionContext` 和 `Date`；事件发布器是 fake。编译阶段使用的仍是真实依赖。**这些测试不证明 WebLogic、业务事务、数据库配置、队列和 MNG 已经端到端成功。**

尚未执行：Oracle SQL/重复执行验证、真实审批调用、BM managed-server 发布、MNG Email/SMS 和最终收件测试。配置表结构、约束及部署缓存也必须与目标环境核对。

本包没有修改正式业务代码、没有执行发送、没有更改远程仓库。它可以作为同事实现具体 BM 通知 story 的起点；完成第 13 节对应范围的验证后，才具备上线评审依据。
