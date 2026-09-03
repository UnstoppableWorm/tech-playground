package olapreadlab.api.graphql;

import java.time.Instant;
import java.time.format.DateTimeParseException;

import olapreadlab.aggregation.application.QueryRequest;
import olapreadlab.aggregation.model.QueryMode;

public record OlapQueryInput(
		String model,
		String view,
		QueryMode mode,
		String fromInclusive,
		String toExclusive,
		OlapPredicateInput where,
		OlapPredicateInput having) {

	public QueryRequest toQuery() {
		return new QueryRequest(
				model, view, mode, parseInstant("fromInclusive", fromInclusive),
				parseInstant("toExclusive", toExclusive),
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
