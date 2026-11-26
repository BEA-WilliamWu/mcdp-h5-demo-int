# HTH Batch 04u AIX 手工部署

## 构建部署包

### 1. 在公司电脑准备源码

同步 Git 中的整个 `hth-batch-job` 目录，或解压交付的 `hth-batch-job-source-<commit>.zip`。保留目录下的父 `pom.xml`、两个模块、`bin/`、`deploy/`、`sftp/` 和 `tools/`。源码 ZIP 自带 `SOURCE_REVISION`，打包时用它给产物命名。

### 2. 在 IntelliJ IDEA 构建

1. 用 **File → Open** 打开 `hth-batch-job/pom.xml`，加载 Maven 项目。确认 Maven 窗口显示 `hth-mtb` 和 `hth-eAdvice`。
2. 在 **File → Project Structure → Project SDK** 选择公司电脑上的 JDK 21。在 **Settings → Build, Execution, Deployment → Build Tools → Maven → Runner** 的 **JRE** 也选 JDK 21。若依赖走公司 Maven 镜像，在 Maven 设置的 **User settings file** 选择公司的 `settings.xml`。
3. 在 **Run → Edit Configurations → + → Maven** 新建配置。项目路径选 `hth-batch-job`，**Run** 填下面的参数。执行后等到 `BUILD SUCCESS`。 [IntelliJ IDEA Maven 配置](https://www.jetbrains.com/help/idea/run-debug-configuration-maven.html)

```text
-pl hth-mtb,hth-eAdvice clean verify
```

Maven 会运行单元测试，然后生成 `hth-mtb/target/hth-batch-job-mtb-1.0.0.jar` 和 `hth-eAdvice/target/hth-eAdvice-1.0.0.jar`。两个 JAR 都包含运行所需的依赖。

### 3. 生成 04u 的 tar 包

在同一个 `hth-batch-job` 目录进入终端。Windows 用 Git Bash，可在 IDEA 的 Terminal 中打开 Git Bash。macOS 和 Linux 可用各自的终端。

```sh
sh deploy/04u/package.sh
```

如果要在终端完成构建，也可先执行 `mvn -B -pl hth-mtb,hth-eAdvice clean verify`，再运行上面的打包命令。
打包命令会打印产物目录 `target/04u-aix/<commit>/`。将两个 `.tar`、`HTHBatchEnv.sh` 和 `SHA256SUMS` 上传到 04u。该目录还包含部署说明 `README-04u-AIX.md`。

## 上传和解包

将两个 `.tar`、`HTHBatchEnv.sh` 和 `SHA256SUMS` 上传到 `/HTHBatch`。以下命令由 batch 运行账号执行。
`RELEASE` 使用包文件名末尾的提交号。

```sh
cd /HTHBatch
RELEASE=替换成提交号
sha256sum -c SHA256SUMS
```

没有 `sha256sum` 时，用 `openssl dgst -sha256 HTHBatchEnv.sh "hth-mtb-04u-aix-${RELEASE}.tar" "hth-eAdvice-04u-aix-${RELEASE}.tar"` 并逐项比对 `SHA256SUMS`。校验通过后执行首次解包。

```sh
tar -xf "hth-mtb-04u-aix-${RELEASE}.tar"
tar -xf "hth-eAdvice-04u-aix-${RELEASE}.tar"
mkdir -p /HTHBatch/conf
if [ ! -f /HTHBatch/conf/HTHBatchEnv.sh ]; then
    cp HTHBatchEnv.sh /HTHBatch/conf/HTHBatchEnv.sh
fi
. /HTHBatch/conf/HTHBatchEnv.sh
"$HTH_JAVA_HOME/bin/java" -version
```

升级时先解到暂存目录，核对并保留各模块的 `config/`，再按调度窗口切换 `/HTHBatch/hth-mtb` 和 `/HTHBatch/hth-eAdvice`。公共配置留在 `/HTHBatch/conf`。

## 配置 Oracle

通过调度系统注入以下三个环境变量，或在交互测试时创建两份仅运行账号可读的环境文件。

```sh
umask 077
vi /HTHBatch/conf/mtb-db.env
vi /HTHBatch/conf/eadvice-db.env
chmod 600 /HTHBatch/conf/mtb-db.env /HTHBatch/conf/eadvice-db.env
```

两份文件格式相同，各填对应的 04u 数据库地址、账号和密码。

```sh
export HTH_BATCH_JDBC_URL='jdbc:oracle:thin:@//实际主机:实际端口/实际service'
export HTH_BATCH_JDBC_USER='实际账号'
export HTH_BATCH_JDBC_PASSWORD='实际密码'
```

各模块的 `config/db.ibank.batch.properties` 已引用这三个变量，保留其中的 `jdbc.conn.autocommit=false`。真实凭据只存放在 04u 的受限文件或调度配置中。

| 作业 | JDBC 权限 |
| --- | --- |
| MTB | 读取 `HTH_BEA.HTH_CRM_EVENT` |
| eAdvice | 读取 `HTH_BEA.HTH_PAYMENT_PAYOUT`，更新 `BATCH_STATUS_FLAG` |
| eAdvice 作业 P | 另需读取 `DIGX_PI_PARTY_ACCOUNTS` 和 `DIGX_CZ_AC_EACCTPREF`，由连接 schema 或 synonym 解析 |

各模块的输出路径由 `config/system_batch.properties` 的 `batch.output.dir` 决定。默认 `./output` 相对于模块目录，日志默认在模块的 `logs/`。部署账号须能在输出目录创建文件和锁，目录须支持文件锁和同目录原子重命名。

## 启动和验证 MTB

下面用 `20261126` 举例。执行前替换为 04u 有测试数据的业务日。

```sh
run_date=20261126
(
    . /HTHBatch/conf/mtb-db.env &&
    sh /HTHBatch/hth-mtb/bin/GenHthCrmExtract.sh "$run_date"
)
printf 'MTB exit=%s\n' "$?"

mtb_file="/HTHBatch/hth-mtb/output/hthtomtb_mvp1apis_D${run_date#??}.csv"
ls -l "$mtb_file"
LC_ALL=C awk -v day="$run_date" \
    -f /HTHBatch/hth-mtb/tools/verify_hth_crm_csv.awk "$mtb_file"
```

脚本打印本次运行日志的完整路径。退出码应为 `0`，日志应有 `HTH_CRM_1293 stage=END status=SUCCESS`。AIX 上用 `awk` 核对首尾控制记录、业务笔数和尾行校验值。把输出的 `MTB data rows`、日志的 `count` 与同一业务日的数据库笔数对照。若要逐字段验证 CSV 格式，可将文件交给开发环境的 `tools/verify_hth_crm_csv.py` 检查。

```sql
SELECT COUNT(*)
FROM HTH_BEA.HTH_CRM_EVENT
WHERE CREATION_DATE >= DATE '2026-11-26'
  AND CREATION_DATE <  DATE '2026-11-27';
```

## 启动和验证 eAdvice

首次用隔离目录和专用测试交易验证。作业会回写 `BATCH_STATUS_FLAG`。作业 `1`、`2`、`3`、`P` 分别生成 `A`、`B`、`C`、`P` 档。作业 1、2、3 共用成功 FPS 的取数范围，每次验证准备对应的未处理测试交易。

```sh
mkdir -p /HTHBatch/hth-eAdvice/uat-output
vi /HTHBatch/hth-eAdvice/config/system_batch.properties
```

把其中的 `batch.output.dir` 改为 `/HTHBatch/hth-eAdvice/uat-output`。以下命令以作业 3 为例。

```sh
run_date=20261126
(
    . /HTHBatch/conf/eadvice-db.env &&
    sh /HTHBatch/hth-eAdvice/bin/EAdviceBatch.sh 3 "$run_date"
)
printf 'eAdvice exit=%s\n' "$?"

advice_file="/HTHBatch/hth-eAdvice/uat-output/EBKP.HOSTPC.HTHTOEBK.EBFPADV.C.D${run_date}.TXT"
ls -l "$advice_file"
awk -v day="$run_date" '
    NR == 1 { header_ok = (substr($0, 1, 10) == "01" day) }
    substr($0, 1, 2) == "02" { rows++ }
    END {
        count = substr($0, 3, 8)
        checksum = substr($0, 11, 10)
        if (!header_ok || NR != rows + 2 || substr($0, 1, 2) != "99" ||
            count !~ /^[0-9]+$/ || length(count) != 8 || count + 0 != rows ||
            checksum !~ /^[0-9]+$/ || length(checksum) != 10 || checksum + 0 != day + rows) exit 1
        printf "eAdvice data rows=%d\n", rows
    }
' "$advice_file"
```

退出码应为 `0`，本次日志应有 `HTH_EADVICE stage=END status=SUCCESS`。核对文件控制行和业务笔数，再用实际测试交易的 `PAYMENT_ID` 查询。

```sql
SELECT PAYMENT_ID, BATCH_STATUS_FLAG
FROM HTH_BEA.HTH_PAYMENT_PAYOUT
WHERE PAYMENT_ID = '替换为测试交易的 PAYMENT_ID';
```

作业 3 成功处理测试交易后应追加标记 `3`。同日已有文件且本次无待处理交易时，作业保留原文件并返回 `0`。有待处理交易时保留原文件和未处理标记，返回 `50`。传输只读取最终的 `.csv` 和 `.TXT` 文件。eAdvice 包内 `transfer/windows/` 的 WSF 由 Windows 传输节点使用。
