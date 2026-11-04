# BCOH2H-881 — BM 移除 Corporate API Audit Log 授权维护

日期：2026-11-03；DSP 补充：2026-11-04
源码基线：`hth-application / b74c5d9f`
范围：BM 改动已交付；2026-11-04 经用户确认，补充 `hth-dsp` 公司默认日志权限。DSP 基线为 `e105c167`；876 的 CM 和 DSP 本人范围校验独立交付。

## 1. 需求和交付边界

公司启用 HTH 后，API 用户应默认可以查询自己的 API 日志，不需要 BM 再分配 Audit Log 服务。BM 的 HTH Management 不再显示这个服务，也不能通过提交接口分配或取消它。

来源：[BCOH2H-881 原始 AC](./%5B%23BCOH2H-881%5D%20BM%20-%20HTH%20Management%20-%20Removal%20of%20Corporate%20API%20Audit%20Log%20Service.pdf)。2026-10-26 的需求评论已确认所有 API Profiles 默认启用 Audit Log。

| AC | 需求 | 本次处理 |
| --- | --- | --- |
| AC1 | BM API Authorization List 不显示 Audit Log，不能维护 | 本仓库实现：查询、查看、编辑、全选、Review、提交及审批保存都排除 `AUDIT_LOG` |
| AC2 | 新用户不需要额外授权即可查询本人日志 | BM 已移除选项；本次 DSP 在公司启用检查后提供默认查询权限，配合 876 本人范围校验，待 UAT 联调 |
| AC3 | 仅返回当前 API 用户自己的日志 | 由 876 DSP 改动将查询绑定 Token 公司/用户；联合测试通过，待 UAT 验证 |
| AC4 | 查询其他 API 用户的日志必须拒绝 | 由 876 DSP 改动明确拒绝，返回 500074 和 AC 文案；联合测试通过，待 UAT 验证 |
| AC5 | 已有公司、用户无需人工迁移权限，继续查询本人日志 | 不删除或批量修改历史授权。普通 BM Edit 保留已有有效 Audit Log 映射；本次 DSP 默认权限也覆盖从未授权过的公司，配合 876，待 UAT 验证 |

这里移除的是 HTH 可维护服务项。普通 BCO 的 Audit Log Maintenance、791 的审计活动、审计历史和 API 日志数据继续保留。

## 2. 当前实现和修改原因

- `HTH_API_MASTER` 中包含 `API_CODE = AUDIT_LOG`，显示名称是 Audit Log。
- `HostToHostManagement.fetchApiAuthorizations` 原先直接返回有效 Master，BM 因而能够选择该服务。
- 前端详情页可以从服务器响应或上一步的 `flowState` 恢复列表；Checker Review 可以直接读取平台保存的 `transactionSnapshot`。仅过滤后端新查询，无法覆盖旧 pending 单和页面返回状态。
- 保存原先会先停用公司全部有效 API 映射，再恢复本次选中的映射。界面隐藏 Audit Log 后如果仍沿用这一整表替换行为，普通 Edit 就可能停用原有 Audit Log 授权。
- 本机 HTH/DSP 参考代码的公司权限过滤仍查询 `HTH_MANAGEMENT_API`。最初只交付 BM 时采用保留映射兼容；本次 DSP 补充默认权限，见第 10 节。历史数据保存规则保持不变。

## 3. BM 后端设计

仅修改 HTH 模块的 `HostToHostManagement.java`，按稳定的 API Code 判断，不依赖英文名称或翻译。

### 3.1 可维护服务

新增本类常量 `API_CODE_AUDIT_LOG = "AUDIT_LOG"`，以及 `isMaintainableApiCode` 判断。

| 处理点 | 新规则 |
| --- | --- |
| `fetchApiAuthorizations` | 不返回 Audit Log，覆盖 BM 查询及 UAM 校验后返回的服务目录 |
| `fetchSelectedApiCodes` | 读取当前公司的可维护选项时排除 Audit Log |
| `extractSelectedApiCodes` | 提交参数和旧审批快照里的 Audit Log 不作为选项处理，也不新增或重新激活对应映射 |
| `hasSelectedApiAuthorization` | 使用过滤后的选项，Audit Log 不能单独满足原有的至少一项业务 API 校验 |
| 修改比较、通知判断 | 旧选项和新选项使用同一规则，避免因为隐藏 Audit Log 就误判成业务权限修改 |
| 审批快照新增 | 自建快照的 API 明细使用过滤后的服务集合；不直接修改既有历史快照 |

普通业务 API 的目录校验、无修改校验、审批流程、收费账户校验和 UAM Client ID 规则沿用原实现。

### 3.2 有效权限保存

`refreshEffectiveManagementApis` 按动作区分处理：

| 动作 | Audit Log 映射 | 其他 API 映射 |
| --- | --- | --- |
| Enable / Edit | 已有效的映射原样保留，不改变更新时间/操作者；没有映射则不新增；无效映射不恢复 | 沿用原来的选择校验及保存规则 |
| Disable | 沿用公司停用规则，停用包括 Audit Log 在内的全部有效映射 | 沿用原规则 |

