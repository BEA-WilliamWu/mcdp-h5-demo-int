#!/bin/sh
# Sourced by the HTH launchers in either a project checkout or a 04u installation.
hth_launcher_home=$(CDPATH= cd "$(dirname "$0")/.." && pwd) || return 50
case "${0##*/}" in
    EAdviceBatch.sh)
        hth_module=hth-eAdvice
        hth_jar_name=hth-eAdvice-1.0.0.jar
        ;;
    *)
        hth_module=hth-mtb
        hth_jar_name=hth-batch-job-mtb-1.0.0.jar
        ;;
esac

if [ "${hth_launcher_home##*/}" = "$hth_module" ]; then
    hth_config_subdir=config
    hth_jar_subdir=lib
    hth_common_root=$hth_launcher_home/..
    hth_default_java=/HTHBatch/jdk-21.0.13.0-jre
else
    hth_config_subdir=$hth_module/config
    hth_jar_subdir=$hth_module/target
    hth_common_root=$hth_launcher_home
    hth_default_java=/usr/java/jdk-21
fi

hth_common_env=${HTH_BATCH_COMMON_ENV:-$hth_common_root/conf/HTHBatchEnv.sh}
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

JAVA_HOME=${HTH_JAVA_HOME:-${JAVA_HOME:-$hth_default_java}}
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

BATCH_HOME=${BATCH_HOME:-$hth_launcher_home}
CONFIG_DIR=${CONFIG_DIR:-$BATCH_HOME/$hth_config_subdir}
LOG_DIR=${LOG_DIR:-$BATCH_HOME/logs}
BATCH_JAR=${BATCH_JAR:-$BATCH_HOME/$hth_jar_subdir/$hth_jar_name}
JVM_OPTS=${JVM_OPTS:--Xmx1024M}
export JAVA_HOME BATCH_HOME CONFIG_DIR LOG_DIR BATCH_JAR JVM_OPTS
export HTH_BATCH_BCO_WRAPPING_KEY
