package olapreadlab.aggregation.application;

import java.time.Instant;
import java.util.Optional;

import olapreadlab.aggregation.model.AggregateStore;

public record AggregationReadPlan(
		Optional<AggregateStore> aggregateStore,
		Optional<InstantRange> aggregateRange,
		Optional<InstantRange> rawRange,
		Instant aggregateCoveredUntil) {

	public AggregationReadPlan {
		aggregateStore = aggregateStore == null ? Optional.empty() : aggregateStore;
		aggregateRange = aggregateRange == null ? Optional.empty() : aggregateRange;
		rawRange = rawRange == null ? Optional.empty() : rawRange;
		if (aggregateRange.isPresent() != aggregateStore.isPresent()) {
			throw new IllegalArgumentException("Aggregate range and store must be present together");
		}
	}
}