公司 Disable 不属于可分配服务的单项移除。不能为“默认可查询”绕过公司停用。

保留已有映射只是兼容措施，不代表补齐默认查询实现：新公司、从未分配 Audit Log 的旧公司，以及停用后重新启用的公司，由本次 DSP 默认访问规则与 876 的本人范围规则共同覆盖，仍需 UAT 验收。

## 4. BM 前端设计

不改 HTML 布局、通用组件或资源文案，只处理 HTH 页面数据。

| 文件 | 修改 |
| --- | --- |
| `host-to-host-details.js` | 初始化、恢复 flowState、UAM 校验结果都排除 Audit Log；已选代码限定在可维护目录；全选和提交不包含 Audit Log |
| `host-to-host-review.js` | 从输入记录或平台快照建立过滤后的浅拷贝，Review 和提交不带 Audit Log；不改写原始快照对象 |

后端仍是保存规则的最终保障。前端过滤同时覆盖旧页面数据，不能作为唯一权限防线。

## 5. 数据库和历史数据

本次没有 DDL/DML 部署脚本，也没有逐用户权限迁移：

- 保留 `HTH_API_MASTER` 和 `HTH_API_URI` 中该 API 的记录及状态。
- 保留 `HTH_MANAGEMENT_API` 中已有 Audit Log 授权；普通 Edit 不停用该记录。
- 不修改 `HTH_MANAGEMENT_REQUEST_API` 历史记录、平台 `transactionSnapshot`、用户账户/API 授权表和实际审计日志表。
- 不在批处理中补权限，不在用户创建流程里补一条 Audit Log 授权。

可在 UAT 执行以下只读查询，确认某公司 Edit 前后 Audit Log 映射没有改变：

```sql
SELECT m.PARTY_ID, m.HTH_STATUS, a.ID, p.API_CODE,
       a.OBJECT_STATUS, a.LAST_UPDATED_BY, a.LAST_UPDATE_DATE
FROM HTH_BEA.HTH_MANAGEMENT m
JOIN HTH_BEA.HTH_MANAGEMENT_API a ON a.HTH_MANAGEMENT_ID = m.ID
JOIN HTH_BEA.HTH_API_MASTER p ON p.ID = a.API_MASTER_ID
WHERE m.PARTY_ID = :party_id
  AND p.API_CODE = 'AUDIT_LOG'
ORDER BY a.LAST_UPDATE_DATE DESC;
```

## 6. BCO 影响及不包含内容

- 应用侧改动仅在 BM HTH 服务及两个 HTH ViewModel；DSP 补充见第 10 节。不改变普通 BCO 授权或 Audit Log 页面。
- 不改公共包、batch、通用审批 assembler、通知发送器和通知 SQL。
- 不修改 CM 用户级服务分配；这一部分属于 876。
- 本次补充 HTH/DSP 默认权限，876 配套补本人范围和错误码；网关路由、公共认证流程不改。

公司 API 选择的既有通知条件会继续使用当前及请求选项比较；隐藏 Audit Log 本身不能触发“API 权限有变”的通知判断。

## 7. 兼容边界与接口团队确认

1. **只选过 Audit Log 的公司或旧 pending 单**：查看仍可打开，Audit Log 被隐藏。Enable/Edit 仍保留原先“至少一项业务 API”的要求；若过滤后没有业务 API，不能直接提交/批准。需要 BA 决定是否另行支持“仅默认日志服务”的公司；本次不扩大该原有业务规则。
2. **接口默认权限**：HTH/DSP 应在认证和公司启用状态检查之后，让本人日志查询独立于 `HTH_MANAGEMENT_API` 及用户账户 API 授权；不能加入提前跳过检查的公共路径。
3. **本人隔离**：查询条件应绑定 Token 中的公司和用户；请求明确指定其他用户时拒绝。876 还指定文案 `Access denied. You are authorized to view only your own audit logs.`。
4. **路由**：本机参考 BFF 路径为 `/auditLog/v1/logs`，Support 路径为 `/audit-log/v1`；本次仅为 Support `POST /audit-log/v1` 增加默认权限，不将运维查询接口一并放开；真实部署路由仍需 UAT 核对。

AC2–AC5 已补充 DSP 本地实现和测试，但最终验收仍需 UAT；单独部署 BM 代码不足，需同时部署两个 story 的 DSP 文件。

## 8. 测试及上线

### 本地验证（已通过）

2026-11-03：下列编译、运行回归和 OBDX ESLint 均通过。两个生产 JS 的 ESLint 结果为 0 errors / 0 warnings；Java 仅有 JDK 对 Java 8 目标版本的提示。

