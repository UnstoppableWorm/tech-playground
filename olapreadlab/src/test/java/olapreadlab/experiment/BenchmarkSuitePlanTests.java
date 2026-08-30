package olapreadlab.experiment;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BenchmarkSuitePlanTests {

	@Test
	void coversAllTimeWindowsWithColdAndWarmCache() {
		assertThat(BenchmarkSuitePlan.queryCases()).hasSize(8);
		assertThat(BenchmarkSuitePlan.queryCases())
				.extracting(BenchmarkSuitePlan.QueryCase::window)
				.containsOnly(BenchmarkSuitePlan.Window.values());
		assertThat(BenchmarkSuitePlan.queryCases())
				.extracting(BenchmarkSuitePlan.QueryCase::cache)
				.containsOnly(BenchmarkSuitePlan.Cache.values());
	}

	@Test
	void increasesAggregateDiversityAndConcurrencyIndependently() {
		assertThat(BenchmarkSuitePlan.ACTIVE_GRAIN_COUNTS).containsExactly(1, 3, 5, 8);
		assertThat(BenchmarkSuitePlan.CONCURRENCY_LEVELS).containsExactly(1, 10, 50, 100);
	}
}
