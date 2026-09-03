package olapreadlab.aggregation.adapter.jdbc.compiler;

import java.util.List;

import olapreadlab.aggregation.model.TimeBucket;

public record RollupResultProjection(
		TimeBucket bucket,
		String bucketAlias,
		List<DimensionProjection> dimensions,
		List<MeasureProjection> measures) {

	public RollupResultProjection {
		dimensions = List.copyOf(dimensions);
		measures = List.copyOf(measures);
	}
}
