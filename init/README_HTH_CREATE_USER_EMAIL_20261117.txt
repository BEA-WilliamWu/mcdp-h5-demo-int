HTH 创建用户邮件定位补丁 — 2026-11-17

来源：bco-applicaion-main / hth-integration-20261110
源码提交：f331ca312cdc737d94213912a1faadabdcc933d8

本次交付是定位补丁，尚未确认 UAT 漏发根因，不能作为邮件已修复的结论。
已确认：创建成功后有 BCO 原有通知调用；HTH Code 从 PENDING 激活时有独立邮件事件。
SDK 会从线程读取审批 SessionContext，传入 null 不是这次已证实的缺陷。
原代码忽略了事件接口的返回 TransactionStatus；无收件人也会静默跳过。
本次通过 BeaSystemOut 补齐这些状态的日志，不打印 Code、密码或邮箱地址。
保留既有收件人、去重、审批时机、失败处理、重放不重发和 BCO 通知逻辑。
没有执行数据库更新，没有新增邮件重试或重发行为。

生产源码改动：
- module.sms / UserExtensionData：HTH 审批触发入口、缺少提交 Code ID 的提示。
- module.hosttohost / HostToHostApiPassword：Code 激活、重复审批跳过、联系人缺失、
  邮箱为空/重复、邮件事件注册开始、返回失败和异常。

将两个源码文件按原路径覆盖完整工程，重新构建并部署这两个模块。
只读查询脚本：consulting/db/branch_change_history/hosttohost/verify_create_user_email.sql
替换脚本中 PARTY_ID / USER_ID；不要把 Code 明文或密码发出。
本补丁不要求重跑 hth_onboarding.sql。

日志关键词：HTH_CODE_EMAIL
- NO_SUBMITTED_CODE：请求没有带 HTH Code ID，所以未进入激活/Code 邮件流程。
- APPROVAL_ACTIVATE_BEGIN / APPROVED：进入激活 / 进入 Code 审批通知处理。
- APPROVAL_REPLAY_SKIPPED：Code 已处理，按原规则不重发。
- USER_NOT_FOUND / COMPANY_NOT_FOUND：查不到联系人来源。
- USER_LOOKUP_FAILED / COMPANY_LOOKUP_FAILED：联系人查询异常。
- NO_EMAIL_ADDRESS：联系人邮箱为空。
- DUPLICATE_ADDRESS_SKIPPED：与本次已处理的邮箱相同，保持原有去重。
- REGISTER_BEGIN：开始调用事件框架。
- REGISTER_FAILED：返回状态为空或 replyCode 非零。
- REGISTER_EXCEPTION：事件注册抛异常。
- REGISTERED：框架返回 replyCode=0，只代表注册调用成功，不证明最终邮件送达。
使用 BeaSystemOut 的 trace reference 关联同一次审批的完整日志。
若 REGISTERED 但没有邮件历史，应继续查看事件分发/EmailDispatcher 的同时间日志。

下一步需要 UAT 同一次创建/审批的 HTH_CODE_EMAIL 日志与只读查询结果，
并确认缺少的是公司、操作人还是新 HTH 用户的邮件。
目前不能通过静态代码确定线上配置、联系人数据或分发服务是否正常。

验证：两个完整 Java 源码及公共日志类用仓库 SDK/依赖编译通过（Java 8 target）。
CRLF 保持；补丁空白检查通过；三个交付文件与来源提交逐字节一致。
只读 SQL 已核对仓库表/字段和事件名，未连接 Oracle 执行。
没有执行服务器构建、UAT 联调或部署。已有 init 交付保留。