- Java 8 目标编译：完整修改后的生产服务及当前 DTO，使用项目本地依赖。
- 后端回归：执行实际生产目录/保存方法，仓储由内存测试桩替代；验证列表过滤、伪造/旧请求过滤、隐藏授权不导致误判、普通 Edit 保留有效映射、不新增/恢复 Audit Log、Disable 停用全部、其他公司隔离及无效业务 API 校验。
- 前端回归：执行生产 AMD ViewModel，UI/网络由测试桩替代；覆盖 View/Edit、全选、flowState 恢复、UAM 校验响应、提交、旧 Maker/Checker 快照和原对象不被改写。
- 回归现有 1288 通知测试；其测试装配只增加新私有方法依赖，生产通知实现不变。
- 对两个修改的 JS 执行 OBDX ESLint。

运行入口：

```sh
python3 devtools/backend-compile/tests/verify_hth_881.py
node devtools/frontend-tests/hth-881-management.test.js
python3 devtools/backend-compile/tests/verify_hth_1288.py
```

Python 检查需要设置 `JAVA_HOME`，并保留本地 OBDX 依赖和已有编译基线。同步包是增量代码，不是独立可运行工程。

### UAT 验收

1. 新/旧公司分别测试 View、Enable、Edit、Review 和旧 pending 单，均不显示或提交 Audit Log。
2. 已经有 Audit Log 授权的公司，修改其他 API 后检查隐藏映射 ID、状态、更新时间和操作者未变。
3. 公司 Disable 仍正常停用；其他业务 API 的选择、审批及通知行为正常。
4. 联调新旧 API 用户、没有账户授权的用户及没有历史 Audit Log 授权的公司，验证默认查询只返回本人数据。
5. 查询同公司其他用户、其他公司、公司停用及异常 Token，验证权限拒绝；回归普通 BCO 审计查询。

上述应用侧测试不连接 Oracle、真实审批引擎或远程 DSP，不发送邮件/短信；DSP 本地测试见第 10 节，UAT 端到端验收另行记录。

## 9. 交付目录和回退

源码在 `hth-application` 提交。mcdp 仅同步到：

```text
/Users/devs/DWksp/mcdp-h5-demo-int/
  bcohth-881/
    README.md
    hth-dsp/                      # 881 DSP 默认权限及测试，保持 DSP 仓库相对路径
    hhfi/
      consulting/                  # 与源码 consulting 相同的相对目录，只含本次改动
      devtools/                    # 回归测试及测试装配改动
      story/account/               # 本技术设计
```

不写入 mcdp 根目录原有的 `consulting`、`devtools`、`story` 或 `hth`。

应用侧部署时构建 HTH 模块 JAR，并发布两个修改后的前端组件。按既有方式刷新前端缓存，不执行数据迁移。回退使用上一版模块和两个前端组件，无本次 SQL 需要回滚。

## 10. DSP 补充实现（2026-11-04）

本节补充默认权限实现，原 BM 生产代码不再修改。

仅修改 `hth-ms-support/hth-ms-support-svc/src/main/java/com/hkbea/hth/filter/CoarseEntitlementFilter.java`：

1. 保留原 HTH 通道、Token 解析和公司启用检查（`HTH_STATUS = ENABLE`、`OBJECT_STATUS = A`）。
2. 检查通过后，客户本人查询 `POST /audit-log/v1` 默认允许，不再查 `HTH_MANAGEMENT_API` 是否分配过 Audit Log。
3. 其他路径和 HTTP 方法继续走原 API 授权检查；不放开 `/operations/audit-log/v1/{partyId}`，不使用通配前缀，不加入 public-paths。
4. 请求进入日志服务后，由 876 DSP 代码校验本人范围，并将 closeId/clientId 绑定 Token。公司启用继续沿用上述原过滤器检查，服务入口不再重复查询公司。

这覆盖新公司、旧公司、无历史日志映射、停用后重新启用等情况，不需补权限数据，也不需人工迁移。账户关联或业务 API 的增删不影响本人日志查询；公司停用仍拒绝。

交付仅含该生产文件及 `CoarseEntitlementAuditLogTest.java`，放在 `bcohth-881/hth-dsp/`，不复制 876 的服务/错误码文件。两个 story 的 DSP 文件需一起合入、构建和部署。只部署 881 时，旧日志服务尚不满足“指定他人必须明确拒绝”的 AC。

### 验证与部署

完整 Maven 编译通过；联合 60 项测试全部通过（0 failure、0 error、0 skipped），包括新默认权限 9 项、既有过滤器 14 项，以及 876 的本人范围、日志查询、错误响应、身份解析回归。测试使用内存边界替身，未连接 UAT/Oracle/APIC。

内部依赖已构建后，在 DSP 根目录运行：

```sh
mvn -f hth-ms-support/pom.xml -pl hth-ms-support-svc -am test \
  -Dtest=CoarseEntitlementFilterTest,CoarseEntitlementAuditLogTest \
  -Dsurefire.failIfNoSpecifiedTests=false -Dmaven.test.failure.ignore=false -Dexec.skip=true
```

合入 876 与 881 的 DSP 文件后重新构建 Support API/SVC 并部署 Support 服务。无 SQL，无配置修改；公共认证、其他 API 授权、BCO、通知和 batch 不改。UAT 验证本人查询（新旧公司/用户及无历史授权）、他人/其他公司拒绝、公司停用、无效/过期 Token，以及其他业务权限和运维查询。
