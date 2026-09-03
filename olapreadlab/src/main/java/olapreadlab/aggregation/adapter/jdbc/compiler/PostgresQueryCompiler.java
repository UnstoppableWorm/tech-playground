package olapreadlab.aggregation.adapter.jdbc.compiler;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Map;

import olapreadlab.aggregation.adapter.jdbc.JdbcBucketValueMapper;
import olapreadlab.aggregation.adapter.jdbc.mapping.RollupTableBinding;
import olapreadlab.aggregation.adapter.jdbc.mapping.JdbcStorageKeys;
import olapreadlab.aggregation.adapter.jdbc.mapping.RawTableBinding;
import olapreadlab.aggregation.adapter.jdbc.mapping.SqlIdentifier;
import olapreadlab.aggregation.adapter.jdbc.mapping.StorageBindingKey;
import olapreadlab.aggregation.application.InstantRange;
import olapreadlab.aggregation.application.ResolvedQuery;
import olapreadlab.aggregation.model.ModelDefinition;
import olapreadlab.aggregation.model.MeasureDefinition;
import olapreadlab.aggregation.model.MeasureDefinition.RawAggregation;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Component;

@Component
public class PostgresQueryCompiler implements QueryCompiler {

	@Override
	public StorageBindingKey storageKey() {
		return JdbcStorageKeys.POSTGRES;
	}

	@Override
	public boolean supports(ResolvedQuery query) {
		return true;
	}

	@Override
	public int priority() {
		return 0;
	}

	@Override
	public boolean supports(RawTableBinding table) {
		return true;
	}

	@Override
	public boolean supports(RollupTableBinding table) {
		return true;
	}

	@Override
	public CompiledQuery<RawResultProjection> compile(
			ResolvedQuery resolved,
			RawTableBinding raw,
			InstantRange range) {
		var sql = new StringBuilder("SELECT ")
				.append(raw.eventTimeColumn().value()).append(" AS __event_time");
		var dimensions = appendDimensions(sql, resolved, raw.dimensionColumns());
		var measures = appendRawMeasures(sql, resolved.model(), raw.measureColumns());
		appendFromAndRange(sql, raw.table().value(), raw.eventTimeColumn().value());
		var parameters = rawRangeParameters(range);
		QueryCompilerSupport.appendFilters(sql, parameters, resolved, raw.dimensionColumns());

		return new CompiledQuery<>(
				sql.toString(),
				parameters,
				new RawResultProjection(
						resolved.view().bucket(), "__event_time", dimensions, measures));
	}

	@Override
	public CompiledQuery<RollupResultProjection> compile(
			ResolvedQuery resolved,
			RollupTableBinding table,
			InstantRange range) {
		var sql = new StringBuilder("SELECT ")
				.append(table.bucketColumn().value()).append(" AS __bucket");
		var dimensions = appendDimensions(sql, resolved, table.dimensionColumns());
		var measures = appendRollupMeasures(sql, resolved.model(), table.measureColumns());
		appendFromAndRange(sql, table.table().value(), table.bucketColumn().value());
		var parameters = rollupRangeParameters(resolved, range);
		QueryCompilerSupport.appendFilters(sql, parameters, resolved, table.dimensionColumns());
		appendGroupBy(sql, resolved, table.bucketColumn().value(), table.dimensionColumns());

		return new CompiledQuery<>(sql.toString(), parameters,
				new RollupResultProjection(resolved.view().bucket(), "__bucket", dimensions, measures));
	}

	private static ArrayList<DimensionProjection> appendDimensions(
			StringBuilder sql,
			ResolvedQuery resolved,
			Map<String, SqlIdentifier> columns) {
		var dimensions = new ArrayList<DimensionProjection>();
		for (int index = 0; index < resolved.view().dimensions().size(); index++) {
			var name = resolved.view().dimensions().get(index);
			var alias = "__d" + index;
			sql.append(", ").append(columns.get(name).value()).append(" AS ").append(alias);
			dimensions.add(new DimensionProjection(name, resolved.model().dimensions().get(name).type(), alias));
		}
		return dimensions;
	}

	private static ArrayList<MeasureProjection> appendRawMeasures(
			StringBuilder sql,
			ModelDefinition model,
			Map<String, SqlIdentifier> measureColumns) {
		var measures = new ArrayList<MeasureProjection>();
		var definitions = orderedMeasures(model);
		for (int index = 0; index < definitions.size(); index++) {
			var measure = definitions.get(index);
			var alias = measure.rawAggregation() == RawAggregation.SUM ? "__m" + index : null;
			if (alias != null) {
				sql.append(", ").append(measureColumns.get(measure.name()).value())
						.append(" AS ").append(alias);
			}
			measures.add(new MeasureProjection(measure.name(), measure.rawAggregation(), alias));
		}
		return measures;
	}

	private static ArrayList<MeasureProjection> appendRollupMeasures(
			StringBuilder sql,
			ModelDefinition model,
			Map<String, SqlIdentifier> measureColumns) {
		var measures = new ArrayList<MeasureProjection>();
		var definitions = orderedMeasures(model);
		for (int index = 0; index < definitions.size(); index++) {
			var measure = definitions.get(index);
			var alias = "__m" + index;
			sql.append(", sum(").append(measureColumns.get(measure.name()).value())
					.append(") AS ").append(alias);
			measures.add(new MeasureProjection(measure.name(), measure.rawAggregation(), alias));
		}
		return measures;
	}

	private static void appendFromAndRange(
			StringBuilder sql,
			String table,
			String timeColumn) {
		sql.append(" FROM ").append(table)
				.append(" WHERE ").append(timeColumn).append(" >= :fromInclusive")
				.append(" AND ").append(timeColumn).append(" < :toExclusive");
	}

	private static MapSqlParameterSource rawRangeParameters(InstantRange range) {
		return new MapSqlParameterSource()
				.addValue("fromInclusive", Timestamp.from(range.fromInclusive()))
				.addValue("toExclusive", Timestamp.from(range.toExclusive()));
	}

	private static MapSqlParameterSource rollupRangeParameters(
			ResolvedQuery resolved,
			InstantRange range) {
		return new MapSqlParameterSource()
				.addValue("fromInclusive", JdbcBucketValueMapper.toJdbcValue(resolved.view().bucket(), range.fromInclusive()))
				.addValue("toExclusive", JdbcBucketValueMapper.toJdbcValue(resolved.view().bucket(), range.toExclusive()));
	}

	private static void appendGroupBy(
			StringBuilder sql,
			ResolvedQuery resolved,
			String bucketColumn,
			Map<String, SqlIdentifier> dimensionColumns) {
		sql.append(" GROUP BY ").append(bucketColumn);
		resolved.view().dimensions().forEach(name -> sql.append(", ").append(dimensionColumns.get(name).value()));
	}

	private static java.util.List<MeasureDefinition> orderedMeasures(ModelDefinition model) {
		return model.measures().values().stream()
				.sorted(Comparator.comparing(MeasureDefinition::name))
				.toList();
	}
}
