package olapreadlab.batch;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

class MedicalHistoryRollupJobConfigurationTests {

	private final MedicalHistoryRollupJobConfiguration configuration =
			new MedicalHistoryRollupJobConfiguration();

	@Test
	void rollupAndCheckpointAreWrittenByOneTaskletExecution() throws Exception {
		var postgres = mock(NamedParameterJdbcTemplate.class);
		var jdbc = mock(JdbcTemplate.class);
		var contribution = mock(StepContribution.class);
		when(postgres.getJdbcTemplate()).thenReturn(jdbc);
		when(postgres.update(contains("INSERT INTO olap.agg_person_organ_disease"),
				any(SqlParameterSource.class))).thenReturn(42);

		var tasklet = configuration.medicalHistoryRollupTasklet(
				postgres, "2026-01-01T00:00:00Z");
		tasklet.execute(contribution, null);

		verify(jdbc).execute("TRUNCATE TABLE olap.agg_person_organ_disease");
		verify(jdbc).execute("ANALYZE olap.agg_person_organ_disease");
		verify(postgres).update(contains("(occurred_at AT TIME ZONE 'UTC')::date"), any(SqlParameterSource.class));
		verify(postgres).update(contains("pipeline, covered_until"), any(SqlParameterSource.class));
		verify(contribution).incrementWriteCount(42);
	}

	@Test
	void coveredUntilMustBeAnInstant() {
		var postgres = mock(NamedParameterJdbcTemplate.class);
		var tasklet = configuration.medicalHistoryRollupTasklet(postgres, "2026-01-01");

		assertThatThrownBy(() -> tasklet.execute(mock(StepContribution.class), null))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void coveredUntilMustAlignWithDailyView() {
		var postgres = mock(NamedParameterJdbcTemplate.class);
		var tasklet = configuration.medicalHistoryRollupTasklet(
				postgres, "2026-01-01T01:00:00Z");

		assertThatThrownBy(() -> tasklet.execute(mock(StepContribution.class), null))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("UTC day boundary");
	}
}
