\set ON_ERROR_STOP on
\timing on

-- 실행기가 :block_size 단위로 아래 범위를 나눠 호출한다.
-- 예: psql -v range_start=1 -v range_end=1000000 -f backfill-10m.sql
UPDATE olap.medical_history
SET
    metric_value = metric_value + 100,
    updated_at = clock_timestamp()
WHERE event_id BETWEEN :range_start AND :range_end;
