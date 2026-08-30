\set ON_ERROR_STOP on
\timing on

WITH target AS
(
    SELECT event_id
    FROM olap.medical_history
    WHERE organ_code = :organ_code
      AND disease_code = :disease_code
    ORDER BY event_id
    LIMIT :target_count
)
UPDATE olap.medical_history AS history
SET metric_value = history.metric_value + 100,
    updated_at = clock_timestamp()
FROM target
WHERE history.event_id = target.event_id;
