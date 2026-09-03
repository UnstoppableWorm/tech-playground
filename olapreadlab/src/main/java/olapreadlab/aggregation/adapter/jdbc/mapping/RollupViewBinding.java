package olapreadlab.aggregation.adapter.jdbc.mapping;

import java.util.Map;

public record RollupViewBinding(
		String view,
		Map<StorageBindingKey, RollupTableBinding> tables) {

	public RollupViewBinding {
		tables = Map.copyOf(tables);
	}

	public RollupTableBinding requiredTableFor(StorageBindingKey storageKey) {
		var table = tables.get(storageKey);
		if (table == null) {
			throw new IllegalArgumentException(
					"No " + storageKey.value() + " table binding for view: " + view);
		}
		return table;
	}
}
