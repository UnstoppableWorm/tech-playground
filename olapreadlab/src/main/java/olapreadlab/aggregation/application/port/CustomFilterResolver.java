package olapreadlab.aggregation.application.port;

import olapreadlab.aggregation.model.ViewDefinition;
import olapreadlab.aggregation.model.ModelDefinition;
import olapreadlab.aggregation.model.filter.FilterExpression;

/** Business extension point that expands a named domain condition into the common predicate AST. */
public interface CustomFilterResolver {

	boolean supports(String model, String filterName);

	FilterExpression resolve(
			FilterExpression.Custom filter,
			ModelDefinition model,
			ViewDefinition view);
}
