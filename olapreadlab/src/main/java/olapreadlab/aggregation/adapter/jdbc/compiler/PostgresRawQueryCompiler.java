package olapreadlab.aggregation.adapter.jdbc.compiler;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Comparator;

import olapreadlab.aggregation.adapter.jdbc.mapping.RawTableBinding;
import olapreadlab.aggregation.adapter.jdbc.mapping.JdbcStorageKeys;
import olapreadlab.aggregation.adapter.jdbc.mapping.StorageBindingKey;
import olapreadlab.aggregation.application.InstantRange;
import olapreadlab.aggregation.application.ResolvedAggregationQuery;
import olapreadlab.aggregation.model.MeasureDefinition.RawAggregation;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Component;

@Component
public class PostgresRawQueryCompiler implements RawQueryCompiler {

	@Override
	public StorageBindingKey storageKey() {
		return JdbcStorageKeys.POSTGRES;
	}

	@Override
	public CompiledQuery<RawResultProjection> compile(
			ResolvedAggregationQuery resolved,
			RawTableBinding raw,
			InstantRange range) {
		var model = resolved.model();
		var sql = new StringBuilder("SELECT ")
				.append(raw.eventTimeColumn().value()).append(" AS __event_time");
		var dimensions = new ArrayList<DimensionProjection>();
		for (int index = 0; index < resolved.view().dimensions().size(); index++) {
			var name = resolved.view().dimensions().get(index);
			var alias = "__d" + index;
			sql.append(", ").append(raw.dimensionColumns().get(name).value())
					.append(" AS ").append(alias);
			dimensions.add(new DimensionProjection(name, model.dimensions().get(name).type(), alias));
		}
		var definitions = model.measures().values().stream()
				.sorted(Comparator.comparing(measure -> measure.name()))
				.toList();
		var measures = new ArrayList<MeasureProjection>();
		for (int index = 0; index < definitions.size(); index++) {
			var measure = definitions.get(index);
			var alias = measure.rawAggregation() == RawAggregation.SUM ? "__m" + index : null;
			if (alias != null) {
				sql.append(", ").append(raw.measureColumns().get(measure.name()).value())
						.append(" AS ").append(alias);
			}
			measures.add(new MeasureProjection(measure.name(), measure.rawAggregation(), alias));
		}
		sql.append(" FROM ").append(raw.table().value())
				.append(" WHERE ").append(raw.eventTimeColumn().value()).append(" >= :fromInclusive")
				.append(" AND ").append(raw.eventTimeColumn().value()).append(" < :toExclusive");
		var parameters = new MapSqlParameterSource()
				.addValue("fromInclusive", Timestamp.from(range.fromInclusive()))
				.addValue("toExclusive", Timestamp.from(range.toExclusive()));
		QueryCompilerSupport.appendFilters(sql, parameters, resolved, raw.dimensionColumns());

		return new CompiledQuery<>(
				sql.toString(),
				parameters,
				new RawResultProjection(
						resolved.view().bucket(), "__event_time", dimensions, measures));
	}
}
