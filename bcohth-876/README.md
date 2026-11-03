# BCOH2H-876 CM 增量交付

源码仓库：`hth-application`
业务代码提交：`9286b2082ad8acb79f988095afec86a00e9e127a`
技术设计交付路径更新：`0f1ce235ee69f52eb0ebaceb7c51f0f7f8f9c99f`
源码基线：`947f8784`（已有 881 BM 实现）
范围：仅修改 hth-application，不修改 hth-dsp；本包只包含 876，881 单独保存在 `../bcohth-881/`。

技术设计：[BCOH2H-876-TECHNICAL-DESIGN.md](hhfi/story/account/BCOH2H-876-TECHNICAL-DESIGN.md)。

## 目录

- `hhfi/consulting/`：4 个修改后的 CM 生产文件，保持源码 consulting 的相对目录。
- `hhfi/devtools/`：876 的前后端回归测试。
- `hhfi/story/account/`：Markdown 技术设计和原始 876 AC PDF。
- `SHA256SUMS`：上述 9 个文件的 SHA-256；全部从对应源码提交导出并逐文件核对。

未覆盖 mcdp 根目录原有的 consulting、devtools、story 或 hth。

## 使用方式

这是增量文件，不是完整应用。将 hhfi 下的文件按相同相对路径合入完整基线工程，不能用增量目录整体替换 consulting。按原构建流程生成 HTH 模块 JAR，发布三个 CM 前端组件并刷新缓存；本次不执行数据库迁移。

本地验证已通过：完整服务 Java 8 目标编译、实际生产方法内存仓储回归、生产 AMD ViewModel / Knockout 回归、原账户切页/Next、1216 通知及 881 回归；三个生产 JS 的 OBDX ESLint 为 0 errors / 0 warnings。此次仅拆分交付目录，代码字节与已验证的源码提交保持一致。

后端测试需要完整源码、已有编译基线和本地 OBDX JAR。本包不携带这些依赖。

## 验收边界

CM 的 Audit Log 服务选项移除已实现；普通 Edit 保留仍选中有效账户的隐藏授权，移除账户/整个关联时仍停用全部子授权，新建/重新激活不会创建或恢复 Audit Log 授权。

新旧 API 用户默认查询本人日志、越权拒绝仍需 HTH/DSP 接口团队联调；本包不能单独证明 AC2–AC4 已完成。仅有 Audit Log 的旧维护单仍需补选业务 API 才能通过原有最少选择校验。
