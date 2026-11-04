# BCOH2H-876 — CM 移除用户 API Audit Log 授权维护

日期：2026-11-03；DSP 补充：2026-11-04

源码基线：`hth-application / 947f8784`（已包含 881 BM 修改）

范围：CM 改动已交付；2026-11-04 经用户确认，补充 `hth-dsp` 本人范围校验。DSP 基线为 `e105c167`，公司默认授权由 881 配套交付。

## 1. 需求与交付范围

API 用户应默认能够查询本人日志，不需要在 CM 的 User Accounts & Service Access 中选择 Audit Log 服务。

来源：[876 原始 AC](./%5B%23BCOH2H-876%5D%20CM%20-%20HTH%20User%20Accounts%20%26%20Service%20Access%20-%20Removal%20of%20User%20API%20Audit%20Log%20Service.pdf)。

| AC | 需求 | 本次实现与验收边界 |
| --- | --- | --- |
| AC1 | CM 不显示 Audit Log，不能分配或移除此服务 | 本次实现。新建、查看、编辑、批量复制、Review、旧审批单和后端保存均排除可维护选项 `AUDIT_LOG` |
| AC2 | 公司 HTH 启用后，新 API 用户默认可查本人日志 | CM 已移除选项；配合 881 DSP 默认权限，本次 DSP 将查询绑定 Token 的公司和用户，本地测试通过，待 UAT 联调 |
| AC3 | 已有 API 用户在移除服务项后继续查本人日志 | 不清理历史授权；普通 Edit 保留有效历史映射。配合 881 后本人查询不再依赖历史服务授权，待 UAT 验证 |
| AC4 | 请求其他用户日志时拒绝，并返回指定文案 | DSP 已增加显式拒绝，错误码 500074，文案按 AC；本地错误响应链路测试通过，待 UAT 验证 |

AC4 指定文案：`Access denied. You are authorized to view only your own audit logs.`

876 处理 CM 用户账户级选择，881 处理 BM 公司级选择。两者均不删除日志数据，也不改变普通 BCO 的 Audit Log Maintenance 界面和 791 审计活动。

## 2. 当前代码与修改原因

- `HostToHostUserAccess.listEnterpriseApis` 从公司有效 API 授权读取 Master，原先把 Audit Log 一并返回，账户的 API 选项由此产生。
- `validateSelectedAccess` 会在 Maker 提交和最终审批时重新查询公司 API 与账户目录。隐藏选项后，旧 pending 单仍可能含 Audit Log，需要兼容处理。
- 审批保存原先先停用整个关联下的账户/API，再恢复选择。若仅隐藏界面，普通 Edit 就会停用原有隐藏授权。
- 前端账户页保留返回状态，Review 使用平台审批快照。需要过滤旧数据，并避免隐藏选项本身造成“有未保存修改”的误判。

## 3. 后端设计

仅修改 HTH 专用服务 `HostToHostUserAccess.java`，按稳定的 `API_CODE = AUDIT_LOG` 判断，忽略大小写和首尾空白，不依赖显示名称。

| 处理位置 | 修改后行为 |
| --- | --- |
| `listEnterpriseApis` | 排除 Audit Log。`eligibleApis`、每个账户的服务列表和有效选择回显均使用过滤后的目录 |
| `removeAuditLogServices` | 对请求中的所有账户去掉 Audit Log，包括旧 pending 快照；不直接更新数据库内的历史快照 |
| `validateSelectedAccess` | 去掉 Audit Log 后按原规则重新校验账户归属、公司 API、重复选择和至少一个业务 API；以服务器目录覆盖客户端 API ID/名称 |
| `selectedApis` | 保存时再次排除 Audit Log，不能靠旧请求或伪造参数新增/恢复该授权 |
| `replaceEffectiveAccounts` | 按现有账户主键及账号别名找到仍选中的有效账户，只对这些账户保留原有有效 Audit Log 子记录 |
| `deactivateAccounts` | 删除账户/整个关联时仍停用全部子授权；其他业务 API 的替换逻辑沿用原流程 |

具体保存规则：

| 场景 | Audit Log 历史授权的处理 |
| --- | --- |
| 普通 Edit，原有效账户仍被选中 | 有效 Audit Log 子记录原样保留，ID、状态和更新时间/操作者不改 |
| 原账户取消选择 | 账户及其全部 API 子授权停用，包括 Audit Log |
| Delete 整个 Related/Associated 关联 | 关联下全部账户及 API 子授权停用 |
| 新建账户授权，或重新选择原无效账户 | 不新增或恢复 Audit Log；普通业务 API 正常保存 |
| Maker 提交、待审批、拒绝 | 沿用现有审批流程，不修改有效权限 |

以上历史保留用于避免无关 Edit 意外删除已有数据；默认日志查询最终必须独立于账户授权。删除账户关联不应影响本人日志查询，本次 DSP 配合 881 按此规则实现，并非靠留下有效子授权来绕过账户停用。

