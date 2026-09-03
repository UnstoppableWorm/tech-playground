package olapreadlab.api.graphql;

import java.util.List;

public record OlapFilterArgumentInput(String name, List<String> values) {
	public OlapFilterArgumentInput {
		values = values == null ? List.of() : List.copyOf(values);
	}
}
