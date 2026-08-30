package olapreadlab.aggregation.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record AggregationRow(
		Instant bucket,
		Map<String, Object> dimensions,
		Map<String, BigDecimal> measures) {

	public AggregationRow {
		dimensions = immutableOrderedCopy(dimensions);
		measures = immutableOrderedCopy(measures);
	}

	public AggregationRow add(AggregationRow other) {
		if (!key().equals(other.key())) throw new IllegalArgumentException("Aggregation keys differ");
		var sums = new LinkedHashMap<>(measures);
		other.measures.forEach((name, value) -> sums.merge(name, value, BigDecimal::add));
		return new AggregationRow(bucket, dimensions, sums);
	}

	public AggregationKey key() {
		return new AggregationKey(bucket, dimensions);
	}

	private static <K, V> Map<K, V> immutableOrderedCopy(Map<K, V> source) {
		return Collections.unmodifiableMap(new LinkedHashMap<>(source));
	}
}
