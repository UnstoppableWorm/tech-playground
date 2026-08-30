package olapreadlab.api.graphql;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import olapreadlab.aggregation.application.AggregationQuery;
import olapreadlab.aggregation.model.QueryMode;

public record AggregationQueryInput(
		String model,
		String view,
		QueryMode mode,
		String fromInclusive,
		String toExclusive,
		List<AggregationFilterInput> filters,
		AggregationPredicateInput where,
		AggregationPredicateInput having) {

	public AggregationQueryInput {
		filters = filters == null ? List.of() : List.copyOf(filters);
	}

	public AggregationQuery toQuery() {
		var mappedFilters = new LinkedHashMap<String, List<Object>>();
		for (var filter : filters) {
			if (filter.field() == null || filter.field().isBlank()) {
				throw new IllegalArgumentException("Filter field must not be blank");
			}
			if (filter.values().size() > 1_000) {
				throw new IllegalArgumentException("Each filter accepts at most 1000 values");
			}
			var values = mappedFilters.computeIfAbsent(filter.field(), ignored -> new ArrayList<>());
			values.addAll(filter.values());
		}
		if (mappedFilters.size() > 32) {
			throw new IllegalArgumentException("At most 32 filters are allowed");
		}
		return new AggregationQuery(
				model, view, mode, parseInstant("fromInclusive", fromInclusive),
				parseInstant("toExclusive", toExclusive), mappedFilters,
				where == null ? null : where.toExpression(),
				having == null ? null : having.toExpression());
	}

	private static Instant parseInstant(String field, String value) {
		try {
			return Instant.parse(value);
		}
		catch (DateTimeParseException | NullPointerException exception) {
			throw new IllegalArgumentException(field + " must be an ISO-8601 UTC instant", exception);
		}
	}
}
