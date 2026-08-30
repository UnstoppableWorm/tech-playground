package olapreadlab.aggregation.adapter.jdbc;

import java.time.Instant;
import java.util.List;

import olapreadlab.aggregation.adapter.jdbc.compiler.AggregateQueryCompilerRegistry;
import olapreadlab.aggregation.adapter.jdbc.mapping.AggregationStorageBindingCatalog;
import olapreadlab.aggregation.adapter.jdbc.mapping.JdbcStorageKeys;
import olapreadlab.aggregation.application.InstantRange;
import olapreadlab.aggregation.application.ResolvedAggregationQuery;
import olapreadlab.aggregation.application.port.AggregateStoreQueryPort;
import olapreadlab.aggregation.model.AggregateStore;
import olapreadlab.aggregation.model.AggregationRow;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class PostgresAggregateQueryAdapter implements AggregateStoreQueryPort {

	private final NamedParameterJdbcTemplate postgres;
	private final AggregationStorageBindingCatalog storageBindings;
	private final AggregateQueryCompilerRegistry compilers;

	PostgresAggregateQueryAdapter(
			@Qualifier("postgresJdbcTemplate") NamedParameterJdbcTemplate postgres,
			AggregationStorageBindingCatalog storageBindings,
			AggregateQueryCompilerRegistry compilers) {
		this.postgres = postgres;
		this.storageBindings = storageBindings;
		this.compilers = compilers;
	}

	@Override
	public AggregateStore store() {
		return AggregateStore.POSTGRES;
	}

	@Override
	public List<AggregationRow> queryAggregate(
			ResolvedAggregationQuery query, Instant fromInclusive, Instant toExclusive) {
		var table = storageBindings.get(query.model().name())
				.requiredView(query.view().name()).requiredTableFor(JdbcStorageKeys.POSTGRES);
		var compiled = compilers.compile(
				JdbcStorageKeys.POSTGRES, query, table,
				new InstantRange(fromInclusive, toExclusive));
		return JdbcAggregationQueryExecutor.queryAggregate(postgres, compiled);
	}
}
