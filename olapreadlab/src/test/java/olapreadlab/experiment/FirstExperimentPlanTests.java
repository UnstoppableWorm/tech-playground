package olapreadlab.experiment;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import olapreadlab.experiment.FirstExperimentPlan.Engine;
import olapreadlab.experiment.FirstExperimentPlan.ExperimentCase;
import olapreadlab.experiment.FirstExperimentPlan.Phase;

class FirstExperimentPlanTests {

	@Test
	void clickHouseIsMeasuredWithAndWithoutLeafMerges() {
		assertThat(FirstExperimentPlan.MergeMode.values()).containsExactly(
				FirstExperimentPlan.MergeMode.BACKGROUND_MERGES_ENABLED,
				FirstExperimentPlan.MergeMode.LEAF_AGGREGATE_MERGES_STOPPED
		);
	}

	@Test
	void initialClickHouseCasesUseRequestedBlockCounts() {
		assertThat(blockCounts(Phase.INITIAL_BUILD)).containsExactlyInAnyOrderEntriesOf(Map.of(
				1_000_000L, 100L,
				100_000L, 1_000L,
				10_000L, 10_000L
		));
	}

	@Test
	void backfillClickHouseCasesCorrectTenPercentUsingRequestedBlockCounts() {
		assertThat(FirstExperimentPlan.BACKFILL_ROW_COUNT)
				.isEqualTo(FirstExperimentPlan.SOURCE_ROW_COUNT / 10);
		assertThat(blockCounts(Phase.BACKFILL_REAGGREGATION)).containsExactlyInAnyOrderEntriesOf(Map.of(
				1_000_000L, 10L,
				100_000L, 100L,
				10_000L, 1_000L
		));
	}

	private Map<Long, Long> blockCounts(Phase phase) {
		return FirstExperimentPlan.cases().stream()
				.filter(testCase -> testCase.phase() == phase)
				.filter(testCase -> testCase.engine() == Engine.CLICKHOUSE)
				.collect(Collectors.toMap(ExperimentCase::blockSize, ExperimentCase::blockCount));
	}
}
