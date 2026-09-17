# BCOH2H-1216 — 账户及服务权限通知（简版）

2026-09-17 · 基线：`8b338c5c` · 用户已认可设计；本地实现及测试完成，待 UAT 联调。

**H2H 关联或编辑账户及服务权限，在全部审批完成且权限保存成功后登记通知。公司收邮件；目标用户、最终审批人收邮件和短信。文案与 BCO 一致，普通 BCO 继续原流程。**

## AC 对照

| AC | BCO 当前代码 | H2H 本次实现 |
| --- | --- | --- |
| AC1：关联账户及服务权限 | 创建账户权限时登记 `USER_ACCOUNT_ACCESS_UPDATE`；部分创建场景有 `isAdmin` 抑制条件，收件路由还受环境配置影响 | 在 HTH `submit` 最终批准执行时，权限保存成功后，通知公司、目标用户和最终审批人 |
| AC2：编辑账户及服务权限 | 修改权限也登记 `USER_ACCOUNT_ACCESS_UPDATE` | 在 HTH `edit` 最终批准执行时，按相同规则通知 |

原 HTH 权限服务没有这条通知入口，所以 1216 需要补上入口及专用通知配置。普通 BCO 的账户权限代码、事件和模板配置均不修改。

## 发给谁、如何去重

| 收件人 | 邮件 | 短信 |
| --- | --- | --- |
| 目标用户 | 已验证 `closeId` 对应的用户邮箱 | 该用户的手机及国家码 |
| 最终审批人 | 本笔已批准交易的最后一位审批人邮箱 | 该审批人的手机及国家码 |
| 公司 | 目标用户所属公司 `partyId` 的 `officeEmailId` | 不发；不将办公电话当作短信手机 |

- 最终审批人沿用 851 的规则：检查交易为 APPROVED、服务匹配，再取 `signedBy` 最后一位审批人；不使用当前执行账号，也不通知 Maker 或前序审批人。
- 即使关联其他公司的账户，公司通知仍发目标用户所属公司，不能改取账户所属的 `accessPartyId`。
- 收集顺序为“目标用户 → 最终审批人 → 公司”。邮箱去首尾空格、不区分大小写；短信按规范化后的“国家码 + 手机号”去重。相同邮箱只登记一次，相同手机只登记一次。
- 去重只覆盖本次通知登记；不新增跨重试去重表。联系方式缺失时跳过该通道并记录角色、阶段、异常类型，其他有效收件人继续处理；验收仍须确认全部应收联系人收到通知。

## 代码改哪里

| 文件 | 改动 |
| --- | --- |
| [HostToHostUserAccess.java](../../consulting/middleware/projects/module/com.ofss.digx.cz.bea.module.hosttohost/src/com/ofss/digx/cz/bea/app/hosttohost/service/HostToHostUserAccess.java) | 唯一修改的原有业务类，增加 4 行：两个保存分支在 `applyApprovedAccess` 成功后调用通知辅助类 |
| [HthUserAccessNotification.java](../../consulting/middleware/projects/module/com.ofss.digx.cz.bea.module.hosttohost/src/com/ofss/digx/cz/bea/app/hosttohost/service/HthUserAccessNotification.java) | 新增于 HTH 模块：检查最终审批、读取联系人、去重，组装原有 DTO；用户与公司分别登记对应 HTH 事件，以匹配各自 BCO 模板 |
| [1216_HTH_User_Access_Notification.sql](../../consulting/db/branch_change_history/20260917_BCOH2H-1216/1216_HTH_User_Access_Notification.sql) | 新增 HTH 事件、活动映射、动作、外部收件配置，以及保持 BCO 文案的专用模板和属性绑定 |

**不改 batch、scheduler、EmailDispatcher、SMSDispatcher、公共 DTO/common 包、BCO 账户权限类、851 逻辑及前端。不新增业务表。** 独立 Delete、公司 HTH 启停不在本次 AC 范围；通过 Edit 调整账户/API 勾选属于 AC2。

正文中的 `UserNameId` 始终是目标用户；路由中的 `UserId` / `inputfacts.recipientUserId` 是实际收件人，使审批人短信使用审批人自己的国家码。公司只使用显式邮箱地址。

通知登记使用原框架的异步 Alert 机制（`FLG_TRANSACTIONAL=N`）。权限保存抛错时不会进入登记；Maker、验证阶段、中间审批、拒绝及 Delete 都不登记。没有另建线程、队列或补偿任务。真实数据库事务关闭、回滚及异步投递行为仍需在 UAT 验收，本地测试不替代这项验证。

## 为什么需要 HTH 专用 SQL

BCO 公共发送类对部分事件会重新计算收件人；直接多次发原事件，可能覆盖指定地址或重复通知公司。因此使用以下两个 HTH 专用事件，仅绑定 HTH `submit`、`edit`，明确使用 EXTERNAL 收件人，不加入公共发送类的地址/国家码重算名单。

| 实际收件人 | HTH 事件 | BCO 来源类别 | HTH 模板 |
| --- | --- | --- | --- |
| 目标用户、最终审批人 | `HTH_USER_ACCOUNT_ACCESS_UPDATE` | `CORPORATE / USER` | `HTH1216_EMAIL_<locale>`、`HTH1216_SMS_<locale>` |
| 公司 | `HTH_USER_ACCOUNT_ACCESS_COMPANY` | `PARTY / CUSTOMER` | `HTH1216_COMPANY_EMAIL_<locale>`；不复制公司短信 |