## 4. 前端设计

只修改三个 HTH ViewModel，不改通用组件、HTML 布局或公共资源。

| 文件 | 修改目的 |
| --- | --- |
| `hth-account-linkage.js` | 初始化、返回/取消恢复时过滤旧服务数据；比较原始与当前选择时忽略 Audit Log，避免误报未保存修改 |
| `hth-api-service-mapping.js` | 查看/编辑服务列表排除 Audit Log；复制第一账户选择到其他账户、切换 CASA/TD、返回/Review 均沿用过滤后的数据 |
| `review-hth-user-access.js` | Maker 和 Checker 的旧快照都不显示 Audit Log；提交 payload 不包含此服务；原始快照不被前端改写 |

前端过滤用于展示与兼容，后端仍执行最终校验；其他未知或无权分配的业务 API 继续报原有错误，不一概忽略。

## 5. 数据库与 BCO 影响

无需新增表、字段或部署 SQL，无需批量迁移用户。

- 保留 `HTH_API_MASTER`、公司 API 映射、URI 配置和历史审批记录。
- 继续使用 `HTH_USER_ACCESS_ACCOUNT` 和 `HTH_USER_ACCESS_ACCOUNT_API`；只调整本次审批保存时对隐藏历史授权的处理。
- 不修改普通 BCO Account Access、公共审批框架、batch、通知发送器或 1216 通知规则。
- CM 的四个生产文件均属于 HTH；本次另有两个 DSP 生产文件，见第 9 节。881 的 BM 文件保持已有版本。

UAT 可在同一账户 Edit 前后执行只读查询，对比隐藏授权：

```sql
SELECT a.ID AS ACCOUNT_GRANT_ID, a.ACCOUNT_NUMBER, a.OBJECT_STATUS AS ACCOUNT_STATUS,
       g.ID AS API_GRANT_ID, m.API_CODE, g.OBJECT_STATUS AS API_STATUS,
       g.LAST_UPDATED_BY, g.LAST_UPDATE_DATE
FROM HTH_BEA.HTH_USER_ACCESS_ACCOUNT a
JOIN HTH_BEA.HTH_USER_ACCESS_ACCOUNT_API g ON g.HTH_USER_ACCESS_ACCOUNT_ID = a.ID
JOIN HTH_BEA.HTH_API_MASTER m ON m.ID = g.API_MASTER_ID
WHERE a.PARTY_ID = :party_id
  AND a.CLOSE_ID = :close_id
  AND a.ACCESS_PARTY_ID = :access_party_id
  AND a.LINKAGE_TYPE = :linkage_type
ORDER BY a.ID, m.API_CODE;
```

## 6. 兼容和联调边界

1. 原先只选 Audit Log 的账户/旧 pending 单可以查看，但 Create/Edit 仍要求每个选择的账户至少有一个业务 API。过滤后为空时返回既有 `DIGX_CZ_HTH_UA_009`；不放松账户授权校验。日志查询本身不应要求先创建账户授权。
2. 有其他业务 API 的旧 pending 单，审批时忽略历史 Audit Log 选择，其余字段重新校验；其他业务 API 已失效仍按原规则拒绝。
3. HTH/DSP 要在认证及公司 HTH 启用检查之后允许本人日志查询，并绑定 Token 的公司、用户；指定他人用户时明确拒绝，不能仅覆盖请求参数。
4. 无历史 Audit Log 授权的新/旧用户、无账户关联用户、删除全部账户关联后的用户都需接口侧验证。仅部署本次 CM 与 881 BM，不能保证 AC2–AC4 已满足。
5. 本次补齐 DSP 实现；不新增公共路径、不批量补授权，876 与 881 的 DSP 文件需一起合入。

## 7. 验证与上线

2026-11-03 已通过：完整服务 Java 8 目标编译；执行实际服务方法的内存仓储回归；生产 AMD ViewModel 配合 Knockout 的回归；原账户切页/Next、1216 通知和 881 回归；三个生产 JS 的 OBDX ESLint（0 errors / 0 warnings）。Java 编译保留当前 JDK 对 Java 8 目标版本及既有审计类 unchecked 操作的提示。

运行入口（在完整源码目录执行，后端检查使用 `JAVA_HOME` 和项目本地依赖）：

```sh
python3 devtools/backend-compile/tests/verify_hth_876.py
node devtools/frontend-tests/hth-876-user-access.test.js
node devtools/backend-compile/tests/verify_hth_account_tabs.js
python3 devtools/backend-compile/tests/verify_hth_1216.py
python3 devtools/backend-compile/tests/verify_hth_881.py
node devtools/frontend-tests/hth-881-management.test.js
```

重点场景：

