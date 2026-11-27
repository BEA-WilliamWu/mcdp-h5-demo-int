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
staging=$(mktemp -d "${TMPDIR:-/tmp}/hth-04u.XXXXXX")
trap 'rm -rf "$staging"' 0
trap 'exit 1' HUP INT TERM
install -d -m 0755 "$staging/release"

while IFS='|' read -r source destination mode; do
    case "$source" in ''|\#*) continue ;; esac
    if [ ! -r "$batch_root/$source" ]; then
        printf '%s\n' "Missing $batch_root/$source. Run mvn verify from $batch_root first." >&2
        exit 1
    fi
    install -d -m 0755 "$(dirname "$staging/$destination")"
    install -m "$mode" "$batch_root/$source" "$staging/$destination"
done < "$scripts/upload-files.txt"

cp -p "$staging/conf/HTHBatchEnv.sh" "$staging/release/"
for folder in release hth-mtb hth-eAdvice; do
    cp -p "$staging/README-04u-AIX.md" "$staging/$folder/"
done
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
