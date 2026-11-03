# BCOH2H-881 BM 增量交付

源码仓库：`hth-application`
源码提交：`947f8784d6e62e3fbb6195bd036a946b761a00f2`
基线：`b74c5d9f`
范围：仅修改 hth-application，不修改 hth-dsp；本包不是 876 的 CM 实现。

技术设计：[BCOH2H-881-TECHNICAL-DESIGN.md](hhfi/story/account/BCOH2H-881-TECHNICAL-DESIGN.md)。

## 目录

- `hhfi/consulting/`：3 个修改后的 BM 生产文件，保持源码 consulting 的相对目录。
- `hhfi/devtools/`：本次测试及既有 1288 测试装配的依赖更新。
- `hhfi/story/account/`：Markdown 技术设计和原始 881 AC PDF。
- `SHA256SUMS`：上述文件的 SHA-256；同步时已逐文件比对源码字节。

本次没有覆盖本仓库根目录原有的 consulting、devtools、story 或 hth。

## 使用方式

这是增量文件，不是完整应用。将 hhfi 下的文件按相同相对路径合入对应基线工程，不能用增量目录整体替换完整 consulting。按原构建流程生成 HTH 模块 JAR，发布两个前端组件；本次不执行数据库迁移。

本地验证已通过：Java 8 目标编译、881 后端运行回归、881 前端 AMD 回归、既有 1288 通知回归；两个生产 JS 的 OBDX ESLint 为 0 errors / 0 warnings。

后端测试需要完整源码、已有编译基线和本地 OBDX JAR。本包不携带这些依赖。

## 验收边界

BM 列表及维护移除已实现，普通 Edit 保留已有隐藏 Audit Log 授权；Disable 仍停用全部服务。新旧 API 用户默认查询本人日志、越权拒绝仍需 HTH/DSP 接口团队联调；本包不能单独证明 AC2–AC5 已完成。具体兼容边界和测试项见技术设计。
