package olapreadlab.aggregation.application;

import java.util.List;

import olapreadlab.aggregation.application.port.CustomFilterResolver;
import olapreadlab.aggregation.model.ViewDefinition;
import olapreadlab.aggregation.model.ModelDefinition;
import olapreadlab.aggregation.model.ScalarType;
import olapreadlab.aggregation.model.filter.FilterExpression;

import org.springframework.stereotype.Component;

@Component
public class PredicateResolver {

	private final List<CustomFilterResolver> customResolvers;

	public PredicateResolver(List<CustomFilterResolver> customResolvers) {
		this.customResolvers = List.copyOf(customResolvers);
	}

	public FilterExpression resolveWhere(
			FilterExpression expression,
			ModelDefinition model,
			ViewDefinition view) {
		return resolveExpression(normalize(expression), model, view, Target.WHERE, 0);
	}

	public FilterExpression resolveHaving(
			FilterExpression expression,
			ModelDefinition model,
			ViewDefinition view) {
		return resolveExpression(normalize(expression), model, view, Target.HAVING, 0);
	}

	private FilterExpression resolveExpression(
			FilterExpression expression,
			ModelDefinition model,
			ViewDefinition view,
			Target target,
			int customDepth) {
		if (expression instanceof FilterExpression.MatchAll) return expression;
		if (expression instanceof FilterExpression.Junction junction) {
			return new FilterExpression.Junction(junction.operator(), junction.children().stream()
					.map(child -> resolveExpression(child, model, view, target, customDepth)).toList());
		}
		if (expression instanceof FilterExpression.Custom custom) {
			if (customDepth >= 4) throw new IllegalArgumentException("Custom filter expansion is too deep");
			var resolver = customResolvers.stream()
					.filter(candidate -> candidate.supports(model.name(), custom.name()))
					.findFirst()
					.orElseThrow(() -> new IllegalArgumentException("Unknown custom filter: " + custom.name()));
			return resolveExpression(resolver.resolve(custom, model, view), model, view, target, customDepth + 1);
		}

		var field = fieldOf(expression);
		var type = typeFor(field, model, view, target);
		try {
			if (expression instanceof FilterExpression.Comparison comparison) {
				return new FilterExpression.Comparison(field, comparison.operator(), convert(type, comparison.value()));
			}
			if (expression instanceof FilterExpression.In in) {
				return new FilterExpression.In(field, in.values().stream().map(type::convert).toList());
			}
			if (expression instanceof FilterExpression.Between between) {
				return new FilterExpression.Between(
						field, convert(type, between.lower()), convert(type, between.upper()));
			}
			if (expression instanceof FilterExpression.Like like) {
				if (type != ScalarType.STRING) throw new IllegalArgumentException("LIKE requires a STRING field: " + field);
				return new FilterExpression.Like(field, like.pattern(), like.mode());
			}
			if (expression instanceof FilterExpression.NullCheck) return expression;
		}
		catch (RuntimeException exception) {
			if (exception instanceof IllegalArgumentException && exception.getMessage() != null
					&& exception.getMessage().startsWith("LIKE requires")) throw exception;
			throw new IllegalArgumentException("Invalid value for filter " + field, exception);
		}
		throw new IllegalArgumentException("Unsupported filter expression: " + expression.getClass().getSimpleName());
	}

	private static FilterExpression normalize(FilterExpression expression) {
		return expression == null ? FilterExpression.MATCH_ALL : expression;
	}

	private static Object convert(ScalarType type, Object value) {
		return type.convert(value);
	}

	private static ScalarType typeFor(
			String field,
			ModelDefinition model,
			ViewDefinition view,
			Target target) {
		if (target == Target.WHERE) {
			if (!view.allowedFilters().contains(field)) {
				throw new IllegalArgumentException("Filter is not allowed for this view: " + field);
			}
			return required(model.dimensions().get(field), "Unknown dimension: " + field).type();
		}
		if (model.measures().containsKey(field)) return ScalarType.DECIMAL;
		throw new IllegalArgumentException("HAVING requires a measure field: " + field);
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

	private static <T> T required(T value, String message) {
		if (value == null) throw new IllegalArgumentException(message);
		return value;
	}

	private enum Target { WHERE, HAVING }

}
