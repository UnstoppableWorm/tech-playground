package olapreadlab.aggregation.adapter.jdbc;

import java.time.Instant;
import java.util.List;

import olapreadlab.aggregation.adapter.jdbc.compiler.QueryCompilerRegistry;
import olapreadlab.aggregation.adapter.jdbc.mapping.StorageBindingRegistry;
import olapreadlab.aggregation.adapter.jdbc.mapping.JdbcStorageKeys;
import olapreadlab.aggregation.application.InstantRange;
import olapreadlab.aggregation.application.ResolvedQuery;
import olapreadlab.aggregation.application.port.RawQueryPort;
import olapreadlab.aggregation.model.ResultRow;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
class PostgresRawQueryAdapter implements RawQueryPort {

	private final NamedParameterJdbcTemplate postgres;
	private final StorageBindingRegistry storageBindings;
	private final QueryCompilerRegistry compilers;

	PostgresRawQueryAdapter(
			@Qualifier("postgresJdbcTemplate") NamedParameterJdbcTemplate postgres,
			StorageBindingRegistry storageBindings,
			QueryCompilerRegistry compilers) {
		this.postgres = postgres;
		this.storageBindings = storageBindings;
		this.compilers = compilers;
	}

	@Override
	@Transactional(readOnly = true)
	public List<ResultRow> queryRaw(
			ResolvedQuery resolved, Instant fromInclusive, Instant toExclusive) {
		var raw = storageBindings.get(resolved.model().name()).raw();
		var compiled = compilers.compile(
				JdbcStorageKeys.POSTGRES, resolved, raw,
				new InstantRange(fromInclusive, toExclusive));
		return JdbcQueryExecutor.queryRaw(postgres, compiled);
	}
}
