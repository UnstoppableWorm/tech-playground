package olapreadlab.aggregation.application;

import java.time.Instant;

public record InstantRange(Instant fromInclusive, Instant toExclusive) {

	public InstantRange {
		if (!fromInclusive.isBefore(toExclusive)) {
			throw new IllegalArgumentException("Range must not be empty");
		}
	}
}
