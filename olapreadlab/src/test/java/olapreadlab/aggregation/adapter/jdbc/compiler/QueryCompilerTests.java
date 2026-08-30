package olapreadlab.aggregation.adapter.jdbc.compiler;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import olapreadlab.aggregation.adapter.jdbc.mapping.AggregateTableBinding;
import olapreadlab.aggregation.adapter.jdbc.mapping.JdbcStorageKeys;
import olapreadlab.aggregation.adapter.jdbc.mapping.RawTableBinding;
import olapreadlab.aggregation.adapter.jdbc.mapping.SqlIdentifier;
import olapreadlab.aggregation.adapter.jdbc.mapping.StorageBindingKey;
import olapreadlab.aggregation.application.AggregationQuery;
import olapreadlab.aggregation.application.InstantRange;
import olapreadlab.aggregation.application.ResolvedAggregationQuery;
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
import olapreadlab.aggregation.model.filter.LikeMode;
import olapreadlab.aggregation.model.filter.LogicalOperator;

class QueryCompilerTests {

	private static final Instant FROM = Instant.parse("2026-08-01T00:00:00Z");
	private static final Instant TO = Instant.parse("2026-08-02T00:00:00Z");

	@Test
	void postgresRawCompilerUsesOnlyTheProvidedRawBinding() {
		var fixture = fixture();
		var raw = new RawTableBinding(
				id("source.orders"), id("created_at"),
				Map.of("customerId", id("customer_id"), "region", id("region_code")),
				Map.of("amountSum", id("amount")));

		var compiled = new PostgresRawQueryCompiler().compile(
				fixture.query(), raw, new InstantRange(FROM, TO));

		assertThat(compiled.sql())
				.contains("FROM source.orders")
				.contains("created_at >= :fromInclusive")
				.contains("customer_id IN (:where0)")
				.contains("amount AS")
				.doesNotContain("order_count");
		assertThat(compiled.parameters().getValue("where0")).isEqualTo(List.of(7L));
		assertThat(compiled.projection().dimensions())
				.extracting(DimensionProjection::name)
				.containsExactly("customerId", "region");
	}

	@Test
	void compilesNestedComparisonBetweenLikePrefixNullAndOrPredicates() {
		var fixture = fixture(new FilterExpression.Junction(LogicalOperator.OR, List.of(
				new FilterExpression.Junction(LogicalOperator.AND, List.of(
						new FilterExpression.Comparison("customerId", ComparisonOperator.GTE, "10"),
						new FilterExpression.Between("customerId", "10", "20"),
						new FilterExpression.Like("region", "SEO%", LikeMode.LIKE),
						new FilterExpression.Like("region", "BUS", LikeMode.PREFIX))),
				new FilterExpression.NullCheck("region", true))));
		var raw = new RawTableBinding(
				id("source.orders"), id("created_at"),
				Map.of("customerId", id("customer_id"), "region", id("region_code")),
				Map.of("amountSum", id("amount")));

		var compiled = new PostgresRawQueryCompiler().compile(
				fixture.query(), raw, new InstantRange(FROM, TO));

		assertThat(compiled.sql())
				.contains("customer_id >= :where0")
				.contains("customer_id BETWEEN :where1 AND :where2")
				.contains("region_code LIKE :where3")
				.contains("region_code LIKE :where4")
				.contains("OR region_code IS NULL");
		assertThat(compiled.parameters().getValue("where0")).isEqualTo(10L);
		assertThat(compiled.parameters().getValue("where4")).isEqualTo("BUS%");
	}

	@Test
	void postgresAggregateCompilerBuildsSqlFromThePostgresTableBinding() {
		var fixture = fixture();
		var table = aggregateTable("reporting.pg_order_rollup", "pg_bucket");

		var compiled = new PostgresAggregateQueryCompiler().compile(
				fixture.query(), table, new InstantRange(FROM, TO));

		assertThat(compiled.sql())
				.contains("FROM reporting.pg_order_rollup")
				.contains("sum(order_count) AS")
				.contains("sum(amount_total) AS")
				.contains("GROUP BY pg_bucket, customer_id, region_code");
	}

