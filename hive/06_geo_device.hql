-- ==============================================================================
-- FR-13: Geo, Device, Browser, and OS Breakdown (HiveQL Equivalent)
-- ==============================================================================

USE visitor_analytics;

-- Country breakdown
SELECT 'COUNTRY' AS dimension, country AS dim_value, COUNT(1) AS count
FROM weblogs_clean GROUP BY country
UNION ALL
-- Device type breakdown
SELECT 'DEVICE' AS dimension, device_type AS dim_value, COUNT(1) AS count
FROM weblogs_clean GROUP BY device_type
UNION ALL
-- Browser breakdown
SELECT 'BROWSER' AS dimension, browser AS dim_value, COUNT(1) AS count
FROM weblogs_clean GROUP BY browser
UNION ALL
-- OS breakdown
SELECT 'OS' AS dimension, os AS dim_value, COUNT(1) AS count
FROM weblogs_clean GROUP BY os;

