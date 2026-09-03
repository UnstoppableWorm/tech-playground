package olapreadlab.aggregation.adapter.jdbc;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

import olapreadlab.aggregation.application.port.CoverageCheckpointPort;
import olapreadlab.aggregation.model.RollupPipeline;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class PostgresCoverageCheckpointAdapter implements CoverageCheckpointPort {

	private static final String SQL = """
			SELECT covered_until
			FROM olap.aggregation_checkpoint
			WHERE model_key = :model
			  AND view_key = :view
			  AND pipeline = :pipeline
			""";

	private final NamedParameterJdbcTemplate postgres;

	PostgresCoverageCheckpointAdapter(
			@Qualifier("postgresJdbcTemplate") NamedParameterJdbcTemplate postgres) {
		this.postgres = postgres;
	}

	@Override
	public Optional<Instant> findCoveredUntil(
			String model, String view, RollupPipeline pipeline) {
		var parameters = new MapSqlParameterSource()
				.addValue("model", model)
				.addValue("view", view)
				.addValue("pipeline", pipeline.name());
		return postgres.query(SQL, parameters,
				(resultSet, rowNumber) -> resultSet.getObject("covered_until", Timestamp.class).toInstant())
				.stream().findFirst();
	}
}
