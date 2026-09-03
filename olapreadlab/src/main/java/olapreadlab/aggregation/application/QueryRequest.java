package olapreadlab.aggregation.application;

import java.time.Instant;

import olapreadlab.aggregation.model.QueryMode;
import olapreadlab.aggregation.model.filter.FilterExpression;

public record QueryRequest(
		String model,
		String view,
		QueryMode mode,
		Instant fromInclusive,
		Instant toExclusive,
		FilterExpression where,
		FilterExpression having) {

	public QueryRequest {
		where = where == null ? FilterExpression.MATCH_ALL : where;
		having = having == null ? FilterExpression.MATCH_ALL : having;
	}
}
