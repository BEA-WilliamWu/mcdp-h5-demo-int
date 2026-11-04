# BCOH2H-876 + BCOH2H-881 DSP 合并交付

日期：2026-11-04。

本目录合并 `../bcohth-876/hth-dsp/` 与 `../bcohth-881/hth-dsp/` 的最终修改文件，包含两个 story 的完整 DSP 增量。共 7 个 Java 文件：3 个生产文件、4 个测试文件。两份增量没有重复文件路径，没有发生互相覆盖；原 story 目录保留。

## 目录

- `hth-dsp/`：保持 DSP 工程原相对路径的最终文件。
- `DSP-CHANGES.patch`：两个 story 合并后的差异补丁。
- `SHA256SUMS`：上述交付文件及本说明的 SHA-256 校验清单。

## 修改范围

- **876**：日志查询绑定 Token 的 closeId 和 clientId；明确请求其他用户/公司时拒绝，返回 `500074` 及 AC 文案；复用公司启用检查。
- **881**：认证及公司启用检查通过后，客户 `POST /audit-log/v1` 默认可查本人日志，无需手工分配 Audit Log 服务。
- 其他业务 API 和运维查询继续沿用原规则。无需 SQL、授权数据迁移或 public-paths 配置变更。

| Story | 类型 | 文件 |
| --- | --- | --- |
| 876 | 生产 | `hth-dsp/hth-ms-support/hth-ms-support-api/src/main/java/com/hkbea/hth/exception/HTHSupportErrorMapping.java` |
| 881 | 生产 | `hth-dsp/hth-ms-support/hth-ms-support-svc/src/main/java/com/hkbea/hth/filter/CoarseEntitlementFilter.java` |
| 876 | 生产 | `hth-dsp/hth-ms-support/hth-ms-support-svc/src/main/java/com/hkbea/hth/service/AuditLogServiceImpl.java` |
| 881 | 测试 | `hth-dsp/hth-ms-support/hth-ms-support-svc/src/test/java/com/hkbea/hth/filter/CoarseEntitlementAuditLogTest.java` |
| 876 | 测试 | `hth-dsp/hth-ms-support/hth-ms-support-svc/src/test/java/com/hkbea/hth/service/AuditLogErrorResponseTest.java` |
| 876 | 测试 | `hth-dsp/hth-ms-support/hth-ms-support-svc/src/test/java/com/hkbea/hth/service/AuditLogOwnScopeTest.java` |
| 876 | 测试 | `hth-dsp/hth-ms-support/hth-ms-support-svc/src/test/java/com/hkbea/hth/service/AuditLogServiceImplTest.java` |

## 合并与部署

将本目录 `hth-dsp/` 内的文件按相同相对路径合入完整 DSP 工程，或者在完整工程根目录应用 `DSP-CHANGES.patch`，两种方式任选一种。文件夹是增量交付，不是完整工程，不能整体替换原工程目录。

两个 story 的 DSP 改动已合并在此包内，无需再先后覆盖两份独立包。重新构建 `hth-ms-support-api` 与 `hth-ms-support-svc`，部署 Support 服务；原 876/881 的 CM/BM 文件仍使用各自 `hhfi/` 交付。

本次在当前 DSP 基线 `949508282e6db1b87f9c6c40f59dd85a30d52e17` 的临时副本上验证合并补丁可以应用，应用后 7 个文件与本目录及当前 DSP 源码逐字节一致。若部署工程还有其他改动，应合并差异，避免整文件替换掉那些改动。

## 验证

- 逐文件核对两份原交付的 SHA-256，并比对当前 DSP 源码。
- 检查重复路径；没有重复路径或不同版本冲突。
- 合并补丁在当前基线成功应用，结果与最终交付文件完全一致。
- Java 内容与上一轮通过 62 项测试的交付一致。本次仅整理目录，不改 Java 内容，未重复执行测试；真实 UAT 验收仍需完成。

在本目录执行 `shasum -a 256 -c SHA256SUMS` 可检查交付完整性。
