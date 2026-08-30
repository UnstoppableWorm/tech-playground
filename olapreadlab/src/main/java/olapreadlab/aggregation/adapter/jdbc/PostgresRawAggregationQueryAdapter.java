package olapreadlab.aggregation.adapter.jdbc;

import java.time.Instant;
import java.util.List;

import olapreadlab.aggregation.adapter.jdbc.compiler.RawQueryCompilerRegistry;
import olapreadlab.aggregation.adapter.jdbc.mapping.AggregationStorageBindingCatalog;
import olapreadlab.aggregation.adapter.jdbc.mapping.JdbcStorageKeys;
import olapreadlab.aggregation.application.InstantRange;
import olapreadlab.aggregation.application.ResolvedAggregationQuery;
import olapreadlab.aggregation.application.port.RawAggregationQueryPort;
import olapreadlab.aggregation.model.AggregationRow;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
class PostgresRawAggregationQueryAdapter implements RawAggregationQueryPort {

	private final NamedParameterJdbcTemplate postgres;
	private final AggregationStorageBindingCatalog storageBindings;
	private final RawQueryCompilerRegistry compilers;

	PostgresRawAggregationQueryAdapter(
			@Qualifier("postgresJdbcTemplate") NamedParameterJdbcTemplate postgres,
			AggregationStorageBindingCatalog storageBindings,
			RawQueryCompilerRegistry compilers) {
		this.postgres = postgres;
		this.storageBindings = storageBindings;
		this.compilers = compilers;
	}

	@Override
	@Transactional(readOnly = true)
	public List<AggregationRow> queryRaw(
			ResolvedAggregationQuery resolved, Instant fromInclusive, Instant toExclusive) {
		var raw = storageBindings.get(resolved.model().name()).raw();
		var compiled = compilers.compile(
				JdbcStorageKeys.POSTGRES, resolved, raw,
				new InstantRange(fromInclusive, toExclusive));
		return JdbcAggregationQueryExecutor.queryRaw(postgres, compiled);
	}
}
