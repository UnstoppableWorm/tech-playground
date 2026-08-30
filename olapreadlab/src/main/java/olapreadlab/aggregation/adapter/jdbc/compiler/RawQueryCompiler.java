package olapreadlab.aggregation.adapter.jdbc.compiler;

import olapreadlab.aggregation.adapter.jdbc.mapping.RawTableBinding;
import olapreadlab.aggregation.adapter.jdbc.mapping.StorageBindingKey;
import olapreadlab.aggregation.application.InstantRange;
import olapreadlab.aggregation.application.ResolvedAggregationQuery;

public interface RawQueryCompiler {
	StorageBindingKey storageKey();

	default boolean supports(ResolvedAggregationQuery query) {
		return true;
	}

	default int priority() {
		return 0;
	}

	CompiledQuery<RawResultProjection> compile(
			ResolvedAggregationQuery query,
			RawTableBinding table,
			InstantRange range);
}
