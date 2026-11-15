-- ==============================================================================
-- FR-9: Hits Per Page (HiveQL Equivalent)
-- Counts requests per URL excluding static assets, ranked by hits descending
-- ==============================================================================

USE visitor_analytics;

SELECT 
    url,
    COUNT(1) AS hits
FROM weblogs_clean
WHERE 
    url NOT LIKE '%.css' AND
    url NOT LIKE '%.js' AND
    url NOT LIKE '%.png' AND
    url NOT LIKE '%.jpg' AND
    url NOT LIKE '%.jpeg' AND
    url NOT LIKE '%.gif' AND
    url NOT LIKE '%.svg' AND
    url NOT LIKE '%.ico'
GROUP BY url
ORDER BY hits DESC;

