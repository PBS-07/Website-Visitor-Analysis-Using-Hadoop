# PowerShell equivalent runner for Windows environments
param (
    [Parameter(Position=0)]
    [string]$Target = "help"
)

$COMPOSE_FILE = "docker/docker-compose.yml"
$CONTAINER_NAME = "hadoop-cluster"

switch ($Target.ToLower()) {
    "up" {
        Write-Host "Building and starting Hadoop + Hive single-node cluster..." -ForegroundColor Cyan
        docker compose -f $COMPOSE_FILE up -d --build
        Write-Host "Waiting for HDFS NameNode to be responsive..." -ForegroundColor Yellow
        $ready = $false
        while (-not $ready) {
            docker exec $CONTAINER_NAME hdfs dfs -test -d /weblogs/raw 2>$null
            if ($LASTEXITCODE -eq 0) {
                $ready = $true
            } else {
                Start-Sleep -Seconds 3
            }
        }
        Write-Host "Hadoop & Hive single-node cluster is UP and READY!" -ForegroundColor Green
        Write-Host "NameNode Web UI:       http://localhost:9870"
        Write-Host "ResourceManager Web UI: http://localhost:8088"
        Write-Host "HiveServer2 Web UI:    http://localhost:10002"
    }
    "down" {
        docker compose -f $COMPOSE_FILE down
    }
    "clean" {
        docker compose -f $COMPOSE_FILE down -v --remove-orphans
        Remove-Item -Path "data/raw/*", "exports/*.csv", "exports/*.json", "mapreduce/target" -Recurse -Force -ErrorAction SilentlyContinue
    }
    "status" {
        docker compose -f $COMPOSE_FILE ps
        docker exec $CONTAINER_NAME hdfs dfsadmin -report
        docker exec $CONTAINER_NAME yarn node -list -all
    }
    "test-infra" {
        Write-Host "Running Sample WordCount Job on YARN..." -ForegroundColor Cyan
        docker exec $CONTAINER_NAME bash -c 'echo -e "hadoop mapreduce\nhadoop hdfs yarn\nwebsite visitor analysis hadoop" > /tmp/wc_input.txt && hdfs dfs -rm -r -f -skipTrash /tmp/wc_in /tmp/wc_out 2>/dev/null; hdfs dfs -mkdir -p /tmp/wc_in && hdfs dfs -put -f /tmp/wc_input.txt /tmp/wc_in/ && yarn jar /opt/hadoop/share/hadoop/mapreduce/hadoop-mapreduce-examples-*.jar wordcount /tmp/wc_in /tmp/wc_out && hdfs dfs -cat /tmp/wc_out/part-* && hdfs dfs -rm -r -f -skipTrash /tmp/wc_in /tmp/wc_out'
    }
    Default {
        Write-Host "Usage: .\scripts\make.ps1 [up|down|clean|status|test-infra|generate-data|ingest|build-mapreduce|run-all]"
    }
}

