#!/bin/sh
# Usage: sh MTBBatch.sh CRM_EXTRACT [yyyyMMdd]
umask 027
case "${1:-}" in
    CRM_EXTRACT)
        if [ "$#" -gt 2 ]; then echo "Usage: MTBBatch.sh CRM_EXTRACT [yyyyMMdd]" >&2; exit 2; fi
        if [ "$#" -eq 2 ]; then
            case "$2" in *[!0-9]*) echo "Date must be yyyyMMdd" >&2; exit 2 ;; esac
            if [ "${#2}" -ne 8 ]; then echo "Date must be yyyyMMdd" >&2; exit 2; fi
        fi ;;
    LOAD_DATA|PROCESS_TXN|GENERATE_REPORT) ;; # Existing project scaffold tasks.
    *) echo "Usage: MTBBatch.sh CRM_EXTRACT [yyyyMMdd]" >&2; exit 2 ;;
esac
HTH_SCRIPT_DIR=$(CDPATH= cd "$(dirname "$0")" && pwd) || exit 50
. "$HTH_SCRIPT_DIR/Env.sh" || exit 50
# Relative deployment paths resolve from the installation, including logs.
cd "$BATCH_HOME" || exit 50
if [ ! -f "$BATCH_JAR" ] || [ ! -x "$JAVA_HOME/bin/java" ]; then
    echo "HTH_BATCH missing executable Java 21 or batch JAR" >&2
    exit 50
fi
mkdir -p "$LOG_DIR" || exit 50
LOG_DIR=$(CDPATH= cd "$LOG_DIR" && pwd) || exit 50
HTH_LOG_FILE="$LOG_DIR/MTBBatch.$1.$(date +%Y%m%d%H%M%S).$$.log"
echo "HTH_BATCH log=$HTH_LOG_FILE"
# Relative output paths in system_batch.properties also resolve from BATCH_HOME.
set -f
# JVM_OPTS intentionally splits on whitespace; task arguments retain their original quoting.
"$JAVA_HOME/bin/java" $JVM_OPTS "-Dconfig.path=$CONFIG_DIR" "-Dbatch.log.dir=$LOG_DIR" \
    -jar "$BATCH_JAR" "$@" > "$HTH_LOG_FILE" 2>&1
HTH_EXIT_CODE=$?
case "$HTH_EXIT_CODE" in 0|2) ;; *) HTH_EXIT_CODE=50 ;; esac
echo "HTH_BATCH exit=$HTH_EXIT_CODE log=$HTH_LOG_FILE"
exit "$HTH_EXIT_CODE"
