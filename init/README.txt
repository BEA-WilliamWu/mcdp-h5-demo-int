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
