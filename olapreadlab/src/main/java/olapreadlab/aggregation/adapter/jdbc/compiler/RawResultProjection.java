package olapreadlab.aggregation.adapter.jdbc.compiler;

import java.util.List;

import olapreadlab.aggregation.model.TimeBucket;

public record RawResultProjection(
		TimeBucket bucket,
		String eventTimeAlias,
		List<DimensionProjection> dimensions,
		List<MeasureProjection> measures) {

	public RawResultProjection {
		dimensions = List.copyOf(dimensions);
		measures = List.copyOf(measures);
	}
}
