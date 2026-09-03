package olapreadlab.api.graphql;

import java.util.List;

import olapreadlab.aggregation.application.QueryResult;
import olapreadlab.aggregation.model.QueryMode;

public record OlapGraphQlResult(
		String model,
		String view,
		QueryMode mode,
		String coveredUntil,
		List<OlapGraphQlRow> rows) {

	public static OlapGraphQlResult from(QueryResult result) {
		return new OlapGraphQlResult(
				result.model(),
				result.view(),
				result.mode(),
				result.coveredUntil() == null
						? null : result.coveredUntil().toString(),
				result.rows().stream().map(OlapGraphQlRow::from).toList());
	}
}
