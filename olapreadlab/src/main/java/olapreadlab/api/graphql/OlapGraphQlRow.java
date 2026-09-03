package olapreadlab.api.graphql;

import java.util.List;

import olapreadlab.aggregation.model.ResultRow;

public record OlapGraphQlRow(
		String bucket,
		List<OlapGraphQlValue> dimensions,
		List<OlapGraphQlValue> measures) {

	static OlapGraphQlRow from(ResultRow row) {
		return new OlapGraphQlRow(
				row.bucket().toString(),
				row.dimensions().entrySet().stream()
						.map(entry -> new OlapGraphQlValue(entry.getKey(), entry.getValue().toString()))
						.toList(),
				row.measures().entrySet().stream()
						.map(entry -> new OlapGraphQlValue(entry.getKey(), entry.getValue().toPlainString()))
						.toList());
	}
}
