\set ON_ERROR_STOP on
\timing on

TRUNCATE TABLE olap.agg_person_organ_disease;

INSERT INTO olap.agg_person_organ_disease
    (bucket_date, person_id, organ_code, disease_code, event_count, metric_sum, refreshed_at)
SELECT
    (occurred_at AT TIME ZONE 'UTC')::date,
    person_id,
    organ_code,
    disease_code,
    count(*),
    sum(metric_value),
    clock_timestamp()
FROM olap.medical_history
GROUP BY (occurred_at AT TIME ZONE 'UTC')::date, person_id, organ_code, disease_code;

ANALYZE olap.agg_person_organ_disease;
