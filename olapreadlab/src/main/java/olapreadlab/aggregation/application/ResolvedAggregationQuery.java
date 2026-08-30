package olapreadlab.aggregation.application;

import olapreadlab.aggregation.model.AggregateViewDefinition;
import olapreadlab.aggregation.model.AggregationModelDefinition;
import olapreadlab.aggregation.model.filter.FilterExpression;

public record ResolvedAggregationQuery(
		AggregationQuery query,
		AggregationModelDefinition model,
		AggregateViewDefinition view,
		FilterExpression where,
		FilterExpression having) {

	public ResolvedAggregationQuery {
		where = where == null ? FilterExpression.MATCH_ALL : where;
		having = having == null ? FilterExpression.MATCH_ALL : having;
	}
}
