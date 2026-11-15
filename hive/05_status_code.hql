-- ==============================================================================
-- FR-12: Status Code and Error Analysis (HiveQL Equivalent)
-- ==============================================================================

USE visitor_analytics;

-- Status Code Distribution
SELECT 
    status_code,
    COUNT(1) AS count
FROM weblogs_clean
GROUP BY status_code
ORDER BY count DESC;

-- Status Class Distribution
SELECT 
    CONCAT(CAST(FLOOR(status_code / 100) AS STRING), 'xx') AS status_class,
    COUNT(1) AS count
FROM weblogs_clean
GROUP BY FLOOR(status_code / 100)
ORDER BY count DESC;

-- Top Error URLs (4xx and 5xx)
SELECT 
    status_code,
    url,
    COUNT(1) AS error_count
FROM weblogs_clean
WHERE status_code >= 400
GROUP BY status_code, url
ORDER BY error_count DESC;

