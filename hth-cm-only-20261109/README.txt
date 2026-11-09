HTH 仅在 CM 显示 — 页面修改交付包
日期：2026-11-09
源仓库：/Users/devs/CProj/hth-application
源仓库提交：0e6fc5df31ce17ea4202183402a470d092f172d3

目录结构
  hhfi/consulting/channel/...：本次修改的 28 个部署文件（14 JS + 14 HTML）。
  hhfi/devtools/frontend-tests/...：3 个本地测试文件，不用于服务器部署。
  SHA256SUMS.txt：以上 31 个文件的 SHA-256 校验清单。
  ESLINT-RESULTS.json：原项目 OBDX ESLint 规则检查结果。

页面行为
  当前操作人的 dashboard.appData.segment === CORPADMIN 时显示 HTH。
  BM（ADMIN）创建用户保持 BCO，隐藏 HTH 选项及 User Channel Type 字段。
  BM 的 User Management 和 User Accounts & Services Access 列表过滤 HTH 用户。
  HTH 用户详情、编辑、新增/编辑 Review、Suspend/Revoke Review 及 HTH 账户授权步骤均补入口检查。
  通过旧页面参数或审核单进入受限 HTH 页面时，使用已有 access-denied 页面。
  仅含 HTH 用户的 BM 查询返回空列表，不报错。
  CM 的 Code 生成/查看、pending 单、账户关联、服务授权流程保留。

改动边界
  这是前端显示与导航限制，不替代后端接口授权。
  未修改 Java、SQL、数据库、DSP、batch 或 framework。
  共用 user-list-details.js 只有调用方明确传 allowHthUsers=false 时才过滤 HTH；其他调用默认沿用原行为。
  未修改 HTH Code 保存、审批、生效、密码 setup/reset 规则。
  未改 BM 的公司 Host to Host Management 功能。
  包中不包含工作区已有的通知 SQL/文档修改。

本地验证（未连接 UAT）
  14 个业务 JS：BCO _build/.eslintrc.json + obdx-eslint-custom-rules，全量 0 error。
  hth-cm-only.test.js：列表过滤、空列表、共用组件默认行为、BCO OMB 导航、直接/审核入口检查及实际 Knockout DOM 绑定通过。
  hth-876-user-access.test.js：CM RELATED/ASSOCIATED、CASA/TD、Copy、Review、Next/Back/Cancel 回归通过。
  hth-api-password-code-view.test.js：43 项 Code 展示、生命周期、眼睛操作及 BCO 隔离断言通过。
  后两项测试更新了 CM 登录环境 fixture；Code 测试也对齐了现有页面在 purpose 缺失时隐藏详情的行为。
  保留原文件 CRLF 换行；按 cr-at-eol 执行 git diff --check 通过。

部署和 UAT 检查
  本目录是增量交付包。将 hhfi/consulting/channel 下文件按相对路径合入目标 channel 源码并按现有流程构建/部署。
  JS 与对应 HTML 应一起部署；更新静态资源缓存后重新登录验证。
  CM：BCO/HTH 用户均可查；HTH 创建、Code、修改、审核及账户授权可用。
  BM：普通 BCO 创建、查询、修改、Suspend/Revoke、账户授权可用；HTH 用户及选项不可见。
  用旧 HTH 页面参数和审核入口检查 BM 的 access-denied；CM 同一流程保持正常。
  本包未替代真实 UAT 双端验证。

本次文件清单
  hhfi/consulting/channel/extensions/components/user-management/users-create/users-create.js
  hhfi/consulting/channel/extensions/partials/admin/create-panel.html
  hhfi/consulting/channel/extensions/partials/admin/update-panel.html
  hhfi/consulting/channel/extensions/components/user-management/user-search-list/user-search-list.js
  hhfi/consulting/channel/extensions/components/user-management/user-search-list/user-search-list.html
  hhfi/consulting/channel/extensions/components/user-management/user-read/user-read.js
  hhfi/consulting/channel/extensions/components/user-management/user-read/user-read.html
  hhfi/consulting/channel/extensions/components/user-management/users-update/users-update.js
  hhfi/consulting/channel/extensions/components/user-management/users-update/users-update.html
  hhfi/consulting/channel/extensions/components/user-management/review-user-create/review-user-create.js
  hhfi/consulting/channel/extensions/components/user-management/review-user-create/review-user-create.html
  hhfi/consulting/channel/extensions/components/user-management/review-user-update/review-user-update.js
  hhfi/consulting/channel/extensions/components/user-management/review-user-update/review-user-update.html
  hhfi/consulting/channel/extensions/components/account-access-management/validation/validation.js
  hhfi/consulting/channel/extensions/components/account-access-management/validation/validation.html
  hhfi/consulting/channel/extensions/components/common/user-list-details/user-list-details.js
  hhfi/consulting/channel/extensions/components/account-access-management/summary/summary.js
  hhfi/consulting/channel/extensions/components/account-access-management/summary/summary.html
  hhfi/consulting/channel/extensions/components/account-access-management/hth-account-linkage/hth-account-linkage.js
  hhfi/consulting/channel/extensions/components/account-access-management/hth-account-linkage/hth-account-linkage.html
  hhfi/consulting/channel/extensions/components/account-access-management/hth-api-service-mapping/hth-api-service-mapping.js
  hhfi/consulting/channel/extensions/components/account-access-management/hth-api-service-mapping/hth-api-service-mapping.html
  hhfi/consulting/channel/extensions/components/account-access-management/review-hth-user-access/review-hth-user-access.js
  hhfi/consulting/channel/extensions/components/account-access-management/review-hth-user-access/review-hth-user-access.html
  hhfi/consulting/channel/extensions/components/user-management/review-user-status/review-user-status.js
  hhfi/consulting/channel/extensions/components/user-management/review-user-status/review-user-status.html
  hhfi/consulting/channel/extensions/components/user-management/review-user-channel-access/review-user-channel-access.js
  hhfi/consulting/channel/extensions/components/user-management/review-user-channel-access/review-user-channel-access.html
  hhfi/devtools/frontend-tests/hth-cm-only.test.js
  hhfi/devtools/frontend-tests/hth-876-user-access.test.js
  hhfi/devtools/frontend-tests/hth-api-password-code-view.test.js
