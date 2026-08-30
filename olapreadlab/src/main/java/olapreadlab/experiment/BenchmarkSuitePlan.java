package olapreadlab.experiment;

import java.util.List;

/**
 * 제품 비교가 아니라 두 집계 아키텍처의 구축, 조회, 보정 및 복구 특성을 비교한다.
 */
public final class BenchmarkSuitePlan {

	public static final List<Integer> ACTIVE_GRAIN_COUNTS = List.of(1, 3, 5, 8);
	public static final List<Integer> CONCURRENCY_LEVELS = List.of(1, 10, 50, 100);
	public static final List<Double> BACKFILL_RATIOS = List.of(0.001, 0.01, 0.10);

	private BenchmarkSuitePlan() {
	}

	public static List<QueryCase> queryCases() {
		return List.of(
						new QueryCase(Window.ONE_DAY, Cache.COLD),
						new QueryCase(Window.ONE_DAY, Cache.WARM),
						new QueryCase(Window.ONE_MONTH, Cache.COLD),
						new QueryCase(Window.ONE_MONTH, Cache.WARM),
						new QueryCase(Window.ONE_YEAR, Cache.COLD),
						new QueryCase(Window.ONE_YEAR, Cache.WARM),
						new QueryCase(Window.ALL, Cache.COLD),
						new QueryCase(Window.ALL, Cache.WARM)
				);
	}

	public enum Window {
		ONE_DAY,
		ONE_MONTH,
		ONE_YEAR,
		ALL
	}

	public enum Cache {
		COLD,
		WARM
	}

	public enum BackfillDistribution {
		RECENT_CONTIGUOUS,
		SINGLE_MONTH,
		HISTORY_RANDOM,
		SINGLE_DIMENSION_HOTSPOT
	}

	public record QueryCase(Window window, Cache cache) {
	}
}
