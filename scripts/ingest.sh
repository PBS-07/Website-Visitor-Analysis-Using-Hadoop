#!/bin/bash
# ==============================================================================
# Ingestion Script: Uploads raw web server access logs to HDFS
# Following PRD Section 4.3 & 6.1 (FR-2):
# Upload logs into date-partitioned folders: /weblogs/raw/dt=YYYY-MM-DD/
# Idempotent: Overwrites existing partitions cleanly without duplication.
# ==============================================================================

set -euo pipefail

INPUT_DIR="${1:-/workspace/data/raw}"
HDFS_TARGET_BASE="${2:-/weblogs/raw}"
OVERWRITE="${OVERWRITE:-true}"

echo "=== [HDFS Ingestion] Starting Log Ingestion Pipeline ==="
echo "  Local Source:  ${INPUT_DIR}"
echo "  HDFS Base:     ${HDFS_TARGET_BASE}"
echo "  Overwrite:     ${OVERWRITE}"

if [ ! -d "${INPUT_DIR}" ]; then
    echo "ERROR: Input directory '${INPUT_DIR}' does not exist." >&2
    exit 1
fi

# Ensure HDFS base path exists
hdfs dfs -mkdir -p "${HDFS_TARGET_BASE}"

# Count total partitions to process
PARTITION_DIRS=$(find "${INPUT_DIR}" -mindepth 1 -maxdepth 1 -type d -name "dt=*" | sort)

if [ -z "${PARTITION_DIRS}" ]; then
    echo "WARNING: No 'dt=YYYY-MM-DD' partitions found in ${INPUT_DIR}."
    # Check if there are raw logs directly in INPUT_DIR
    if ls "${INPUT_DIR}"/*.log 1> /dev/null 2>&1; then
        echo "Found loose log files. Uploading to ${HDFS_TARGET_BASE}/default/..."
        hdfs dfs -mkdir -p "${HDFS_TARGET_BASE}/default"
        hdfs dfs -put -f "${INPUT_DIR}"/*.log "${HDFS_TARGET_BASE}/default/"
        echo "Ingestion completed for default partition."
        exit 0
    else
        echo "ERROR: No log files found to ingest." >&2
        exit 1
    fi
fi

TOTAL_PARTITIONS=$(echo "${PARTITION_DIRS}" | wc -l)
CURRENT_INDEX=0

for PART_DIR in ${PARTITION_DIRS}; do
    CURRENT_INDEX=$((CURRENT_INDEX + 1))
    PART_NAME=$(basename "${PART_DIR}")
    HDFS_PART_DIR="${HDFS_TARGET_BASE}/${PART_NAME}"

    echo "[${CURRENT_INDEX}/${TOTAL_PARTITIONS}] Ingesting partition: ${PART_NAME} -> ${HDFS_PART_DIR}"

    # Check if partition already exists in HDFS
    if hdfs dfs -test -d "${HDFS_PART_DIR}"; then
        if [ "${OVERWRITE}" = "true" ]; then
            echo "  Partition exists in HDFS. Overwrite enabled: cleaning previous data..."
            hdfs dfs -rm -r -skipTrash "${HDFS_PART_DIR}"
        else
            echo "  Partition already exists in HDFS. Skipping (OVERWRITE=false)."
            continue
        fi
    fi

    # Create target directory and upload atomically via staging
    HDFS_STAGE_DIR="${HDFS_TARGET_BASE}/.staging_${PART_NAME}"
    hdfs dfs -rm -r -skipTrash "${HDFS_STAGE_DIR}" 2>/dev/null || true
    hdfs dfs -mkdir -p "${HDFS_STAGE_DIR}"

    # Upload local files into HDFS staging
    hdfs dfs -put "${PART_DIR}"/* "${HDFS_STAGE_DIR}/"

    # Atomically rename staging to final partition path
    hdfs dfs -mv "${HDFS_STAGE_DIR}" "${HDFS_PART_DIR}"

    # Verify presence and file count
    FILE_COUNT=$(hdfs dfs -ls "${HDFS_PART_DIR}" | grep -v '^Found' | wc -l)
    echo "  Successfully ingested ${FILE_COUNT} file(s) into ${HDFS_PART_DIR}"
done

echo "=== [HDFS Ingestion] Ingestion Finished Successfully ==="
hdfs dfs -ls "${HDFS_TARGET_BASE}"

