#!/bin/sh
# Install as /HTHBatch/conf/HTHBatchEnv.sh.
# Both batch launchers read this file. Keep the Oracle key in bco-db-key.env beside it.
hth_04u_java_home=/HTHBatch/jdk-21.0.13.0-jre
if [ -z "${HTH_JAVA_HOME:-}" ] && [ -x "$hth_04u_java_home/bin/java" ]; then
    HTH_JAVA_HOME=$hth_04u_java_home
    export HTH_JAVA_HOME
fi
