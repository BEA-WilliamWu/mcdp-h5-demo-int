# BCO Notification — 880 + 2110 合并交付

本目录是 **BCOH2H-880 与 BCOH2H-2110 的统一交付版本**。以后一起部署这两个 Story 时，只使用这里的文件，不再依次覆盖 `bcohth-880`、`bcohth-2110` 的旧副本。

## 版本依据与核查结论

全部 22 个文件均逐字节提取自 `hth-application` 同一个源码提交：

`876dd4376179720fb90f637fea85659c120ceada`

该提交在 880 最终修订 `e91aa291` 之后，因此已包含两项最后确认的改动。本次合并没有新增业务规则，也没有把其他 Story 的未提交修改带进来。

| 核查项 | 结果 |
|---|---|
| 最后一次 2110 是否基于 880 最新修订 | 是。`876dd437` 的父提交是 `e91aa291`。 |
| 为什么两个交付目录的同名文件不同 | 两个目录是在不同时间生成的；旧 880 包仍保留后续已撤掉的 2110 分支与测试依赖。混用旧文件会重新引入旧逻辑。 |
| 实际重叠的文件 | `UserExtensionData.java`、`verify_hth_code_approval.py`。本包各保留一份最终版本。 |
| 880 专用实现是否被 2110 回退 | 没有。五个 880 专用生产 Java 文件与 `e91aa291` 完全一致。共享 `UserExtensionData` 中的“用户更新成功后才激活 Code”也仍保留。 |
| 看起来恢复旧代码的部分 | 是按确认撤回的 2110 专用分支、线程标记和审批回调，不是撤回 880 的成功判断。 |

## 合并后保留的行为

| Story | 最终行为 |
|---|---|
| 880：API Password Code 批核通知 | 保留数据库计算的批核后 24 小时有效期；目标用户与公司邮件、邮箱去重、无 Code 短信；使用原有事件框架登记通知。已撤回新增的 880 线程标记和 APPROVE 回调；保留拒绝审批的清理逻辑。 |
| 2110：同时修改 Email + Mobile | 沿用 BCO 原有事件、模板及收件人流程；移除额外复制给最终审批人的两条联系方式变更短信；原有 `USER_MANAGEMENT_EDIT` 通用通知继续保留。 |
| 原有保护 | 保留旧邮箱快照、旧手机号码、业务成功判断、PIN 状态变更判断；普通 BCO 和 HTH 单字段修改流程保持原状。 |

## 文件结构

```text
bco-notification/
  README.md
  SOURCE_REVISION.txt
  SHA256SUMS
  DELETED_FILES.txt
  hhfi/
    consulting/middleware/projects/...  # 8 个生产 Java 文件
    consulting/db/branch_change_history/...  # 3 个 SQL 文件
    devtools/backend-compile/tests/...  # 9 个回归文件
    story/notification/...  # 两份技术设计
```

`hhfi` 下保留源仓库相对路径。这里是覆盖文件集合，不是独立可编译项目；需要匹配的完整源代码、既有配置及依赖。mcdp 根目录的 `consulting` 未作同步。

## 使用与部署

1. 将本目录 `hhfi/` 下文件按相同相对路径放入完整构建源码，只使用这一份合并包。
2. 按 `DELETED_FILES.txt` 移除构建源码中已废弃的 6 个文件，并清理旧编译产物。列表包含 880 旧有效期工具、2110 两个旧 Java 类、两个旧测试和旧模板创建 SQL。仅复制新文件不会自动删除这些旧文件。
3. 一起构建并部署 hosttohost、sms、approval 模块，避免混用旧 JAR。
4. 需要安装或更新 880 通知配置时，使用本包的 `20261103_BCOH2H-880/1_HTH_Code_Approval_Notification.sql`。先满足已有 HTH 基础脚本前提，执行整个块，成功后 COMMIT；再运行同目录验证 SQL。缓存刷新沿用环境的标准部署流程。
5. 2110 最小版本 **不需要新增模板或配置 DML**。只保留 `20261103_BCOH2H-2110/2_HTH_Contact_Update_Verification.sql` 用于查询。不要重跑已撤回的 2110 模板创建 SQL；若先前执行过，其专用配置可以暂留不用，不删除历史发送记录。
6. 验收新交易，分别核对 880 Code 激活/通知，以及 2110 旧、新联系方式通知和审批人通用通知。旧部署已排队的通知需与新交易区分。

已有待确认事项仍保留：880 的已评审模板文字含“翌日 23:59:59”，与当前 24 小时有效期不一致；本次文件合并没有擅自改模板文字。详见 880 技术设计。

## 已完成的合并验证

- 从四次相关源码提交取文件并集，核对最终源码树：22 个现存文件、6 个删除项，无遗漏。
- 所有交付文件均与统一源码提交逐字节一致；同名路径仅保留一份。
- 880：24 小时有效期、通知路由、EclipseLink/H2 实际事务激活及回滚、重复处理、缓存刷新和模板原文检查通过。
- 2110：执行生产通知方法，与 BCO 原流程对比；旧/新联系人保留，额外两条审批人短信移除；单字段、PIN、审批清理回归及 Java 8 目标编译通过。
- 测试中的银行资料、部分配置、通知登记/发送边界使用替身；尚未验证实际 Oracle、WebLogic、MNG 投递或收件箱/手机到达。

在本目录检查文件完整性：

```sh
shasum -a 256 -c SHA256SUMS
```

在完整源代码目录运行回归，先设置 `JAVA_HOME`，事务测试另需 `H2_JAR`：

```sh
python3 devtools/backend-compile/tests/verify_hth_880.py
python3 devtools/backend-compile/tests/verify_hth_notifications.py
python3 devtools/backend-compile/tests/verify_hth_password_transactions.py
python3 devtools/backend-compile/tests/verify_hth_2110.py
```

各 Story 技术设计保留原文；涉及分别交付目录的历史说明，以本合并包 README 为准。
