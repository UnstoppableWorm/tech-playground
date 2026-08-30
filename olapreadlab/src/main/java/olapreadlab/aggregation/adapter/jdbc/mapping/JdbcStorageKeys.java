package olapreadlab.aggregation.adapter.jdbc.mapping;

public final class JdbcStorageKeys {

	public static final StorageBindingKey POSTGRES = new StorageBindingKey("postgres");
	public static final StorageBindingKey CLICKHOUSE = new StorageBindingKey("clickhouse");

	private JdbcStorageKeys() {
	}
}
