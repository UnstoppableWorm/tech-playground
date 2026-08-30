package olapreadlab.aggregation.adapter.jdbc.compiler;

import olapreadlab.aggregation.model.MeasureDefinition.RawAggregation;

public record MeasureProjection(
		String name,
		RawAggregation rawAggregation,
		String alias) {
}
