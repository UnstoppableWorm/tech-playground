package olapreadlab.aggregation.application;

import java.time.Instant;
import java.util.List;

import olapreadlab.aggregation.model.ResultRow;
import olapreadlab.aggregation.model.QueryMode;

public record QueryResult(
		String model,
		String view,
		QueryMode mode,
		Instant coveredUntil,
		List<ResultRow> rows) {

	public QueryResult {
		rows = List.copyOf(rows);
	}
}
