package olapreadlab.batch;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;

import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration(proxyBeanMethods = false)
public class MedicalHistoryRollupJobConfiguration {

	public static final String JOB_NAME = "medicalHistoryRollupJob";

	private static final String BUILD_ROLLUP_SQL = """
			INSERT INTO olap.agg_person_organ_disease
			    (bucket_date, person_id, organ_code, disease_code, event_count, metric_sum, refreshed_at)
			SELECT
			    (occurred_at AT TIME ZONE 'UTC')::date,
			    person_id,
			    organ_code,
			    disease_code,
			    count(*),
			    sum(metric_value),
			    clock_timestamp()
			FROM olap.medical_history
			WHERE occurred_at < :coveredUntil
			GROUP BY (occurred_at AT TIME ZONE 'UTC')::date, person_id, organ_code, disease_code
			""";

	private static final String ADVANCE_CHECKPOINT_SQL = """
			INSERT INTO olap.aggregation_checkpoint
			    (model_key, view_key, pipeline, covered_until, completed_at)
			VALUES
			    ('medical-history', 'person-organ-disease-daily', 'SPRING_BATCH',
			     :coveredUntil, clock_timestamp())
			ON CONFLICT (model_key, view_key, pipeline) DO UPDATE
			SET covered_until = EXCLUDED.covered_until,
			    completed_at = EXCLUDED.completed_at
			""";

	@Bean
	Job medicalHistoryRollupJob(JobRepository repository, Step medicalHistoryRollupStep) {
		return new JobBuilder(JOB_NAME, repository)
				.start(medicalHistoryRollupStep)
				.build();
	}

	@Bean
	Step medicalHistoryRollupStep(
			JobRepository repository,
			PlatformTransactionManager transactionManager,
			Tasklet medicalHistoryRollupTasklet) {
		return new StepBuilder("medicalHistoryRollupStep", repository)
				.tasklet(medicalHistoryRollupTasklet, transactionManager)
				.build();
	}

	@Bean
	@StepScope
	Tasklet medicalHistoryRollupTasklet(
			@Qualifier("postgresJdbcTemplate") NamedParameterJdbcTemplate postgres,
			@Value("#{jobParameters['coveredUntil']}") String coveredUntilParameter) {
		return (contribution, chunkContext) -> {
			var coveredUntil = requiredInstant(coveredUntilParameter);
			var parameters = new MapSqlParameterSource(
					"coveredUntil", OffsetDateTime.ofInstant(coveredUntil, ZoneOffset.UTC));

			postgres.getJdbcTemplate().execute("TRUNCATE TABLE olap.agg_person_organ_disease");
			var rollupRows = postgres.update(BUILD_ROLLUP_SQL, parameters);
			postgres.getJdbcTemplate().execute("ANALYZE olap.agg_person_organ_disease");
			postgres.update(ADVANCE_CHECKPOINT_SQL, parameters);

			contribution.incrementWriteCount(rollupRows);
			return RepeatStatus.FINISHED;
		};
	}

	private static Instant requiredInstant(String value) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException("coveredUntil job parameter is required");
		}
		try {
			var instant = Instant.parse(value);
			if (!instant.equals(instant.truncatedTo(ChronoUnit.DAYS))) {
				throw new IllegalArgumentException(
						"coveredUntil job parameter must align with a UTC day boundary");
			}
			return instant;
		}
		catch (DateTimeParseException exception) {
			throw new IllegalArgumentException(
					"coveredUntil job parameter must be an ISO-8601 instant", exception);
		}
	}
}
