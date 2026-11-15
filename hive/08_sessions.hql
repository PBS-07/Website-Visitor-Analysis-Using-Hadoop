-- ==============================================================================
-- FR-15 & FR-16: Session Analysis & Bounce Rate (HiveQL Window Functions)
-- 30-minute inactivity sessionization using LAG() and SUM() window functions
-- ==============================================================================

USE visitor_analytics;

WITH human_pageviews AS (
    SELECT 
        visitor_ip_hash,
        event_time,
        UNIX_TIMESTAMP(event_time, 'yyyy-MM-dd HH:mm:ss') AS epoch_sec,
        url
    FROM weblogs_clean
    WHERE device_type != 'Bot'
      AND url NOT LIKE '%.css'
      AND url NOT LIKE '%.js'
      AND url NOT LIKE '%.png'
      AND url NOT LIKE '%.jpg'
),
session_flags AS (
    SELECT 
        visitor_ip_hash,
        event_time,
        epoch_sec,
        CASE 
            WHEN epoch_sec - LAG(epoch_sec, 1) OVER (PARTITION BY visitor_ip_hash ORDER BY epoch_sec) > 1800 
                 OR LAG(epoch_sec, 1) OVER (PARTITION BY visitor_ip_hash ORDER BY epoch_sec) IS NULL 
            THEN 1 
            ELSE 0 
        END AS is_new_session
    FROM human_pageviews
),
sessions_assigned AS (
    SELECT 
        visitor_ip_hash,
        event_time,
        epoch_sec,
        SUM(is_new_session) OVER (PARTITION BY visitor_ip_hash ORDER BY epoch_sec ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW) AS session_id
    FROM session_flags
),
session_summaries AS (
    SELECT 
        visitor_ip_hash,
        session_id,
        COUNT(1) AS page_views,
        MAX(epoch_sec) - MIN(epoch_sec) AS duration_seconds
    FROM sessions_assigned
    GROUP BY visitor_ip_hash, session_id
)
SELECT 
    COUNT(1) AS total_sessions,
    SUM(CASE WHEN page_views = 1 THEN 1 ELSE 0 END) AS total_bounces,
    ROUND(SUM(CASE WHEN page_views = 1 THEN 1 ELSE 0 END) / COUNT(1), 4) AS bounce_rate,
    ROUND(AVG(page_views), 2) AS avg_pages_per_session,
    ROUND(AVG(duration_seconds), 1) AS avg_session_duration_seconds
FROM session_summaries;

