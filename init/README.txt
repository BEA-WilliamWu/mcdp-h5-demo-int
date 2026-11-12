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
