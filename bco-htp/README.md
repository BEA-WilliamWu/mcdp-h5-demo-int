# BCOH2H-876 + BCOH2H-881 DSP 合并交付

日期：2026-11-04。

本目录是 876/881 的统一 DSP 交付目录，包含两个 story 合并后的最终增量。共 7 个 Java 文件：3 个生产文件、4 个测试文件。后续 DSP 修改统一同步到本目录，部署时以本目录为准，无需再从两个独立 story 目录收集或覆盖文件；原 story 目录保留。

## 目录

- `hth-dsp/`：保持 DSP 工程原相对路径的最终文件。
- `DSP-CHANGES.patch`：两个 story 合并后的差异补丁。
- `SHA256SUMS`：上述交付文件及本说明的 SHA-256 校验清单。

## 修改范围

- **876**：日志查询绑定 Token 的 closeId 和 clientId；明确请求其他用户/公司时拒绝，返回 `500074` 及 AC 文案；公司启用沿用原过滤器检查，不在服务里重复查询。
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

本次在当前 DSP 基线 `94219bfee449dfb823d69e430cd13c5c13fe28da` 的临时副本上验证合并补丁可以应用，应用后 7 个文件与本目录及当前 DSP 源码逐字节一致。若部署工程还有其他改动，应合并差异，避免整文件替换掉那些改动。

## 验证

- 逐文件核对两份原交付的 SHA-256，并比对当前 DSP 源码。
- 检查重复路径；没有重复路径或不同版本冲突。
- 合并补丁在当前基线成功应用，结果与最终交付文件完全一致。
- 2026-11-04 范围复核后移除重复公司查询及新增空请求处理，恢复原日志语句。最终 3 个生产文件仅新增 20 行；其他接口、参数处理和公共包沿用原流程。
- 精简后重新执行 60 项测试，0 failures / 0 errors / 0 skipped：既有权限 14、新默认权限 9、既有日志查询 15、本人范围 10、错误响应 2、PartyResolver 10。数据库及身份解析使用测试桩，真实 UAT 验收仍需完成。

在本目录执行 `shasum -a 256 -c SHA256SUMS` 可检查交付完整性。
