#!/bin/sh
set -eu
JOB_HOME=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
CLASS_DIR=${HTH_CRM_CLASS_DIR:-"$JOB_HOME/classes"}
: "${HTH_CRM_JDBC_JAR:?Set HTH_CRM_JDBC_JAR to the Oracle JDBC driver path}"
: "${HTH_CRM_JDBC_URL:?Set HTH_CRM_JDBC_URL}"
: "${HTH_CRM_JDBC_USER:?Set HTH_CRM_JDBC_USER}"
: "${HTH_CRM_JDBC_PASSWORD:?Set HTH_CRM_JDBC_PASSWORD}"
: "${HTH_CRM_OUTPUT_DIR:?Set HTH_CRM_OUTPUT_DIR}"
exec "${JAVA_HOME:+$JAVA_HOME/bin/}java" -cp "$CLASS_DIR:$HTH_CRM_JDBC_JAR" \
  com.ofss.digx.cz.bea.hth.crm.batch.HthCrmExtractJob "$@"
