-- 모든 전용 집계 결과는 최하위 집계를 다시 roll-up한 결과와 일치해야 한다.
WITH
leaf AS
(
    SELECT bucket_date, organ_code, disease_code,
           sum(event_count) AS event_count,
           sum(metric_sum) AS metric_sum
    FROM olap_clickhouse.agg_person_organ_disease
    GROUP BY bucket_date, organ_code, disease_code
),
dedicated AS
(
    SELECT bucket_date, organ_code, disease_code,
           sum(event_count) AS event_count,
           sum(metric_sum) AS metric_sum
    FROM olap_clickhouse.agg_organ_disease
    GROUP BY bucket_date, organ_code, disease_code
)
SELECT count() AS mismatch_count
FROM
(
    SELECT * FROM leaf
    EXCEPT DISTINCT
    SELECT * FROM dedicated

    UNION ALL

    SELECT * FROM dedicated
    EXCEPT DISTINCT
    SELECT * FROM leaf
);
