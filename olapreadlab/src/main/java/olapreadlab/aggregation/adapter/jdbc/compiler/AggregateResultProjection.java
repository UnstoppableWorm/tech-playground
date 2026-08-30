package olapreadlab.aggregation.adapter.jdbc.compiler;

import java.util.List;

import olapreadlab.aggregation.model.TimeBucket;

public record AggregateResultProjection(
		TimeBucket bucket,
		String bucketAlias,
		List<DimensionProjection> dimensions,
		List<MeasureProjection> measures) {

	public AggregateResultProjection {
		dimensions = List.copyOf(dimensions);
		measures = List.copyOf(measures);
	}
}
