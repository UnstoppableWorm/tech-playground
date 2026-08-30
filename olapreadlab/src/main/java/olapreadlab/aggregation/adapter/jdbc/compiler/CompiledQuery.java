package olapreadlab.aggregation.adapter.jdbc.compiler;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

public record CompiledQuery<P>(
		String sql,
		MapSqlParameterSource parameters,
		P projection) {
}
