# BCOH2H-1293：HTH/DSP CRM 每日抽取（SIT 手动执行）

本作业只读取 `HTH_BEA.HTH_CRM_EVENT`，不调用 BCO 的 `GenTxnLog2CRM`，
不修改 `DIGX_CZ_CRM_EVENT3_DETAILS` 或其 `BATCH_PROCESSED_DATE`。
按照 `GenTxnLog2CRM` 的仓库布局，Java 类及启动脚本在外层、`UAT`、`PRD`
各保留一份相同内容。现有 pipeline 实际从 `PRD` 编译；更新 HTH 作业时
必须同步三份，避免 SIT 和生产部署取到不同版本。现有
`consulting/ant/build_batch_Jenkins.xml` 的 `outboundbatchprocessor/**/*.java`
会将它编入 `CDCBatchesUAT.jar`。现有 Ant 构建**只生成 JAR**；新 shell
脚本仍须由部署步骤或 SIT 人员复制到批处理服务器。

## 服务器准备

1. 在 BCO batch 服务器确认新版 `/CDCBatch/lib/CDCBatchesUAT.jar` 含有
   `outboundbatchprocessor/hth/HthCrmExtractJob.class`：
   `jar tf /CDCBatch/lib/CDCBatchesUAT.jar | grep HthCrmExtractJob`。
2. 复制本目录对应的 `PRD/Deployables/sh/GenHthCrmExtract.sh` 到
   `/CDCBatch/sh/GenHthCrmExtract.sh`，并执行
   `chmod 750 /CDCBatch/sh/GenHthCrmExtract.sh`。
3. BCO 的 `/CDCBatch/sh/Env.sh` 导出 `batchConfigPath=/CDCBatch/config`，
   `GenericOutBoundProcessor` 从该目录的 `batch_config.properties` 读取 AES 加密的
   `encr_passphrase`、`db.username`、`db.password`、`db.hostname`、`db.port`、
   `db.servicename`。HTH 使用**相同的加密/解密方式**，但从同一目录的独立文件
   `/CDCBatch/config/hth_crm_batch_config.properties` 读取这些同名字段。
   由运维按现有 BCO 配置流程生成 HTH 数据库的加密值并部署该文件，权限仅授予
   batch 作业用户；不要复制 BCO 的数据库密文，也不要把明文密码写进脚本、
   命令行或 Git。HTH 数据库用户需要 `HTH_BEA.HTH_CRM_EVENT` 的 `SELECT` 权限。
   新配置文件缺失或缺少字段时作业会失败，不会回退到 BCO 数据库。可选
   `HTH_CRM_OUTPUT_DIR`；未设置时写入 `/project/CDC/ftp/snd/hth`，与 BCO 文件分开。
4. 默认文件名按 v16 Data Extraction 使用 `hthtomtb_mvp1apis_Dyymmdd.csv`。
   保留最新同步版本的 `HTH_CRM_FILE_PATTERN` 规则：必须包含 `{date}`，
   替换值为 **yyMMdd（6 位）**，且以 `.csv` 结尾。例如 `HTH_CRM_{date}.csv`
   在业务日 20261007 输出 `HTH_CRM_261007.csv`。采用文档文件名时可取消
   override，或设置为 `hthtomtb_mvp1apis_D{date}.csv`。
   不使用上一版补丁的 `{shortDate}`。命令行日期和文件内首行日期仍为 8 位。

## 手动执行

登录**部署 `/CDCBatch` 的 BCO batch 服务器**，确认上述 HTH 加密配置文件已部署后执行：

```sh
sh /CDCBatch/sh/GenHthCrmExtract.sh             # 昨天（香港时间）
sh /CDCBatch/sh/GenHthCrmExtract.sh 20261007    # 指定业务日，重跑同日文件
echo $?                                         # 0 成功；50 失败
```

脚本首先打印本次日志路径，然后将详细输出写入
`/project/CDC/log/GenHthCrmExtract.<运行时间>.log`。
保留最新版本的 `stage=END` 日志字段 `size`：成功时是完整文件的字节数，
包含首行、列名、数据、尾行和换行；失败前未取得大小时为 `-1`。
它与尾行的 `file_size` 不同，后者按文档固定填 15 个空格。
例如日期 `20261007` 的默认输出是
`/project/CDC/ftp/snd/hth/hthtomtb_mvp1apis_D261007.csv`。
失败时不会替换既有的同日文件。同日成功重跑会原子替换同一文件，不会改其他日期。
`.lock` 是并发控制文件，不是交给 MTB 的数据文件。

## 文件与检查

1293 AC 要求 **CSV**；BCO spike 中的 `.dat` 定宽文件只用于参考
batch 服务器、构建和运行链路，不能直接作为本作业的输出格式。
本作业按 `HTH MVP1 MTB - Implementation plan-v16-20261027_165453.docx`
的 Data Extraction 增加首尾控制行，并保留原有列名行和 61 列业务数据。
文件采用 UTF-8、LF 换行，无 BOM，顺序如下：

