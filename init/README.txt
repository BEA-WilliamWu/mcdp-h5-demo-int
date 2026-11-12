BCOH2H-1955 — API Password 仅保存到 DSP
交付日期：2026-11-12

本目录是本次改造的增量交付，不是整个项目快照。

内容
- bco-applicaion-main/：CM 新增及修改文件，保留源码相对路径。
- hth-dsp/：DSP 新增及修改文件，保留源码相对路径。
- 两个同名 .patch：各自源仓库本次完整提交的补丁，包含新增、修改及删除。
- DELETED_FILES.txt：旧文件删除清单；仅覆盖新文件不能完成本次改造。
- FILES.tsv：所有变更路径、交付方式及完整文件校验值。
- SOURCE_COMMITS.json：源提交与基线提交。
- SHA256SUMS：本交付目录文件校验值。

应用方式
建议在各自目标源码仓库中先运行 git apply --check <对应仓库.patch>，检查通过后再运行 git apply <对应仓库.patch>。
不要把两个补丁应用到同一个仓库；若有冲突，按源提交和目标改动人工合并。
也可手动复制各仓库子目录里的完整文件，并按 DELETED_FILES.txt 删除旧文件。
DSP application.yaml 原文件含既有登录密码，因此没有复制完整文件。
它的本次配置变化在完整 hth-dsp.patch 内，也单独提供 application.yaml.patch。
若使用手工复制方式，在 DSP 仓库根目录以 git apply --unidiff-zero 应用该配置补丁；不要覆盖现有环境配置。

改造内容
- 移除 CM 本地 HTH_API_PASSWORD_CREDENTIAL / OPERATION / STATE 的 Java、ORM 和首次部署建表内容。
- 保留 HTH_API_PASSWORD_CODE 的审批、24 小时有效期、尝试次数及一次性使用控制。
- 通过 internal APIC 调用 /password/v1/public-key 与 /password/v1/modification。
- 浏览器计算 Base64(SHA256(password)) 后用 DSP 公钥加密；CM 不保存密码或密码哈希。
- DSP 新增 CM_SHA256_BASE64_V1 协议，含状态查询 queryOnly 和审批 Code 重设 R；CM 与 DSP 必须配套部署。

SQL 与部署
详细说明：bco-applicaion-main/consulting/db/release/HTH_1955_DEPLOYMENT.txt
生产首次安装：HTH_FIRST_DEPLOY.sql。
现有 UAT：先执行 HTH_1955_UAT_UPGRADE.sql，部署并验证；备份及处理未完成操作后，才执行 HTH_1955_RETIRE_LOCAL_PASSWORD_TABLES.sql。
上述脚本用途不同，不要在已有 UAT 重跑首次安装脚本；清理脚本会实际删除三张旧表。
本次交付未连接数据库执行 SQL，也未部署服务。

验证范围
39 项 DSP 相关测试通过；含真实 RSA、H2 setup/reset、旧签名失效及新签名通过。
CM internal APIC 本地 HTTPS 调用、Code 生命周期、60 个多语言错误处理用例通过。
CM 全量源码编译仍有 4 个原有 EnumerationConstants 缺失错误；隔离依赖验证编译通过。
UAT / Oracle / WebLogic / internal APIC / 实际 UAM 尚需联调。
Hash-only 协议下密码复杂度由前端检查，服务端不能从哈希验证明文复杂度。
