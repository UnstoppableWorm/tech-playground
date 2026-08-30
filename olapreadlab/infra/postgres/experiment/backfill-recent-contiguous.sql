\set ON_ERROR_STOP on
\timing on

UPDATE olap.medical_history
SET metric_value = metric_value + 100,
    updated_at = clock_timestamp()
WHERE event_id BETWEEN :range_start AND :range_end;
