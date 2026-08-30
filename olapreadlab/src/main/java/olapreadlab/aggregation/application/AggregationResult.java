package olapreadlab.aggregation.application;

import java.time.Instant;
import java.util.List;

import olapreadlab.aggregation.model.AggregationRow;
import olapreadlab.aggregation.model.QueryMode;

public record AggregationResult(
		String model,
		String view,
		QueryMode mode,
		Instant aggregateCoveredUntil,
		List<AggregationRow> rows) {

	public AggregationResult {
		rows = List.copyOf(rows);
	}
}
