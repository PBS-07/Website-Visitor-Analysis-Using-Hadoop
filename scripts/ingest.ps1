# Windows PowerShell Ingestion Helper
param (
    [string]$InputDir = "data/raw",
    [string]$HdfsTargetBase = "/weblogs/raw",
    [string]$ContainerName = "hadoop-cluster"
)

Write-Host "=== [Ingestion Helper] Ingesting logs via Docker container $ContainerName ===" -ForegroundColor Cyan
docker exec $ContainerName bash -c "/workspace/scripts/ingest.sh $InputDir $HdfsTargetBase"

