BCOH2H-1955 — Code reset 无需旧密码；HTH 保存状态、DSP 保存密码 Hash
交付日期：2026-11-12。在原 init 目录原位更新；本版同时交付 CM 和 DSP。

当前结论
1955 AC1/AC2 的保存和替换、AC3 的失败提示，以及 AC4 使用最新 Hash 的登录校验已有实现及本地验证。
不是 UAT 验收结果：没有连接真实 Oracle、Redis、WebLogic、APIC 或 UAM，尚未取得实际 UAT token。
代码由 CM 和 DSP 两部分组成；仅部署其中一边无法构成当前协议。

实现范围
- 页面先计算 Base64(raw SHA-256(UTF-8(password)))，再用 DSP 公钥 RSA-OAEP 加密。
- DSP OAEP 和 MGF1 均为 SHA-256，解密后原样保存 Hash，不重复 Hash。
- 使用现有 /password/v1/public-key 和 /password/v1/modification 两个接口。
  function A 首次创建；M 用审批 Code 重设，不要求旧密码；不存在 R 或新增状态查询接口。
- 状态保存在 HTH_USER_PROFILE 的四个新增字段。不恢复三张旧密码表，CM 不保存密码/Hash。
- NOT_SETUP 自动生成 SETUP Code，ACTIVE 自动生成 RESET Code；不确定状态阻止操作。
- 预约 Code 和状态使用同一本地事务；DSP 成功才消费 Code、更新 ACTIVE 并通知。
  DSP 明确拒绝可重新尝试；超时/结果不明进入 UNKNOWN/IN_PROGRESS，人工对账，禁止自动重发。
- CM DSP 调用复用已上线 INQ01 的 H2H.SERVICE_URL / CLIENT_ID / CLIENT_SECRET / CHANNEL。
- DSP 密码入口校验内部 CM 订阅。BCO Login PIN、Inquiry 业务逻辑和公共加解密类没有改动。

应用方式
CM：
  尚未应用前一版 1955：使用 bco-applicaion-main.patch（从 SOURCE_COMMITS.json 的 CM base 开始）。
  已应用上一版 init：使用 UPDATE_FROM_PREVIOUS_INIT.patch（从 b8851501 开始）。
  两者选一，先 git apply --check，再 git apply；不要叠加应用。
DSP：
  使用 hth-dsp.patch，基于提供方最新同步 f9378da；请勿套到更早或自行改动过的源码而不检查。
手工复制：
  两个同名子目录保留源码原目录，按 FILES.tsv 复制；同时按 DELETED_FILES.txt 清理 CM 旧源码。
  application.yaml 只交付 application.yaml.patch，合并新增配置，不覆盖环境原配置。
  FILES.tsv 的 PATCH_ONLY 行校验值对应 .patch 文件，其余 FILE 行对应完整文件。
本包是 1955 的累计修改，依赖既有 HTH 迁移基线；未重新生成其他 story 的迁移目录。

部署步骤
1. 已有 UAT：先执行 HTH_1955_LOCAL_STATE_UPGRADE.sql 一次，增加四个状态字段。
   已有字段不要重复 ALTER ADD。若 Code 表缺 REMOTE_CLIENT_ID，再执行 HTH_1955_UAT_UPGRADE.sql 一次。
   生产首次部署：使用合并的 HTH_FIRST_DEPLOY.sql，不叠加 UAT 升级脚本。
2. 已有 UAT profile 默认 UNKNOWN，必须对账：DSP 已设置 -> ACTIVE + 业务 clientId；
   确认未设置 -> NOT_SETUP + 业务 clientId。不依据旧 USED Code 批量推断。
3. DSP 注入 HTH_CM_APIC_CLIENT_ID 和 HTH_CM_APIC_CLIENT_SECRET，复用 CM INQ01 的现有凭据。
   空配置会拒绝密码接口。确认 APIC 校验并转发这两个请求头、Channel=BCO，以及两个接口的内部路由。
4. 部署 DSP API/service，再部署 CM HTH module/ext-xface 和 init 中前端；刷新缓存。
5. UAT 验收：首次 setup；重新生成 RESET Code 并 reset；新密码可获取 token、旧密码失败；
   错误/过期/已用 Code 拒绝；DSP 故障显示错误且不错误消费 Code/发成功通知。
6. API 客户端须按同一 Base64 SHA-256 Hash 生成密码签名。旧 hex/二次 Hash 不自动转换。
旧表退役脚本单列保留，不因复制本包就执行删除；先完成 DSP 数据迁移对账和备份。

验证与边界
- DSP 17 项测试通过：实际浏览器加密与 DSP 解密、真实服务/MyBatis/H2 保存和事务回滚。
- 新增验证：现有 CorporateTokenServiceImpl + CypherServiceImpl 读取真实测试数据库，
  reset 后旧密码签名失败、新密码签名通过；UAM 发 token/公钥来源为测试替身。
- CM Code/状态 SQL 测试通过：并发、预约/消费、回滚、过期/错误次数、重复请求及 UNKNOWN。
- CM 业务流程、本地 HTTPS/APIC 调用、60 项前端多语言错误提示测试通过；修改的 CM 服务编译通过。
- 源码交付，不含已构建部署包。实际 UAT Oracle/APIC/UAM/Redis/WebLogic 链路仍需验收。
- 密码强度和两次输入一致性由页面校验；只传 Hash 的服务端不另行验证明文强度。

详细部署说明及日志关键字：
bco-applicaion-main/consulting/db/release/HTH_1955_DEPLOYMENT.txt
SOURCE_COMMITS.json 记录源提交和补丁基线；SHA256SUMS 可校验交付文件。
