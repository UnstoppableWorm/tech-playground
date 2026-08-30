\set ON_ERROR_STOP on
\timing on

-- hash 조건으로 재현 가능한 분산 표본을 만든다. ORDER BY random()은 1억 건 sort를 유발하므로 사용하지 않는다.
UPDATE olap.medical_history
SET metric_value = metric_value + 100,
    updated_at = clock_timestamp()
WHERE mod(hashint8(event_id), :sample_modulus) = 0;
