package olapreadlab.api.graphql;

import java.util.List;

import olapreadlab.aggregation.model.AggregationRow;

public record AggregationGraphQlRow(
		String bucket,
		List<AggregationGraphQlValue> dimensions,
		List<AggregationGraphQlValue> measures) {

	static AggregationGraphQlRow from(AggregationRow row) {
		return new AggregationGraphQlRow(
				row.bucket().toString(),
				row.dimensions().entrySet().stream()
						.map(entry -> new AggregationGraphQlValue(entry.getKey(), entry.getValue().toString()))
						.toList(),
				row.measures().entrySet().stream()
						.map(entry -> new AggregationGraphQlValue(entry.getKey(), entry.getValue().toPlainString()))
						.toList());
	}
}