1. `00` 首行：定长 100 个 ASCII 字符（不计 LF）。
2. 原有 61 列列名行（`API_MTB_latest.xlsx` 的 `HTH_CRM_MAPPING_APIs` 第 12–72 行）。
3. N 行业务数据，仍为 61 列 CSV，以 `"50","1",...` 开头。
4. `99` 尾行：定长 100 个 ASCII 字符（不计 LF）。

| 控制行 | 位置（从 1 开始） | 内容 |
| --- | --- | --- |
| 首行 | 1–2 / 3–10 / 11–13 / 14–16 | `00` / 抽取业务日期 yyyyMMdd / `HTH` / `MTB` |
| 首行 | 17–19 / 20–22 / 23–100 | 3 个空格 / 3 个空格 / 78 个空格 |
| 尾行 | 1–2 / 3–17 / 18–32 | `99` / N（15 位，左补零）/ yyyyMMdd 数值加 N（15 位，左补零） |
| 尾行 | 33–47 / 48–100 | file_size 为 15 个空格 / 53 个空格 |

首尾行不加引号或逗号，尾部空格不可裁剪。N 只统计业务数据，不包括首行、
列名行、尾行；总物理行数为 N + 3。零数据文件有 3 行，尾行 count 为 0，
checksum 为业务日期数值左补零。文件名、首行和 checksum 全部使用传入的
抽取日期，历史日期重跑也不使用实际运行日期。例如日期 20250106、N=2 时，
checksum 为 `000000020250108`。

列名和业务数据的每个字段用双引号
包围，字段内的反斜线、双引号、CR、LF 分别写成 `\\`、`\"`、`\r`、`\n`，
符合 `MTB Interface File Spec.xlsx` 的 CSV escape 约定。
首行、列名、数据、尾行全部写入同一个临时文件，成功后才原子替换正式文件；
中途失败不会发布缺少尾行的文件。没有生成 BCO `.eof`，因为 1293 的
传输/Control-M 对接在后续 story；不能把测试文件自动推送到 MTB。

```sh
f=/project/CDC/ftp/snd/hth/hthtomtb_mvp1apis_D261007.csv
ls -l "$f"
file -bi "$f"                           # 应为 UTF-8 / text
tail -n 3 /project/CDC/log/GenHthCrmExtract.*.log
python3 /CDCBatch/sh/verify_hth_crm_csv.py "$f" 20261007
```

将仓库 `consulting/middleware/batchJobs/tests/hth/verify_hth_crm_csv.py`
复制到上述服务器路径后，校验脚本会检查首尾行长度、字段、空格、抽取日期、
count/checksum，以及中间 CSV 的 61 列、列名、引号/转义和 LF。
最后的 yyyyMMdd 参数可省略；建议显式传入以核对业务日。不要用旧的
“总行数减 1”或“每行都有 61 列”检查方式。数据库再核对同一天的笔数：

```sql
SELECT COUNT(*) AS DB_ROWS, COUNT(DISTINCT ID) AS DISTINCT_IDS
FROM HTH_BEA.HTH_CRM_EVENT
WHERE CREATION_DATE >= TO_DATE('2026-10-07', 'YYYY-MM-DD')
  AND CREATION_DATE <  TO_DATE('2026-10-08', 'YYYY-MM-DD');
```

`DB_ROWS` 应等于校验脚本的 data rows、日志的 `count`、尾行的 record_count 和
`DISTINCT_IDS`；`wc -l` 应为 `DB_ROWS + 3`。可按 `ID` 查一笔记录，再用列名在 CSV 中比对值。
数据库 `CREATION_DATE` 必须按香港业务时间存储；运行前与 HTH/DSP 团队确认。
此次不新增数据库表或 SQL，不修改 BCO 抽取逻辑、配置或 shell。
外层、UAT、PRD 统一采用最新外层代码的加密配置读取方式，与三份现有
启动脚本一致；不再使用旧 UAT 类中的 `HTH_CRM_JDBC_*` 环境变量方式。

## 本地回归

`tests/hth/HthCrmExtractJobTest.java` 使用 JDBC 测试驱动运行实际抽取和文件发布逻辑，
覆盖 0/1/多笔、历史日期、重复执行、UTF-8/CSV 转义、字段溢出和失败保护。
使用 Java 8 或 `javac --release 8` 编译该测试、HTH 抽取类及既有
`util/AESUtil.java`、`util/Base64.java`、`util/LoadProperties.java`，运行
`outboundbatchprocessor.hth.HthCrmExtractJobTest`。对输出的 `DATA_CSV`、
`MULTI_CSV`、`CSV` 路径运行上述 Python 校验器。
另可运行 `python3 -m unittest discover -s consulting/middleware/batchJobs/tests/hth -p 'test_*.py'`，
验证损坏或缺失控制行、错误笔数/校验值等会被拒绝。真实 Oracle/AIX 联调仍需 SIT 执行。
