# BCOH2H-791 — HTH onboarding audit

实现日期：2026-09-16。沿用 `DIGX_AL_AUDIT_LOGGING`、details、原 JMS listener、Audit Enquiry 和 FMO 权限；不建新审计表，不新增业务授权。

## 部署

1. 先完成原 HTH API Password、HTH User Access 的 process/task 部署。
2. 在 OBDX 配置 schema 执行 `1_HTH_Audit_Mapping.sql` 的完整 PL/SQL 块，再执行 `2_Verification.sql`。七个 HTH task 必须存在；缺失、重复或冲突映射会报错并回滚本块。脚本可以重跑，只补 HTH resource/audit aspect，不改 approval/2FA 和角色授权。
3. 部署修改后的 `com.ofss.digx.cz.bea.common`（新增共享 helper）、module.common（审计 Handler）、module.access（查询扩展）、module.hosttohost、module.sms。共享 helper 放在已有 common 依赖中，避免给 SMS 新增对 module.common 的编译依赖。
4. 部署三个 `CommonTask*.properties`。按现有流程刷新 task/resource 缓存或重启相关应用。
5. 前端包含 **`consulting/channel/components/audit/audit-log-results/` 的 JS/HTML**，以及 extensions/resources/nls 下 `hth-audit.js`、`zh-CN/hth-audit.js`、`zh-Hant/hth-audit.js`。这是原审计详情组件，不能只发布 extensions。通过现有 channel 构建打包并清缓存。

未修改原公共用户维护的 task/aspect。第二个 SQL 会列出 Create/Edit 的实际映射，预期沿用已有 `MT_N_CUS` / `MT_N_UUS` 且 audit=Y；若环境不同，先确认其现有 BCO 映射，不能另造一套授权。

## 业务记录

| 操作 | 内容 |
| --- | --- |
| HTH User Create/Edit | actor/target、公司、渠道类型、旧/新渠道（包括 HTH→BCO）、Code 记录 ID、结果 |
| Code Generate/Re-generate | 查库判断是否有原记录，记录 GENERATE/REGENERATE、前后 Code 记录 ID、SETUP/RESET 用途、PENDING 状态；不把生成等同批准生效 |
| Account/Service Access | 保留原 maker/checker 审计；最终执行读取有效 before/after，记录账户/服务 ADD/REMOVE、Related/Associated 公司及审批 reference。服务替换表现为 REMOVE+ADD |
| API Password Setup/Reset | SUCCESS/FAILURE、安全错误码、requestId/reference、idempotentReplay；原密码传输、存储和通知逻辑不变 |

所有摘要是单独的 AuditDetails 副本。禁止 Password、Code、CODE_CIPHER、passwordHash、encryptedCredentials、transportKeyId、token/nonce/cookie 进入摘要。授权用户查看 Code 的原响应不清空。没有新增 ThreadLocal/key；使用框架已有 audit stack，HTH REST 审计结束后 finally 清理，BCO 路径不改变。

JMS 使用新建、无请求/响应 nonce 的 ChannelContext，保留原 SessionContext 及五个企业路由属性，避免把会话凭证复制到队列。使用公开构造器及 setter；不反射私有字段。

## 查询和导出

原列表按 actor、日期、activity、reference 等条件查询，User ID 的既有语义不变。HTH task 补齐英/繁/简名称；目标用户在详情显示，不新增跨公司 targetUser 搜索。

原详情页新增条件显示的 HTH 摘要和 CSV 下载。下载直接使用同一次 `audit/{id}` 经 FMO 校验后的脱敏结果，不另发绕过权限的查询，保留 false 和全部权限差异，并对公式前缀转义。普通 BCO 详情不显示此区域。

代码检索确认当前 Audit Log 搜索/详情组件没有通用导出按钮；另有 migration log 下载。现存 `AuditListResponseDTO.xsl` 没有在当前组件找到调用链，因此本次没有猜改 XSL。原列表报告路径不变，新 HTH 明细通过原详情页 CSV 导出。

## 验证及边界

已完成本地 Java 8 定向编译、生产审计 helper + 真 AuditDTO/JMS 序列化、ChannelContext 副本、敏感字段/伪造摘要排除、错误 wrapper、渠道切换、BCO 不变、1000 项权限差异、生产 setup/reset 编排 + H2/OBDX ORM 成功失败/幂等、851/1216 回归、前端 VM 及 ESLint（含 OBDX 规则）。命令见 technical design。

数据库 PL/SQL、WebLogic 审批/AOP、JMS listener 实际落库、跨公司 FMO、部署后页面/CSV 必须在共用 UAT 验收；本地测试的银行配置/会话和外部 DSP/MNG 是 fixture，没有宣称已真实送达或部署。

UAT 至少执行：HTH 创建；BCO↔HTH 编辑；首次/再次生成及旧 Code reveal；Access 新增、删除、服务替换；setup/reset 成功、错误/过期 Code、解密错误、同 requestId 重放；原 BCO User/Access/Login PIN/Audit 查询回归。核对 actor≠target、主表结果和详情一致，并在安全环境确认 details/JMS 中没有凭证值。

回退应用代码前应先停止 HTH 凭证操作，避免旧审计代码重新记录原始 Code 响应。本 SQL 不删除历史审计，不自动授予或撤销权限。
