HTH 创建用户邮件：免审核链路检查及预校验修复 — 2026-11-17

来源：bco-applicaion-main / hth-integration-20261110
本次源码提交：dae832701177c6a21c2f0ad7a6dd86066f03a56f
前次定位日志提交：f331ca312cdc737d94213912a1faadabdcc933d8

检查结果：
1. 免审核会在当前请求中继续执行 UserExtensionData.create。
   创建成功、domain.create 完成后，调用原 BCO 通知及 activateHthApiPasswordCode。
   HTH 通知入口不限定 checker，也没有单独跳过免审核的条件。
2. 发现一个确定的阶段不一致：HTH Code 激活使用原生 SQL，之前没有跳过 VALIDATE；
   公共邮件框架 ActivityRegistrationApplicationService 在 VALIDATE 阶段跳过注册。
   如果此阶段的激活被保存，正式执行看到 ACTIVE 就会按重放规则跳过邮件。
3. 本次只在 HTH 的 activateApprovedCode 增加 8 行保护：读取事件框架同样使用的
   线程 CTX；VALIDATE 返回而不激活、不退役旧 Code、不启动有效期、不注册邮件。
   NORMAL 正式执行仍激活并通知；重复执行仍不重发。
4. 没有证据证明当前 UAT 的免审核创建一定经过 VALIDATE。因此本次修复的是已验证的
   代码缺口，不能把 UAT 此次未收到邮件的根因宣称为已确认。

范围：
- 生产代码仅 HostToHostApiPassword.java。
- 新增 verify_code_approval_notification.py 回归脚本。
- 原有 BCO 创建/通知、审批规则、收件人、邮箱去重及前端均未修改。
- 没有 SQL 变更或数据库操作，不需要重跑 hth_onboarding.sql。
- 不自动恢复/重发以前已 ACTIVE 的 Code，历史漏发仍需按日志和邮件记录确认。
- 保持 CRLF；沿用 BeaSystemOut 公共日志，不打印 Code、密码或邮箱地址。

部署：
将 init/bco-applicaion-main 下文件按原路径覆盖到完整工程，构建并部署 hosttohost 模块。
此前 init 中 SMS 日志补丁及只读 SQL 保留；若从未部署前次补丁，需同时部署相应 SMS 模块。
未替换 UAT/QTES2 本地仓库，未推送远程，未直接部署服务器。

验证：
- 完整 HostToHostApiPassword、UserExtensionData 和 BeaSystemOut 使用仓库依赖
  编译通过，Java 8 target；只有已有的 Java 8/unchecked 编译提示。
- 回归使用生产激活方法体及 SQL，在 H2 Oracle mode 下执行。
  仅将 NUMTODSINTERVAL 翻译为等价 H2 interval 表达式；SQL 条件不变。
- 验证 VALIDATE 不写库、不使旧 Code 失效、不启动有效期；随后 NORMAL 激活并触发一次
  通知；直接 NORMAL 执行、重复执行、已删除 Code 拒绝均通过。
- 免审核操作人与 checker 身份均测试了同一激活入口；审批引擎本身未在测试中运行。
- 同一回归对修复前源码失败，错误为 validation must not activate Code。
- ORM/线程/事件投递边界使用测试替身；未连接 Oracle、UAT 或 SMTP，不能证明邮件送达。

日志关键词：HTH_CODE_EMAIL
- ACTIVATION_DEFERRED_VALIDATE：本次新增，预校验推迟激活；正式执行应继续出现下面日志。
- NO_SUBMITTED_CODE：HTH 请求没有提交 Code ID，未进入 Code 激活/通知。
- APPROVAL_ACTIVATE_BEGIN / APPROVED：进入激活 / Code 审批通知处理。
- APPROVAL_REPLAY_SKIPPED：Code 已处理，按原规则不重发。
- USER_NOT_FOUND / COMPANY_NOT_FOUND：查不到联系人。
- USER_LOOKUP_FAILED / COMPANY_LOOKUP_FAILED：联系人查询异常。
- NO_EMAIL_ADDRESS：邮箱为空。
- DUPLICATE_ADDRESS_SKIPPED：本次相同邮箱去重。
- REGISTER_BEGIN / REGISTER_FAILED / REGISTER_EXCEPTION：开始注册 / 返回失败 / 注册异常。
- REGISTERED：框架返回 replyCode=0，只代表注册调用返回成功，不证明邮件送达。

UAT 验收：
用免审核用户新建一个 HTH 用户，查看同次请求的 HTH_CODE_EMAIL 日志和邮件历史。
目标 HTH 用户及公司应按既有规则收到 Code 邮件；相同邮箱只发一次。
若未收到，使用已保留的只读查询脚本：
consulting/db/branch_change_history/hosttohost/verify_create_user_email.sql
结合交易号与 BeaSystemOut trace reference 检查事件配置、联系人和 EmailDispatcher。
仍需服务器实测确认此次 UAT 漏发原因及实际送达结果。
