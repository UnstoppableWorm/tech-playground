package olapreadlab.aggregation.adapter.jdbc;

import java.time.Instant;
import java.util.List;

import olapreadlab.aggregation.adapter.jdbc.compiler.QueryCompilerRegistry;
import olapreadlab.aggregation.adapter.jdbc.mapping.StorageBindingRegistry;
import olapreadlab.aggregation.adapter.jdbc.mapping.JdbcStorageKeys;
import olapreadlab.aggregation.application.InstantRange;
import olapreadlab.aggregation.application.ResolvedQuery;
import olapreadlab.aggregation.application.port.RollupQueryPort;
import olapreadlab.aggregation.model.RollupStore;
import olapreadlab.aggregation.model.ResultRow;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class ClickHouseRollupQueryAdapter implements RollupQueryPort {

	private final NamedParameterJdbcTemplate clickHouse;
	private final StorageBindingRegistry storageBindings;
	private final QueryCompilerRegistry compilers;

	ClickHouseRollupQueryAdapter(
			@Qualifier("clickHouseJdbcTemplate") NamedParameterJdbcTemplate clickHouse,
			StorageBindingRegistry storageBindings,
			QueryCompilerRegistry compilers) {
		this.clickHouse = clickHouse;
		this.storageBindings = storageBindings;
		this.compilers = compilers;
	}

	@Override
	public RollupStore store() {
		return RollupStore.CLICKHOUSE;
	}

	@Override
	public List<ResultRow> queryRollup(
			ResolvedQuery query, Instant fromInclusive, Instant toExclusive) {
		var table = storageBindings.get(query.model().name())
				.requiredView(query.view().name()).requiredTableFor(JdbcStorageKeys.CLICKHOUSE);
		var compiled = compilers.compile(
				JdbcStorageKeys.CLICKHOUSE, query, table,
				new InstantRange(fromInclusive, toExclusive));
		return JdbcQueryExecutor.queryRollup(clickHouse, compiled);
	}
}
