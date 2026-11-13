HTH 密码诊断日志统一公共方法 — 2026-11-13
源代码：bco-applicaion-main commit 52357486ea5d5b5c6cb33600993bad54c97d8d8b，基线 1d827f71948fdbdbc2a0d63df577e75c2e8d3b39。

HthDspApiCredentialAdapter 的四处诊断输出，以及 HostToHostApiPassword 内的
logPhaseFailure()，统一调用现有 BeaSystemOut.println()，由公共方法添加请求追踪号。
保留 HTH_API_PASSWORD、stage、operation、reason、phase、httpStatus 和异常类型链。
仍只输出诊断分类，不输出密码、Code、密钥、请求/响应正文或原始异常消息。
只修改两个业务 Java 文件和已有测试；公共日志类本身、TLS、业务流程与 SQL 未改。

已按原路径更新 bco-applicaion-main/ 下完整文件；也可在上一版基础上应用
UPDATE_PASSWORD_COMMON_LOGGER_20261113.patch。完整文件覆盖与补丁应用二选一。
本补丁须在 UPDATE_PASSWORD_SUBMIT_20261113.patch 和
UPDATE_DSP_TLS_COMPAT_20261113.patch 后应用。
重新编译部署 extxface.app.impl 与 module.hosttohost，并重新加载应用。无须执行 SQL。

验证：实际公共日志类与 DSP Java 源码通过 Java 8 编译；本地 HTTPS 回归通过。
实际执行 adapter 与从服务源码提取的 lifecycle 日志方法，验证请求追踪号、
异常类型链与敏感字段不输出。完整应用构建与 UAT 未执行。
详见 PASSWORD_COMMON_LOGGER_VALIDATION_20261113.txt。

Earlier delivery notes:

DSP 原请求 TLS 行为恢复 — 2026-11-13
源代码：bco-applicaion-main commit 1d827f71948fdbdbc2a0d63df577e75c2e8d3b39，基线 c1cfe1323。

按用户确认恢复 QTES2 原 DspClient.getOkHttpClient()，方法内容与 QTES2 一致：
- 继续使用 SDK 的 OkHttp 连接池、超时配置和原 DSP.* 地址/APIC 请求头。
- 恢复跳过服务器证书信任校验和主机名校验；这替代此前默认 JVM 校验的行为。
- BM validate/exists 以及两个密码 API 均通过这个 getOkHttpClient() 创建请求客户端。
- 密码接口保留自己的 Feign 包装，以禁止自动重试、重定向和 BODY 日志；
  该包装继承上述原 TLS 配置，没有另建不带该配置的底层客户端。
- 不修改 JVM 全局信任库、SDK jar、DSP 服务端、数据库或接口业务参数。

本次仅修改 DspClient.java 和 verify_apic_transport.py 两个源文件。
完整文件已按原路径更新到 bco-applicaion-main/，准确路径见最后一条
SOURCE_COMMITS.json supplemental_changes。增量补丁：
  UPDATE_DSP_TLS_COMPAT_20261113.patch
先应用上一轮 UPDATE_PASSWORD_SUBMIT_20261113.patch，再应用本补丁；
两个补丁已按顺序在临时 Git index 验证，结果与当前全部六个改动文件一致。
如果上一轮已经应用，仅应用本补丁。完整文件复制与补丁应用二选一。

本次需重新编译部署 extxface.app.impl 模块，并重启/重新加载应用使已缓存的
DspClient 和 Feign/OkHttp 客户端失效。无须执行 SQL。
上一轮错误弹窗及诊断日志仍需部署其 frontend 和 hosttohost 模块。

验证：Java 8 编译通过；本地 HTTPS 使用不受默认 JVM 信任、且主机名不匹配
的证书，未设置 JVM trustStore。普通客户端分别拒绝证书/主机名；本次恢复的
客户端在 BM validate/exists、公钥和 SETUP/RESET 请求中均成功。
同时验证 APIC 头、请求路径、失败分类、日志脱敏、超时不重试和不跟随重定向。
见 DSP_TLS_COMPAT_VALIDATION_20261113.txt。尚未验证 UAT 实际连接。

Earlier delivery notes:

