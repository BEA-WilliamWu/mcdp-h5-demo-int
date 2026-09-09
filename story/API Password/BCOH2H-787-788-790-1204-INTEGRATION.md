# HTH API Password 合并说明（787 / 788 / 790 / 1204）

存储设计更新：按用户要求，密码存储现为 DATABASE（默认）/UAM 可配置；下文历史 UAM-only 描述已被 [当前存储与部署说明](../../consulting/db/branch_change_history/20260907_HTH_API_Password/README.md) 替代。787 Code 密文及原密钥迁移与此选择独立。

同步范围：来自主项目 `a33c20e1`，此 mcdp 提交仅包含 API Password Story 787/788/790/1204 的整合及必要依赖。三个 HTH account-access-management 组件已与主项目一致。共享 SMS 文件使用其他业务快照合入前的 Story 合并版本，未包含其他业务更新。下列 18 文件编译记录针对主项目当时的完整依赖环境，不代表 mcdp 可独立构建。

日期：2026-09-09。合并来源为本次补齐的 `merge/ApiPassword/{middleware,config,db}`。
此文档取代早期 788/790 设计中对独立 hash Code 表及 789 生成依赖的假设。

## 实现范围

以同事 787 的 Code 生成实现为基础，保留 generate/masked/reveal，合入我们的 status/setup/reset。
同名 Service、REST 及两个接口各保留一份，REST 注册也仅一项。
只合入 API Password 所需代码、映射和 SQL。输入快照中其他 SMS、iToken、CRM 变更以及已退役的
HthUserAccessRequest 审批映射均未覆盖当前代码。
前端仍使用 `host-to-host/api-password`，首次设置与重置入口不变。本次输入未包含创建用户的前端目录。

| HTTP | 相对 `/hostToHostApiPassword` 路径 | 作用 |
| --- | --- | --- |
| POST | /generate | AP 在用户维护期间生成 PENDING Code |
| GET | /masked | 按公司和用户名读取最新 Code 状态、用途及掩码 |
| POST | /reveal | 授权操作者查看同公司 Code |
| GET | /status | 当前 HTH 用户读取凭证状态和密码策略 |
| POST | /setup | 消费 SETUP Code 设置密码 |
| PUT | /reset | 消费 RESET Code 重置密码 |

## Code 数据契约

复用唯一的 `HTH_BEA.HTH_API_PASSWORD_CODE`：

- 保留 787 的 USER_NAME、CODE_CIPHER、ATTEMPT_COUNT、EXPIRY_TIME、USED_TIME。
- 密文格式不变：Base64(12-byte IV || AES-GCM ciphertext || 128-bit tag)。
- 不再要求 CODE_HASH / CODE_SALT，不再另建第二套 Code 表。
- 增量添加 PURPOSE（默认 SETUP）、REQUEST_ID、MAX_ATTEMPTS（默认 5）。
- 保留 PENDING / ACTIVE / USED / EXPIRED / INVALID，增加 IN_PROGRESS / UNKNOWN。
- Code 生成早于用户落库，Code 表不添加指向尚未创建用户的外键。
- 保留 HTH_API_PASSWORD_STATE 和 HTH_API_PASSWORD_OPERATION，分别记录凭证投影和请求幂等状态。
- 接受裸用户名及同公司 `username@partyId` 形式；新生成记录使用裸用户名。
- 唯一索引按标准化用户名、公司和用途限制活跃 Code，包含 IN_PROGRESS / UNKNOWN。

`generate` 请求可添加 `purpose: "RESET"`；省略时仍按 787 首次创建行为生成 SETUP Code。
返回的 Code DTO 增加 purpose。Reset Code 仍通过原用户维护审批变为 ACTIVE，不能靠 generate
直接生效。创建用户前端继续使用默认 SETUP；Reset Code 调用方必须显式传 RESET。由于输入未包含
该前端调用方，本次没有新增 AP 的 Reset Code 按钮或页面。

## 审批和事务

UserExtensionDataDTO 保留 hthApiPasswordCodeId 作为原流程快照字段。
UserExtensionData 的正常 HTH create/update 执行入口调用带公司、用户名的 activation overload：
校验 Code 归属后才激活。非 HTH 或没有 Code ID 的请求维持原逻辑。
仅原审批执行负责 PENDING -> ACTIVE，重复审批不重新激活或重复发布事件。

审批激活和重新生成的 PENDING 作废加入原业务事务；用户操作回滚时激活也回滚。
重新生成用条件 UPDATE，仅作废仍为 PENDING 的记录，不误作废并发刚激活的 Code。

