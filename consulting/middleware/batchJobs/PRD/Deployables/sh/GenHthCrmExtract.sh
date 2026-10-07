#!/bin/sh
# 1293: independent HTH/DSP daily extract in the existing batch JAR.
. /CDCBatch/sh/Env.sh

if [ ! -d "$LogFilePath" ]; then
    echo "HTH_CRM_1293 log directory is missing" >&2
    exit 1
fi
log="$LogFilePath/GenHthCrmExtract.$(date +%Y%m%d%H%M%S).log"
echo "HTH_CRM_1293 log=$log"
exec >"$log" 2>&1

if [ "$#" -gt 1 ]; then
    echo "Usage: GenHthCrmExtract.sh [yyyyMMdd]" >&2
    exit 2
fi

# Set these in the HTH job configuration; do not reuse BCO database or output settings.
if [ -z "${HTH_CRM_JDBC_URL:-}" ] || [ -z "${HTH_CRM_JDBC_USER:-}" ] ||
   [ -z "${HTH_CRM_JDBC_PASSWORD:-}" ]; then
    echo "HTH_CRM_1293 missing required HTH database/output environment" >&2
    exit 50
fi
HTH_CRM_OUTPUT_DIR=${HTH_CRM_OUTPUT_DIR:-$Outputfile/hth}
export HTH_CRM_OUTPUT_DIR

if [ "$#" -eq 1 ]; then
    "$JAVA_HOME/bin/java" -cp "$CLASSPATH" outboundbatchprocessor.hth.HthCrmExtractJob --date "$1"
else
    "$JAVA_HOME/bin/java" -cp "$CLASSPATH" outboundbatchprocessor.hth.HthCrmExtractJob
fi
rc=$?
if [ "$rc" -ne 0 ]; then exit 50; fi
exit 0