	@Test
	void clickHouseCompilerOwnsClickHouseSqlGeneration() {
		var fixture = fixture();
		var table = aggregateTable("olap_clickhouse.order_rollup", "bucket_start");

		var compiled = new ClickHouseAggregateQueryCompiler().compile(
				fixture.query(), table, new InstantRange(FROM, TO));

		assertThat(compiled.sql())
				.contains("FROM olap_clickhouse.order_rollup")
				.contains("sum(order_count)")
				.contains("GROUP BY bucket_start, customer_id, region_code")
				.doesNotContain("FINAL");
	}

	@Test
	void higherPriorityBusinessCompilerOverridesTheDefaultForItsView() {
		var fixture = fixture();
		var table = aggregateTable("reporting.pg_order_rollup", "pg_bucket");
		AggregateQueryCompiler specialized = new AggregateQueryCompiler() {
			@Override public StorageBindingKey storageKey() {
				return JdbcStorageKeys.POSTGRES;
			}

			@Override public boolean supports(ResolvedAggregationQuery query) {
				return query.view().name().equals("customer-region-daily");
			}

			@Override public int priority() {
				return 100;
			}

			@Override public CompiledQuery<AggregateResultProjection> compile(
					ResolvedAggregationQuery query,
					AggregateTableBinding ignored,
					InstantRange range) {
				return new CompiledQuery<>("SPECIALIZED SQL", null, null);
			}
		};
		var registry = new AggregateQueryCompilerRegistry(
				List.of(new PostgresAggregateQueryCompiler(), specialized));

		var compiled = registry.compile(
				JdbcStorageKeys.POSTGRES, fixture.query(), table, new InstantRange(FROM, TO));

		assertThat(compiled.sql()).isEqualTo("SPECIALIZED SQL");
	}

	private static Fixture fixture() {
		return fixture(FilterExpression.MATCH_ALL);
	}

	private static Fixture fixture(FilterExpression where) {
		var customer = new DimensionDefinition("customerId", ScalarType.LONG);
		var region = new DimensionDefinition("region", ScalarType.STRING);
		var view = new AggregateViewDefinition(
				"customer-region-daily", TimeBucket.DAY,
				List.of("customerId", "region"), Set.of("customerId", "region"));
		var model = new AggregationModelDefinition(
				"orders",
				Map.of(customer.name(), customer, region.name(), region),
				Map.of(
						"orderCount", new MeasureDefinition("orderCount", RawAggregation.COUNT_ROWS),
						"amountSum", new MeasureDefinition("amountSum", RawAggregation.SUM)),
				Map.of(view.name(), view));
		var request = new AggregationQuery(
				"orders", view.name(), QueryMode.POSTGRES_RAW, FROM, TO,
				where instanceof FilterExpression.MatchAll ? Map.of("customerId", List.of(7L)) : Map.of(),
				where, FilterExpression.MATCH_ALL);
		return new Fixture(new ResolvedAggregationQuery(
				request, model, view,
				where instanceof FilterExpression.MatchAll
						? new FilterExpression.In("customerId", List.of(7L))
						: resolveValues(where),
				FilterExpression.MATCH_ALL));
	}

	private static FilterExpression resolveValues(FilterExpression expression) {
		if (expression instanceof FilterExpression.Junction junction) {
			return new FilterExpression.Junction(junction.operator(),
					junction.children().stream().map(QueryCompilerTests::resolveValues).toList());
		}
		if (expression instanceof FilterExpression.Comparison comparison
				&& comparison.field().equals("customerId")) {
			return new FilterExpression.Comparison(
					comparison.field(), comparison.operator(), Long.valueOf(comparison.value().toString()));
		}
		if (expression instanceof FilterExpression.Between between
				&& between.field().equals("customerId")) {
			return new FilterExpression.Between(
					between.field(), Long.valueOf(between.lower().toString()), Long.valueOf(between.upper().toString()));
		}
		return expression;
	}

	private static AggregateTableBinding aggregateTable(String table, String bucket) {
		return new AggregateTableBinding(
				id(table), id(bucket),
				Map.of("customerId", id("customer_id"), "region", id("region_code")),
				Map.of("orderCount", id("order_count"), "amountSum", id("amount_total")));
	}

	private static SqlIdentifier id(String value) {
		return SqlIdentifier.of(value);
	}

	private record Fixture(ResolvedAggregationQuery query) {
	}
}
