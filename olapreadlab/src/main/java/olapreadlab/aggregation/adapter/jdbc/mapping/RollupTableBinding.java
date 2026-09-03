package olapreadlab.aggregation.adapter.jdbc.mapping;

import java.util.Map;

public record RollupTableBinding(
		SqlIdentifier table,
		SqlIdentifier bucketColumn,
		Map<String, SqlIdentifier> dimensionColumns,
		Map<String, SqlIdentifier> measureColumns) {

	public RollupTableBinding {
		dimensionColumns = Map.copyOf(dimensionColumns);
		measureColumns = Map.copyOf(measureColumns);
	}
}
