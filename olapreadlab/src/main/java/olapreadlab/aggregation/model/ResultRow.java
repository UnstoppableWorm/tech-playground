package olapreadlab.aggregation.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record ResultRow(
		Instant bucket,
		Map<String, Object> dimensions,
		Map<String, BigDecimal> measures) {

	public ResultRow {
		dimensions = immutableOrderedCopy(dimensions);
		measures = immutableOrderedCopy(measures);
	}

	public ResultRow add(ResultRow other) {
		if (!key().equals(other.key())) throw new IllegalArgumentException("Aggregation keys differ");
		var sums = new LinkedHashMap<>(measures);
		other.measures.forEach((name, value) -> sums.merge(name, value, BigDecimal::add));
		return new ResultRow(bucket, dimensions, sums);
	}

	public RowKey key() {
		return new RowKey(bucket, dimensions);
	}

	private static <K, V> Map<K, V> immutableOrderedCopy(Map<K, V> source) {
		return Collections.unmodifiableMap(new LinkedHashMap<>(source));
	}
}
