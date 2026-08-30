package olapreadlab.aggregation.model;

public record MeasureDefinition(
		String name,
		RawAggregation rawAggregation) {

	public enum RawAggregation {
		COUNT_ROWS,
		SUM
	}
}
