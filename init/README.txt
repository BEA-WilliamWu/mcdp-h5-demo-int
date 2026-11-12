BCOH2H-1955 — 仅修改 CM 调用方的修正版
交付日期：2026-11-12；在上一版 init 目录原位更新。

重要状态
本版已撤回 DSP 改动，但 setup/reset 业务仍未打通，不是可直接上线的完整密码功能。
按已确认范围：先交付调用方修正并列出接口缺口；缺口确认前保持相关功能不可用。
DSP 原代码已恢复，不需要部署本包中的任何 DSP 修改，也不再交付 hth-dsp/ 或 hth-dsp.patch。

修正内容
- CM 公钥请求只发送原接口定义的 hashValue（六位随机数的 SHA256 小写 hex）。
- 移除自加的 protocol、queryOnly、credentialStatus、DSP requestId 和 function R 协议依赖。
- 现有接口没有状态查询，因此状态返回 UNKNOWN，不会猜测为未设置/已设置。
- setup/reset 返回 DIGX_CZ_HTH_API_PASSWORD_009，在 Code 查询、预留、消费和密码写入之前停止。
- 不发送成功通知，不把旧 USED 记录当成本次成功；页面遇到 UNKNOWN 显示现有失败提示。
- 自动判断 SETUP/RESET 的 Code 生成同样暂不可用；已有 Code 的查看/审批生命周期代码保留。
- CM 三张密码存储表的 Java/ORM 仍移除，只保留 Code 表；没有改成把密码保存回本地。

待 DSP 提供方确认
1. 原样保存 Hash：现有源码会再次计算 SHA256，并输出 hex，与 Base64(SHA256(password)) 要求不同。
2. 无旧密码重设：现有 A 只新增，M 要旧密码；需要确认审批 Code 重设采用哪个已提供的接口/参数。
3. 是否已设置密码：现有两个 API 均不返回这个状态，需确认可靠来源。
4. RSA OAEP 的 MGF1 参数：现有 DSP 使用运行时默认值，不能据此保证与浏览器 SHA256/MGF1-SHA256 一致。
5. 最终接口的 internal APIC 地址、订阅和发布版本。
详情及日志关键字见 bco-applicaion-main/consulting/db/release/HTH_1955_DEPLOYMENT.txt。

应用方式（二选一）
- 尚未应用上一版 CM 改造：使用 bco-applicaion-main.patch，它包含从原基线起的全部 CM 改动和删除。
- 已应用上一版 init 的 CM 改造：使用 UPDATE_FROM_PREVIOUS_INIT.patch，仅包含这次修正。
在 CM 源码仓库先执行 git apply --check <补丁>，再执行 git apply <补丁>；不要把两个补丁叠加应用。
手工复制时使用 bco-applicaion-main/ 内完整文件，并按 DELETED_FILES.txt 删除旧文件。
REMOVED_FROM_PREVIOUS_INIT.txt 列出撤回的 DSP 交付项。
若已把旧 DSP 补丁应用到别处，需要与提供方撤回相应改动；不要直接覆盖对方后续改动。

SQL
首次安装脚本、现有 UAT 升级脚本及退役脚本保留在 consulting/db/release/。
本次修正没有新增 DDL；已执行过 UAT 的 ADD COLUMN 不要重跑。
退役三张旧表的脚本暂不要执行：须先解决接口缺口、验证数据在 DSP 并完成备份/对账。
本次没有执行数据库 SQL 或部署任何服务。

验证
本地 HTTPS 验证公钥请求/响应、APIC 请求头、超时、无重试和无重定向。
setup/reset 拦截测试验证没有 Code 处理或成功通知，前端 UNKNOWN 状态及 60 项多语言错误处理通过。
CM 4829 个源码隔离编译通过（四处既有 EnumerationConstants 缺失仅使用临时测试替身，未修改 BCO 源码）。
此前自定义 DSP 协议的 39 项测试不能作为原接口可用的证明；本版没有宣称真实 setup/reset 或 UAT 联调成功。

清单
FILES.tsv：从原 CM 基线到本版的所有变更。
SOURCE_COMMITS.json：CM 提交、原基线、上一版提交及 DSP 恢复提交。
SHA256SUMS：本目录文件校验值。
