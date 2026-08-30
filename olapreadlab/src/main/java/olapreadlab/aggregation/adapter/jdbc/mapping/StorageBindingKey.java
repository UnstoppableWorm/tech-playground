package olapreadlab.aggregation.adapter.jdbc.mapping;

public record StorageBindingKey(String value) {

	public StorageBindingKey {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException("Storage binding key must not be blank");
		}
	}
}
