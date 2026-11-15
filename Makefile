# Website Visitor Analysis Using Hadoop
# Reproducible Pipeline Orchestration

COMPOSE_FILE = docker/docker-compose.yml
CONTAINER_NAME = hadoop-cluster

.PHONY: help up down clean status test-infra generate-data ingest build-mapreduce run-all

help:
	@echo "Website Visitor Analysis Using Hadoop - Command Reference:"
	@echo "  make up               - Build and start Hadoop + Hive single-node cluster"
	@echo "  make down             - Stop the cluster containers"
	@echo "  make clean            - Stop cluster and remove all volumes, exports, and build outputs"
	@echo "  make status           - Display cluster service status and HDFS report"
	@echo "  make test-infra       - Run sample WordCount job to verify HDFS and YARN execution"
	@echo "  make generate-data    - Generate synthetic web server access logs"
	@echo "  make ingest           - Ingest generated logs into HDFS date-partitioned raw paths"
	@echo "  make build-mapreduce  - Compile and package MapReduce Java Maven project"
	@echo "  make run-all          - Execute full pipeline: ingest, clean, analytics, exports, and dashboard"

up:
	docker compose -f $(COMPOSE_FILE) up -d --build
	@echo "Waiting for HDFS NameNode to be responsive..."
	@until docker exec $(CONTAINER_NAME) hdfs dfs -test -d /weblogs/raw 2>/dev/null; do \
		echo "Waiting for HDFS initialization..."; \
		sleep 3; \
	done
	@echo "Hadoop & Hive single-node cluster is UP and READY!"
	@echo "NameNode Web UI:       http://localhost:9870"
	@echo "ResourceManager Web UI: http://localhost:8088"
	@echo "HiveServer2 Web UI:    http://localhost:10002"

down:
	docker compose -f $(COMPOSE_FILE) down

clean:
	docker compose -f $(COMPOSE_FILE) down -v --remove-orphans
	rm -rf data/raw/* exports/*.csv exports/*.json mapreduce/target

status:
	docker compose -f $(COMPOSE_FILE) ps
	@echo "--- HDFS DFSAdmin Report ---"
	docker exec $(CONTAINER_NAME) hdfs dfsadmin -report
	@echo "--- YARN NodeManager List ---"
	docker exec $(CONTAINER_NAME) yarn node -list -all
	@echo "--- HDFS Root Listing ---"
	docker exec $(CONTAINER_NAME) hdfs dfs -ls -R /weblogs

test-infra:
	@echo "=== [Phase 1 Verification] Running Sample WordCount Job on YARN ==="
	docker exec $(CONTAINER_NAME) bash -c '\
		echo -e "hadoop mapreduce\nhadoop hdfs yarn\nwebsite visitor analysis hadoop" > /tmp/wc_input.txt && \
		hdfs dfs -rm -r -f -skipTrash /tmp/wc_in /tmp/wc_out && \
		hdfs dfs -mkdir -p /tmp/wc_in && \
		hdfs dfs -put -f /tmp/wc_input.txt /tmp/wc_in/ && \
		yarn jar /opt/hadoop/share/hadoop/mapreduce/hadoop-mapreduce-examples-*.jar wordcount /tmp/wc_in /tmp/wc_out && \
		echo "--- WordCount Output Results ---" && \
		hdfs dfs -cat /tmp/wc_out/part-* && \
		hdfs dfs -rm -r -f -skipTrash /tmp/wc_in /tmp/wc_out'
	@echo "=== [Phase 1 Verification] WordCount verification PASSED successfully ==="

generate-data:
	python3 generator/log_generator.py --size 100MB --days 7 --out-dir ./data/raw

ingest:
	docker exec $(CONTAINER_NAME) bash /workspace/scripts/ingest.sh

build-mapreduce:
	docker exec $(CONTAINER_NAME) bash -c "cd /workspace/mapreduce && mvn clean package -DskipTests=false"

run-all: up generate-data ingest build-mapreduce
	@echo "Executing End-to-End Pipeline..."
	docker exec $(CONTAINER_NAME) bash /workspace/scripts/run_pipeline.sh
	docker exec $(CONTAINER_NAME) bash /workspace/scripts/export.sh
	@echo "Pipeline completed! Analytical results exported to /exports/."
	@echo "Starting Dashboard..."
	cd dashboard && npm run dev

