-- 최하위 MV 하나만 유지하는 전략의 장기+질병 조회 예시.
-- SummingMergeTree는 background merge 전 중복 key가 남을 수 있으므로 항상 다시 sum한다.
SELECT
    bucket_date,
    organ_code,
    disease_code,
    sum(event_count) AS event_count,
    sum(metric_sum) AS metric_sum
FROM olap_clickhouse.agg_person_organ_disease
GROUP BY bucket_date, organ_code, disease_code
ORDER BY bucket_date, organ_code, disease_code;
