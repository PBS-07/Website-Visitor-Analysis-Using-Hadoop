#!/bin/bash
# ==============================================================================
# Export Script: Dumps HDFS job outputs and runs export_results.py
# Following PRD Section 6.5 (FR-22)
# ==============================================================================

set -euo pipefail

EXPORTS_DIR="${1:-/workspace/exports}"
HDFS_OUTPUT_BASE="${2:-/weblogs/output}"

echo "=== [Export Script] Starting Export Pipeline ==="
echo "  Target exports dir: ${EXPORTS_DIR}"
echo "  HDFS Output Base:   ${HDFS_OUTPUT_BASE}"

mkdir -p "${EXPORTS_DIR}"

python3 /workspace/scripts/export_results.py \
    --exports-dir "${EXPORTS_DIR}" \
    --hdfs-base "${HDFS_OUTPUT_BASE}"

echo "=== [Export Script] Export completed successfully! ==="
ls -lh "${EXPORTS_DIR}"

