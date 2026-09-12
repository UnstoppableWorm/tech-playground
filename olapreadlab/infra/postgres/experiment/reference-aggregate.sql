SELECT
    (occurred_at AT TIME ZONE 'UTC')::date AS bucket_date,
    person_id,
    organ_code,
    disease_code,
    count(*) AS event_count,
    sum(metric_value) AS metric_sum
FROM olap.medical_history
GROUP BY (occurred_at AT TIME ZONE 'UTC')::date, person_id, organ_code, disease_code
ORDER BY bucket_date, person_id, organ_code, disease_code;
