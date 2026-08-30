package olapreadlab.aggregation.adapter.jdbc.mapping;

import java.util.Map;

public record AggregateViewStorageBinding(
		String view,
		Map<StorageBindingKey, AggregateTableBinding> tables) {

	public AggregateViewStorageBinding {
		tables = Map.copyOf(tables);
	}

	public AggregateTableBinding requiredTableFor(StorageBindingKey storageKey) {
		var table = tables.get(storageKey);
		if (table == null) {
			throw new IllegalArgumentException(
					"No " + storageKey.value() + " table binding for view: " + view);
		}
		return table;
	}
}
