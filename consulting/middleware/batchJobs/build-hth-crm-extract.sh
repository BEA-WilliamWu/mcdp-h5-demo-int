#!/bin/sh
# Package only the HTH extract class. The existing BCO batch JAR is not rebuilt.
set -eu
batch_root=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
classes=$(mktemp -d)
trap 'rm -rf "$classes"' EXIT HUP INT TERM
compiler=${JAVA_HOME:+$JAVA_HOME/bin/}javac
archiver=${JAVA_HOME:+$JAVA_HOME/bin/}jar
"$compiler" -source 8 -target 8 \
    -cp "$batch_root/Deployables/lib/CDCBatchesUAT.jar:$batch_root/Deployables/lib/ojdbc8.jar" \
    -d "$classes" "$batch_root/outboundbatchprocessor/HthCRMExtractJob.java"
"$archiver" cf "$batch_root/Deployables/lib/hth-crm-extract.jar" \
    -C "$classes" outboundbatchprocessor
echo "$batch_root/Deployables/lib/hth-crm-extract.jar"
