#!/bin/sh
set -eu
JOB_HOME=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
CLASS_DIR=${HTH_CRM_CLASS_DIR:-"$JOB_HOME/classes"}
mkdir -p "$CLASS_DIR"
"${JAVA_HOME:+$JAVA_HOME/bin/}javac" -source 8 -target 8 -Xlint:-options -d "$CLASS_DIR" \
  "$JOB_HOME/src/com/ofss/digx/cz/bea/hth/crm/batch/HthCrmExtractJob.java"
printf 'Built HTH CRM extract classes in %s\n' "$CLASS_DIR"
