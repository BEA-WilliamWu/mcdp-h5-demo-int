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
3. 由运维在**HTH 作业的受控环境**提供
   `HTH_CRM_JDBC_URL`、`HTH_CRM_JDBC_USER`、`HTH_CRM_JDBC_PASSWORD`。
   数据库用户需要 `HTH_BEA.HTH_CRM_EVENT` 的 `SELECT` 权限。
   不要把密码写进脚本或命令行。可选 `HTH_CRM_OUTPUT_DIR`；未设置时
   写入 `/project/CDC/ftp/snd/hth`，与 BCO 的文件分开。
4. 可选 `HTH_CRM_FILE_PATTERN`，必须包含 `{date}` 且以 `.csv` 结尾。
   测试默认名为 `HTH_CRM_{date}.csv`；正式文件名需由接收方确认。

## 手动执行

登录**部署 `/CDCBatch` 的 BCO batch 服务器**，在已注入上述 HTH 环境变量的
会话中执行：

```sh
sh /CDCBatch/sh/GenHthCrmExtract.sh             # 昨天（香港时间）
sh /CDCBatch/sh/GenHthCrmExtract.sh 20261007    # 指定业务日，重跑同日文件
echo $?                                         # 0 成功；50 失败
```

脚本首先打印本次日志路径，然后将详细输出写入
`/project/CDC/log/GenHthCrmExtract.<运行时间>.log`。
例如日期 `20261007` 的默认输出是
`/project/CDC/ftp/snd/hth/HTH_CRM_20261007.csv`。
失败时不会替换既有的同日文件。同日成功重跑会原子替换同一文件，不会改其他日期。
`.lock` 是并发控制文件，不是交给 MTB 的数据文件。

## 文件与检查

1293 AC 要求 **CSV**；BCO spike 中的 `.dat` 定宽文件只用于参考
batch 服务器、构建和运行链路，不能直接作为本作业的输出格式。
本作业使用 UTF-8、LF 换行、61 列（`API_MTB_latest.xlsx` 的
`HTH_CRM_MAPPING_APIs` 第 12–72 行），第一行是列名。每个字段用双引号
包围，字段内的反斜线、双引号、CR、LF 分别写成 `\\`、`\"`、`\r`、`\n`，
符合 `MTB Interface File Spec.xlsx` 的 CSV escape 约定。
零数据日生成只有一行列名的 CSV。没有生成 BCO `.eof`，因为 1293 的
传输/Control-M 对接在后续 story；不能把测试文件自动推送到 MTB。

```sh
f=/project/CDC/ftp/snd/hth/HTH_CRM_20261007.csv
ls -l "$f"
file -bi "$f"                           # 应为 UTF-8 / text
tail -n 3 /project/CDC/log/GenHthCrmExtract.*.log
python3 /CDCBatch/sh/verify_hth_crm_csv.py "$f"
```

将仓库 `consulting/middleware/batchJobs/tests/hth/verify_hth_crm_csv.py`
复制到上述服务器路径后，校验脚本会输出数据行数并检查 61 列、表头、
CSV 引号/转义和 LF。数据库再核对同一天的笔数：

```sql
SELECT COUNT(*) AS DB_ROWS, COUNT(DISTINCT ID) AS DISTINCT_IDS
FROM HTH_BEA.HTH_CRM_EVENT
WHERE CREATION_DATE >= TO_DATE('2026-10-07', 'YYYY-MM-DD')
  AND CREATION_DATE <  TO_DATE('2026-10-08', 'YYYY-MM-DD');
```

`DB_ROWS` 应等于校验脚本的 data rows、日志的 `count` 和
`DISTINCT_IDS`。可按 `ID` 查一笔记录，再用列名在 CSV 中比对值。
数据库 `CREATION_DATE` 必须按香港业务时间存储；运行前与 HTH/DSP 团队确认。
正式文件名、零数据文件规则，以及接收方是否要求额外 `00/99` 记录，
仍需接口方确认；当前版本用于 SIT 数据与格式验证。
