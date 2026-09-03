package olapreadlab.api.graphql;

import java.util.LinkedHashMap;
import java.util.List;

import olapreadlab.aggregation.model.filter.ComparisonOperator;
import olapreadlab.aggregation.model.filter.FilterExpression;
import olapreadlab.aggregation.model.filter.LikeMode;
import olapreadlab.aggregation.model.filter.LogicalOperator;

public record OlapPredicateInput(
		OlapPredicateOperator operator,
		String field,
		String value,
		List<String> values,
		String lower,
		String upper,
		List<OlapPredicateInput> children,
		String name,
		List<OlapFilterArgumentInput> arguments) {

	public OlapPredicateInput {
		values = values == null ? List.of() : List.copyOf(values);
		children = children == null ? List.of() : List.copyOf(children);
		arguments = arguments == null ? List.of() : List.copyOf(arguments);
	}

	FilterExpression toExpression() {
		return toExpression(new Budget(), 0);
	}

	private FilterExpression toExpression(Budget budget, int depth) {
		if (operator == null) throw new IllegalArgumentException("Predicate operator is required");
		if (depth > 8) throw new IllegalArgumentException("Predicate nesting exceeds 8 levels");
		if (++budget.nodes > 64) throw new IllegalArgumentException("At most 64 predicate nodes are allowed");
		if (values.size() > 1_000) throw new IllegalArgumentException("IN accepts at most 1000 values");
		return switch (operator) {
			case AND, OR -> new FilterExpression.Junction(
					operator == OlapPredicateOperator.AND ? LogicalOperator.AND : LogicalOperator.OR,
					children.stream().map(child -> child.toExpression(budget, depth + 1)).toList());
			case EQ, NE, GT, GTE, LT, LTE -> new FilterExpression.Comparison(
					field, ComparisonOperator.valueOf(operator.name()), required("value", value));
			case IN -> new FilterExpression.In(field, List.copyOf(values));
			case BETWEEN -> new FilterExpression.Between(
					field, required("lower", lower), required("upper", upper));
			case LIKE -> new FilterExpression.Like(field, required("value", value), LikeMode.LIKE);
			case PREFIX -> new FilterExpression.Like(field, required("value", value), LikeMode.PREFIX);
			case IS_NULL -> new FilterExpression.NullCheck(field, true);
			case IS_NOT_NULL -> new FilterExpression.NullCheck(field, false);
			case CUSTOM -> custom();
		};
	}

	private FilterExpression custom() {
		var mapped = new LinkedHashMap<String, List<Object>>();
		for (var argument : arguments) {
			if (argument.name() == null || argument.name().isBlank()) {
				throw new IllegalArgumentException("Custom filter argument name must not be blank");
			}
			if (argument.values().size() > 1_000) {
				throw new IllegalArgumentException("Custom filter arguments accept at most 1000 values");
			}
			if (mapped.putIfAbsent(argument.name(), List.copyOf(argument.values())) != null) {
				throw new IllegalArgumentException("Duplicate custom filter argument: " + argument.name());
			}
		}
		return new FilterExpression.Custom(required("name", name), mapped);
	}

	private static String required(String field, String value) {
		if (value == null) throw new IllegalArgumentException(field + " is required for the predicate");
		return value;
	}

	private static final class Budget {
		private int nodes;
	}
}