HTH API password submit error dialog and DSP diagnostics — 2026-11-13
Source: bco-applicaion-main commit 1d827f71948fdbdbc2a0d63df577e75c2e8d3b39,
base c1cfe1323bd9854339fc902c8acd654ddc83d118. Commit includes both updates above.

Three production files and two regression scripts only:
- api-password.js: submission errors use the existing BCO message dialog and keep
  the input form visible; only a successful save shows confirmation. Password/Code
  clearing and the request ID used for retry remain unchanged. The initial UNKNOWN
  state guard remains unchanged. Shared framework session/503 handling is unchanged.
- HthDspApiCredentialAdapter.java: safe diagnostic categories for PUBLIC_KEY and
  MODIFICATION failures. Logs contain reason, HTTP status and exception class chain,
  with no request/response bodies, passwords, Code, keys or APIC header values.
- HostToHostApiPassword.java: log DSP_ADAPTER if loading the HTH DSP adapter fails.
- UI error/retry integration tests and the local HTTPS transport test are included.

Submit still first calls GET status?requestId=...&transport=true to prepare session
and DSP public keys, then POST setup / PUT reset to save encrypted credentials.
A 009 error during the first call prevents the save call. Ordinary status without
transport=true only reads local state and does not prove DSP connectivity.
The observed UAT 400/009 root cause is still unconfirmed without backend evidence.

Apply AFTER UPDATE_FRONTEND_ESLINT_20261113.patch, by copying the five full files
listed in SOURCE_COMMITS.json under password-submit-errors-20261113,
preserving paths, OR applying
UPDATE_PASSWORD_SUBMIT_20261113.patch at the application repository root.
Run git apply --check first. The patch is validated against c1cfe1323.
Rebuild frontend, extxface.app.impl and hosttohost modules. No SQL, shared BCO
framework/client, DSP server or endpoint-contract changes are included.

Search the CM backend logs around the failing request time for:
  HTH_API_PASSWORD stage=DSP_RESPONSE
  HTH_API_PASSWORD lifecycle: stage=DSP_ADAPTER
  Exception while invoking HTH API password operation 'status'
New log reasons:
  HTTP_STATUS: non-200 from the gateway/DSP; inspect httpStatus.
  EMPTY_BODY / INVALID_STATUS / MISSING_PUBLIC_KEY: response does not match contract.
  EXCEPTION: inspect phase and exceptionTypes for configuration, connection/TLS,
             timeout or JSON parsing failures. httpStatus=NONE means no response
             object was obtained by the adapter, not proof the remote was untouched.
  DSP_ADAPTER: the HTH DSP implementation could not be loaded/instantiated.
The browser's HTTP 400 is CM's error mapping; it is not necessarily DSP's HTTP status.

Validation: UI error mapping and 14 SETUP/RESET failure-and-retry flows passed;
57 changed channel JS files passed BCO ESLint (0 errors, 6 pre-existing warnings);
local HTTPS transport/diagnostic tests passed, including existing BM SDK regression.
See PASSWORD_SUBMIT_VALIDATION_20261113.txt. No full build or UAT connection verified.

Earlier delivery notes:

HTH frontend ESLint build-error fix — 2026-11-13
Source: bco-applicaion-main c1cfe1323bd9854339fc902c8acd654ddc83d118 (base 5e7c6ed3).

Eight JavaScript files only; no behavior, template wording, SQL or backend change.
- api-password.js: insert the required blank line after the if statement.
- transport.js: put RSA-OAEP importKey object properties on separate lines.
- Six channel NLS files: remove unnecessary quotes from added property names.

Apply after the earlier init updates: copy the eight files listed in the latest
SOURCE_COMMITS.json entry, preserving original paths, OR apply
UPDATE_FRONTEND_ESLINT_20261113.patch at the application repository root.
Run git apply --check first. This patch is validated on 5e7c6ed3; deployments with
other changes should merge the formatting hunks rather than overwrite custom work.
Earlier cumulative patches retain their earlier contents; apply this fix last.

Validation: 57 changed channel JS files checked with BCO ESLint 5.16.0 and custom
rules: 0 errors, 6 pre-existing BCO warnings. Equivalent JavaScript ASTs verified
for all eight edits; existing UI error tests passed. Full frontend build and UAT
have not been run. See FRONTEND_ESLINT_VALIDATION_20261113.txt for details.

Earlier delivery notes:

