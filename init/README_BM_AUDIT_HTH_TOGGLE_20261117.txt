BM Audit Log HTH 开关修复 — 2026-11-17

来源：bco-applicaion-main / hth-integration-20261110
源码提交：b55d143b8c7c85e56fcf0162c545198736b35b8e

交付范围：本次修改的 Java 文件和回归测试，按原目录放在 bco-applicaion-main 下。
将其中 consulting 目录合并到目标 BCO 工程，再重新构建、部署包含 module.common 的后端。
这是本次修复的增量文件，不是完整工程。

修改内容：
HthAuditTaskVisibility 复用已有 isAdmin 标志识别 BM 请求。
BM 请求读取 CM 类 HTH 活动时，先判断 ENABLE_HTH_BM_ONBOARDING。
BM 开关为 N 时，即使 CM 开关为 Y，也隐藏对应 HTH onboarding 选项。
BM 开关开启时继续沿用原来的任务分类和 CM 开关规则。
生产代码仅此一个 Java 文件，增加 10 行，保持 CRLF。
未修改前端、SQL、DSP 调用、权限审批或其他 BCO 业务逻辑。

生效接口：
GET /digx/v1/resourceTasks?aspects=audit&view=list
GET /digx/cz/v1/transactionList/highRiskTransactions

验证：
修复前：回归测试复现 BM activity CM task MT_N_CUS_HTH 断言失败。
修复后：Audit Log 682 项断言通过，覆盖两个实际过滤入口及 CM/BM 开关组合。
同一测试脚本中的 DSP 179 项断言通过，20 次 HTTPS 请求均使用本地测试服务。
测试使用真实生产类、DTO 和 HighRiskTask 配置，隔离容器/配置/日志边界。
这不是完整服务器构建或 UAT 联调；尚未部署服务器。

部署后核对：
1. BM=N、CM=Y：BM 两个下拉隐藏新 HTH onboarding 选项。
2. BM=Y、CM=Y：恢复原有 HTH 选项。
3. BM=N、CM=Y：CM 原有 HTH 选项不受 BM 开关影响。
4. 普通 BCO 和已上线 Inquiry 选项保持原有行为。

Java SHA-256：7190cacb22fd29b7c3fd44c6123ea224fd1f5cbadbebd29cb7afa2f7910c3127
