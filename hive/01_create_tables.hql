-- ==============================================================================
-- Schema Definition: External Partitioned Hive Table over /weblogs/clean/
-- Conforms to PRD Section 5.2 and FR-19, FR-21
-- ==============================================================================

CREATE DATABASE IF NOT EXISTS visitor_analytics;
USE visitor_analytics;

-- 1. External partitioned table mapped directly to cleaned MapReduce output
CREATE EXTERNAL TABLE IF NOT EXISTS weblogs_clean (
    visitor_ip_hash STRING,
    event_time STRING,
    method STRING,
    url STRING,
    query_string STRING,
    status_code INT,
    bytes_sent BIGINT,
    referrer STRING,
    user_agent STRING,
    browser STRING,
    os STRING,
    device_type STRING,
    country STRING
)
PARTITIONED BY (dt STRING)
ROW FORMAT DELIMITED
FIELDS TERMINATED BY '\t'
STORED AS TEXTFILE
LOCATION '/weblogs/clean/';

-- Recover all date partitions automatically
MSCK REPAIR TABLE weblogs_clean;

-- 2. Columnar ORC Table for Query Performance Comparison (FR-21)
CREATE TABLE IF NOT EXISTS weblogs_clean_orc (
    visitor_ip_hash STRING,
    event_time STRING,
    method STRING,
    url STRING,
    query_string STRING,
    status_code INT,
    bytes_sent BIGINT,
    referrer STRING,
    user_agent STRING,
    browser STRING,
    os STRING,
    device_type STRING,
    country STRING
)
PARTITIONED BY (dt STRING)
STORED AS ORC;

-- Populate ORC table from text table
SET hive.exec.dynamic.partition=true;
SET hive.exec.dynamic.partition.mode=nonstrict;

INSERT OVERWRITE TABLE weblogs_clean_orc PARTITION (dt)
SELECT 
    visitor_ip_hash,
    event_time,
    method,
    url,
    query_string,
    status_code,
    bytes_sent,
    referrer,
    user_agent,
    browser,
    os,
    device_type,
    country,
    dt
FROM weblogs_clean;

