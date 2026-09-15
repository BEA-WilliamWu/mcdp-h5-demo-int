# BCOH2H-851 部署与验证

2026-09-16：已完成 20260915 变更包的 CM User Edit 联系方式通知实现验证，按本次确认的规则执行。默认关闭，仅绑定 `OBDX_BU`。本地运行测试通过；未连接 UAT、未执行 Oracle SQL、未发送真实邮件或短信。

## 业务行为

| 最终批准的变更 | API 用户通知 | 审批人通知 |
| --- | --- | --- |
| Email + Mobile | 旧/新 Email、旧/新 Mobile | 最终审批人的 Email + SMS |
| 仅 Email | 旧/新 Email；未变 Mobile 一次 | 最终审批人的 Email + SMS |
| 仅 Mobile（包含国家码） | 旧/新 Mobile；未变 Email 一次 | 最终审批人的 Email + SMS |
| 均未变，或验证/中间审批 | 无 851 通知 | 无 851 通知 |

API 联系方式取 HTH 用户的 CM User profile。旧值在更新前复制，新值来自获批请求。HTH 身份由同一 Party 的 HTH Profile 记录判断，兼容完整/短 CloseID；不依赖 API Password、账户权限或 DSP。

旧/新相同地址去重。API/AP 邮件模板语义不同，同邮箱可保留两份；短信内容相同，同号码跨角色只发一次。空联系方式不发送，不猜国家码。最终审批人取批准记录 `signedBy` 中最后一个非空用户，早期审批人不收 851。

API Email 收到明确 MNG 拒绝或 CCBEMAIL 负回执后，读取当前公司 `DIGX_PI_PARTY_PREFERENCES.OFFICE_EMAIL`，与失败地址不同才补发一次。每个原失败派送最多一条 `PARENT_ID` 子记录；重跑不会产生第二条。审批人邮件、短信、补发邮件失败不再扩散。超时/空响应属于 `UNKNOWN`，不等于确认失败，不自动重发或补发。

## 必须成套发布的文件

| 模块/配置 | 851 内容 |
| --- | --- |
| `com.ofss.digx.cz.bea.app.xface` | `HthContactNotificationPlan`、`HthProfileContactUpdateActivityLogDTO` |
| `com.ofss.digx.cz.bea.module.sms` | `HthProfileContactNotification`、`UserExtensionData` 中的快照/事务挂钩 |
| `com.ofss.digx.cz.bea.domain.service.dispatch` | `HthContactNotificationRepository`、`HthContactNotificationService`、`HthContactNotificationDispatch`；Email/SMS Dispatcher 的 851 分支 |
| `com.ofss.digx.cz.bea.scheduler.impl` | 现有 `BatchExecutionScheduler` 的 851 消费入口，放在 BCO 原批次之后 |
| `consulting/config/Preferences.xml` | `HthProfileContactNotification` DB provider，90 秒同步配置 |
| 当前环境的 `ValidateAndSendBounceNotify` batch | 从通用高风险通知级联中排除六个 851 事件；源码的 root/UAT/PRD 三份已同步修改 |
| 本目录 SQL | 记录表、配置/模板、只读验收查询 |

无需前端发布、新 REST 接口、Password/OTP/DSP 变更。Scheduler 原先已依赖 dispatch 模块，本次未增加新的外部 SDK。应用、Alert/MDB 消费者、Scheduler 都须能加载同版 xface 和 dispatch JAR；只更新业务 WAR 会漏掉后台派送。

## SQL 顺序

1. 在现有 OBDX 配置/用户资料所在 schema 执行 `1_HTH_Contact_Notification_Ledger.sql` 的完整 `DECLARE ... END;` 块。Oracle DDL 隐式提交。重复执行保留所有流水，并补缺失的 pending index；不擅自改造一个已有但结构不一致的同名表。
2. 执行 `2_HTH_Contact_Notification_Config.sql` 的完整 `BEGIN ... END;` 块，成功后单独执行 `COMMIT;`。失败会回滚本次配置块，再抛出原错误。DBeaver 不要对块内语句逐条运行；SQL*Plus/SQLcl 在完整块之后另起一行输入 `/`。
3. 执行 `3_Verify_HTH_Contact_Notification.sql`。检查记录表 18 列、8 项具名约束、pending index；6 个事件/动作、36 个 recipient/template、每个 EMAIL 模板 2 个有效绑定，SMS 0 个。原 BCO Activity 保留原配置，须处于环境认可的有效状态。
4. 确认应用 DIGX 与 `dispatchDataSource`（默认 `NONXA`）访问的是同一 schema；两者都能读/写 ledger，派送连接能写 MNG、读公司邮箱与 CCBEMAIL 回执表。确认 MNG `REFNUMBER` 和负回执 `SRC_SYS_REF_NUM` 可保存完整的 34 字符 `HC` + ledger ID，不能截断。
5. 发布上表模块和配置，刷新平台 Event/Template 缓存或按现有流程重启相关应用；确认现有 `BatchExecutionScheduler` 正在调度、Alert 消费者正常，以及 CCBEMAIL 负回执导入任务运行。
6. 核对矩阵 #7/#8/#9 的 EN/TC/SC 文案后启用，再执行下方 UAT 用例。当前模板是可运行文案，不能据此视为 BA 已签字的最终文案。

首次部署 flag 为 `false`，重跑保留当前值，不会重新关闭已启用功能。运行 SQL 不会重发已成功通知，不删除 ledger/MNG，也不覆盖原 BCO 模板。配置脚本会重置 **851 自己的**模板为版本内文案，UAT 对模板的手工修改需要先回存源码。

启用：

