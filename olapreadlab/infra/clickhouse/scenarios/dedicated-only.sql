-- 전용 MV 실험에서는 020-rollups.sql의 8개 MV를 모두 유지한다.
-- 이 파일은 실행 전에 예상 활성 MV 수를 검증한다.
SELECT throwIf(
    count() != 8,
    concat('expected 8 active rollup materialized views, actual=', toString(count()))
)
FROM system.tables
WHERE database = 'olap_clickhouse'
  AND engine = 'MaterializedView'
  AND name LIKE 'mv_%';
