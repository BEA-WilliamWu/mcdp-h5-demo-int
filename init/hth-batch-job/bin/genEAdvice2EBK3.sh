#!/bin/sh
script_dir=$(CDPATH= cd "$(dirname "$0")" && pwd) || exit 50
exec sh "$script_dir/EAdviceBatch.sh" 3 "$@"
