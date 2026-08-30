package olapreadlab.aggregation.adapter.jdbc.compiler;

import java.util.Comparator;
import java.util.List;

import olapreadlab.aggregation.adapter.jdbc.mapping.RawTableBinding;
import olapreadlab.aggregation.adapter.jdbc.mapping.StorageBindingKey;
import olapreadlab.aggregation.application.InstantRange;
import olapreadlab.aggregation.application.ResolvedAggregationQuery;

import org.springframework.stereotype.Component;

@Component
public class RawQueryCompilerRegistry {

	private final List<RawQueryCompiler> compilers;

	public RawQueryCompilerRegistry(List<RawQueryCompiler> compilers) {
		this.compilers = List.copyOf(compilers);
	}

	public CompiledQuery<RawResultProjection> compile(
			StorageBindingKey storageKey,
			ResolvedAggregationQuery query,
			RawTableBinding table,
			InstantRange range) {
		return select(storageKey, query).compile(query, table, range);
	}

	private RawQueryCompiler select(StorageBindingKey storageKey, ResolvedAggregationQuery query) {
		var candidates = compilers.stream()
				.filter(compiler -> compiler.storageKey().equals(storageKey))
				.filter(compiler -> compiler.supports(query))
				.sorted(Comparator.comparingInt(RawQueryCompiler::priority).reversed())
				.toList();
		if (candidates.isEmpty()) {
			throw new IllegalStateException("No raw query compiler for " + storageKey.value());
		}
		if (candidates.size() > 1 && candidates.get(0).priority() == candidates.get(1).priority()) {
			throw new IllegalStateException("Ambiguous raw query compilers for " + storageKey.value());
		}
		return candidates.get(0);
	}
}
