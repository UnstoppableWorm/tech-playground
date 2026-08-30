\set ON_ERROR_STOP on
\timing on

SELECT
    bucket_date,
    organ_code,
    disease_code,
    sum(event_count) AS event_count,
    sum(metric_sum) AS metric_sum
FROM olap.agg_person_organ_disease
WHERE bucket_date >= :'window_start'::date
  AND bucket_date < :'window_end'::date
GROUP BY bucket_date, organ_code, disease_code
ORDER BY bucket_date, organ_code, disease_code;
