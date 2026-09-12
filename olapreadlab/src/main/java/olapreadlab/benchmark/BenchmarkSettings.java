package olapreadlab.benchmark;

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import olapreadlab.aggregation.model.QueryMode;
import olapreadlab.experiment.BenchmarkSuitePlan.Window;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("benchmark")
public record BenchmarkSettings(
		boolean enabled,
		URI endpoint,
		Path output,
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
		Duration requestTimeout) {

	public BenchmarkSettings {
		modes = modes == null ? List.of() : List.copyOf(modes);
		windows = windows == null ? List.of() : List.copyOf(windows);
	}
}
