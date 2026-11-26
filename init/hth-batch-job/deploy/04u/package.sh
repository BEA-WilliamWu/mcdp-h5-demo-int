#!/bin/sh
# Build both deployment archives on the development machine after Maven verify.
set -eu

if [ "$#" -gt 1 ]; then
    printf '%s\n' "Usage: $0 [output-directory]" >&2
    exit 2
fi

scripts=$(CDPATH= cd "$(dirname "$0")" && pwd)
batch_root=$(CDPATH= cd "$scripts/../.." && pwd)
if [ -f "$batch_root/SOURCE_REVISION" ]; then
    revision=$(cat "$batch_root/SOURCE_REVISION")
else
    revision=$(git -C "$batch_root" rev-parse --short=7 HEAD 2>/dev/null) || {
        printf '%s\n' "Run from a Git checkout or extract the HTH Batch source ZIP with its SOURCE_REVISION file." >&2
        exit 1
    }
fi
case "$revision" in
    ''|*[!0-9a-f]*) printf '%s\n' "Invalid source revision." >&2; exit 1 ;;
esac
release_dir=${1:-$batch_root/target/04u-aix/$revision}
mtb_jar=hth-batch-job-mtb-1.0.0.jar
advice_jar=hth-eAdvice-1.0.0.jar

for jar in "$batch_root/hth-mtb/target/$mtb_jar" "$batch_root/hth-eAdvice/target/$advice_jar"; do
    if [ ! -r "$jar" ]; then
        printf '%s\n' "Missing $jar. Run mvn verify from $batch_root first." >&2
        exit 1
    fi
done

staging=$(mktemp -d "${TMPDIR:-/tmp}/hth-04u.XXXXXX")
trap 'rm -rf "$staging"' 0
trap 'exit 1' HUP INT TERM
install -d -m 0755 "$staging/release"

stage_module() {
    module=$1
    jar=$2
    package_home="$staging/$module"
    install -d -m 0755 "$package_home/bin" "$package_home/config" "$package_home/lib"
    install -m 0750 "$scripts/Env.sh" "$package_home/bin/Env.sh"
    install -m 0644 "$scripts/README.md" "$package_home/README-04u-AIX.md"
    install -m 0640 "$batch_root/$module/config/db.ibank.batch.properties" "$package_home/config/"
    install -m 0640 "$batch_root/$module/config/system_batch.properties" "$package_home/config/"
    install -m 0644 "$batch_root/$module/target/$jar" "$package_home/lib/"
}

stage_module hth-mtb "$mtb_jar"
install -m 0750 "$batch_root/bin/GenHthCrmExtract.sh" "$batch_root/bin/MTBBatch.sh" "$staging/hth-mtb/bin/"
install -d -m 0755 "$staging/hth-mtb/tools"
install -m 0644 "$batch_root/tools/verify_hth_crm_csv.awk" "$staging/hth-mtb/tools/"

stage_module hth-eAdvice "$advice_jar"
install -m 0750 "$scripts/EAdviceBatch.sh" "$staging/hth-eAdvice/bin/"
install -d -m 0755 "$staging/hth-eAdvice/transfer/windows"
install -m 0644 "$batch_root"/sftp/*.wsf "$staging/hth-eAdvice/transfer/windows/"

install -m 0640 "$scripts/HTHBatchEnv.sh" "$staging/release/"
install -m 0644 "$scripts/README.md" "$staging/release/README-04u-AIX.md"
COPYFILE_DISABLE=1 tar --format ustar -cf "$staging/release/hth-mtb-04u-aix-$revision.tar" -C "$staging" hth-mtb
COPYFILE_DISABLE=1 tar --format ustar -cf "$staging/release/hth-eAdvice-04u-aix-$revision.tar" -C "$staging" hth-eAdvice
(
    cd "$staging/release"
    if command -v sha256sum >/dev/null 2>&1; then
        sha256sum HTHBatchEnv.sh ./*.tar
    else
        shasum -a 256 HTHBatchEnv.sh ./*.tar
    fi
) > "$staging/release/SHA256SUMS"

mkdir -p "$release_dir"
cp -p "$staging"/release/* "$release_dir/"
printf '04u packages: %s\n' "$release_dir"
