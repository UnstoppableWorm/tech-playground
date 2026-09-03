package olapreadlab.aggregation.application;

import java.time.Instant;
import java.util.Optional;

import olapreadlab.aggregation.model.RollupStore;

public record ReadPlan(
		Optional<RollupStore> rollupStore,
		Optional<InstantRange> rollupRange,
		Optional<InstantRange> rawRange,
		Instant coveredUntil) {

	public ReadPlan {
		rollupStore = rollupStore == null ? Optional.empty() : rollupStore;
		rollupRange = rollupRange == null ? Optional.empty() : rollupRange;
		rawRange = rawRange == null ? Optional.empty() : rawRange;
		if (rollupRange.isPresent() != rollupStore.isPresent()) {
			throw new IllegalArgumentException("Rollup range and store must be present together");
		}
	}
}
