package olapreadlab.aggregation.application;

import java.math.BigDecimal;
import java.util.regex.Pattern;

import olapreadlab.aggregation.model.ResultRow;
import olapreadlab.aggregation.model.filter.FilterExpression;
import olapreadlab.aggregation.model.filter.LikeMode;
import olapreadlab.aggregation.model.filter.LogicalOperator;

final class HavingEvaluator {

	private HavingEvaluator() {
	}

	static boolean test(FilterExpression expression, ResultRow row) {
		if (expression instanceof FilterExpression.MatchAll) return true;
		if (expression instanceof FilterExpression.Junction junction) {
			return junction.operator() == LogicalOperator.AND
					? junction.children().stream().allMatch(child -> test(child, row))
					: junction.children().stream().anyMatch(child -> test(child, row));
		}
		var actual = row.measures().get(fieldOf(expression));
		if (expression instanceof FilterExpression.NullCheck nullCheck) {
			return nullCheck.isNull() == (actual == null);
		}
		if (actual == null) return false;
		if (expression instanceof FilterExpression.Comparison comparison) {
			var compared = compare(actual, comparison.value());
			return switch (comparison.operator()) {
				case EQ -> compared == 0;
				case NE -> compared != 0;
				case GT -> compared > 0;
				case GTE -> compared >= 0;
				case LT -> compared < 0;
				case LTE -> compared <= 0;
			};
		}
		if (expression instanceof FilterExpression.In in) {
			return in.values().stream().anyMatch(expected -> compare(actual, expected) == 0);
		}
		if (expression instanceof FilterExpression.Between between) {
			return compare(actual, between.lower()) >= 0 && compare(actual, between.upper()) <= 0;
		}
		if (expression instanceof FilterExpression.Like like) {
			return like.mode() == LikeMode.PREFIX
					? actual.toString().startsWith(like.pattern())
					: sqlLike(like.pattern()).matcher(actual.toString()).matches();
		}
		throw new IllegalArgumentException("Unresolved HAVING expression: " + expression);
	}

	private static int compare(Object actual, Object expected) {
		if (actual instanceof Number || expected instanceof Number) {
			return new BigDecimal(actual.toString()).compareTo(new BigDecimal(expected.toString()));
		}
		return actual.toString().compareTo(expected.toString());
	}

	private static Pattern sqlLike(String pattern) {
		var regex = new StringBuilder("^");
		boolean escaped = false;
		for (int index = 0; index < pattern.length(); index++) {
			char character = pattern.charAt(index);
			if (escaped) {
				regex.append(Pattern.quote(String.valueOf(character)));
				escaped = false;
			}
			else if (character == '\\') escaped = true;
			else if (character == '%') regex.append(".*");
			else if (character == '_') regex.append('.');
			else regex.append(Pattern.quote(String.valueOf(character)));
		}
		if (escaped) regex.append(Pattern.quote("\\"));
		return Pattern.compile(regex.append('$').toString(), Pattern.DOTALL);
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
}
