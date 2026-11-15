-- ==============================================================================
-- FR-14: Referrer Analysis (HiveQL Equivalent)
-- ==============================================================================

USE visitor_analytics;

SELECT 
    CASE 
        WHEN referrer IS NULL OR referrer = '' OR referrer = '-' THEN 'Direct / None' 
        ELSE referrer 
    END AS referrer_source,
    COUNT(1) AS visits
FROM weblogs_clean
WHERE 
    url NOT LIKE '%.css' AND
    url NOT LIKE '%.js' AND
    url NOT LIKE '%.png' AND
    url NOT LIKE '%.jpg'
GROUP BY 
    CASE 
        WHEN referrer IS NULL OR referrer = '' OR referrer = '-' THEN 'Direct / None' 
        ELSE referrer 
    END
ORDER BY visits DESC;

