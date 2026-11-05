#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
scripts/fetch-llama.sh
cd iosApp
xcodegen generate
