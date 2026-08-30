\set ON_ERROR_STOP on
\timing on

WITH target AS
(
    SELECT event_id
    FROM olap.medical_history
    WHERE occurred_at >= :'month_start'::timestamptz
      AND occurred_at < :'month_start'::timestamptz + INTERVAL '1 month'
    ORDER BY event_id
    LIMIT :target_count
)
UPDATE olap.medical_history AS history
SET metric_value = history.metric_value + 100,
    updated_at = clock_timestamp()
FROM target
WHERE history.event_id = target.event_id;
