package olapreadlab.aggregation.adapter.jdbc.compiler;

import java.util.ArrayList;
import java.util.Comparator;

import olapreadlab.aggregation.adapter.jdbc.JdbcBucketValueMapper;
import olapreadlab.aggregation.adapter.jdbc.mapping.AggregateTableBinding;
import olapreadlab.aggregation.adapter.jdbc.mapping.JdbcStorageKeys;
import olapreadlab.aggregation.adapter.jdbc.mapping.StorageBindingKey;
import olapreadlab.aggregation.application.InstantRange;
import olapreadlab.aggregation.application.ResolvedAggregationQuery;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Component;

@Component
public class PostgresAggregateQueryCompiler implements AggregateQueryCompiler {

	@Override
	public StorageBindingKey storageKey() {
		return JdbcStorageKeys.POSTGRES;
	}

	@Override
	public CompiledQuery<AggregateResultProjection> compile(
			ResolvedAggregationQuery resolved,
			AggregateTableBinding table,
			InstantRange range) {
		var model = resolved.model();
		var view = resolved.view();
		var sql = new StringBuilder("SELECT ")
				.append(table.bucketColumn().value()).append(" AS __bucket");
		var dimensions = new ArrayList<DimensionProjection>();
		for (int index = 0; index < view.dimensions().size(); index++) {
			var name = view.dimensions().get(index);
			var alias = "__d" + index;
			sql.append(", ").append(table.dimensionColumns().get(name).value())
					.append(" AS ").append(alias);
			dimensions.add(new DimensionProjection(name, model.dimensions().get(name).type(), alias));
		}
		var definitions = model.measures().values().stream()
				.sorted(Comparator.comparing(measure -> measure.name()))
				.toList();
		var measures = new ArrayList<MeasureProjection>();
		for (int index = 0; index < definitions.size(); index++) {
			var measure = definitions.get(index);
			var alias = "__m" + index;
			sql.append(", sum(").append(table.measureColumns().get(measure.name()).value())
					.append(") AS ").append(alias);
			measures.add(new MeasureProjection(measure.name(), measure.rawAggregation(), alias));
		}
		sql.append(" FROM ").append(table.table().value())
				.append(" WHERE ").append(table.bucketColumn().value()).append(" >= :fromInclusive")
				.append(" AND ").append(table.bucketColumn().value()).append(" < :toExclusive");
		var parameters = new MapSqlParameterSource()
				.addValue("fromInclusive", JdbcBucketValueMapper.toJdbcValue(view.bucket(), range.fromInclusive()))
				.addValue("toExclusive", JdbcBucketValueMapper.toJdbcValue(view.bucket(), range.toExclusive()));
		QueryCompilerSupport.appendFilters(sql, parameters, resolved, table.dimensionColumns());
		sql.append(" GROUP BY ").append(table.bucketColumn().value());
		view.dimensions().forEach(name -> sql.append(", ").append(table.dimensionColumns().get(name).value()));

		return new CompiledQuery<>(sql.toString(), parameters,
				new AggregateResultProjection(view.bucket(), "__bucket", dimensions, measures));
	}
}
