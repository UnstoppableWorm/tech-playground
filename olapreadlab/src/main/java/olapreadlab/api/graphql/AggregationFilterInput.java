package olapreadlab.api.graphql;

import java.util.List;

public record AggregationFilterInput(String field, List<String> values) {

	public AggregationFilterInput {
		values = values == null ? List.of() : List.copyOf(values);
	}
}
