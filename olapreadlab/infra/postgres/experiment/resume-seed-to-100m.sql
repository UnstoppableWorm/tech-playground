\set ON_ERROR_STOP on
\timing on

SELECT format($statement$
    INSERT INTO olap.medical_history
        (event_id, person_id, organ_code, disease_code, occurred_at, metric_value, updated_at)
    SELECT
        sequence AS event_id,
        1 + ((sequence - 1) %% 10000000) AS person_id,
        1 + ((sequence * 7) %% 20) AS organ_code,
        1 + ((sequence * 13 + sequence / 1000) %% 500) AS disease_code,
        TIMESTAMPTZ '2025-01-01 00:00:00+00'
            + ((sequence - 1) %% 365) * INTERVAL '1 day'
            + ((sequence * 17) %% 86400) * INTERVAL '1 second' AS occurred_at,
        1 + ((sequence * 31) %% 10000) AS metric_value,
        TIMESTAMPTZ '2026-01-01 00:00:00+00' AS updated_at
    FROM generate_series(%s::bigint, %s::bigint) AS source(sequence)
    ON CONFLICT (event_id) DO NOTHING;
$statement$, range_start, least(range_start + 999999, 100000000))
FROM generate_series(
    (SELECT coalesce(max(event_id), 0) + 1 FROM olap.medical_history),
    100000000,
    1000000
) AS ranges(range_start)
\gexec

CREATE INDEX IF NOT EXISTS medical_history_occurred_at_idx
    ON olap.medical_history (occurred_at);

ANALYZE olap.medical_history;

SELECT count(*) AS seeded_row_count,
       min(event_id) AS min_event_id,
       max(event_id) AS max_event_id
FROM olap.medical_history;
