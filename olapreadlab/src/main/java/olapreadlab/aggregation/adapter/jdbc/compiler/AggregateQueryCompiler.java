package olapreadlab.aggregation.adapter.jdbc.compiler;

import olapreadlab.aggregation.adapter.jdbc.mapping.AggregateTableBinding;
import olapreadlab.aggregation.adapter.jdbc.mapping.StorageBindingKey;
import olapreadlab.aggregation.application.InstantRange;
import olapreadlab.aggregation.application.ResolvedAggregationQuery;

public interface AggregateQueryCompiler {
	StorageBindingKey storageKey();

	default boolean supports(ResolvedAggregationQuery query) {
		return true;
	}

	default int priority() {
		return 0;
	}

	CompiledQuery<AggregateResultProjection> compile(
			ResolvedAggregationQuery query,
			AggregateTableBinding table,
			InstantRange range);
}
