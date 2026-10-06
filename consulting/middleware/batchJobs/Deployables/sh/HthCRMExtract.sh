#!/bin/sh
# BCOH2H-1293. No argument: previous Asia/Hong_Kong calendar day.
# One yyyy-MM-dd argument: authorized operator rerun of that date.
set -u
if [ "$#" -gt 1 ]; then
    echo 'Usage: HthCRMExtract.sh [yyyy-MM-dd]' >&2
    exit 2
fi
EnvSH=${HTH_CRM_ENV_SH:-/CDCBatch/sh/Env.sh}
if [ ! -r "$EnvSH" ]; then
    echo 'HTH CRM batch environment is unavailable' >&2
    exit 1
fi
. "$EnvSH"
: "${LogFilePath:?}"
: "${batchConfigPath:?}"
: "${JAVA_HOME:?}"
: "${CLASSPATH:?}"
umask 027
log_file="$LogFilePath/HthCRMExtract.$(date +'%Y%m%d%H%M%S').$$.log"
exec >"$log_file" 2>&1
config_file=${HTH_CRM_EXTRACT_CONFIG:-$batchConfigPath/hth_crm_extract.properties}
class_path="$CLASSPATH:/CDCBatch/lib/hth-crm-extract.jar"
if [ "$#" -eq 1 ]; then
    "$JAVA_HOME/bin/java" -cp "$class_path" outboundbatchprocessor.HthCRMExtractJob \
        "--config=$config_file" "--date=$1"
else
    "$JAVA_HOME/bin/java" -cp "$class_path" outboundbatchprocessor.HthCRMExtractJob \
        "--config=$config_file"
fi
result=$?
echo "HTH_CRM_EXTRACT shellExit=$result log=$log_file"
exit "$result"