- Related / Associated、CASA / TD、新建 / 查看 / 编辑 / 删除 / 旧审批单均不显示 Audit Log。
- 前端复制、切页、Next/Back/Cancel 保留业务选择，无多余 discard 提示。
- 后端忽略 Audit Log 注入，但仍拒绝未知业务 API、无效账户和空选择；客户端不能用 Audit Log 的 ID 冒充业务 API。
- 仍选中有效账户的 Audit Log 子记录不被更新；移除账户和 Delete 会停用全部子授权；其他用户、关联及公司数据不受影响。
- 新账户不新增 Audit Log、无效历史授权不被激活；保留原账户及授权 ID。

上述应用侧测试使用仓储、网络和审批边界替身，不连接 Oracle/远程 DSP、不发送真实通知。DSP 本地测试见第 9 节；UAT 需补实际 Maker/Checker 和 AC2–AC4 接口验收。

应用侧部署：构建 HTH 模块，发布三个前端组件并刷新缓存；无 SQL。回退使用上一版模块及这三个组件。

## 8. 提交和同步

在 `hth-application` 独立提交并 push。876 与 881 分开交付，876 增量仅同步到：

```text
mcdp-h5-demo-int/bcohth-876/
  README.md
  SHA256SUMS
  hth-dsp/              # 876 DSP 本人范围校验及测试，保持 DSP 仓库相对路径
  hhfi/
    consulting/          # 与源码相同的目录结构，仅包含 876 四个生产文件
    devtools/            # 本次回归测试
    story/account/       # 876 技术设计及原始 AC
```

881 单独保存在 `bcohth-881/hhfi/...`，不包含 876 文件。两个交付目录均不写入 mcdp 根目录的原 `consulting`。交付为增量覆盖文件，应合并到完整工程后构建，不能用增量文件夹替换整套工程。

## 9. DSP 补充实现（2026-11-04）

本节替代此前“DSP 不在实施范围”的限制。原有 CM 代码不再修改。

| 文件 | 改动 |
| --- | --- |
| `hth-ms-support/hth-ms-support-svc/src/main/java/com/hkbea/hth/service/AuditLogServiceImpl.java` | 在客户本人查询入口读取 Token 的 closeId、clientId；明确指定其他用户或 client 时拒绝；查询两个字段始终绑定 Token；复用 PartyResolver 检查公司启用状态；空请求返回参数错误 |
| `hth-ms-support/hth-ms-support-api/src/main/java/com/hkbea/hth/exception/HTHSupportErrorMapping.java` | 新增 `AUDIT_LOG_ACCESS_DENIED_EXCEPTION = 500074`，返回 AC 指定文案 |

请求不传 closeId/clientId、传空白或传本人值时均按 Token 查询；首尾空白去除后比较，大小写不折叠。指定别人的值会拒绝，不再静默改成本人。messageId、日期、分页、排序沿用原逻辑。仅本人查询入口增加校验，`queryAuditLogsByPartyId` 运维入口保持原行为。

返回文案：`Access denied. You are authorized to view only your own audit logs.`。沿用现有 DSP `ParameterException` / HTTP 417 / `ExceptionEntity` 协议，不新增公共异常处理，不把它改成 HTTP 403。现有 Feign 解码器保留错误码和文案；已用真实响应构造器及解码器验证，无需修改 BFF 生产代码。

876 DSP 文件只放在 `bcohth-876/hth-dsp/`；881 默认公司权限文件只放在 `bcohth-881/hth-dsp/`。两者没有重复生产文件，应合入同一 DSP 基线后一起构建、部署。只部署 876 时，未分配过日志服务的公司仍可能被旧公司权限过滤器拒绝。

### 验证与部署

- 完整 Maven 编译 Support API/SVC 和依赖模块成功。
- 联合 62 项测试全部通过（0 failure、0 error、0 skipped）：既有权限 14、新默认权限 9、既有日志查询 15、本人范围 12、错误响应链路 2、PartyResolver 10。
- 新测试：`AuditLogOwnScopeTest`、`AuditLogErrorResponseTest`；已有 `AuditLogServiceImplTest` 只补 Token clientId/公司测试桩。
- 仓储和身份解析边界使用测试桩；检查了实际查询条件绑定用户及 client，未连接 Oracle、UAT、APIC 或真实 Token 服务。
- 内部依赖已构建后，可在 DSP 根目录运行：

```sh
mvn -f hth-ms-support/pom.xml -pl hth-ms-support-svc -am test \
  -Dtest=AuditLogServiceImplTest,AuditLogOwnScopeTest,AuditLogErrorResponseTest,PartyResolverTest \
  -Dsurefire.failIfNoSpecifiedTests=false -Dmaven.test.failure.ignore=false -Dexec.skip=true
```

合入两个包的 DSP 文件后，重新构建 `hth-ms-support-api`、`hth-ms-support-svc`，部署 Support 服务；本次无 SQL、无 public-paths 配置变更、无 batch/公共包改动。UAT 仍需验证新旧用户、无账户授权用户、删除全部账户关联用户、同公司他人/跨公司拒绝、停用公司和无效/过期 Token，以及普通业务 API 和运维查询回归。
