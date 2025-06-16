#!/usr/bin/env bash

# Resolve the directory containing this script:
SCRIPT_DIR="$( cd -- "$( dirname -- "${BASH_SOURCE[0]:-$0}" )" >/dev/null 2>&1 && pwd )"

# Change into that directory:
cd "$SCRIPT_DIR" || exit 1

docker build -t simple_mcp_ts . && docker run --rm -it -p 8000:8000 simple_mcp_ts