-- 질의 grain별 전용 MV를 유지하는 전략의 장기+질병 조회 예시.
SELECT
    bucket_date,
    organ_code,
    disease_code,
    sum(event_count) AS event_count,
    sum(metric_sum) AS metric_sum
FROM olap_clickhouse.agg_organ_disease
GROUP BY bucket_date, organ_code, disease_code
ORDER BY bucket_date, organ_code, disease_code;
