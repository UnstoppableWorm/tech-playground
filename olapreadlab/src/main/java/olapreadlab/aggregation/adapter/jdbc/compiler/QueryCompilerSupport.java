package olapreadlab.aggregation.adapter.jdbc.compiler;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import olapreadlab.aggregation.adapter.jdbc.mapping.SqlIdentifier;
import olapreadlab.aggregation.application.ResolvedQuery;
import olapreadlab.aggregation.model.filter.FilterExpression;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

final class QueryCompilerSupport {

	private QueryCompilerSupport() {
	}

	static void appendFilters(
			StringBuilder sql,
			MapSqlParameterSource parameters,
			ResolvedQuery resolved,
			Map<String, SqlIdentifier> dimensionColumns) {
		if (resolved.where() instanceof FilterExpression.MatchAll) return;
		sql.append(" AND ");
		appendExpression(sql, parameters, resolved.where(), dimensionColumns, new AtomicInteger());
	}

	private static void appendExpression(
			StringBuilder sql,
			MapSqlParameterSource parameters,
			FilterExpression expression,
			Map<String, SqlIdentifier> columns,
			AtomicInteger parameterSequence) {
		if (expression instanceof FilterExpression.Junction junction) {
			sql.append('(');
			for (int index = 0; index < junction.children().size(); index++) {
				if (index > 0) sql.append(' ').append(junction.operator().name()).append(' ');
				appendExpression(sql, parameters, junction.children().get(index), columns, parameterSequence);
			}
			sql.append(')');
			return;
		}
		var field = fieldOf(expression);
		var column = columns.get(field);
		if (column == null) throw new IllegalArgumentException("No physical column binding for filter: " + field);
		if (expression instanceof FilterExpression.NullCheck nullCheck) {
			sql.append(column.value()).append(nullCheck.isNull() ? " IS NULL" : " IS NOT NULL");
			return;
		}
		var parameter = "where" + parameterSequence.getAndIncrement();
		if (expression instanceof FilterExpression.Comparison comparison) {
			sql.append(column.value()).append(' ').append(sqlOperator(comparison.operator()))
					.append(" :").append(parameter);
			parameters.addValue(parameter, comparison.value());
		}
		else if (expression instanceof FilterExpression.In in) {
			sql.append(column.value()).append(" IN (:").append(parameter).append(')');
			parameters.addValue(parameter, in.values());
		}
		else if (expression instanceof FilterExpression.Between between) {
			var upperParameter = "where" + parameterSequence.getAndIncrement();
			sql.append(column.value()).append(" BETWEEN :").append(parameter)
					.append(" AND :").append(upperParameter);
			parameters.addValue(parameter, between.lower());
			parameters.addValue(upperParameter, between.upper());
		}
		else if (expression instanceof FilterExpression.Like like) {
			sql.append(column.value()).append(" LIKE :").append(parameter);
			parameters.addValue(parameter,
					like.mode() == olapreadlab.aggregation.model.filter.LikeMode.PREFIX
							? escapeLike(like.pattern()) + "%" : like.pattern());
		}
		else {
			throw new IllegalArgumentException("Unresolved filter expression: " + expression);
		}
	}

	private static String sqlOperator(olapreadlab.aggregation.model.filter.ComparisonOperator operator) {
		return switch (operator) {
			case EQ -> "=";
			case NE -> "<>";
			case GT -> ">";
			case GTE -> ">=";
			case LT -> "<";
			case LTE -> "<=";
		};
	}

	private static String fieldOf(FilterExpression expression) {
		return switch (expression) {
			case FilterExpression.Comparison value -> value.field();
			case FilterExpression.In value -> value.field();
			case FilterExpression.Between value -> value.field();
			case FilterExpression.Like value -> value.field();
			case FilterExpression.NullCheck value -> value.field();
			default -> throw new IllegalArgumentException("Expression does not reference a field");
		};
	}

	private static String escapeLike(String value) {
		return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
	}
}
