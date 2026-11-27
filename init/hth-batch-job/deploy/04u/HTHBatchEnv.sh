#!/bin/sh
# Install as /HTHBatch/conf/HTHBatchEnv.sh.
# Both batch launchers read this file. Keep the Oracle key in bco-db-key.env beside it.
HTH_JAVA_HOME=${HTH_JAVA_HOME:-/HTHBatch/jdk-21.0.13.0-jre}
export HTH_JAVA_HOME
