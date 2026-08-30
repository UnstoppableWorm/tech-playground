\set ON_ERROR_STOP on
\timing on

-- PostgreSQL가 직접 GROUP BY하는 공정한 raw baseline.
SELECT
    occurred_at::date AS bucket_date,
    organ_code,
    disease_code,
    count(*) AS event_count,
    sum(metric_value) AS metric_sum
FROM olap.medical_history
WHERE occurred_at >= :'window_start'::timestamptz
  AND occurred_at < :'window_end'::timestamptz
GROUP BY occurred_at::date, organ_code, disease_code
ORDER BY bucket_date, organ_code, disease_code;
