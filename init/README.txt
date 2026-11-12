BCOH2H-1955 — 沿用原 BM DSP 调用方式的修正版
交付日期：2026-11-12。原 init 目录原位更新，包含 CM 和 DSP。

本次修正
- 原 QTES2 已有 BM Host-to-Host Management 的 Correlate UAM Client ID 校验：启用 API Channel 后点 Next，
  调用 validateUamClientId -> DSP POST /client/v1/validate。这不是本次密码功能新增的校验。
- DSPApiClientFactory.java 已恢复为原 QTES2 同步版，BM 的业务 Java、页面和校验接口未改。
- 三个接口共用 DSPApiAdapterImplConfig：DSP.SERVICE_URL、DSP.APIC_CLIENT_ID、
  DSP.APIC_CLIENT_SECRET、DSP.CHANNEL（原默认 BCO）。不读取或回退到 H2H.*。
- 密码接口仍为 POST /password/v1/public-key 与 POST /password/v1/modification，
  通过原 DspClient/SDK 的 Header、连接配置调用。密码专用方法禁用 body 日志、自动重试和重定向。
- DSP 密码入口的 CM 凭据取 BM 现有 DSP.APIC_CLIENT_ID/SECRET 对应值；不再要求使用 INQ01 配置。
- 首次生产 SQL 恢复补建缺失的 BM DSP.* 配置，已有值不覆盖，H2H.* 不改。

沿用上一版功能
- Code reset 无需旧密码；浏览器传 Base64(raw SHA-256(UTF-8(password))) 的 RSA-OAEP 密文。
- DSP 解密后原样保存 Hash，A 首次创建、M 重设；HTH_USER_PROFILE 四列保存本地状态。
- DSP 成功才完成 Code 和状态；失败/结果不明正确处理，UNKNOWN/IN_PROGRESS 阻止自动重试。
- 三张旧密码表不恢复，CM 不存密码或 Hash。

应用方式
CM：首次应用 1955 使用 bco-applicaion-main.patch（base 见 SOURCE_COMMITS.json）；
    已应用上一版 init/df672a32，使用 UPDATE_FROM_PREVIOUS_INIT.patch。二选一，不叠加。
DSP：从提供方同步 f9378da 开始使用 hth-dsp.patch；
     已应用上一版 29e4dfa，使用 UPDATE_DSP_FROM_PREVIOUS_INIT.patch。二选一。
所有补丁先 git apply --check，再 git apply。本次 DSP 增量只有配置说明的修正。
手工复制使用各仓库同名子目录，保留原路径，并按 DELETED_FILES.txt 清理旧源码。
特别注意：FILES.tsv 的 RESTORE 行也必须覆盖复制，恢复原 BM 工厂；不是删除文件。
application.yaml 只交付 .patch，合并配置而非覆盖环境原文件。
PATCH_ONLY 行的校验值对应 .patch；FILE 行对应完整文件。
本包是 1955 累计改动，依赖已有 HTH 迁移基线；其他交付目录未改。

部署前提
1. 已有 UAT 执行 HTH_1955_LOCAL_STATE_UPGRADE.sql 一次，加四个状态字段；已存在勿重复执行。
   仅 Code 表缺 REMOTE_CLIENT_ID 时执行 HTH_1955_UAT_UPGRADE.sql 一次。
   生产首次部署使用 HTH_FIRST_DEPLOY.sql，不叠加 UAT 脚本；已配 DSP.* 不覆盖。
2. 已有 profile 默认 UNKNOWN，按实际 DSP 数据核对为 ACTIVE/NOT_SETUP 并绑定业务 clientId。
   不按历史 USED Code 或缺少本地记录盲目推断。
3. 沿用原 BM DSP.* 地址和 APIC 订阅，确认两个密码路径也已加入该内部订阅。
   DSP 注入 HTH_CM_APIC_CLIENT_ID / HTH_CM_APIC_CLIENT_SECRET，值与 BM DSP.* 一致。
   空配置拒绝调用；网关须校验并转发相应身份请求头，Channel 为 BCO。
4. 部署 CM/DSP 的修正包，刷新前端及配置缓存/重启；不要只部署一边。
5. UAT 验证 setup/reset、错误/过期/已用 Code、DSP 故障、新密码获取 token 成功及旧密码失败。
   API 客户端签名须使用同一 Base64 SHA-256 Hash。旧 hex/二次 Hash 不自动转换。

本地验证
- 新回归：故意设置与 DSP 不同的 H2H.*，确认三个接口均用 DSP.*；密码调用前后 BM 校验都正常。
- 共用 SDK、本地 HTTPS 请求头/路径、A/M 无旧密码、错误分类、不重试/重定向及 Header 清理通过。
- 原 BM 工厂与 QTES2 按字节一致，BM 业务服务和页面与 1955 改造前一致。
- 上一版 17 项 DSP 测试、CM Code/状态/并发测试、60 项前端错误提示测试仍适用于未变的业务代码。
- AC4 本地用现有 DSP 登录/签名代码读取测试数据库，验证新密码通过、旧密码拒绝；UAM 使用测试替身。
- 未连接真实 UAT Oracle、Redis、WebLogic、APIC 或 UAM，不代表 UAT 获取 token 已完成验收。
- 密码强度和两次输入一致性由页面校验；仅接收 Hash 的服务端不另行验证明文强度。

完整部署说明：bco-applicaion-main/consulting/db/release/HTH_1955_DEPLOYMENT.txt
SOURCE_COMMITS.json 记录源提交和补丁基线；SHA256SUMS 校验交付文件。