HTH audit dropdown toggles and DSP Client ID existence — 2026-11-12
Source: bco-applicaion-main 5e7c6ed3c91f9e6d2a08c4c03e0e18148ae676f8 (branch hth-integration-20261110).

This update includes six Java files and one regression script, with their original
repository paths under bco-applicaion-main/. FILES.tsv lists every delivered file.
- Audit activity and high-risk dropdowns read the existing ENABLE_HTH_CM_ONBOARDING
  and ENABLE_HTH_BM_ONBOARDING flags. Only the explicit HTH onboarding task IDs are
  filtered; existing BCO and released Inquiry tasks remain available.
- resourceTasks filtering applies only to aspects=audit&view=list in the existing
  CZTaskExt.postList extension. The high-risk endpoint uses the same visibility rule.
- BM Client ID validation now calls POST /client/v1/exists, with body clientId.
  Boolean exists is mapped to the existing validUamClientId response. True proceeds
  to the existing local Client ID binding check; false means not available in DSP.
  HTTP/UAM errors and missing/malformed exists are service failures.
- The original DSP SDK transport, DSP.* base URL and APIC headers are retained.
  The API declaration is caller-side because the bundled SDK predates /exists.

Update the prior init by EITHER copying the seven files named in the latest
SOURCE_COMMITS.json supplemental_changes entry OR applying
UPDATE_AUDIT_TOGGLE_DSP_EXISTS_20261112.patch
at the application repository root. Run git apply --check before applying.
The patch is verified against both 2ecf4c62 and prior init CM source
d56fd460; it contains only this change. The previous summary patch remains separate.
For cumulative installation, apply the existing 1955 package, then the summary
update, then this patch. Packaged full files contain the latest combined content
for those paths; the older cumulative patches retain their documented base/scope.

Rebuild and deploy the common module, hosttohost module and extxface.app.impl module
through the normal application build. This update adds no SQL or DSP server change.
Existing onboarding toggle configuration and the BCO CZTaskExt registration apply.
The APIC route for /client/v1/exists must be available at the configured DSP base URL.

Validation passed: Java 8 production compilation; 349 audit assertions; 20 local
HTTPS requests / 179 DSP assertions; existing password transport regression.
Run the new regression with JAVA_HOME set to JDK 21:
  python3 consulting/tests/hth_onboarding/verify_audit_dropdowns_and_client_exists.py
It uses local test fixtures and HTTPS only; UAT connectivity/deployment is unverified.

Earlier delivery notes (BM Client ID validation is superseded by the update above):

HTH account access summary update — 2026-11-12
Source: bco-applicaion-main 2ecf4c62b2ca611529164e8448fd845eaf378ddb (branch hth-integration-20261110).

This update adds one application file:
  bco-applicaion-main/consulting/channel/extensions/components/account-access-management/summary/summary.js
The summary now matches the supplied QTES2 / hth-account version:
- Use the BCO-prefixed title alongside the existing HTH title.
- Preserve the LM resource group id in both account-loading paths.
- Clear the BCO copy-candidate list before rebuilding it.

Apply this summary update by EITHER copying the single file above OR applying
UPDATE_SUMMARY_20261112.patch at the application repository root.
The patch preimage matches both 08693aa2 and the previous init CM source
d56fd460. Run git apply --check before applying the patch.

The earlier 1955 cumulative and update patches below retain their original scope.
For a fresh cumulative installation, apply that existing package first, then this
summary update. The complete summary file is included for manual-copy deployments.
SOURCE_COMMITS.json records this additional file under supplemental_changes.
This is not a full export of source HEAD; the other files from sync 08693aa2
have not been added by this summary-only update.

Scope: no SQL or DSP changes. LD downstream filtering and review-page changes
are not included; retaining the resource id alone does not enable that whole flow.
Validation: JavaScript syntax, whitespace with existing CRLF line endings,
source equality, and focused title/candidate-list/resource-id behavior checks passed.
UAT and a full browser deployment have not been run for this update.

Previous 1955 delivery instructions (unchanged scope):

BCOH2H-1955 — DSP 密码接口移除重复 APIC 校验
交付日期：2026-11-12。原 init 目录原位更新。
源提交：DSP a366c89；CM 部署说明 d56fd460。

