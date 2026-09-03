package olapreadlab.aggregation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import olapreadlab.aggregation.application.port.CustomFilterResolver;
import olapreadlab.aggregation.model.ViewDefinition;
import olapreadlab.aggregation.model.ModelDefinition;
import olapreadlab.aggregation.model.DimensionDefinition;
import olapreadlab.aggregation.model.MeasureDefinition;
import olapreadlab.aggregation.model.MeasureDefinition.RawAggregation;
import olapreadlab.aggregation.model.ScalarType;
import olapreadlab.aggregation.model.TimeBucket;
import olapreadlab.aggregation.model.filter.ComparisonOperator;
import olapreadlab.aggregation.model.filter.FilterExpression;
import olapreadlab.aggregation.model.filter.LogicalOperator;

class PredicateResolverTests {

	@Test
	void keepsMatchAllPredicatesAsIs() {
		var model = model();
		var resolver = new PredicateResolver(List.of());

		var where = resolver.resolveWhere(FilterExpression.MATCH_ALL, model, view(model));
		var having = resolver.resolveHaving(FilterExpression.MATCH_ALL, model, view(model));

		assertThat(where).isSameAs(FilterExpression.MATCH_ALL);
		assertThat(having).isSameAs(FilterExpression.MATCH_ALL);
	}

	@Test
	void resolvesCustomConditionsAndConvertsDimensionAndMeasureValues() {
		var model = model();
		CustomFilterResolver customResolver = new CustomFilterResolver() {
			@Override public boolean supports(String model, String filterName) {
				return model.equals("orders") && filterName.equals("vipRegion");
			}

			@Override public FilterExpression resolve(
					FilterExpression.Custom filter,
					ModelDefinition model,
					ViewDefinition view) {
				return new FilterExpression.Junction(LogicalOperator.AND, List.of(
						new FilterExpression.Comparison("customerId", ComparisonOperator.GTE,
								filter.arguments().get("minimumCustomerId").getFirst()),
						new FilterExpression.In("region", List.of("SEOUL", "BUSAN"))));
			}
		};
		var resolver = new PredicateResolver(List.of(customResolver));

		var where = resolver.resolveWhere(
				new FilterExpression.Custom("vipRegion", Map.of("minimumCustomerId", List.of("100"))),
				model,
				view(model));
		var having = resolver.resolveHaving(
				new FilterExpression.Between("amountSum", "10.5", "20.5"),
				model,
				view(model));

		var junction = (FilterExpression.Junction) where;
		assertThat(((FilterExpression.Comparison) junction.children().getFirst()).value()).isEqualTo(100L);
		assertThat(((FilterExpression.Between) having).lower()).isEqualTo(new java.math.BigDecimal("10.5"));
	}

	@Test
	void resolvesNestedJunctionPredicatesRecursively() {
		var model = model();
		var resolver = new PredicateResolver(List.of());

		var resolved = resolver.resolveWhere(
				new FilterExpression.Junction(LogicalOperator.OR, List.of(
						new FilterExpression.Junction(LogicalOperator.AND, List.of(
								new FilterExpression.Comparison("customerId", ComparisonOperator.GTE, "100"),
								new FilterExpression.NullCheck("region", false))),
						new FilterExpression.Comparison("customerId", ComparisonOperator.LT, "200"))),
				model,
				view(model));

		var root = (FilterExpression.Junction) resolved;
		var andBranch = (FilterExpression.Junction) root.children().getFirst();
		assertThat(((FilterExpression.Comparison) andBranch.children().getFirst()).value()).isEqualTo(100L);
		assertThat(((FilterExpression.Comparison) root.children().get(1)).value()).isEqualTo(200L);
	}

	@Test
	void rejectsLikeForNonStringFieldsAndDimensionsInHaving() {
		var model = model();
		var resolver = new PredicateResolver(List.of());

		assertThatThrownBy(() -> resolver.resolveWhere(
				new FilterExpression.Like("customerId", "1", olapreadlab.aggregation.model.filter.LikeMode.PREFIX),
				model,
				view(model)))
				.hasMessageContaining("LIKE requires a STRING field");
		assertThatThrownBy(() -> resolver.resolveHaving(
				new FilterExpression.Comparison("region", ComparisonOperator.EQ, "SEOUL"),
				model,
				view(model)))
				.hasMessageContaining("HAVING requires a measure");
	}

	@Test
	void rejectsUnknownCustomFilters() {
		var model = model();
		var resolver = new PredicateResolver(List.of());

		assertThatThrownBy(() -> resolver.resolveWhere(
				new FilterExpression.Custom("missingFilter", Map.of()),
				model,
				view(model)))
				.hasMessageContaining("Unknown custom filter: missingFilter");
	}

	@Test
	void rejectsEndlessCustomExpansion() {
		var model = model();
		CustomFilterResolver customResolver = new CustomFilterResolver() {
			@Override public boolean supports(String model, String filterName) {
				return model.equals("orders") && filterName.equals("loop");
			}

			@Override public FilterExpression resolve(
					FilterExpression.Custom filter,
					ModelDefinition model,
					ViewDefinition view) {
				return new FilterExpression.Custom("loop", Map.of());
			}
		};
		var resolver = new PredicateResolver(List.of(customResolver));

		assertThatThrownBy(() -> resolver.resolveWhere(
				new FilterExpression.Custom("loop", Map.of()),
				model,
				view(model)))
				.hasMessageContaining("Custom filter expansion is too deep");
	}

	@Test
	void wrapsScalarConversionFailuresWithFilterContext() {
		var model = model();
		var resolver = new PredicateResolver(List.of());

		assertThatThrownBy(() -> resolver.resolveWhere(
				new FilterExpression.Comparison("customerId", ComparisonOperator.EQ, "not-a-long"),
				model,
				view(model)))
				.hasMessageContaining("Invalid value for filter customerId");
	}

	private static ModelDefinition model() {
		var customer = new DimensionDefinition("customerId", ScalarType.LONG);
		var region = new DimensionDefinition("region", ScalarType.STRING);
		var view = new ViewDefinition(
				"customer-region-daily", TimeBucket.DAY,
				List.of("customerId", "region"), Set.of("customerId", "region"));
		return new ModelDefinition(
				"orders",
				Map.of(customer.name(), customer, region.name(), region),
				Map.of("amountSum", new MeasureDefinition("amountSum", RawAggregation.SUM)),
				Map.of(view.name(), view));
	}

	private static ViewDefinition view(ModelDefinition model) {
		return model.views().get("customer-region-daily");
	}

}
