# HTH Batch 04u AIX 手工部署

## 构建与准备上传

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

### 3. 在 Windows 生成上传目录

Maven 构建成功后，在 IDEA Terminal 的 PowerShell 中进入父 `pom.xml` 所在目录。首次部署执行下列命令。

```powershell
.\deploy\04u\prepare-upload.ps1
```

脚本把所有要上传的文件放到打印出来的 `target\04u-upload\full-<时间>\`。该目录的 `conf`、`hth-mtb`、`hth-eAdvice` 对应 04u 上 `/HTHBatch` 下的目录。通过 SFTP 从这个目录上传即可。PowerShell 和 tar 打包共用 `deploy/04u/upload-files.txt`。两个模块的启动脚本从 `bin/` 取用，共用一份 `bin/Env.sh`。各模块的 Oracle 和输出配置模板来自自己的 `config/`。

### 使用 tar 包

在同一个 `hth-batch-job` 目录进入终端。Windows 用 Git Bash，可在 IDEA 的 Terminal 中打开 Git Bash。macOS 和 Linux 可用各自的终端。

```sh
sh deploy/04u/package.sh
```

如果要在终端完成构建，也可先执行 `mvn -B -pl hth-mtb,hth-eAdvice clean verify`，再运行上面的打包命令。
打包命令会打印产物目录 `target/04u-aix/<commit>/`。将两个 `.tar`、`HTHBatchEnv.sh` 和 `SHA256SUMS` 上传到 04u。该目录还包含部署说明 `README-04u-AIX.md`。

## 已部署环境升级外部 Oracle 密文读取

升级 `hth-mtb` 和 `hth-eAdvice` 的两个 JAR。用 PowerShell 生成本次上传目录。

```powershell
.\deploy\04u\prepare-upload.ps1 -JarsOnly
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

按 [Oracle 配置](#配置-oracle) 更新外部 JDBC 文件并提供包装密钥。若 eAdvice 使用 `uat-config`，更新该目录下的 JDBC 文件。要启用密钥文件自动加载，按下文的 [公共环境](#公共环境) 更新两处 `bin/Env.sh`。

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

## 上传和解包

直接上传方式将 `full-<时间>` 下的文件放入对应安装目录。在初装目录执行 `chmod 750 /HTHBatch/hth-mtb/bin/*.sh /HTHBatch/hth-eAdvice/bin/*.sh`，并按下面的公共 Java 配置步骤验证路径和版本。

使用 tar 包时执行下面的解包步骤。

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
```

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

已部署环境升级公共密钥加载时，把源码里的同一份 `bin/Env.sh` 上传到 `/HTHBatch/hth-mtb/bin/Env.sh` 和 `/HTHBatch/hth-eAdvice/bin/Env.sh`，保留执行权限。两个模块的 `config/`、`/HTHBatch/conf/HTHBatchEnv.sh` 和现有密钥文件继续沿用。如果以前在 `HTHBatchEnv.sh` 里手工加过读取密钥文件的命令，可以移除该行，由启动脚本统一加载。

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

脚本打印本次运行日志的完整路径。退出码应为 `0`，日志应有 `HTH_CRM_1293 stage=END status=SUCCESS`。AIX 上用 `awk` 核对首尾控制记录、业务笔数和尾行校验值。把输出的 `MTB data rows`、日志的 `count` 与同一业务日的数据库笔数对照。若要逐字段验证 CSV 格式，可将文件交给开发环境的 `tools/verify_hth_crm_csv.py` 检查。

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
