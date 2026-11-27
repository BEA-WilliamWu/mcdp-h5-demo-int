#!/bin/sh
umask 027
usage() {
    echo "Usage: EAdviceBatch.sh <1|2|3|P> [yyyyMMdd]" >&2
    exit 2
}

[ "$#" -ge 1 ] && [ "$#" -le 2 ] || usage
case "$1" in
    1|GEN_EADVICE_2EBK1) task=1 ;;
    2|GEN_EADVICE_2EBK2) task=2 ;;
    3|GEN_EADVICE_2EBK3) task=3 ;;
    P|p|GEN_PAPER_ADVICE_2EBK) task=P ;;
    *) usage ;;
esac
if [ "$#" -eq 2 ]; then
    case "$2" in ''|*[!0-9]*) usage ;; esac
    [ "${#2}" -eq 8 ] || usage
fi

script_dir=$(CDPATH= cd "$(dirname "$0")" && pwd) || exit 50
. "$script_dir/Env.sh" || exit 50
cd "$BATCH_HOME" || exit 50
if [ ! -r "$BATCH_JAR" ]; then
    echo "HTH_EADVICE cannot read batch JAR: $BATCH_JAR" >&2
    exit 50
fi

mkdir -p "$LOG_DIR" || exit 50
LOG_DIR=$(CDPATH= cd "$LOG_DIR" && pwd) || exit 50
log_file="$LOG_DIR/EAdviceBatch.$task.$(date +%Y%m%d%H%M%S).$$.log"
echo "HTH_EADVICE log=$log_file"
set -f
# JVM_OPTS contains whitespace-separated JVM flags.
"$JAVA_HOME/bin/java" $JVM_OPTS -Duser.timezone=Asia/Hong_Kong \
    "-Dconfig.path=$CONFIG_DIR" "-Dbatch.log.dir=$LOG_DIR" -jar "$BATCH_JAR" "$@" > "$log_file" 2>&1
code=$?
case "$code" in 0|2) ;; *) code=50 ;; esac
echo "HTH_EADVICE exit=$code log=$log_file"
exit "$code"