本次修改
- 删除 CmPasswordCallerGuard 和它的测试，移除两条密码接口中的 Guard 调用。
- 删除 DSP 的 dsp.password.cm-client-id / cm-client-secret 配置。
- DSP 不再比较 X-IBM-Client-Id / X-IBM-Client-Secret，也不再要求重复的 HTH_CM_APIC_* 环境配置。
- 调用方认证及接口授权由 internal APIC 负责；CM 仍携带原 BM DSP.* 凭据请求 APIC。
- DSP 沿用既有公共请求头处理，CM 仍发送 Message-Id 和 Channel=BCO。
- Body 的 clientId 是业务用户所属的 DSP clientId，仍必填，用于定位密码记录。
- 原 BM 校验及公共代码未改，密码加解密、Hash 格式、Code 校验和状态处理保持原实现。

更新上一版 init
- CM 以 e39009d4 为起点，使用 UPDATE_FROM_PREVIOUS_INIT.patch；本次仅更新部署说明。
- DSP 以 c603fd2 为起点，使用 UPDATE_DSP_FROM_PREVIOUS_INIT.patch。
- 补丁先 git apply --check，再 git apply；环境配置有本地差异时按补丁逐项合并。
- 手工复制时必须删除 DSP 的 CmPasswordCallerGuard.java 和 CmPasswordCallerGuardTest.java，
  同时删除 application.yaml 中的 dsp.password.cm-client-id / cm-client-secret 两项。
- 使用 clean 编译再打包，避免旧 Guard class 残留在部署产物中。
- 本次不需要重新执行 SQL，也不需要改变 CM 的 DSP.* 配置。

首次应用累计 1955 改动
- CM 使用 bco-applicaion-main.patch，基线 1df981ed；DSP 使用 hth-dsp.patch，基线 f9378da。
- 累计补丁和对应的上一版增量补丁二选一，不叠加。准确基线见 SOURCE_COMMITS.json。
- 手工复制使用各仓库同名子目录并保留原路径，按 DELETED_FILES.txt 清理旧源码/旧 SQL 文件。
- FILES.tsv 的 RESTORE 行也必须复制覆盖：原 BM DSPApiClientFactory.java 仍保留，不能删除。
- application.yaml 仅交付累计 .patch，合并配置而非覆盖环境原文件。
  该累计补丁面向原提供方版本；升级上一版时请使用 DSP 增量补丁删除旧 Guard 配置。
- PATCH_ONLY 行校验值对应 .patch，FILE 行对应完整文件，DELETE 行列明须删除的文件。
- 本包依赖已有 HTH 迁移基线；其他交付目录未改。

数据库入口沿用上一版
- 首次生产部署：consulting/db/branch_change_history/hosttohost/hth_onboarding.sql。
  仅用于只有 BCO + Inquiry、没有 HTH 表的首次部署，不叠加 story 字段升级脚本。
- 已有 UAT：按 story/20261112-BCOHTH-1955/HTH_1955_DEPLOYMENT.txt，按需执行缺失字段的升级。
- HTH_USER_PROFILE 四列保存状态，Code 表保留 REMOTE_CLIENT_ID；CM 不保存密码或 Hash。
- HTH_API_PASSWORD_CREDENTIAL / STATE / OPERATION 三张旧表不再创建。
- 已有 profile 的 UNKNOWN 状态需按 DSP 实际记录核对；退役旧表需单独按部署说明处理。

验证与部署
- clean 编译通过，18 项本地密码回归全部通过，无失败、错误或跳过。
- 测试不注入 APIC 请求头或重复凭据配置，验证公钥、setup/reset、真实 MyBatis/H2 持久化及失败回滚。
- 实际 CM 浏览器加密与 DSP 解密互通；重设后原密码签名失败、新密码签名通过，UAM 发 token 使用测试替身。
- 无效参数、密文、密钥、重复 setup 和不存在密码的 reset 仍会被拒绝。
- 完整/增量补丁经临时 Git 索引验证可重建源提交；交付文件与源码及 SHA256 清单一致。
- 本次未部署 UAT，未执行数据库，未验证真实 internal APIC/UAM 环境。

完整部署说明：bco-applicaion-main/consulting/db/branch_change_history/hosttohost/story/20261112-BCOHTH-1955/HTH_1955_DEPLOYMENT.txt
