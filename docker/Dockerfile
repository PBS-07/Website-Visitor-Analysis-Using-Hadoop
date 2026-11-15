FROM eclipse-temurin:11-jdk-jammy

LABEL maintainer="Bhagath"
LABEL description="Single-node pseudo-distributed Hadoop 3.3.6 + Hive 3.1.3 for Website Visitor Analysis"

ENV DEBIAN_FRONTEND=noninteractive
ENV HADOOP_VERSION=3.3.6
ENV HIVE_VERSION=3.1.3
ENV HADOOP_HOME=/opt/hadoop
ENV HIVE_HOME=/opt/hive
ENV JAVA_HOME=/opt/java/openjdk
ENV PATH=$PATH:$HADOOP_HOME/bin:$HADOOP_HOME/sbin:$HIVE_HOME/bin

# 1. Install system dependencies, Python, Maven, tools
RUN apt-get update && apt-get install -y --no-install-recommends \
    curl \
    wget \
    procps \
    ssh \
    rsync \
    netcat \
    dos2unix \
    python3 \
    python3-pip \
    python3-venv \
    maven \
    && rm -rf /var/lib/apt/lists/*

# 2. Download and unpack Hadoop 3.3.6
WORKDIR /opt
RUN wget -q https://archive.apache.org/dist/hadoop/common/hadoop-${HADOOP_VERSION}/hadoop-${HADOOP_VERSION}.tar.gz \
    && tar -xzf hadoop-${HADOOP_VERSION}.tar.gz \
    && mv hadoop-${HADOOP_VERSION} hadoop \
    && rm hadoop-${HADOOP_VERSION}.tar.gz

# 3. Download and unpack Hive 3.1.3
RUN wget -q https://archive.apache.org/dist/hive/hive-${HIVE_VERSION}/apache-hive-${HIVE_VERSION}-bin.tar.gz \
    && tar -xzf apache-hive-${HIVE_VERSION}-bin.tar.gz \
    && mv apache-hive-${HIVE_VERSION}-bin hive \
    && rm apache-hive-${HIVE_VERSION}-bin.tar.gz

# 4. Resolve Hive 3.1.3 Guava incompatibility with Hadoop 3.3.x
RUN rm -f /opt/hive/lib/guava-19.0.jar \
    && cp /opt/hadoop/share/hadoop/common/lib/guava-27.0-jre.jar /opt/hive/lib/

# 5. Copy Hadoop and Hive XML Configurations
COPY docker/conf/core-site.xml /opt/hadoop/etc/hadoop/core-site.xml
COPY docker/conf/hdfs-site.xml /opt/hadoop/etc/hadoop/hdfs-site.xml
COPY docker/conf/yarn-site.xml /opt/hadoop/etc/hadoop/yarn-site.xml
COPY docker/conf/mapred-site.xml /opt/hadoop/etc/hadoop/mapred-site.xml
COPY docker/conf/hive-site.xml /opt/hive/conf/hive-site.xml

# Configure hadoop-env.sh
RUN echo "export JAVA_HOME=${JAVA_HOME}" >> /opt/hadoop/etc/hadoop/hadoop-env.sh \
    && echo "export HADOOP_HOME=${HADOOP_HOME}" >> /opt/hadoop/etc/hadoop/hadoop-env.sh

# 6. Create directories for HDFS storage and workspace
RUN mkdir -p /opt/hadoop/data/dfs/name \
             /opt/hadoop/data/dfs/data \
             /opt/hadoop/data/tmp \
             /opt/hadoop/logs \
             /opt/hive/logs \
             /workspace

# 7. Copy and prepare entrypoint script
COPY docker/entrypoint.sh /entrypoint.sh
RUN dos2unix /entrypoint.sh && chmod +x /entrypoint.sh

WORKDIR /workspace

EXPOSE 9870 9000 8088 8042 10000 10002

ENTRYPOINT ["/entrypoint.sh"]