2026-09-17 UAT 查询确认：BCO 的 SMS/en 同时配置用户模板和公司模板，属于不同收件用途。原脚本只按通道/语言判断唯一性，因此误报 `ambiguous BCO template for SMS/en`。修正后按“收件类别＋通道＋语言”检查及复制；不删除任何 BCO 记录，也不任意选第一条模板。

脚本从环境现有 `UserAccountAccess.update / USER_ACCOUNT_ACCESS_UPDATE` 读取上述两类配置。**标题和正文原样复制，仅建立 HTH 自己的活动和属性映射，不覆盖 BCO 模板。** 两个事件分别绑定自己的模板，避免同一个显式地址收到用户、公司两套通知。仍先统一去重，再登记事件；若公司邮箱与用户或最终审批人相同，只发送一次用户模板。BCO 日后修改文案，重新运行本 SQL 可同步当时的文案。

脚本检查源事件、动作、模板、占位符属性及来源是否齐全；同一收件类别/通道/语言若仍有多个不同模板，或来源带条件规则，会报错停止，避免自行选错。使用一个事务，仅替换本功能配置，失败回滚。设计为可重复执行，但修正版尚未在真实 Oracle 环境验证。

## 部署与验收

1. 在 OBDX 通知配置 schema、无其他未提交工作的连接中执行上述 SQL。将 `DECLARE` 至 `END;` 作为一个完整块执行；使用 SQL*Plus/SQLcl 时在块后另起一行输入 `/`。出现 `1216:` 错误应先补齐提示的源配置，不跳过检查。
2. **修正版 SQL 与 HTH JAR 要一起部署**：重新构建并部署 `com.ofss.digx.cz.bea.module.hosttohost`，确保 JAR 包含按用户/公司选择事件的辅助类及原有服务调用。只更新 SQL、保留旧 JAR，会让公司邮件继续走用户事件。按环境既有流程刷新通知配置缓存或重启应用；不需要部署前端、batch 或公共包。
3. 验证两个新事件只绑定 `submit`、`edit`，每个事件/通道/语言/活动只有一个收件映射；公司事件只有 EMAIL。重复执行 SQL 后行数不增加。
4. 分别完成一笔 Link 和 Edit 最终审批：联系人均不同且资料齐全时，应为 **3 封邮件、2 条短信**；用户事件登记 4 次，公司事件登记 1 次。检查标题、正文分别和 BCO 对应的用户/公司模板一致，占位符全部替换，正文是目标用户。
5. 测试多级审批、相同地址去重、不同手机国家码、关联其他公司账户；测试未批准、拒绝、保存失败及事务回滚无成功通知。
6. 检查 Alert/MNG 登记、失败状态及实际送达；回归普通 BCO 账户权限通知和 851。日志前缀 `HTH_1216` 只记录交易关联、阶段、角色和异常类型，不记录邮箱、电话或正文。

查询配置，可用于执行前后核对：

```sql
SELECT COD_ACT_ID, COD_EVENT_ID, TXT_DEST_TYP, LOCALE, COUNT(*) AS MAPPING_COUNT
FROM DIGX_EP_EVT_REC_B
WHERE COD_EVENT_ID IN ('HTH_USER_ACCOUNT_ACCESS_UPDATE',
                       'HTH_USER_ACCOUNT_ACCESS_COMPANY')
GROUP BY COD_ACT_ID, COD_EVENT_ID, TXT_DEST_TYP, LOCALE
ORDER BY COD_ACT_ID, COD_EVENT_ID, TXT_DEST_TYP, LOCALE;
```

如需完整回退 1216，先部署实现 1216 前的 HTH 模块，再清理本功能两个事件与两个活动的映射、`HTH1216_` 模板和 `.1216.` 专用属性映射；保留 HTH 活动父记录和全部 BCO 配置。不要删除共享模板或重新发送已登记的通知。

## 已完成的本地验证

- `verify_hth_1216_compile.py`：实际项目依赖下 Java 8 编译通过。
- `verify_hth_1216.py`：真实辅助类、保存方法、DTO 序列化、SDK 收件人解析及现有 SMSDispatcher/MNG 请求构建通过；覆盖收件地址、最终审批、用户/公司事件分流、跨模板去重、国家码、保存失败、Delete 隔离及安全日志。
- `verify_hth_1216_sql.py`：抽取实际 SQL 的查询及清理语句，在 SQLite 配置样本中重现 UAT 的用户/公司双模板，验证三种语言的模板分流、同角色歧义保护及清理范围；不等于执行完整 Oracle PL/SQL 块。
- `verify_hth_851_approver.py`：既有 851 回归通过，包括 BCO 原联系人通知行为、短信路由及同内容去重。

测试中的数据库、事务边界、时钟及 MNG 网络由测试桩替代，没有发送真实消息；Oracle SQL 执行、真实审批及邮件/短信送达仍待 UAT。以上验证脚本位于 `devtools/backend-compile/tests/`，使用配置好 `JAVA_HOME` 的 JDK 运行 Python 脚本。
