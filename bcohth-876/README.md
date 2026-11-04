# BCOH2H-876 CM 增量交付

源码仓库：`hth-application`
业务代码提交：`9286b2082ad8acb79f988095afec86a00e9e127a`
技术设计交付路径更新：`0f1ce235ee69f52eb0ebaceb7c51f0f7f8f9c99f`
源码基线：`947f8784`（已有 881 BM 实现）
范围：876 CM 增量；2026-11-04 补充 DSP 本人范围校验。881 默认公司权限单独保存在 `../bcohth-881/`。

技术设计：[BCOH2H-876-TECHNICAL-DESIGN.md](hhfi/story/account/BCOH2H-876-TECHNICAL-DESIGN.md)。

## 目录

- `hhfi/consulting/`：4 个修改后的 CM 生产文件，保持源码 consulting 的相对目录。
- `hhfi/devtools/`：876 的前后端回归测试。
- `hhfi/story/account/`：Markdown 技术设计和原始 876 AC PDF。
- `SHA256SUMS`：hhfi、DSP 文件及 DSP 差异补丁的 SHA-256；原 CM 代码不变，技术设计已补充 DSP 说明。

未覆盖 mcdp 根目录原有的 consulting、devtools、story 或 hth。

## 使用方式

这是增量文件，不是完整应用。将 hhfi 下的文件按相同相对路径合入完整基线工程，不能用增量目录整体替换 consulting。按原构建流程生成 HTH 模块 JAR，发布三个 CM 前端组件并刷新缓存；本次不执行数据库迁移。

本地验证已通过：完整服务 Java 8 目标编译、实际生产方法内存仓储回归、生产 AMD ViewModel / Knockout 回归、原账户切页/Next、1216 通知及 881 回归；三个生产 JS 的 OBDX ESLint 为 0 errors / 0 warnings。上述验证属于既有 CM 交付；本次 CM 生产代码不变，新增 DSP 验证见下文。

后端测试需要完整源码、已有编译基线和本地 OBDX JAR。本包不携带这些依赖。

## 验收边界

CM 的 Audit Log 服务选项移除已实现；普通 Edit 保留仍选中有效账户的隐藏授权，移除账户/整个关联时仍停用全部子授权，新建/重新激活不会创建或恢复 Audit Log 授权。

DSP 本人范围校验已补充；需要一起合入 881 DSP 默认公司权限文件，AC2–AC4 待 UAT 端到端验收。仅有 Audit Log 的旧维护单仍需补选业务 API 才能通过原有最少选择校验。

## DSP 增量（2026-11-04）

DSP 源码基线：`e105c167b3d32b9756c4fda46b4881ffc5aa1b65`。

本人查询按 Token closeId/clientId 绑定；指定他人或其他 client 拒绝，返回 500074 和 AC 指定文案；复用公司启用检查。

文件保持 DSP 仓库的原相对路径，放在 `hth-dsp/`，与 `hhfi/` 同级：

- `hth-dsp/hth-ms-support/hth-ms-support-api/src/main/java/com/hkbea/hth/exception/HTHSupportErrorMapping.java`
- `hth-dsp/hth-ms-support/hth-ms-support-svc/src/main/java/com/hkbea/hth/service/AuditLogServiceImpl.java`
- `hth-dsp/hth-ms-support/hth-ms-support-svc/src/test/java/com/hkbea/hth/service/AuditLogServiceImplTest.java`
- `hth-dsp/hth-ms-support/hth-ms-support-svc/src/test/java/com/hkbea/hth/service/AuditLogOwnScopeTest.java`
- `hth-dsp/hth-ms-support/hth-ms-support-svc/src/test/java/com/hkbea/hth/service/AuditLogErrorResponseTest.java`

`DSP-CHANGES.patch` 是相对上述 DSP 基线的本 story 差异，便于审阅/合并。优先把两个 story 的改动合入当前完整 DSP 工程；若版本不同，应合并差异，避免用整文件覆盖同事的新改动。不要对同一工程既应用补丁又重复应用同一差异。

**部署依赖：876 和 881 的 DSP 文件应一起合入、一起构建部署。** 两个目录没有重复的 DSP 生产文件，不存在先后覆盖；876 负责本人范围，881 负责默认公司权限。只合入其中一份不足以覆盖两个 story 的全部 API AC。

重新构建 `hth-ms-support-api` 与 `hth-ms-support-svc`，部署 Support 服务。无需 SQL、无需新增授权记录、不改 public-paths、公共包、batch 或 BFF 生产代码。原 `hhfi/consulting` 文件保持既有交付版本。

联合验证：完整 Maven 编译通过；62 项测试通过，0 failures / 0 errors / 0 skipped，含实际错误响应构造与 Feign 解码验证。测试替代数据库/身份解析边界，未连接真实 UAT/Oracle/APIC；实际 Token 验证、部署路由与新旧用户端到端验收仍需在 UAT 完成。测试入口见技术设计新增的 DSP 小节。
