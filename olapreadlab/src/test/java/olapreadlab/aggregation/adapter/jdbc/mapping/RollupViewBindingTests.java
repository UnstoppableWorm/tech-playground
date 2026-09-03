package olapreadlab.aggregation.adapter.jdbc.mapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.Map;

import org.junit.jupiter.api.Test;

class RollupViewBindingTests {

	@Test
	void bindingHasNoFixedInfrastructureSlots() {
		assertThat(Arrays.stream(RollupViewBinding.class.getRecordComponents())
				.map(component -> component.getName()))
				.containsExactly("view", "tables");
	}

	@Test
	void resolvesARegisteredTableByAnInfrastructureOwnedKey() {
		var customStore = new StorageBindingKey("custom-warehouse");
		var table = new RollupTableBinding(
				SqlIdentifier.of("warehouse.daily_rollup"),
				SqlIdentifier.of("bucket_date"),
				Map.of(),
				Map.of());
		var binding = new RollupViewBinding("daily", Map.of(customStore, table));

		assertThat(binding.requiredTableFor(customStore)).isSameAs(table);
		assertThatThrownBy(() -> binding.requiredTableFor(JdbcStorageKeys.POSTGRES))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("postgres");
	}
}
