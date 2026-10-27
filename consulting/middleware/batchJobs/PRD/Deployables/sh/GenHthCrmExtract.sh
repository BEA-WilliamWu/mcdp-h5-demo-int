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

# Follow BCO's encrypted batch configuration pattern with a separate HTH file.
if [ -z "${batchConfigPath:-}" ] ||
   [ ! -r "$batchConfigPath/hth_crm_batch_config.properties" ]; then
    echo "HTH_CRM_1293 missing readable HTH CRM configuration under batchConfigPath" >&2
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
