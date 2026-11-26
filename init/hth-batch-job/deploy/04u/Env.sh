#!/bin/sh
# Sourced by an installed HTH Batch launcher.
hth_package_home=$(CDPATH= cd "$(dirname "$0")/.." && pwd) || return 50
case "${hth_package_home##*/}" in
    hth-mtb) hth_jar_name=hth-batch-job-mtb-1.0.0.jar ;;
    hth-eAdvice) hth_jar_name=hth-eAdvice-1.0.0.jar ;;
    *) printf '%s\n' "HTH_BATCH unknown package directory: $hth_package_home" >&2; return 50 ;;
esac

hth_common_env=${HTH_BATCH_COMMON_ENV:-$hth_package_home/../conf/HTHBatchEnv.sh}
if [ -n "${HTH_BATCH_COMMON_ENV:-}" ] || [ -f "$hth_common_env" ]; then
    if [ ! -r "$hth_common_env" ]; then
        printf '%s\n' "HTH_BATCH cannot read common environment: $hth_common_env" >&2
        return 50
    fi
    . "$hth_common_env" || return 50
fi

JAVA_HOME=${HTH_JAVA_HOME:-${JAVA_HOME:-/HTHBatch/jdk-21.0.13.0-jre}}
if [ ! -x "$JAVA_HOME/bin/java" ]; then
    printf '%s\n' "HTH_BATCH Java 21 is missing at $JAVA_HOME/bin/java; set HTH_JAVA_HOME" >&2
    return 50
fi
hth_java_version=$("$JAVA_HOME/bin/java" -version 2>&1) || {
    printf '%s\n' "HTH_BATCH cannot start $JAVA_HOME/bin/java" "$hth_java_version" >&2
    return 50
}
case "$hth_java_version" in
    *'version "21.'*|*'version "21"'*) ;;
    *)
        printf '%s\n' "HTH_BATCH requires Java 21 at $JAVA_HOME/bin/java" "$hth_java_version" >&2
        return 50 ;;
esac

BATCH_HOME=${BATCH_HOME:-$hth_package_home}
CONFIG_DIR=${CONFIG_DIR:-$BATCH_HOME/config}
LOG_DIR=${LOG_DIR:-$BATCH_HOME/logs}
BATCH_JAR=${BATCH_JAR:-$BATCH_HOME/lib/$hth_jar_name}
JVM_OPTS=${JVM_OPTS:--Xmx1024M}
export JAVA_HOME BATCH_HOME CONFIG_DIR LOG_DIR BATCH_JAR JVM_OPTS
