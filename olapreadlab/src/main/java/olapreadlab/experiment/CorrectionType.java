package olapreadlab.experiment;

/**
 * 과거 데이터 보정에서 각 조회 경로의 유지 비용을 비교하기 위한 시나리오이다.
 */
public enum CorrectionType {
	UPDATE,
	DELETE,
	BACKFILL
}
