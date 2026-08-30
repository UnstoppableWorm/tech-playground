package olapreadlab.aggregation.adapter.jdbc.mapping;

import java.util.Map;

public record RawTableBinding(
		SqlIdentifier table,
		SqlIdentifier eventTimeColumn,
		Map<String, SqlIdentifier> dimensionColumns,
		Map<String, SqlIdentifier> measureColumns) {

	public RawTableBinding {
		dimensionColumns = Map.copyOf(dimensionColumns);
		measureColumns = Map.copyOf(measureColumns);
	}
}
