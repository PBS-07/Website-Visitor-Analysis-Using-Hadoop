-- ==============================================================================
-- FR-10: Unique Visitors Per Day (HiveQL Equivalent)
-- Distinct count of visitor hashes grouped by date
-- ==============================================================================

USE visitor_analytics;

SELECT 
    SUBSTR(event_time, 1, 10) AS log_date,
    COUNT(DISTINCT visitor_ip_hash) AS unique_visitors
FROM weblogs_clean
GROUP BY SUBSTR(event_time, 1, 10)
ORDER BY log_date ASC;

