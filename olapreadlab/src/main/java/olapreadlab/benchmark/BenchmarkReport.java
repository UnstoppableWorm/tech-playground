package olapreadlab.benchmark;

import java.time.Instant;
import java.util.List;

import olapreadlab.aggregation.model.QueryMode;
import olapreadlab.experiment.BenchmarkSuitePlan.Window;

public record BenchmarkReport(
		Instant generatedAt,
		Configuration configuration,
		long elapsedMillis,
		boolean equivalent,
		List<String> consistencyErrors,
		List<ModeResult> results) {

	public BenchmarkReport {
		consistencyErrors = List.copyOf(consistencyErrors);
		results = List.copyOf(results);
	}

	public record Configuration(
			String endpoint,
			String model,
			String view,
			Instant fromInclusive,
			Instant toExclusive,
			Instant coveredUntil,
			long sourceRowCount,
			String clickHouseStrategy,
			int clickHouseBlockSize,
			List<QueryMode> modes,
			List<Window> windows,
			int warmupCount,
			int measurementCount,
			long randomSeed,
			String cacheState) {

		public Configuration {
			modes = List.copyOf(modes);
			windows = List.copyOf(windows);
		}
	}

	public record ModeResult(
			Window window,
			QueryMode mode,
			Instant fromInclusive,
			Instant toExclusive,
			int sampleCount,
			long rowCount,
			long responseBytes,
			String responseSha256,
			double minMillis,
			double p50Millis,
			double p95Millis,
			double p99Millis,
			double maxMillis,
			double meanMillis,
			double requestsPerSecond,
			List<Double> samplesMillis) {

		public ModeResult {
			samplesMillis = List.copyOf(samplesMillis);
		}
	}
}
