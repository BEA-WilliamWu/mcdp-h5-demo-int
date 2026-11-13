# HTH Batch Job

独立 HTH 批处理工程，Java 21 + Maven，多模块父工程下由 `hth-mtb` 实现 MTB 抽取。
当前正式任务为 `CRM_EXTRACT`（BCOH2H-1293）：读取 HTH/DSP 的
`HTH_BEA.HTH_CRM_EVENT`，生成带首尾控制行的 CSV。

本工程接替 `hth-application/consulting/middleware/batchJobs` 中原先寄放的 HTH 任务。
849 仍属于 BCO 在线业务及原有 BCO CRM 链路，与本任务的数据源无关。
不依赖 BCO JAR、Ant、`/CDCBatch/sh/Env.sh`、BCO 配置或 BCO AES 工具。

## 架构与职责

调用链：`MTBBatchMain → MTBBatchExecutor → CrmExtractService → JdbcCrmEventRepository / CrmCsvWriter`。

| 层 | 文件 / 包 | 负责内容 |
| --- | --- | --- |
| 启动 | `bin/MTBBatch.sh`、`MTBBatch.bat`、`GenHthCrmExtract.sh` | 环境、独立日志、启动 JAR、返回退出码 |
| 入口 | `MTBBatchMain` | 任务选择、日期检查、配置装配、统一失败日志 |
| 调度 | `MTBBatchExecutor.executeCrmExtract` | 创建任务服务、记录开始/结束时间、笔数及字节数 |
| 任务服务 | `crm/CrmExtractService` | 同日互斥、临时文件、成功后的原子发布、失败清理 |
| 数据访问 | `crm/CrmEventRepository`、`JdbcCrmEventRepository` | 独立 JDBC 连接，按业务日流式读取 HTH 表，关闭连接 |
| 文件格式 | `crm/CrmFileLayout`、`CrmCsvWriter` | 61 列映射、转义、首行、列名行及尾行 |
| 数据 / 参数 | `crm/CrmEvent`、`CrmExtractRequest` | 单笔记录、业务日期、文件名、输出目录校验 |
| 基础设施 | `util/BatchConfig`、`DBUtil` | 项目原生 `jdbc.*` 配置、环境变量引用及连接创建 |

只维护一份代码；SIT/UAT/PROD 使用同一 JAR 和不同的外部配置。
原项目的 `LOAD_DATA` / `PROCESS_TXN` / `GENERATE_REPORT` 是既有示例任务，保留但不是
1293 的实现，`CRM_EXTRACT` 不会调用它们，也不会写示例 `MTB_TRANSACTIONS` 表。

## 构建及部署

```sh
cd hth-batch-job
mvn clean verify
python3 -m unittest discover -s tools -p 'test_*.py'
```

产物为 `hth-mtb/target/hth-batch-job-mtb-1.0.0.jar`，已包含 Oracle JDBC 驱动及日志依赖。
H2 只用于测试，不进入生产 JAR。服务器必须有 **Java 21**；旧 BCO Java 8 不能运行此 JAR。
构建在开发机 / pipeline 执行，运行服务器不需要 Maven。

例如部署到 `/HTHBatch`（示例安装位置，可自行指定）：

```text
/HTHBatch/
  bin/Env.sh
  bin/MTBBatch.sh
  bin/GenHthCrmExtract.sh
  bin/MTBBatch.bat                    # 只供 Windows 使用
  hth-mtb/target/hth-batch-job-mtb-1.0.0.jar
  hth-mtb/config/db.ibank.batch.properties
  hth-mtb/config/system_batch.properties
  tools/verify_hth_crm_csv.py
```

`BATCH_HOME` 默认从脚本位置计算；`JAVA_HOME`、`CONFIG_DIR`、`LOG_DIR`、`BATCH_JAR`、
`JVM_OPTS` 均允许部署环境覆盖。建议使用绝对路径。`.sh` 固定 LF，避免 Windows
检出或上传后出现 `Env.sh^M: not found`；不再引用 BCO 的 Env.sh。

