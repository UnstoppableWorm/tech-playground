package olapreadlab.aggregation.model;

import java.util.Map;

public record AggregationModelDefinition(
		String name,
		Map<String, DimensionDefinition> dimensions,
		Map<String, MeasureDefinition> measures,
		Map<String, AggregateViewDefinition> views) {

	public AggregationModelDefinition {
		dimensions = Map.copyOf(dimensions);
		measures = Map.copyOf(measures);
		views = Map.copyOf(views);
		for (var view : views.values()) {
			if (!dimensions.keySet().containsAll(view.dimensions())) {
				throw new IllegalArgumentException("Unknown dimension in view " + view.name());
			}
			if (!view.dimensions().containsAll(view.allowedFilters())) {
				throw new IllegalArgumentException(
						"A stored aggregate can only filter by dimensions retained in view " + view.name());
			}
		}
	}
}
