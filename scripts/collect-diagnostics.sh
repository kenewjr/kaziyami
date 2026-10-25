#!/usr/bin/env bash
# scripts/collect-diagnostics.sh
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(dirname "$SCRIPT_DIR")"

python3 "$REPO_ROOT/scripts/collect_diagnostics.py" "$@"
