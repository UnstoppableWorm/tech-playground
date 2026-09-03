package olapreadlab.aggregation.adapter.model;

import java.util.List;

import olapreadlab.aggregation.application.port.CustomFilterResolver;
import olapreadlab.aggregation.model.ViewDefinition;
import olapreadlab.aggregation.model.ModelDefinition;
import olapreadlab.aggregation.model.filter.ComparisonOperator;
import olapreadlab.aggregation.model.filter.FilterExpression;
import olapreadlab.aggregation.model.filter.LogicalOperator;

import org.springframework.stereotype.Component;

/** Example business filter: selects one exact organ/disease pair without leaking physical columns. */
@Component
class MedicalHistoryCustomFilterResolver implements CustomFilterResolver {

	static final String FILTER_NAME = "organDiseasePair";

	@Override
	public boolean supports(String model, String filterName) {
		return "medical-history".equals(model) && FILTER_NAME.equals(filterName);
	}

	@Override
	public FilterExpression resolve(
			FilterExpression.Custom filter,
			ModelDefinition model,
			ViewDefinition view) {
		return new FilterExpression.Junction(LogicalOperator.AND, List.of(
				new FilterExpression.Comparison(
						"organCode", ComparisonOperator.EQ, one(filter, "organCode")),
				new FilterExpression.Comparison(
						"diseaseCode", ComparisonOperator.EQ, one(filter, "diseaseCode"))));
	}

	private static Object one(FilterExpression.Custom filter, String argument) {
		var values = filter.arguments().get(argument);
		if (values == null || values.size() != 1) {
			throw new IllegalArgumentException(
					FILTER_NAME + " requires exactly one " + argument + " argument");
		}
		return values.getFirst();
	}
}
