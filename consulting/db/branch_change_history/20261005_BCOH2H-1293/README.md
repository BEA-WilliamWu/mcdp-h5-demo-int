# BCOH2H-1293 HTH CRM 每日抽取部署

此变更只新增 HTH 批处理、`HTH_BEA.HTH_CRM_EXTRACT_RUN` 和 HTH CRM 明细表上的日期索引。BCO 原批处理、表和通知代码不修改。

## 执行顺序

1. 使用 **HTH_BEA** 连接，完整执行 `1_HTH_CRM_1293_Schema.sql` 的 `DECLARE ... END;` 区块。脚本可再次执行；不会删改 `HTH_CRM_EVENT_DETAILS` 的数据。Oracle DDL 会隐式提交，中断后重新执行同一脚本。
2. 用同一连接执行 `2_HTH_CRM_1293_Verify.sql`，核对运行表、索引和列注释。
3. 现有批处理 JDBC 账户需可 `SELECT` `HTH_BEA.HTH_CRM_EVENT_DETAILS`，并可对 `HTH_BEA.HTH_CRM_EXTRACT_RUN` 做 `SELECT/INSERT/UPDATE`。若不是 HTH_BEA 账户，请 DBA 按实际批处理账户单独授权；脚本不猜测账户名。
4. 运行 `consulting/middleware/batchJobs/build-hth-crm-extract.sh`，部署生成的 `hth-crm-extract.jar` 至 `/CDCBatch/lib`；部署 `Deployables/sh/HthCRMExtract.sh` 至 `/CDCBatch/sh`，部署并配置 `Deployables/config/hth_crm_extract.properties` 至 `/CDCBatch/config`。现有 `CDCBatchesUAT.jar`、`ojdbc8.jar`、加密 `batch_config.properties` 和 `Env.sh` 保持原样。
5. 运维在现有作业平台配置**每日**香港时间运行 `/CDCBatch/sh/HthCRMExtract.sh`，具体时点和假日规则由 BA/运维确认；非零退出码接入已有告警。手工指定日期重跑：`/CDCBatch/sh/HthCRMExtract.sh yyyy-MM-dd`。作业平台负责限制操作员权限，不增加客户页面。
6. UAT 做前一日数据对账、零记录、失败/重跑及下游文件验收后再开放生产调度。

## 文件合同待确认

1293 AC 只明确 CSV；此前的 `MTB Interface File Spec.xlsx` 同时描述固定长度头尾记录与可选 CSV 明细，因此**不作为本作业已批准的最终格式**。目前 `hth_crm_extract.properties` 的列、文件名和输出目录是可执行的 HTH 示例，不能直接宣称与 MTB 最终接口匹配。请 BA/MTB 接口方签定字段顺序、表头、编码、文件名、目录、空数据选项以及是否需要头尾/ready 标记，再替换配置或扩展格式器。当前格式是 UTF-8、LF 换行、按标准 CSV 规则处理逗号/引号/换行；没有固定长度头尾或 `.eof` 文件。

## 操作与排查

日志：`$LogFilePath/HthCRMExtract.<时间>.<pid>.log`。关键字 `HTH_CRM_EXTRACT stage=`。数据库运行记录按 `BUSINESS_DATE`、`STARTED_AT` 倒序查询；含开始/结束时间、结果、行数、文件名和异常类型，不含明文数据。`RUNNING` 的唯一索引防止同一天并发启动。进程异常退出可能留下 `RUNNING`；运维先确认没有实际进程，再将该次记录人工标记为 `FAILED` 并说明处理原因，随后重跑。不要为解除锁而删除明细或运行历史。

抽取按 `CREATED_AT >= D 00:00:00 AND CREATED_AT < D+1 00:00:00`，`D` 是香港自然日；不使用 BCO 处理标记。重跑重新读取同一天的全部记录，并在新文件完整生成后原子替换该日期文件。旧文件在查询、生成或写盘失败时保留。若下游已取走旧文件，先协调其重收/去重再重跑。选择 `empty.policy=NO_FILE` 时，成功的零记录运行会移除同日期旧文件；默认 `HEADER_ONLY` 产出表头文件。

若最终文件已成功发布、但随后更新数据库运行状态失败，文件可能已存在而运行记录仍为 `RUNNING` 或 `FAILED`。运维须按文件名、文件大小和日志核对，确认下游是否已拾取后再清除锁并重跑。CSV 字段内可以包含换行，不能用文本行数代替 `RECORD_COUNT`；应按 CSV 解析后的数据行数对账。

批处理 JDBC 账户只能通过既有加密配置取得凭据；日志不输出凭据、SQL 参数或 CSV 内容。数据库不可用时无法在运行表写失败记录，须依据进程非零退出码和本地日志告警。

本地验证：编译单独 HTH JAR 后，运行 `test/outboundbatchprocessor/HthCRMExtractJobTest.java`；测试覆盖日期半开区间、CSV 转义、重跑替换、失败保留旧文件及两种空数据规则。仓库没有 UAT Oracle 连接，本地验证不能代替真实数据库和 MTB 文件接口验收。