密码消费采用独立事务持久化失败次数、占用、完成和 UNKNOWN：
在 WebLogic 池化环境暂停外层 JTA，使用平台 `openNewSession("DIGX")`，结束后恢复外层事务。
这避免密码请求抛出异常时，将错误次数或已提交给 UAM 的占用记录一并回滚。
此实现按随项目提供的 WebLogic / OBDX API 编译，仍须在目标 JTA 环境验证。

校验只接受 ACTIVE、未过期、用途匹配、未耗尽次数的 Code；达到上限设为 INVALID。
校验成功条件占用为 IN_PROGRESS。UAM 成功后提交 USED、SUCCESS 和 ACTIVE 凭证投影。
UAM 失败或本地完成失败按 UNKNOWN 处理，阻止危险重试。UNKNOWN 对账恢复不由本次自动实现。
UAM 状态返回未知值时，写操作失败关闭；页面状态查询可使用原有本地投影降级。

## 密钥配置与兼容

生成、reveal、消费统一使用 HthApiCredentialAdapterConfig 下的
`HTH_API_PWD_CODE_CIPHER_KEY`，值为 Base64 编码的 32-byte AES 密钥。
移除了同事 Service 中的源码固定密钥，不设置随机或默认回退。

**已有密文数据必须配置原先加密这些数据的同一把密钥。** 不要直接轮换成新密钥后部署；密钥轮换
需要独立的数据迁移方案。通过受控配置流程提供密钥，不提交至 SQL、Git 或本文档。
Adapter 脚本不会覆盖密钥，启用前检查该配置存在，配置查询对密钥脱敏。

## SQL 执行顺序

在 HTH_BEA 或具备直接 DDL 权限的部署账户：

1. `20260830_BCOH2H-787_HTH_API_Password/1_HTH_API_Password_Schema.sql`。
2. `20260907_HTH_API_Password/1_HTH_API_Password_Schema.sql`（787 增量升级）。

在 OBDX 配置账户：

1. 787 的 **3 Process -> 2 Permission -> 4 Repository Adapters -> 5 Error Messages**。
2. 788/790/1204 的 **2 Process → 3 Permission → 4 Adapters → 5 Error Messages → 6 Notification → 7 Verification**。本目录文件编号即执行顺序，完整账号和配置说明见最终 SQL README。
3. 4 统一配置原 Code 密钥和存储位置，默认 DATABASE。仅显式选择 UAM 时填写已确认的接口地址及环境凭据。

升级脚本要求 787 密文表结构，遇到我们旧版本 hash 表会退出；不能直接 drop/recreate 现有表。
若旧 hash 版本已部署，需先盘点数据再制定迁移/过期重发方案，hash 不能还原成密文。
创建活跃 Code 唯一索引时若存在重复数据会失败，需核实并处理历史重复，脚本不擅自删除数据。
DDL 隐式提交，因此仍须在备份与部署窗口下执行；本次未连接数据库执行任何脚本。

## 通知边界

保留 787 原有 Code 审批通知调用，和 1204 的首次密码设置成功事件分别触发。
787 的通知在原审批事务中发布，不能把“调用成功”表述为已真实送达。
本次输入的 787 SQL 只有五份，未包含其 Code-approved event/template 的注册 SQL；目标环境需已有对应
`HTH_API_PASSWORD_CODE_APPROVED_USER_EMAIL_EVENT` 配置或由该 Story 交付补齐。
1204 继续使用自己的三语言设置成功通知脚本；Reset 不触发此事件。
通知发布失败不改变已经完成的密码操作；可靠补发/Bounce Back 仍须平台联调验证。

## 验证记录

- Java：临时 JDK 21 以 Java 8 目标，项目随附 JAR，编译本次 18 个新增/修改 Java 文件通过。
- 基线已有的 SMS Domain/Repository 引用了缺失的 BeaSystemOut。编译时使用本次输入中的
  `middleware/projects/framework/com.ofss.digx.cz.bea.appcore.logger/src` 作为额外源码依赖；未将整个框架
  快照覆盖进正式目录。目标构建/部署须保留此既有 logger 依赖。此检查不等同于完整 Ant/EAR 打包。
- Crypto：312 项检查通过，包括解密原始 787 实现生成的合成密文、前导零、100 次生成/往返、
  篡改密文、错误密钥、短密钥、空值与截断数据；测试不使用生产密钥。
- 静态检查：REST 路径、ORM 引用、Code SQL 字段、前端 JS/JSON、Git whitespace。
- 未执行：Oracle SQL、真实 WebLogic/JTA、UAM、审批端到端、通知送达。

上线前重点验证：生成 -> PENDING -> 原用户审批 -> ACTIVE -> setup/reset -> USED；无权限及跨公司请求；
Code 用途隔离、失效、失败次数在 API 回滚后保留；并发生成/审批/消费；UAM 超时后 UNKNOWN 对账；
用户审批失败回滚；非 HTH 用户维护回归；通知两类事件不会串用。
