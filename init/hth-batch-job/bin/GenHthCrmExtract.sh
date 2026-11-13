#!/bin/sh
# Compatibility command name; routes to the standalone HTH application.
HTH_SCRIPT_DIR=$(CDPATH= cd "$(dirname "$0")" && pwd) || exit 50
exec sh "$HTH_SCRIPT_DIR/MTBBatch.sh" CRM_EXTRACT "$@"
