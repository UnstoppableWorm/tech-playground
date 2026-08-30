package olapreadlab.aggregation.model.filter;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Storage-independent predicate AST. Field names are logical model names, never SQL identifiers. */
public sealed interface FilterExpression {

	MatchAll MATCH_ALL = new MatchAll();

	record MatchAll() implements FilterExpression {
	}

	record Comparison(String field, ComparisonOperator operator, Object value)
			implements FilterExpression {
		public Comparison {
			requireField(field);
			Objects.requireNonNull(operator, "operator");
			Objects.requireNonNull(value, "value");
		}
	}

	record In(String field, List<Object> values) implements FilterExpression {
		public In {
			requireField(field);
			values = List.copyOf(values);
			if (values.isEmpty() || values.stream().anyMatch(Objects::isNull)) {
				throw new IllegalArgumentException("IN requires at least one non-null value");
			}
		}
	}

	record Between(String field, Object lower, Object upper) implements FilterExpression {
		public Between {
			requireField(field);
			Objects.requireNonNull(lower, "lower");
			Objects.requireNonNull(upper, "upper");
		}
	}

	record Like(String field, String pattern, LikeMode mode) implements FilterExpression {
		public Like {
			requireField(field);
			Objects.requireNonNull(pattern, "pattern");
			Objects.requireNonNull(mode, "mode");
		}
	}

	record NullCheck(String field, boolean isNull) implements FilterExpression {
		public NullCheck {
			requireField(field);
		}
	}

	record Junction(LogicalOperator operator, List<FilterExpression> children)
			implements FilterExpression {
		public Junction {
			Objects.requireNonNull(operator, "operator");
			children = List.copyOf(children);
			if (children.isEmpty() || children.stream().anyMatch(Objects::isNull)) {
				throw new IllegalArgumentException("A logical predicate requires children");
			}
		}
	}

	record Custom(String name, Map<String, List<Object>> arguments) implements FilterExpression {
		public Custom {
			requireField(name);
			arguments = Map.copyOf(arguments.entrySet().stream().collect(
					java.util.stream.Collectors.toMap(Map.Entry::getKey, entry -> List.copyOf(entry.getValue()))));
		}
	}

	private static void requireField(String value) {
		if (value == null || value.isBlank()) throw new IllegalArgumentException("Field must not be blank");
	}
}