```sql
UPDATE DIGX_FW_CONFIG_ALL_B
SET PROP_VALUE='true', LAST_UPDATED_DATE=SYSDATE, LAST_UPDATED_BY=USER
WHERE CATEGORY_ID='HthProfileContactNotification'
  AND PROP_ID='HTH_PROFILE_CONTACT_NOTIFICATION_ENABLED';
COMMIT;
```

停用将上述值改为 `false`。等待配置缓存刷新；已经在途的 MNG 请求不能撤回。停用期间新 User Edit 走原 BCO 通知分支，851 不产生新意图；已有未派送意图保留，重新启用后继续处理。回退版本时保留 ledger 和 MNG 记录。

## 为什么有 ledger 和 NONXA

既有 ActivityLog 会持久化，但本地框架不能保证事件注册只在最外层事务提交后派送，也没有业务 reference 唯一约束。因此新增 `DIGX_CZ_HTH_CONTACT_NOTIFY`，只存联系方式通知意图和派送状态，不是新的用户/密码表。

User Edit 通过当前 **DIGX ORM 事务**写 READY，与资料更新一起提交/回滚。后台批次只看已提交行，再使用已有 **NONXA 资源本地连接**短事务写派送状态/MNG：先提交 SENDING 与 MNG reference，再调用现有 MNG adapter，最后记结果。这里的 NONXA 仅用于独立通知派送，不改变 BCO User Edit 的事务或 API Password 的存储路径。若环境 JNDI 名不同，仅更新 `dispatchDataSource` 到对应现有资源本地数据源。

| ledger 状态 | 含义/处理 |
| --- | --- |
| READY | 已随业务提交，等待批次 |
| PUBLISHING / PUBLISHED | 注册或等待 Alert 派送；超过 5 分钟可重新注册，派送领取仍保证同一收件记录不重复提交 |
| SENDING | MNG reference 已提交，请求可能正在途；超过 15 分钟转 UNKNOWN |
| SUBMITTED | MNG 已接受；不是最终送达证明 |
| REJECTED / DELIVERY_FAILED | 明确提交失败 / 已收到负回执，待判断一次补发 |
| UNKNOWN | 结果不确定，按 `HC` + ID 联系 MNG 对账；不能直接改 READY 重发 |
| FALLBACK_QUEUED | 已生成唯一子记录 |
| FAILED_FINAL | 无可用补发地址，或非 API Email，或补发再次失败 |

MNG `MESSAGEBODY` 存 Base64(SHA-256(正文))，`RESPONSE_STATUS` 用现有 Pending/Success/Failed/Exception；`ORG_REF_NO` 是原批准 reference，`COD_ACT_DATA_ID` 关联 Alert。新日志仅记录通知 ID、阶段、结果或异常类型，未新增地址/正文日志；既有 User Edit 的详细日志未在本 Story 改造。

普通 BCO Email/SMS Dispatcher 保持原路径。851 只允许六个精确事件 ID；复用 BCO MNG request builder/adapter，邮件 `messageType=6`、短信 `messageType=1`、`appID=89`。保留全局 dispatcher mock switch 和短信长度限制。不要把 851 加入 `DIGX_CZ_BATCH_HIGH_ALERT_EVENT_LIST`；专用消费按 MNG reference 查询负回执，不依赖通用 batch 的 processed flag。

## 测试与 UAT

本地命令（项目根目录；需要现有 backend 编译产物、JDK 和 H2 1.4.200）：

```sh
python3 devtools/backend-compile/tests/verify_hth_contact_compile.py
python3 devtools/backend-compile/tests/verify_hth_contact_sql.py
python3 devtools/backend-compile/tests/verify_hth_contact_runtime.py
```

设置 `JAVA_HOME`；H2 默认 `/tmp/hth-h2-1.4.200.jar`，可通过 `HTH_TEST_H2_JAR` 指向已有依赖。测试不调用银行网关。运行测试使用生产 policy/capture/stage/JDBC/dispatch/DTO 源码、真实 EclipseLink/OBDX ORM wrapper、真实 OBDX metadata lookup；配置、用户/审批仓库、JNDI、Event 注册及网络是测试边界。

已覆盖：flag/业务单位/BCO/最终审批隔离，三种变更、无变更、仅国家码，最终签署人，更新后旧值快照，实际 ORM commit/rollback，重复 stage，多线程 publication/dispatch claim，MNG 插入失败禁止外发，成功/拒绝/超时/空响应、进程中断待对账、回执补发一次、AP/SMS/公司同邮箱/补发失败终止，SQL 重跑/晚期失败回滚/其他 BU 保留，以及真实模板 getter 解析。

UAT 仍需提供以下实际证据：

- 分别完成三种 CM Edit 的多级最终批准；同时验证 Maker、中间审批、拒绝/取消、普通 BCO、Merchant 与非 `update` 路径。
- 按 `APPROVAL_REF` 核对 ledger 收件数量，验证真实旧/新 Email、SMS、最终 AP 收件；API/AP 同邮箱时模板不同、同手机号短信一次。
- 验证 Scheduler 只读取已提交业务、Alert/MDB 跨进程正确反序列化 DTO、三种 locale 无残留占位符。
- 真实 CCBEMAIL 负回执保留完整 MNG reference；API Email 补发当前公司邮箱一次，AP/SMS/fallback 不级联。
- 共享 UAT 观察批次时长与 BCO 原通知次数。851 每批最多取 100 条，网络派送仍依赖现有 adapter 超时和 Alert 配置；放在原 BCO 批次之后不能替代容量验证。

Oracle PL/SQL、真实 WebLogic/JTA/MDB 提交链、MNG 送达与正式文案尚未获得环境验收证据；本地通过不等同 UAT 通过。
