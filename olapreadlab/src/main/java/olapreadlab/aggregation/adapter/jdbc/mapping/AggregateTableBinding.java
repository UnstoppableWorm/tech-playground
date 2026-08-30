package olapreadlab.aggregation.adapter.jdbc.mapping;

import java.util.Map;

public record AggregateTableBinding(
		SqlIdentifier table,
		SqlIdentifier bucketColumn,
		Map<String, SqlIdentifier> dimensionColumns,
		Map<String, SqlIdentifier> measureColumns) {

	public AggregateTableBinding {
		dimensionColumns = Map.copyOf(dimensionColumns);
		measureColumns = Map.copyOf(measureColumns);
	}
}