## 数据库和输出配置

外部配置默认放在 `$BATCH_HOME/hth-mtb/config`。使用 `-Dconfig.path` 指向其他目录时，
两个配置文件均必须存在；缺失时失败，不回退到 JAR 内配置或 BCO 数据库。

`db.ibank.batch.properties`：

```properties
jdbc.driver.class=oracle.jdbc.OracleDriver
jdbc.conn.string=${HTH_BATCH_JDBC_URL}
jdbc.conn.username=${HTH_BATCH_JDBC_USER}
jdbc.conn.password=${HTH_BATCH_JDBC_PASSWORD}
jdbc.conn.autocommit=false
```

由部署环境提供上述三个环境变量；URL 格式为 `jdbc:oracle:thin:@//host:port/service`，
账号必须有 `HTH_BEA.HTH_CRM_EVENT` 的 SELECT 权限。也支持运维管理的受限配置文件直接提供
`jdbc.*` 值，数据库参数不应放入命令行或仓库。`${...}` 变量不存在/为空会立即报错。

**配置迁移差异：** 原 BCO 托管版本的 `hth_crm_batch_config.properties` 使用 BCO AES 密文；
本工程使用已有 HTH 配置体系，不能把那些密文粘贴进 `jdbc.conn.*`。本工程原始
`encrypt.properties` 只是未接线的模板，不代表已实现解密；本任务也不加载它。
由部署方在新环境提供连接值，不需要导出 BCO 的数据库密码。

`system_batch.properties`：

```properties
batch.output.dir=./output
crm.file.pattern=hthtomtb_mvp1apis_D{date}.csv
```

默认输出为 `$BATCH_HOME/output`；使用脚本启动时相对路径以 `BATCH_HOME` 为基准。
可将 `batch.output.dir` 配为 HTH 独立交付目录的绝对路径。
`{date}` 替换为 **yyMMdd（6 位）**；不设置 pattern 时使用上面的默认值。
文件名只允许安全的 `.csv` 名称，不能通过 pattern 指向其他目录。
日志默认位于 `$BATCH_HOME/logs`，通过 `LOG_DIR` 配置，和输出目录独立。

## 手动执行

部署环境准备好 JDBC 配置并设定 Java 21 的 `JAVA_HOME` 后执行：

```sh
sh /HTHBatch/bin/MTBBatch.sh CRM_EXTRACT 20261007
echo $?

# 兼容原命令名，但执行的是新 HTH 独立程序：
sh /HTHBatch/bin/GenHthCrmExtract.sh 20261007

# 不传日期：抽取香港时间的昨天。
sh /HTHBatch/bin/GenHthCrmExtract.sh
```

Windows：`bin\MTBBatch.bat CRM_EXTRACT 20261007`。

只抽取一个业务日，不是运行时间：
`CREATION_DATE >= 2026-10-07 00:00:00 AND CREATION_DATE < 2026-10-08 00:00:00`，
按 `CREATION_DATE, ID` 排序。保留原实现的约定：Oracle 日期不带时区，按 HTH/DSP
存储的香港业务时间解释；不额外转换表内值。

返回码：`0` 成功；`2` 参数/日期错误；`50` 配置、连接、抽取、锁或发布失败。
脚本直接打印本次日志完整路径，并按 Java 退出码返回，不通过搜索日志中 Error 字样判断成败。

默认示例输出：`/HTHBatch/output/hthtomtb_mvp1apis_D261007.csv`。
同日成功重跑原子替换同名文件；失败不替换已存在的正确文件。
同日并发只允许一个任务执行；`.lock` 会保留，它不是交给 MTB 的文件。
输出目录必须支持 Java 文件锁和同一文件系统内的原子重命名。

## CSV 格式（保留迁移前契约）

来自 `API_MTB_latest.xlsx / HTH_CRM_MAPPING_APIs` 第 12–72 行，
首尾行来自 v16 Implementation plan 的 Data Extraction。
UTF-8、无 BOM、LF；先控制首行，再原有列名行、N 笔业务数据，最后控制尾行。

