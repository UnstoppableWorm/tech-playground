package olapreadlab.experiment;

import java.util.List;

/**
 * 1억 건 최초 집계와 10% 보정 재집계의 첫 번째 고정 실험 계획이다.
 */
public final class FirstExperimentPlan {

	public static final long SOURCE_ROW_COUNT = 100_000_000L;
	public static final long BACKFILL_ROW_COUNT = 10_000_000L;
	public static final List<Integer> CLICKHOUSE_BLOCK_SIZES = List.of(
			1_000_000,
			100_000,
			10_000
	);

	private FirstExperimentPlan() {
	}

	public static List<ExperimentCase> cases() {
		return List.of(
				new ExperimentCase(Phase.INITIAL_BUILD, Engine.SPRING_BATCH, SOURCE_ROW_COUNT, SOURCE_ROW_COUNT),
				new ExperimentCase(Phase.INITIAL_BUILD, Engine.CLICKHOUSE, SOURCE_ROW_COUNT, 1_000_000),
				new ExperimentCase(Phase.INITIAL_BUILD, Engine.CLICKHOUSE, SOURCE_ROW_COUNT, 100_000),
				new ExperimentCase(Phase.INITIAL_BUILD, Engine.CLICKHOUSE, SOURCE_ROW_COUNT, 10_000),
				new ExperimentCase(Phase.BACKFILL_REAGGREGATION, Engine.SPRING_BATCH, SOURCE_ROW_COUNT, SOURCE_ROW_COUNT),
				new ExperimentCase(Phase.BACKFILL_REAGGREGATION, Engine.CLICKHOUSE, BACKFILL_ROW_COUNT, 1_000_000),
				new ExperimentCase(Phase.BACKFILL_REAGGREGATION, Engine.CLICKHOUSE, BACKFILL_ROW_COUNT, 100_000),
				new ExperimentCase(Phase.BACKFILL_REAGGREGATION, Engine.CLICKHOUSE, BACKFILL_ROW_COUNT, 10_000)
		);
	}

	public enum Phase {
		INITIAL_BUILD,
		BACKFILL_REAGGREGATION
	}

	public enum Engine {
		SPRING_BATCH,
		CLICKHOUSE
	}

	public enum MergeMode {
		BACKGROUND_MERGES_ENABLED,
		LEAF_AGGREGATE_MERGES_STOPPED
	}

	public record ExperimentCase(
			Phase phase,
			Engine engine,
			long changedRowCount,
			long blockSize
	) {
		public long blockCount() {
			return (changedRowCount + blockSize - 1) / blockSize;
		}
	}
}
