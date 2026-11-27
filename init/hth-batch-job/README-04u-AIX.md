# HTH Batch 04u AIX 手工部署

## Git 与 04u 的目录

从 `hth-batch-job/` 选取下面的运行文件，按**相同相对路径**上传到 `/HTHBatch/`。

```text
hth-batch-job/                    /HTHBatch/
  conf/
    Env.sh
    HTHBatchEnv.sh
  hth-mtb/
    bin/
    config/
    tools/
    lib/                          Maven 打包后准备
  hth-eAdvice/
    bin/
    config/
    tools/
    lib/                          Maven 打包后准备
    transfer/windows/             Windows 传输节点使用
  README-04u-AIX.md
```

公共 Oracle 密钥文件 `conf/bco-db-key.env` 由 04u 的 batch 运行账号维护。根目录 `tools/` 存放公司电脑上的打包脚本和测试。

## 在 IntelliJ IDEA 构建

1. 在公司电脑从 Git 拉取完整的 `hth-batch-job`，用 **File → Open** 打开它的父 `pom.xml`。
2. 在 IDEA 的 **Project SDK** 和 **Maven Runner JRE** 都选 JDK 21。公司 Maven 镜像使用现有的 `settings.xml`。
3. 在 Maven 运行配置里选择 `hth-batch-job` 作为项目目录，执行以下目标，等待 `BUILD SUCCESS`。 [IntelliJ IDEA Maven 配置](https://www.jetbrains.com/help/idea/run-debug-configuration-maven.html)

```text
-pl hth-mtb,hth-eAdvice clean verify
```

Maven 在两个模块的 `target/` 生成可执行 JAR。手工上传时，将 `hth-batch-job-mtb-1.0.0.jar` 和 `hth-eAdvice-1.0.0.jar` 分别放进各模块的 `lib/`。也可以使用 PowerShell 一次准备好完整的上传目录。

```powershell
.\tools\prepare-upload.ps1
```

PowerShell 输出 `target\04u-upload\full-<时间>\`。其中的文件可以按原路径上传到 `/HTHBatch/`，包括已放进 `lib/` 的两个 JAR。清单 `tools/upload-files.txt` 同时用于 PowerShell 和 tar 打包。

### 可选的 tar 包

从项目根目录执行以下命令。Windows 可在 Git Bash 里执行。输出目录为 `target/04u-aix/<commit>/`，内含公共 `conf/`、两个模块 tar 包、部署说明和 `SHA256SUMS`。

```sh
sh tools/package.sh
```

## 已部署环境升级外部 Oracle 密文读取

升级 `hth-mtb` 和 `hth-eAdvice` 的两个 JAR。用 PowerShell 生成本次上传目录。

```powershell
.\tools\prepare-upload.ps1 -JarsOnly
```

在 04u 创建暂存目录。

```sh
mkdir -p /HTHBatch/jar-update
```

将脚本打印的 `target\04u-upload\jars-<时间>\` 中的两个模块目录和 `SHA256SUMS` 上传到该目录，再校验。

```sh
cd /HTHBatch/jar-update
sha256sum -c SHA256SUMS
```

如果没有 `sha256sum`，可用 `openssl dgst -sha256` 计算两个 JAR 的值并对照 `SHA256SUMS`。校验通过且两个作业处于空闲状态后，使用具有安装目录写权限的 batch 部署账号执行。

```sh
STAGE=/HTHBatch/jar-update
MTB=hth-mtb/lib/hth-batch-job-mtb-1.0.0.jar
EA=hth-eAdvice/lib/hth-eAdvice-1.0.0.jar
BACKUP=$(date +%Y%m%d%H%M%S)

cd /HTHBatch &&
cp -p "$MTB" "$MTB.bak.$BACKUP" &&
cp -p "$EA" "$EA.bak.$BACKUP" &&
cp "$STAGE/$MTB" "$MTB.new.$$" &&
cp "$STAGE/$EA" "$EA.new.$$" &&
chmod 644 "$MTB.new.$$" "$EA.new.$$" &&
mv "$MTB.new.$$" "$MTB" &&
mv "$EA.new.$$" "$EA"
```

按 [Oracle 配置](#配置-oracle) 更新外部 JDBC 文件并提供包装密钥。若 eAdvice 使用 `uat-config`，更新该目录下的 JDBC 文件。使用新目录结构时，同时上传公共 `conf/Env.sh` 和两个模块的启动脚本。

## 在 04u 安装 JRE 21

04u 用 IBM Semeru Certified Edition JRE 21.0.13.0（AIX ppc64）运行两个作业。把 JRE 压缩包上传到 `/HTHBatch`，确认文件大小为 70,406,433 字节。 [JRE 安装包](https://github.com/ibmruntimes/semeru21-certified-binaries/releases/download/jdk-21.0.13.0/ibm-semeru-certified-jre_ppc64_aix_21.0.13.0.tar.gz) · [SHA-256 校验文件](https://github.com/ibmruntimes/semeru21-certified-binaries/releases/download/jdk-21.0.13.0/ibm-semeru-certified-jre_ppc64_aix_21.0.13.0.tar.gz.sha256.txt)

```sh
cd /HTHBatch
JRE=ibm-semeru-certified-jre_ppc64_aix_21.0.13.0.tar.gz
wc -c < "$JRE"
gunzip -t "$JRE" && gunzip -c "$JRE" | /usr/bin/tar xf -
/HTHBatch/jdk-21.0.13.0-jre/bin/java -version
```

解压目录为 `/HTHBatch/jdk-21.0.13.0-jre`。验证输出中的版本号应以 `21.0.13` 开头。 [IBM AIX 解包说明](https://www.ibm.com/support/pages/semeru-runtimes-installation)

## 上传和更新

首次部署时，上传上述运行目录，并用有安装权限的 batch 账号设置脚本权限。

```sh
chmod 640 /HTHBatch/conf/Env.sh /HTHBatch/conf/HTHBatchEnv.sh
chmod 750 /HTHBatch/hth-mtb/bin/*.sh /HTHBatch/hth-eAdvice/bin/*.sh
```

已安装的环境在两个作业空闲时先上传 `conf/Env.sh`，再按原路径更新两个模块的 `bin/` 和 `tools/`。沿用服务器已配置的 `conf/HTHBatchEnv.sh`、密钥文件和两个模块的 `config/`。验证两个作业后，可以归档各模块旧的 `bin/Env.sh`。如需更新 JAR，参照前面的独立 JAR 更新步骤。

使用 tar 时，将两个包和公共 `conf/` 上传到一个暂存目录。将 `RELEASE` 替换成包文件名末尾的提交号，在暂存目录校验并解包。

```sh
RELEASE=替换成提交号
sha256sum -c SHA256SUMS &&
    tar -xf "hth-mtb-04u-aix-${RELEASE}.tar" &&
    tar -xf "hth-eAdvice-04u-aix-${RELEASE}.tar"
```

如果没有 `sha256sum`，可以用 `openssl dgst -sha256 conf/*.sh ./*.tar` 对照 `SHA256SUMS`。再把解包后的运行文件按原路径上传或复制到 `/HTHBatch/`。

## 公共环境

两个作业启动时都会读取 `/HTHBatch/conf/HTHBatchEnv.sh`。在这里维护共用的 `HTH_JAVA_HOME` 和需要共用的 `JVM_OPTS`。各作业自己的 `CONFIG_DIR`、日志路径和输出目录由各自的启动环境或 `config/` 管理。

JRE 的默认路径是 `/HTHBatch/jdk-21.0.13.0-jre`。已部署环境可以在现有公共文件中更新 `HTH_JAVA_HOME`。

```sh
HTH_JAVA_HOME=/HTHBatch/jdk-21.0.13.0-jre
export HTH_JAVA_HOME
```

加载公共配置并确认两个作业使用的 Java 路径和版本。

```sh
. /HTHBatch/conf/HTHBatchEnv.sh
printf '%s\n' "$HTH_JAVA_HOME"
"$HTH_JAVA_HOME/bin/java" -version
```

两个模块的启动脚本共用 `/HTHBatch/conf/Env.sh`。由它统一加载密钥。如果以前在 `HTHBatchEnv.sh` 里手工加过读取密钥文件的命令，可以移除该行。

## 配置 Oracle

### 在现有 HTH 配置中使用密文字段

将原先 `hth_crm_batch_config.properties` 的内容追加到 04u 上两个模块的外部 JDBC 配置，保留原字段名。启动脚本通过 `CONFIG_DIR` 指定配置目录，默认使用各模块安装目录下的 `config/`。

```sh
vi /HTHBatch/hth-mtb/config/db.ibank.batch.properties
vi /HTHBatch/hth-eAdvice/config/db.ibank.batch.properties
chmod 600 /HTHBatch/hth-mtb/config/db.ibank.batch.properties \
          /HTHBatch/hth-eAdvice/config/db.ibank.batch.properties
```

加密模式使用 `encr_passphrase`、`db.hostname`、`db.port`、`db.servicename`、`db.username` 和 `db.password`。原文件的 `db.ext.*` 字段也可随内容复制。保留 HTH 原有的 JDBC 驱动和 `jdbc.conn.autocommit=false` 设置。应用识别 BCO 字段后，解密得到的 URL、用户名和密码优先用于 JDBC。

两个作业优先使用调度环境已注入的 `HTH_BATCH_BCO_WRAPPING_KEY`。也可以由 batch 运行账号在 04u 上维护同一份 `/HTHBatch/conf/bco-db-key.env`。文件所有者为 batch 运行账号，权限为 `600`。

```sh
umask 077
key_file=/HTHBatch/conf/bco-db-key.env
touch "$key_file" && chmod 600 "$key_file" && vi "$key_file"
```

写入下面这行，将占位文字替换为解开 `encr_passphrase` 的原始包装密钥。

```sh
export HTH_BATCH_BCO_WRAPPING_KEY='实际包装密钥'
```

启动脚本会自动读取这个文件，重新登录后可以直接运行。若设置了 `HTH_BATCH_COMMON_ENV`，密钥文件放在该公共脚本同目录。密钥文件由 04u 或调度系统管理。

### 使用 JDBC 环境变量

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

各模块的 `config/db.ibank.batch.properties` 已引用这三个变量，保留其中的 `jdbc.conn.autocommit=false`。采用这个方式手工测试时，先在同一个终端用 `. /HTHBatch/conf/mtb-db.env` 或 `. /HTHBatch/conf/eadvice-db.env` 加载对应的配置，再执行下方的启动命令。

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
sh /HTHBatch/hth-mtb/bin/GenHthCrmExtract.sh "$run_date"
printf 'MTB exit=%s\n' "$?"

mtb_file="/HTHBatch/hth-mtb/output/hthtomtb_mvp1apis_D${run_date#??}.csv"
ls -l "$mtb_file"
LC_ALL=C awk -v day="$run_date" \
    -f /HTHBatch/hth-mtb/tools/verify_hth_crm_csv.awk "$mtb_file"
```

脚本打印本次运行日志的完整路径。退出码应为 `0`，日志应有 `HTH_CRM_1293 stage=END status=SUCCESS`。AIX 上用 `awk` 核对首尾控制记录、业务笔数和尾行校验值。把输出的 `MTB data rows`、日志的 `count` 与同一业务日的数据库笔数对照。若要逐字段验证 CSV 格式，可将文件交给开发环境的 `hth-mtb/tools/verify_hth_crm_csv.py` 检查。

```sql
SELECT COUNT(*)
FROM HTH_BEA.HTH_CRM_EVENT
WHERE CREATION_DATE >= DATE '2026-11-26'
  AND CREATION_DATE <  DATE '2026-11-27';
```

## 启动和验证 eAdvice

四个调度入口安装在 `/HTHBatch/hth-eAdvice/bin/`。每个入口只接收可选的业务日 `yyyyMMdd`，沿用同一个 `EAdviceBatch.sh`、JAR 和配置。也可以继续使用原有的 `EAdviceBatch.sh <1|2|3|P> [yyyyMMdd]` 命令。

| 启动脚本 | 作业 | 输出档 |
| --- | --- | --- |
| `genEAdvice2EBK1.sh` | `1` | `A` |
| `genEAdvice2EBK2.sh` | `2` | `B` |
| `genEAdvice2EBK3.sh` | `3` | `C` |
| `genPaperAdvice2EBK.sh` | `P` | `P` |

已有 eAdvice 安装可以把这四个文件加到现有 `bin/`，并设置权限为 `750`。

首次用隔离目录和专用测试交易验证。作业会回写 `BATCH_STATUS_FLAG`。作业 1、2、3 共用成功 FPS 的取数范围，每次验证准备对应的未处理测试交易。

```sh
mkdir -p /HTHBatch/hth-eAdvice/uat-output
vi /HTHBatch/hth-eAdvice/config/system_batch.properties
```

把其中的 `batch.output.dir` 改为 `/HTHBatch/hth-eAdvice/uat-output`。以下命令以作业 3 为例。

```sh
run_date=20261126
sh /HTHBatch/hth-eAdvice/bin/genEAdvice2EBK3.sh "$run_date"
printf 'eAdvice exit=%s\n' "$?"

advice_file="/HTHBatch/hth-eAdvice/uat-output/EBKP.HOSTPC.HTHTOEBK.EBFPADV.C.D${run_date}.TXT"
ls -l "$advice_file"
LC_ALL=C awk -v day="$run_date" \
    -f /HTHBatch/hth-eAdvice/tools/verify_eadvice.awk "$advice_file"
```

退出码应为 `0`，本次日志应有 `HTH_EADVICE stage=END status=SUCCESS`。核对文件控制行和业务笔数，再用实际测试交易的 `PAYMENT_ID` 查询。

```sql
SELECT PAYMENT_ID, BATCH_STATUS_FLAG
FROM HTH_BEA.HTH_PAYMENT_PAYOUT
WHERE PAYMENT_ID = '替换为测试交易的 PAYMENT_ID';
```

作业 3 成功处理测试交易后应追加标记 `3`。同日已有文件且本次无待处理交易时，作业保留原文件并返回 `0`。有待处理交易时保留原文件和未处理标记，返回 `50`。传输只读取最终的 `.csv` 和 `.TXT` 文件。eAdvice 包内 `transfer/windows/` 的 WSF 由 Windows 传输节点使用。
