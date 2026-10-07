#!/bin/sh
set -eu
JOB_HOME=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
TEST_CLASSES=$(mktemp -d)
trap 'rm -rf "$TEST_CLASSES"' EXIT HUP INT TERM
"${JAVA_HOME:+$JAVA_HOME/bin/}javac" -source 8 -target 8 -Xlint:-options -d "$TEST_CLASSES" \
  "$JOB_HOME/src/com/ofss/digx/cz/bea/hth/crm/batch/HthCrmExtractJob.java" \
  "$JOB_HOME/test/com/ofss/digx/cz/bea/hth/crm/batch/HthCrmExtractJobTest.java" \
  "$JOB_HOME/test/com/ofss/digx/cz/bea/hth/crm/batch/FakeHthCrmDriver.java"
"${JAVA_HOME:+$JAVA_HOME/bin/}java" -cp "$TEST_CLASSES" \
  com.ofss.digx.cz.bea.hth.crm.batch.HthCrmExtractJobTest
for source_file in \
  "$JOB_HOME/../outboundbatchprocessor/GenTxnLog2CRM.java" \
  "$JOB_HOME/../UAT/outboundbatchprocessor/GenTxnLog2CRM.java" \
  "$JOB_HOME/../PRD/outboundbatchprocessor/GenTxnLog2CRM.java"; do
  [ "$(grep -c "CHNL_ID is null or CHNL_ID <> 'ELE-HTH'" "$source_file")" -eq 3 ]
done

OUTPUT_DIR=$(mktemp -d)
trap 'rm -rf "$TEST_CLASSES" "$OUTPUT_DIR"' EXIT HUP INT TERM
export HTH_CRM_OUTPUT_DIR="$OUTPUT_DIR"
export HTH_CRM_JDBC_URL='jdbc:fake:hth'
export HTH_CRM_JDBC_USER='test'
export HTH_CRM_JDBC_PASSWORD='test'
export JAVA_TOOL_OPTIONS='-Djdbc.drivers=com.ofss.digx.cz.bea.hth.crm.batch.FakeHthCrmDriver'
"${JAVA_HOME:+$JAVA_HOME/bin/}java" -cp "$TEST_CLASSES" \
  com.ofss.digx.cz.bea.hth.crm.batch.HthCrmExtractJob --date 20261007
[ "$(wc -l < "$OUTPUT_DIR/HTH_CRM_20261007.csv")" -eq 3 ]
"${JAVA_HOME:+$JAVA_HOME/bin/}java" -cp "$TEST_CLASSES" \
  com.ofss.digx.cz.bea.hth.crm.batch.HthCrmExtractJob --date 20261006
[ "$(wc -l < "$OUTPUT_DIR/HTH_CRM_20261006.csv")" -eq 1 ]
cp "$OUTPUT_DIR/HTH_CRM_20261007.csv" "$OUTPUT_DIR/expected.csv"
export HTH_CRM_FAKE_DUPLICATE=Y
if "${JAVA_HOME:+$JAVA_HOME/bin/}java" -cp "$TEST_CLASSES" \
  com.ofss.digx.cz.bea.hth.crm.batch.HthCrmExtractJob --date 20261007; then
  echo 'duplicate rerun unexpectedly succeeded' >&2
  exit 1
fi
cmp "$OUTPUT_DIR/expected.csv" "$OUTPUT_DIR/HTH_CRM_20261007.csv"
[ "$(wc -l < "$OUTPUT_DIR/HTH_CRM_20261006.csv")" -eq 1 ]
printf 'HTH CRM extract end-to-end tests passed\n'