| 行 | 格式 |
| --- | --- |
| 首行 | 100 个 ASCII 字符：`00` + yyyyMMdd + `HTH` + `MTB` + 84 个空格 |
| 列名行 | 原有 61 列列名，顺序不变 |
| 业务行 | 61 列，前两列恒为 `50`、`1`；其余按映射取值 |
| 尾行 | 100 个 ASCII 字符：`99` + N（15 位补零）+ 日期数值加 N（15 位补零）+ 68 个空格 |

尾行最后 68 个空格包含 15 位空白 `file_size` 和 53 位 filler，不写实际文件大小。
日志 `size` 仍记录完整文件的真实字节数。N 只计算业务行，零笔也生成三行，物理总行数为 N+3。
所有 CSV 字段带双引号，反斜线、双引号、CR、LF 分别转义为 `\\`、`\"`、`\r`、`\n`；
NULL 输出空字段，Event_Time 的 `HHmmss` 转为 `HH:mm:ss`。
表内 `ID` 仅校验，不额外成为文件列；其它非映射字段不导出。

不更新源表，不新增运行表，不修改 BCO 的 `BATCH_PROCESSED_DATE`。
本次维持原有功能边界：不生成 BCO `.dat/.eof`，不加入 SFTP、Control-M 或失败补发机制。

## 检查与测试

```sh
python3 /HTHBatch/tools/verify_hth_crm_csv.py \
  /HTHBatch/output/hthtomtb_mvp1apis_D261007.csv 20261007
```

检查日志 `HTH_CRM_1293 stage=END status=SUCCESS`，核对 `count`、`size`、`file`。
失败日志包含 `phase`、异常类型、SQLState / Oracle 错误码；不打印 JDBC URL、密码或记录内容。
配置缺项会另列配置键名。`phase=CONFIG` 排查配置目录和环境变量；`phase=EXTRACT` 排查
数据库权限/字段、数据格式、输出目录及文件锁。失败日志 `count=0 size=-1` 表示未发布新文件。

```sql
SELECT COUNT(*) AS DB_ROWS, COUNT(DISTINCT ID) AS DISTINCT_IDS
FROM HTH_BEA.HTH_CRM_EVENT
WHERE CREATION_DATE >= TO_DATE('2026-10-07', 'YYYY-MM-DD')
  AND CREATION_DATE <  TO_DATE('2026-10-08', 'YYYY-MM-DD');
```

DB_ROWS 应等于尾行 N、日志 count、校验器 data rows；与 DISTINCT_IDS 一起检查源数据。
联调时在抽取完成后、源数据不再变动的同一日期核对。

自动测试覆盖：H2 Oracle 模式下执行真实查询和日期边界、稳定排序、BCO 哨兵表不受影响、
0/1/多笔、UTF-8/转义、同日重跑、半途失败、锁冲突、配置和实际 Java 子进程退出码。
`src/test/resources/golden` 保存从迁移前 HTH 作业生成的合成数据文件，新旧输出逐字节比对。
Python 校验器另覆盖首尾行、笔数/checksum、格式损坏等情况。
H2 不等同于生产 Oracle；部署后的 Oracle 权限、实际数据及目标服务器文件系统仍须 SIT 联调。

## 从 BCO 托管切换

1. 新工程构建和部署到 HTH 安装目录，准备独立配置及 Java 21。
2. 用指定历史日期运行并验证文件、笔数、首尾行，确认输出目录交接。
3. 切换调用方到新 HTH 脚本，停用旧 `/CDCBatch/sh/GenHthCrmExtract.sh` 调用，避免两处重复抽取。
4. BCO 仓库中只删除 HTH 的三套 Java/shell 和配套旧测试；BCO 原作业、Ant 构建及公共类不改。
   BCO Ant 构建已有清理步骤，新构建不再包含旧 HTH class；服务器上的旧脚本须随切换停用。

本次源码迁移不直接更改远程服务器或调度配置。
