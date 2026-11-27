#!/bin/sh
# Sourced by the HTH launchers in either a project checkout or a 04u installation.
case "${hth_module_home##*/}" in
    hth-mtb) hth_jar_name=hth-batch-job-mtb-1.0.0.jar ;;
    hth-eAdvice) hth_jar_name=hth-eAdvice-1.0.0.jar ;;
    *) printf '%s\n' "HTH_BATCH unknown module directory: $hth_module_home" >&2; return 50 ;;
esac

hth_common_env=${HTH_BATCH_COMMON_ENV:-$hth_module_home/../conf/HTHBatchEnv.sh}
if [ -n "${HTH_BATCH_COMMON_ENV:-}" ] || [ -f "$hth_common_env" ]; then
    if [ ! -r "$hth_common_env" ]; then
        printf '%s\n' "HTH_BATCH cannot read common environment: $hth_common_env" >&2
        return 50
    fi
    . "$hth_common_env" || return 50
fi

hth_key_env=$(dirname "$hth_common_env")/bco-db-key.env
if [ -z "${HTH_BATCH_BCO_WRAPPING_KEY:-}" ] && [ -e "$hth_key_env" ]; then
    if [ ! -f "$hth_key_env" ] || [ ! -r "$hth_key_env" ]; then
        printf '%s\n' "HTH_BATCH cannot read Oracle key file: $hth_key_env" >&2
        return 50
    fi
    . "$hth_key_env" || return 50
    if [ -z "${HTH_BATCH_BCO_WRAPPING_KEY:-}" ]; then
        printf '%s\n' "HTH_BATCH missing HTH_BATCH_BCO_WRAPPING_KEY in $hth_key_env" >&2
        return 50
    fi
fi

JAVA_HOME=${HTH_JAVA_HOME:-${JAVA_HOME:-/usr/java/jdk-21}}
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

BATCH_HOME=${BATCH_HOME:-$hth_module_home}
CONFIG_DIR=${CONFIG_DIR:-$BATCH_HOME/config}
LOG_DIR=${LOG_DIR:-$BATCH_HOME/logs}
if [ -z "${BATCH_JAR:-}" ]; then
    BATCH_JAR=$BATCH_HOME/lib/$hth_jar_name
    if [ ! -f "$BATCH_JAR" ] && [ -f "$BATCH_HOME/target/$hth_jar_name" ]; then
        BATCH_JAR=$BATCH_HOME/target/$hth_jar_name
    fi
fi
JVM_OPTS=${JVM_OPTS:--Xmx1024M}
export JAVA_HOME BATCH_HOME CONFIG_DIR LOG_DIR BATCH_JAR JVM_OPTS
export HTH_BATCH_BCO_WRAPPING_KEY
