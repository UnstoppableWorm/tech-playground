package olapreadlab.experiment;

/**
 * 동일한 PostgreSQL 원천 데이터를 조회 결과로 만드는 세 가지 실험 경로이다.
 */
public enum ReadPath {
	POSTGRES_RAW_APPLICATION_AGGREGATION,
	POSTGRES_AGGREGATE_TABLE,
	CLICKHOUSE_MATERIALIZED_VIEW
}
