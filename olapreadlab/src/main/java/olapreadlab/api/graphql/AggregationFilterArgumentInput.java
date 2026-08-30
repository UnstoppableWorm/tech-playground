package olapreadlab.api.graphql;

import java.util.List;

public record AggregationFilterArgumentInput(String name, List<String> values) {
	public AggregationFilterArgumentInput {
		values = values == null ? List.of() : List.copyOf(values);
	}
}
