BCOH2H-1955 — SQL 新目录与首次部署文件更新
交付日期：2026-11-12。原 init 原位更新；本次源代码提交 e39009d4。

本次更新
- 随源仓库将 consulting/db/release 下六个文件迁到 consulting/db/branch_change_history/hosttohost。
- 首次部署入口改为 hth_onboarding.sql，已补齐 API Password 的最新最终结构。
- 不创建 HTH_API_PASSWORD_CREDENTIAL / STATE / OPERATION 三张旧表；不保存密码或 Hash。
- HTH_USER_PROFILE 包含四个 API_PASSWORD_* 状态字段，Code 表包含 REMOTE_CLIENT_ID。
- 删除旧 credentials/persist 配置，只使用原 BM DSP.*；已有 DSP.* 配置不覆盖。
- story/20261112-BCOHTH-1955 保留已有 UAT 的字段升级、旧表退役脚本及最新部署说明。
- story/20261111-BCOHTH-1442&1950 保留 UI 开关脚本，内容沿用用户同步版。
- 本次没有修改 Java、前端业务逻辑或 DSP 源码；两个现有测试脚本改为读取新的 SQL 路径。

应用方式
CM：首次应用 1955 使用 bco-applicaion-main.patch（base 见 SOURCE_COMMITS.json）；
    已应用上一版 init/9fc7fe9a，使用 UPDATE_FROM_PREVIOUS_INIT.patch。二选一，不叠加。
    增量同时包含用户已提交的目录迁移和本次 SQL 修正。
DSP：本次与上一版 c603fd2 完全相同，无需重新应用或部署。
     新安装仍使用从提供方 f9378da 开始的 hth-dsp.patch。旧 DSP 增量补丁已从本包移除，避免误用。
补丁先 git apply --check，再 git apply；只适用于相应基线。
手工复制使用各仓库同名子目录，保留原路径，并按 DELETED_FILES.txt 清理旧源码/旧 SQL 文件。
旧 release 目录下的六个文件要删除，避免继续执行旧入口；这些删除只是文件清理，不是数据库删除。
FILES.tsv 的 RESTORE 行也必须复制覆盖：原 BM DSPApiClientFactory.java 仍交付，不能删除。
application.yaml 仍只交付 .patch，请合并配置而非覆盖环境原文件。
PATCH_ONLY 行的校验值对应 .patch；FILE 行对应完整文件。
本包是 1955 累计改动，依赖已有 HTH 迁移基线；其他交付目录未改。

数据库执行入口（二选一）
1. 首次生产部署：consulting/db/branch_change_history/hosttohost/hth_onboarding.sql。
   适用只有 BCO + Inquiry、没有 HTH 表的数据库。包含全部最终结构，不再叠加 story 字段升级脚本。
   脚本不是可重复执行的 UAT 升级；失败时先核查已执行的 DDL。
2. 已有 UAT：按 story/20261112-BCOHTH-1955/HTH_1955_DEPLOYMENT.txt 执行。
   缺少四个状态字段时执行 HTH_1955_LOCAL_STATE_UPGRADE.sql 一次；已存在勿重复执行。
   仅 Code 表缺 REMOTE_CLIENT_ID 时执行 HTH_1955_UAT_UPGRADE.sql 一次。
   已有 profile 保持 UNKNOWN，按实际 DSP 数据核对为 ACTIVE/NOT_SETUP 并绑定业务 clientId。
   HTH_1955_RETIRE_LOCAL_PASSWORD_TABLES.sql 仅在备份、核对及清理旧操作后单独执行，不能用于首次部署。

沿用上一版的运行配置与功能
- BM Client ID 校验及两条密码接口共用 DSPApiAdapterImplConfig：DSP.SERVICE_URL、DSP.APIC_CLIENT_ID、
  DSP.APIC_CLIENT_SECRET、DSP.CHANNEL（默认 BCO）；密码接口不读取 H2H.*。
- 原 BM 工厂已经恢复原 QTES2 实现，BM 业务服务和页面未改。
- Code reset 无需旧密码；浏览器传 Base64(raw SHA-256(UTF-8(password))) 的 RSA-OAEP 密文。
- DSP 解密后原样保存 Hash，A 首次创建、M 重设；HTH 仅保留状态和 Code。
- DSP 成功才完成 Code 和状态；UNKNOWN/IN_PROGRESS 阻止不确定结果的自动重试。
- 两个密码路径需加入原 BM 的内部 APIC 订阅；DSP 注入 HTH_CM_APIC_CLIENT_ID / HTH_CM_APIC_CLIENT_SECRET，
  与 BM DSP.* 对应凭据相同。空配置拒绝调用，网关须校验并转发身份，Channel 为 BCO。
- 密码调用关闭敏感报文日志、自动重试和重定向。

验证
- 从新 hth_onboarding.sql 提取实际建表语句，Code/状态/并发、本地事务、SETUP/RESET 回归通过。
- 60 个多语言错误提示、SQL 文案一致性及 UNKNOWN 页面门控通过。
- 已核对 13 张 HTH 表、必需字段、退役表/配置移除，以及 DSP 配置只补缺失项。
- 完整/增量补丁经临时 Git 索引验证可重建对应源提交；文件及 SHA256 校验一致。
- 未执行 UAT/生产数据库，未连接真实 Oracle、Redis、WebLogic、APIC 或 UAM。
- 前版真实 DSP 本地服务、存储及签名验证证据保留在部署说明中；仍需 UAT 端到端验收。

完整部署说明：bco-applicaion-main/consulting/db/branch_change_history/hosttohost/story/20261112-BCOHTH-1955/HTH_1955_DEPLOYMENT.txt
SOURCE_COMMITS.json 记录源提交和补丁基线；SHA256SUMS 校验交付文件。
