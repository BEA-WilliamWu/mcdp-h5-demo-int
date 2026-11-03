# BCOH2H-881 BM / BCOH2H-876 CM 增量交付

源码仓库：`hth-application`。范围仅限该仓库，不修改 `hth-dsp`。

| Story | 源码提交 | 源码基线 |
| --- | --- | --- |
| 881 BM | `947f8784d6e62e3fbb6195bd036a946b761a00f2` | `b74c5d9f` |
| 876 CM | `9286b2082ad8acb79f988095afec86a00e9e127a` | `947f8784` |

按用户指定，876 加入现有 `bcohth-881` 文件夹；已有 881 文件原样保留。

技术设计：

- [881 BM](hhfi/story/account/BCOH2H-881-TECHNICAL-DESIGN.md)
- [876 CM](hhfi/story/account/BCOH2H-876-TECHNICAL-DESIGN.md)

## 目录

- `hhfi/consulting/`：7 个生产文件（881 三个、876 四个），保持源码 consulting 的相对目录。
- `hhfi/devtools/`：两项需求的回归测试，及 881 原有的 1288 测试装配依赖更新。
- `hhfi/story/account/`：两份 Markdown 技术设计和原始 AC PDF。
- `SHA256SUMS`：上述 18 个文件的 SHA-256。876 文件从对应 Git 提交逐字节导出；881 原文件校验保持一致。

未写入本仓库根目录原有 consulting、devtools、story 或 hth。

## 使用方式

这是增量文件，不是完整应用。将 hhfi 下文件按相同相对路径合入完整基线工程，不能用增量目录整体替换 consulting。构建 HTH 模块，发布 BM 两个及 CM 三个前端组件并刷新缓存。两个 story 均不需要数据库迁移 SQL。

876 本地验证已通过：完整服务 Java 8 目标编译、实际生产方法内存仓储回归、实际 AMD ViewModel / Knockout 回归、原账户切页与 Next、1216 通知及 881 回归。876 三个生产 JS 的 OBDX ESLint 为 0 errors / 0 warnings。

881 原交付已通过：完整服务编译、后端运行回归、前端 AMD 回归、1288 通知回归及两个生产 JS 的 OBDX ESLint。

后端测试需要完整源码、已有编译基线和本地 OBDX JAR，本包不携带这些依赖。运行命令与 UAT 场景见各自技术设计。

## 验收边界

BM/CM 列表及维护项移除已实现。BM 普通 Edit 保留已有隐藏授权，Disable 仍停用全部服务。CM 普通 Edit 保留仍选中有效账户的隐藏授权，移除账户/整个关联时仍停用全部子授权；新建/重新激活不会创建或恢复 Audit Log 授权。

新旧 API 用户默认查询本人日志、越权拒绝仍需 HTH/DSP 接口团队联调。本包不能单独证明 881 AC2–AC5 或 876 AC2–AC4 已完成；仅有 Audit Log 的旧维护单仍需补选业务 API 才能通过原有最少选择校验。
