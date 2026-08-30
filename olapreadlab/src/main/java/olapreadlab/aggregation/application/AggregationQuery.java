package olapreadlab.aggregation.application;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import olapreadlab.aggregation.model.QueryMode;
import olapreadlab.aggregation.model.filter.FilterExpression;
import olapreadlab.aggregation.model.filter.LogicalOperator;

public record AggregationQuery(
		String model,
		String view,
		QueryMode mode,
		Instant fromInclusive,
		Instant toExclusive,
		Map<String, List<Object>> filters,
		FilterExpression where,
		FilterExpression having) {

	public AggregationQuery {
		var copied = new LinkedHashMap<String, List<Object>>();
		if (filters != null) filters.forEach((name, values) -> copied.put(name, List.copyOf(values)));
		filters = Collections.unmodifiableMap(copied);
		where = where == null ? FilterExpression.MATCH_ALL : where;
		having = having == null ? FilterExpression.MATCH_ALL : having;
	}

	public AggregationQuery(
			String model, String view, QueryMode mode, Instant fromInclusive, Instant toExclusive,
			Map<String, List<Object>> filters) {
		this(model, view, mode, fromInclusive, toExclusive, filters,
				FilterExpression.MATCH_ALL, FilterExpression.MATCH_ALL);
	}

	public FilterExpression combinedWhere() {
		if (filters.isEmpty()) return where;
		var legacy = filters.entrySet().stream()
				.filter(entry -> !entry.getValue().isEmpty())
				.map(entry -> (FilterExpression) new FilterExpression.In(entry.getKey(), entry.getValue()))
				.toList();
		if (legacy.isEmpty()) return where;
		if (where instanceof FilterExpression.MatchAll) {
			return legacy.size() == 1 ? legacy.getFirst() : new FilterExpression.Junction(LogicalOperator.AND, legacy);
		}
		var all = new java.util.ArrayList<>(legacy);
		all.add(where);
		return new FilterExpression.Junction(LogicalOperator.AND, all);
	}
}
