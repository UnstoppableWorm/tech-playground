package olapreadlab.aggregation.adapter.jdbc.compiler;

import olapreadlab.aggregation.adapter.jdbc.mapping.RollupTableBinding;
import olapreadlab.aggregation.adapter.jdbc.mapping.RawTableBinding;
import olapreadlab.aggregation.adapter.jdbc.mapping.StorageBindingKey;
import olapreadlab.aggregation.application.InstantRange;
import olapreadlab.aggregation.application.ResolvedQuery;

public interface QueryCompiler {
	StorageBindingKey storageKey();

	default boolean supports(ResolvedQuery query) {
		return true;
	}

	default int priority() {
		return 0;
	}

	default boolean supports(RawTableBinding table) {
		return false;
	}

	default boolean supports(RollupTableBinding table) {
		return false;
	}

	default CompiledQuery<RawResultProjection> compile(
			ResolvedQuery query,
			RawTableBinding table,
			InstantRange range) {
		throw new UnsupportedOperationException("Raw query compilation is not supported");
	}

	default CompiledQuery<RollupResultProjection> compile(
			ResolvedQuery query,
			RollupTableBinding table,
			InstantRange range) {
		throw new UnsupportedOperationException("Rollup query compilation is not supported");
	}
}
