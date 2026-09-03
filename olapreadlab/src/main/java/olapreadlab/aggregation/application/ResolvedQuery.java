package olapreadlab.aggregation.application;

import olapreadlab.aggregation.model.ViewDefinition;
import olapreadlab.aggregation.model.ModelDefinition;
import olapreadlab.aggregation.model.filter.FilterExpression;

public record ResolvedQuery(
		QueryRequest query,
		ModelDefinition model,
		ViewDefinition view,
		FilterExpression where,
		FilterExpression having) {

	public ResolvedQuery {
		where = where == null ? FilterExpression.MATCH_ALL : where;
		having = having == null ? FilterExpression.MATCH_ALL : having;
	}
}
