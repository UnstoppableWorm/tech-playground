package olapreadlab.aggregation.adapter.jdbc;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;

import olapreadlab.aggregation.adapter.jdbc.compiler.AggregateResultProjection;
import olapreadlab.aggregation.adapter.jdbc.compiler.CompiledQuery;
import olapreadlab.aggregation.adapter.jdbc.compiler.RawResultProjection;
import olapreadlab.aggregation.model.AggregationKey;
import olapreadlab.aggregation.model.AggregationRow;
import olapreadlab.aggregation.model.MeasureDefinition.RawAggregation;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

final class JdbcAggregationQueryExecutor {

	private JdbcAggregationQueryExecutor() {
	}

	static List<AggregationRow> queryRaw(
			NamedParameterJdbcTemplate jdbc,
			CompiledQuery<RawResultProjection> compiled) {
		var projection = compiled.projection();
		return jdbc.query(compiled.sql(), compiled.parameters(), resultSet -> {
			var aggregated = new LinkedHashMap<AggregationKey, AggregationRow>();
			while (resultSet.next()) {
				var dimensions = new LinkedHashMap<String, Object>();
				for (var dimension : projection.dimensions()) {
					dimensions.put(dimension.name(),
							dimension.type().convert(resultSet.getObject(dimension.alias())));
				}
				var measures = new LinkedHashMap<String, BigDecimal>();
				for (var measure : projection.measures()) {
					var value = measure.rawAggregation() == RawAggregation.COUNT_ROWS
							? BigDecimal.ONE : resultSet.getBigDecimal(measure.alias());
					measures.put(measure.name(), value);
				}
				var eventTime = resultSet.getTimestamp(projection.eventTimeAlias()).toInstant();
				var row = new AggregationRow(
						projection.bucket().floor(eventTime), dimensions, measures);
				aggregated.merge(row.key(), row, AggregationRow::add);
			}
			return List.copyOf(aggregated.values());
		});
	}

	static List<AggregationRow> queryAggregate(
			NamedParameterJdbcTemplate jdbc,
			CompiledQuery<AggregateResultProjection> compiled) {
		var projection = compiled.projection();
		return jdbc.query(compiled.sql(), compiled.parameters(), (resultSet, rowNumber) -> {
			var dimensions = new LinkedHashMap<String, Object>();
			for (var dimension : projection.dimensions()) {
				dimensions.put(dimension.name(),
						dimension.type().convert(resultSet.getObject(dimension.alias())));
			}
			var measures = new LinkedHashMap<String, BigDecimal>();
			for (var measure : projection.measures()) {
				measures.put(measure.name(), resultSet.getBigDecimal(measure.alias()));
			}
			return new AggregationRow(
					JdbcBucketValueMapper.fromJdbcValue(resultSet.getObject(projection.bucketAlias())),
					dimensions,
					measures);
		});
	}
}
