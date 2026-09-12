package olapreadlab.benchmark;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class BenchmarkStatisticsTests {

	@Test
	void calculatesNearestRankPercentilesAndMeanInMilliseconds() {
		var samples = List.of(
				1_000_000L, 2_000_000L, 3_000_000L, 4_000_000L,
				5_000_000L, 6_000_000L, 7_000_000L, 8_000_000L,
				9_000_000L, 10_000_000L);

		assertThat(BenchmarkStatistics.percentileMillis(samples, 0.50)).isEqualTo(5.0);
		assertThat(BenchmarkStatistics.percentileMillis(samples, 0.95)).isEqualTo(10.0);
		assertThat(BenchmarkStatistics.percentileMillis(samples, 0.99)).isEqualTo(10.0);
		assertThat(BenchmarkStatistics.meanMillis(samples)).isEqualTo(5.5);
		assertThat(BenchmarkStatistics.requestsPerSecond(samples)).isEqualTo(181.818);
	}
}
