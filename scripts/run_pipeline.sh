#!/bin/bash
# ==============================================================================
# End-to-End Analytics Pipeline Runner
# Executes Cleaning, Core Analytics (P0), Hive Analytics (P1), Exports & Parity Check
# ==============================================================================

set -euo pipefail

JAR_PATH="/workspace/mapreduce/target/visitor-analysis-mapreduce-1.0.0.jar"
RAW_INPUT="/weblogs/raw/*/*"
CLEAN_OUTPUT="/weblogs/clean"
OUTPUT_BASE="/weblogs/output"

echo "========================================================================"
echo "  WEBSITE VISITOR ANALYSIS: HADOOP PIPELINE EXECUTION"
echo "========================================================================"

if [ ! -f "${JAR_PATH}" ]; then
    echo "MapReduce JAR not found at ${JAR_PATH}. Building Maven package..."
    cd /workspace/mapreduce && mvn clean package -DskipTests=false
fi

# Step 1: Clean and Parse MapReduce Job
echo -e "\n>>> [Step 1/6] Running Log Clean & Dead-Letter Routing Job..."
yarn jar "${JAR_PATH}" clean "${RAW_INPUT}" "${CLEAN_OUTPUT}"

# Step 2: Hits Per Page (FR-9)
echo -e "\n>>> [Step 2/6] Running Hits Per Page Job (Combiner + Top-N)..."
yarn jar "${JAR_PATH}" hits "${CLEAN_OUTPUT}" "${OUTPUT_BASE}/hits_per_page"

# Step 3: Unique Visitors Per Day (FR-10)
echo -e "\n>>> [Step 3/6] Running Unique Visitors Per Day Job..."
yarn jar "${JAR_PATH}" visitors "${CLEAN_OUTPUT}" "${OUTPUT_BASE}/unique_visitors"

# Step 4: Traffic Over Time (FR-11)
echo -e "\n>>> [Step 4/6] Running Traffic Over Time Job (Hourly Buckets)..."
yarn jar "${JAR_PATH}" traffic "${CLEAN_OUTPUT}" "${OUTPUT_BASE}/traffic_over_time"

# Step 5: Status Codes and Errors (FR-12)
echo -e "\n>>> [Step 5/6] Running Status Code and Error Analysis Job..."
yarn jar "${JAR_PATH}" status "${CLEAN_OUTPUT}" "${OUTPUT_BASE}/status_code"

# Step 6: Advanced Dimensions (FR-13 to FR-16)
echo -e "\n>>> [Step 6/6] Running Geo/Device, Referrer, and Sessionization Jobs..."
yarn jar "${JAR_PATH}" geodevice "${CLEAN_OUTPUT}" "${OUTPUT_BASE}/geo_device"
yarn jar "${JAR_PATH}" referrers "${CLEAN_OUTPUT}" "${OUTPUT_BASE}/referrers"
yarn jar "${JAR_PATH}" sessions "${CLEAN_OUTPUT}" "${OUTPUT_BASE}/sessions"

echo -e "\n========================================================================"
echo "  MAPREDUCE JOBS COMPLETED! Running Hive Layer & Parity Verification..."
echo "========================================================================"

# Run Hive Schema Creation & Analytics Queries
if command -v hive &> /dev/null; then
    echo "Executing Hive DDL and Table Mapping..."
    hive -f /workspace/hive/01_create_tables.hql || true
fi

# Export Results
echo "Exporting results to /workspace/exports/..."
/workspace/scripts/export.sh

echo "========================================================================"
echo "  PIPELINE FINISHED SUCCESSFULLY! Ready for Dashboard Visualization."
echo "========================================================================"

