# BCOH2H-881 BM 增量交付

源码仓库：`hth-application`
源码提交：`947f8784d6e62e3fbb6195bd036a946b761a00f2`
基线：`b74c5d9f`
范围：881 BM 增量；2026-11-04 补充 DSP 公司默认日志权限。876 CM 及 DSP 本人范围校验独立交付。

技术设计：[BCOH2H-881-TECHNICAL-DESIGN.md](hhfi/story/account/BCOH2H-881-TECHNICAL-DESIGN.md)。

## 目录

- `hhfi/consulting/`：3 个修改后的 BM 生产文件，保持源码 consulting 的相对目录。
- `hhfi/devtools/`：本次测试及既有 1288 测试装配的依赖更新。
- `hhfi/story/account/`：Markdown 技术设计和原始 881 AC PDF。
- `SHA256SUMS`：hhfi、DSP 文件及 DSP 差异补丁的 SHA-256；同步时已逐文件比对源码字节。

本次没有覆盖本仓库根目录原有的 consulting、devtools、story 或 hth。

## 使用方式

这是增量文件，不是完整应用。将 hhfi 下的文件按相同相对路径合入对应基线工程，不能用增量目录整体替换完整 consulting。按原构建流程生成 HTH 模块 JAR，发布两个前端组件；本次不执行数据库迁移。

本地验证已通过：Java 8 目标编译、881 后端运行回归、881 前端 AMD 回归、既有 1288 通知回归；两个生产 JS 的 OBDX ESLint 为 0 errors / 0 warnings。

后端测试需要完整源码、已有编译基线和本地 OBDX JAR。本包不携带这些依赖。

## 验收边界

BM 列表及维护移除已实现，普通 Edit 保留已有隐藏 Audit Log 授权；Disable 仍停用全部服务。DSP 默认权限已补充；需要一起合入 876 DSP 本人范围文件，AC2–AC5 待 UAT 端到端验收。具体兼容边界和测试项见技术设计。

## DSP 增量（2026-11-04）

DSP 源码基线：`e105c167b3d32b9756c4fda46b4881ffc5aa1b65`。

公司 HTH 启用后，客户 `POST /audit-log/v1` 默认允许查询；不依赖手工 API 授权，其他 API 及运维接口保留原规则。

文件保持 DSP 仓库的原相对路径，放在 `hth-dsp/`，与 `hhfi/` 同级：

- `hth-dsp/hth-ms-support/hth-ms-support-svc/src/main/java/com/hkbea/hth/filter/CoarseEntitlementFilter.java`
- `hth-dsp/hth-ms-support/hth-ms-support-svc/src/test/java/com/hkbea/hth/filter/CoarseEntitlementAuditLogTest.java`

`DSP-CHANGES.patch` 是相对上述 DSP 基线的本 story 差异，便于审阅/合并。优先把两个 story 的改动合入当前完整 DSP 工程；若版本不同，应合并差异，避免用整文件覆盖同事的新改动。不要对同一工程既应用补丁又重复应用同一差异。

**部署依赖：876 和 881 的 DSP 文件应一起合入、一起构建部署。** 两个目录没有重复的 DSP 生产文件，不存在先后覆盖；876 负责本人范围，881 负责默认公司权限。只合入其中一份不足以覆盖两个 story 的全部 API AC。

重新构建 `hth-ms-support-api` 与 `hth-ms-support-svc`，部署 Support 服务。无需 SQL、无需新增授权记录、不改 public-paths、公共包、batch 或 BFF 生产代码。原 `hhfi/consulting` 文件保持既有交付版本。

联合验证：完整 Maven 编译通过；62 项测试通过，0 failures / 0 errors / 0 skipped，含实际错误响应构造与 Feign 解码验证。测试替代数据库/身份解析边界，未连接真实 UAT/Oracle/APIC；实际 Token 验证、部署路由与新旧用户端到端验收仍需在 UAT 完成。测试入口见技术设计新增的 DSP 小节。
