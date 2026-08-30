package olapreadlab.experiment;

/**
 * OLAP 조회 방법을 같은 입력과 결과 계약으로 비교하기 위한 경계이다.
 */
public interface ReadStrategy<R> {

	String name();

	R read();
}
