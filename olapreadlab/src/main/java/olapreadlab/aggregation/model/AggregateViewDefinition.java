package olapreadlab.aggregation.model;

import java.util.List;
import java.util.Set;

public record AggregateViewDefinition(
		String name,
		TimeBucket bucket,
		List<String> dimensions,
		Set<String> allowedFilters) {

	public AggregateViewDefinition {
		dimensions = List.copyOf(dimensions);
		allowedFilters = Set.copyOf(allowedFilters);
	}
}
