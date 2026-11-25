#!/bin/sh
# Independent HTH installation; no CDCBatch environment or classpath dependency.
JAVA_HOME=${JAVA_HOME:-/usr/java/jdk-21}
BATCH_HOME=${BATCH_HOME:-$(CDPATH= cd "$(dirname "$0")/.." && pwd)}
CONFIG_DIR=${CONFIG_DIR:-$BATCH_HOME/hth-mtb/config}
LOG_DIR=${LOG_DIR:-$BATCH_HOME/logs}
BATCH_JAR=${BATCH_JAR:-$BATCH_HOME/hth-mtb/target/hth-batch-job-mtb-1.0.0.jar}
JVM_OPTS=${JVM_OPTS:--Xmx1024M}
export JAVA_HOME BATCH_HOME CONFIG_DIR LOG_DIR BATCH_JAR JVM_OPTS
