package olapreadlab.benchmark;

import java.util.List;

final class BenchmarkStatistics {

	private BenchmarkStatistics() {
	}

	static double percentileMillis(List<Long> sortedNanos, double percentile) {
		if (sortedNanos.isEmpty()) throw new IllegalArgumentException("At least one sample is required");
		var index = Math.max(0, (int) Math.ceil(percentile * sortedNanos.size()) - 1);
		return millis(sortedNanos.get(index));
	}

	static double meanMillis(List<Long> nanos) {
		if (nanos.isEmpty()) throw new IllegalArgumentException("At least one sample is required");
		return round(nanos.stream().mapToDouble(Long::doubleValue).average().orElseThrow() / 1_000_000d);
	}

	static double millis(long nanos) {
		return round(nanos / 1_000_000d);
	}

	static double requestsPerSecond(List<Long> nanos) {
		var averageNanos = nanos.stream().mapToDouble(Long::doubleValue).average().orElseThrow();
		return round(1_000_000_000d / averageNanos);
	}

	private static double round(double value) {
		return Math.round(value * 1_000d) / 1_000d;
	}
}
