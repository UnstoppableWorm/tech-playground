package olapreadlab.api.graphql;

import java.util.List;

import olapreadlab.aggregation.application.AggregationResult;
import olapreadlab.aggregation.model.QueryMode;

public record AggregationGraphQlResult(
		String model,
		String view,
		QueryMode mode,
		String aggregateCoveredUntil,
		List<AggregationGraphQlRow> rows) {

	public static AggregationGraphQlResult from(AggregationResult result) {
		return new AggregationGraphQlResult(
				result.model(),
				result.view(),
				result.mode(),
				result.aggregateCoveredUntil() == null
						? null : result.aggregateCoveredUntil().toString(),
				result.rows().stream().map(AggregationGraphQlRow::from).toList());
	}
}
