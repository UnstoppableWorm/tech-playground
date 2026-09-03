package olapreadlab.aggregation.adapter.jdbc.compiler;

import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

import olapreadlab.aggregation.adapter.jdbc.mapping.RollupTableBinding;
import olapreadlab.aggregation.adapter.jdbc.mapping.RawTableBinding;
import olapreadlab.aggregation.adapter.jdbc.mapping.StorageBindingKey;
import olapreadlab.aggregation.application.InstantRange;
import olapreadlab.aggregation.application.ResolvedQuery;

import org.springframework.stereotype.Component;

@Component
public class QueryCompilerRegistry {

	private final List<QueryCompiler> compilers;

	public QueryCompilerRegistry(List<QueryCompiler> compilers) {
		this.compilers = List.copyOf(compilers);
	}

	public CompiledQuery<RawResultProjection> compile(
			StorageBindingKey storageKey,
			ResolvedQuery query,
			RawTableBinding table,
			InstantRange range) {
		return select(storageKey, query, compiler -> compiler.supports(table), "raw")
				.compile(query, table, range);
	}

	public CompiledQuery<RollupResultProjection> compile(
			StorageBindingKey storageKey,
			ResolvedQuery query,
			RollupTableBinding table,
			InstantRange range) {
		return select(storageKey, query, compiler -> compiler.supports(table), "rollup")
				.compile(query, table, range);
	}

	private QueryCompiler select(
			StorageBindingKey storageKey,
			ResolvedQuery query,
			Predicate<QueryCompiler> shapeSupport,
			String queryKind) {
		var candidates = compilers.stream()
				.filter(compiler -> compiler.storageKey().equals(storageKey))
				.filter(compiler -> compiler.supports(query))
				.filter(shapeSupport)
				.sorted(Comparator.comparingInt(QueryCompiler::priority).reversed())
				.toList();
		if (candidates.isEmpty()) {
			throw new IllegalStateException("No " + queryKind + " query compiler for " + storageKey.value());
		}
		if (candidates.size() > 1 && candidates.get(0).priority() == candidates.get(1).priority()) {
			throw new IllegalStateException("Ambiguous " + queryKind + " query compilers for " + storageKey.value());
		}
		return candidates.get(0);
	}
}
