BM/CM 独立 HTH 开关修复 — 2026-11-17

来源：bco-applicaion-main / hth-integration-20261110
源码提交：2df269c51754f9ac54c83617d79f3350fcedf32b
本说明替代上一版 BM Audit 修复说明；当前版本不再要求 BM、CM 开关同时开启。

规则：按当前门户选一个开关，不按任务来源、目标用户 Channel Type 或操作人业务角色选择。
前端沿用 dashboard.appData.segment === ADMIN 判断 BM；其他门户沿用 CM 分支。
后端 Audit 沿用 ThreadAttribute.isAdmin 判断 BM；其余沿用 CM 分支。
BM 只读取 ENABLE_HTH_BM_ONBOARDING；CM 只读取 ENABLE_HTH_CM_ONBOARDING。
只有当前门户开关为 Y 才显示对应 HTH onboarding 区域；缺失/读取失败按关闭处理。
开关开启仅保留原有权限下的入口，不增加用户权限。

修复范围（10 个生产文件）：
1. Audit 两个下拉接口共用的 HthAuditTaskVisibility：
   GET /digx/v1/resourceTasks?aspects=audit&view=list
   GET /digx/cz/v1/transactionList/highRiskTransactions
   显式列出的新增 HTH onboarding 任务均跟随当前门户开关。
   普通 BCO、已上线 Inquiry 和非 Audit 请求保留原有处理。
2. 创建用户：HTH Code 区域、生成/重生成/查看操作及 HTH 提交入口。
3. 用户详情：HTH Code 区域、实时/审批快照的 masked 查询及查看操作。
4. 编辑用户：补当前门户配置查询，Channel Type 行和 Code 区域受控，
   同时控制 masked 查询、生成和查看。BCO 证件/联系方式/角色区域保持原有处理。
5. Account & Services Access summary：HTH 汇总区域、复制和进入映射页面受控。
   配置缺失不再默认开启。HTH 模式关闭时不会转入 BCO 权限编辑流程。

原有 user-search-list、user-list-details、side-menu 和后台菜单组件过滤已按门户选开关，
本次未重复修改。没有改 SQL、DSP、密码协议或 maker/checker 审批规则。

交付：13 个变更文件（10 个生产文件 + 3 个回归测试），按原路径放在 init/bco-applicaion-main。
将 consulting 合并到完整目标工程，重新构建并部署前端以及含 module.common 的后端。
仅部署前端或仅部署后端不能完整覆盖本次修复。此目录是增量交付，不是完整工程。
Java/JS/HTML 保持 CRLF；已有 LF 测试文件保持原格式。没有同步 bin 或编译产物。

本地验证：
- Audit：666 项断言，执行真实过滤入口/DTO/HighRiskTask，覆盖双门户四种开关组合。
- 前端：930 项检查，执行生产回调/操作/KO 条件，覆盖异步顺序、缺失/失败和 BCO 分支。
- 编辑页 Code：8 种状态 × 3 种语言，含开关关闭及 BCO 隔离。
- ESLint 5.16.0：使用现有 _build 配置/自定义规则，5 个修改 JS 文件 0 errors；
  6 条既有 warnings，与提交前一致，没有新增 warning。
- 同一后端测试脚本的 DSP 检查通过：179 项断言、20 次本地 HTTPS 请求。
- 源码修改行 git diff --check 通过；交付文件与源码提交逐字节一致。
  mcdp 全文件新增检查提示的行尾空白已逐行核对，均为原有未修改行，保持原样。
以上是本地定向验证，未执行完整服务器构建、浏览器 UAT 联调或部署。

部署后复核（刷新页面后）：
BM=N / CM=Y：BM 隐藏；CM 按原权限保留。
BM=Y / CM=N：BM 按原权限保留；CM 隐藏。
BM=N / CM=N：双方隐藏。
BM=Y / CM=Y：双方按原权限保留。
同时检查两个 Audit 下拉、创建/详情/编辑用户、HTH 账户权限汇总，以及普通 BCO 流程。

变更文件：
consulting/channel/extensions/components/account-access-management/summary/summary.html
consulting/channel/extensions/components/account-access-management/summary/summary.js
consulting/channel/extensions/components/user-management/user-read/user-read.html
consulting/channel/extensions/components/user-management/user-read/user-read.js
consulting/channel/extensions/components/user-management/users-create/users-create.js
consulting/channel/extensions/components/user-management/users-update/model.js
consulting/channel/extensions/components/user-management/users-update/users-update.js
consulting/channel/extensions/partials/admin/create-panel.html
consulting/channel/extensions/partials/admin/update-panel.html
consulting/middleware/projects/module/com.ofss.digx.cz.bea.module.common/src/com/ofss/digx/cz/bea/app/common/helper/HthAuditTaskVisibility.java
consulting/tests/hth1955/user-code-status.cjs
consulting/tests/hth_onboarding/verify_audit_dropdowns_and_client_exists.py
consulting/tests/hth_onboarding/portal_toggles.cjs
