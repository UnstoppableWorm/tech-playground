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
class ClickHouseAggregateQueryAdapter implements AggregateStoreQueryPort {

	private final NamedParameterJdbcTemplate clickHouse;
	private final AggregationStorageBindingCatalog storageBindings;
	private final AggregateQueryCompilerRegistry compilers;

	ClickHouseAggregateQueryAdapter(
			@Qualifier("clickHouseJdbcTemplate") NamedParameterJdbcTemplate clickHouse,
			AggregationStorageBindingCatalog storageBindings,
			AggregateQueryCompilerRegistry compilers) {
		this.clickHouse = clickHouse;
		this.storageBindings = storageBindings;
		this.compilers = compilers;
	}

	@Override
	public AggregateStore store() {
		return AggregateStore.CLICKHOUSE;
	}

	@Override
	public List<AggregationRow> queryAggregate(
			ResolvedAggregationQuery query, Instant fromInclusive, Instant toExclusive) {
		var table = storageBindings.get(query.model().name())
				.requiredView(query.view().name()).requiredTableFor(JdbcStorageKeys.CLICKHOUSE);
		var compiled = compilers.compile(
				JdbcStorageKeys.CLICKHOUSE, query, table,
				new InstantRange(fromInclusive, toExclusive));
		return JdbcAggregationQueryExecutor.queryAggregate(clickHouse, compiled);
	}
}
