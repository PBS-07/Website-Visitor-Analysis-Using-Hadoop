-- ==============================================================================
-- FR-11: Traffic Over Time (HiveQL Equivalent)
-- Request counts aggregated by hourly bucket
-- ==============================================================================

USE visitor_analytics;

SELECT 
    CONCAT(SUBSTR(event_time, 1, 13), ':00') AS time_bucket,
    COUNT(1) AS hits
FROM weblogs_clean
GROUP BY CONCAT(SUBSTR(event_time, 1, 13), ':00')
ORDER BY time_bucket ASC;

