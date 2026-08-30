package olapreadlab.aggregation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import olapreadlab.aggregation.application.port.CustomFilterResolver;
import olapreadlab.aggregation.model.AggregateViewDefinition;
import olapreadlab.aggregation.model.AggregationModelDefinition;
import olapreadlab.aggregation.model.DimensionDefinition;
import olapreadlab.aggregation.model.MeasureDefinition;
import olapreadlab.aggregation.model.MeasureDefinition.RawAggregation;
import olapreadlab.aggregation.model.QueryMode;
import olapreadlab.aggregation.model.ScalarType;
import olapreadlab.aggregation.model.TimeBucket;
import olapreadlab.aggregation.model.filter.ComparisonOperator;
import olapreadlab.aggregation.model.filter.FilterExpression;
import olapreadlab.aggregation.model.filter.LogicalOperator;

class AggregationModelCatalogPredicateTests {

	@Test
	void resolvesCustomConditionsAndConvertsDimensionAndMeasureValues() {
		CustomFilterResolver resolver = new CustomFilterResolver() {
			@Override public boolean supports(String model, String filterName) {
				return model.equals("orders") && filterName.equals("vipRegion");
			}

			@Override public FilterExpression resolve(
					FilterExpression.Custom filter,
					AggregationModelDefinition model,
					AggregateViewDefinition view) {
				return new FilterExpression.Junction(LogicalOperator.AND, List.of(
						new FilterExpression.Comparison("customerId", ComparisonOperator.GTE,
								filter.arguments().get("minimumCustomerId").getFirst()),
						new FilterExpression.In("region", List.of("SEOUL", "BUSAN"))));
			}
		};
		var catalog = new AggregationModelCatalog(List.of(AggregationModelCatalogPredicateTests::model),
				List.of(resolver));
		var query = query(
				new FilterExpression.Custom("vipRegion", Map.of("minimumCustomerId", List.of("100"))),
				new FilterExpression.Between("amountSum", "10.5", "20.5"));

		var resolved = catalog.resolve(query);

		var where = (FilterExpression.Junction) resolved.where();
		assertThat(((FilterExpression.Comparison) where.children().getFirst()).value()).isEqualTo(100L);
		var having = (FilterExpression.Between) resolved.having();
		assertThat(having.lower()).isEqualTo(new java.math.BigDecimal("10.5"));
	}

	@Test
	void rejectsLikeForNonStringFieldsAndDimensionsInHaving() {
		var catalog = new AggregationModelCatalog(List.of(AggregationModelCatalogPredicateTests::model));

		assertThatThrownBy(() -> catalog.resolve(query(
				new FilterExpression.Like("customerId", "1", olapreadlab.aggregation.model.filter.LikeMode.PREFIX),
				FilterExpression.MATCH_ALL)))
				.hasMessageContaining("LIKE requires a STRING field");
		assertThatThrownBy(() -> catalog.resolve(query(
				FilterExpression.MATCH_ALL,
				new FilterExpression.Comparison("region", ComparisonOperator.EQ, "SEOUL"))))
				.hasMessageContaining("HAVING requires a measure");
	}

	private static AggregationQuery query(FilterExpression where, FilterExpression having) {
		return new AggregationQuery(
				"orders", "customer-region-daily", QueryMode.POSTGRES_RAW,
				Instant.parse("2026-08-01T00:00:00Z"), Instant.parse("2026-08-02T00:00:00Z"),
				Map.of(), where, having);
	}

	private static AggregationModelDefinition model() {
		var customer = new DimensionDefinition("customerId", ScalarType.LONG);
		var region = new DimensionDefinition("region", ScalarType.STRING);
		var view = new AggregateViewDefinition(
				"customer-region-daily", TimeBucket.DAY,
				List.of("customerId", "region"), Set.of("customerId", "region"));
		return new AggregationModelDefinition(
				"orders",
				Map.of(customer.name(), customer, region.name(), region),
				Map.of("amountSum", new MeasureDefinition("amountSum", RawAggregation.SUM)),
				Map.of(view.name(), view));
	}
}
