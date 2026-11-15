#!/bin/bash
set -e

echo "=== [Hadoop/Hive Entrypoint] Initializing Single-Node Pseudo-Distributed Cluster ==="

export HADOOP_HOME=/opt/hadoop
export HADOOP_CONF_DIR=$HADOOP_HOME/etc/hadoop
export HIVE_HOME=/opt/hive
export HIVE_CONF_DIR=$HIVE_HOME/conf
export PATH=$PATH:$HADOOP_HOME/bin:$HADOOP_HOME/sbin:$HIVE_HOME/bin

mkdir -p /opt/hadoop/logs /opt/hive/logs
mkdir -p /opt/hadoop/data/dfs/name /opt/hadoop/data/dfs/data /opt/hadoop/data/tmp

# 1. Format NameNode if not formatted
if [ ! -d "/opt/hadoop/data/dfs/name/current" ]; then
    echo "Formatting HDFS NameNode..."
    $HADOOP_HOME/bin/hdfs namenode -format -force -nonInteractive
fi

# 2. Start HDFS Daemons
echo "Starting HDFS NameNode..."
$HADOOP_HOME/bin/hdfs --daemon start namenode

echo "Starting HDFS DataNode..."
$HADOOP_HOME/bin/hdfs --daemon start datanode

echo "Waiting for HDFS to leave Safe Mode..."
$HADOOP_HOME/bin/hdfs dfsadmin -safemode wait || true

# 3. Create Required HDFS Directory Structure
echo "Setting up HDFS directory layout per PRD Section 4.3..."
$HADOOP_HOME/bin/hdfs dfs -mkdir -p /tmp
$HADOOP_HOME/bin/hdfs dfs -mkdir -p /weblogs/raw
$HADOOP_HOME/bin/hdfs dfs -mkdir -p /weblogs/clean
$HADOOP_HOME/bin/hdfs dfs -mkdir -p /weblogs/rejected
$HADOOP_HOME/bin/hdfs dfs -mkdir -p /weblogs/output
$HADOOP_HOME/bin/hdfs dfs -mkdir -p /user/hive/warehouse
$HADOOP_HOME/bin/hdfs dfs -chmod -R 777 /tmp /weblogs /user

# 4. Start YARN Daemons
echo "Starting YARN ResourceManager..."
$HADOOP_HOME/bin/yarn --daemon start resourcemanager

echo "Starting YARN NodeManager..."
$HADOOP_HOME/bin/yarn --daemon start nodemanager

# 5. Initialize Hive Metastore if needed
if [ ! -d "/opt/hive/metastore_db" ]; then
    echo "Initializing Apache Hive Derby Metastore Schema..."
    $HIVE_HOME/bin/schematool -dbType derby -initSchema --verbose || true
fi

# 6. Start Hive Services
echo "Starting Hive Metastore..."
nohup $HIVE_HOME/bin/hive --service metastore > /opt/hive/logs/metastore.log 2>&1 &

echo "Starting HiveServer2..."
nohup $HIVE_HOME/bin/hive --service hiveserver2 > /opt/hive/logs/hiveserver2.log 2>&1 &

echo "=== [Hadoop/Hive Entrypoint] All services started successfully ==="
echo "  - HDFS NameNode Web UI: http://localhost:9870"
echo "  - YARN ResourceManager Web UI: http://localhost:8088"
echo "  - YARN NodeManager Web UI: http://localhost:8042"
echo "  - HiveServer2 Web UI: http://localhost:10002"
echo "  - Hive Thrift JDBC: localhost:10000"

# Keep container alive and stream logs
exec tail -f /opt/hadoop/logs/*.log

