package olapreadlab.aggregation.adapter.jdbc.compiler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import olapreadlab.aggregation.adapter.jdbc.mapping.RollupTableBinding;
import olapreadlab.aggregation.adapter.jdbc.mapping.JdbcStorageKeys;
import olapreadlab.aggregation.adapter.jdbc.mapping.RawTableBinding;
import olapreadlab.aggregation.adapter.jdbc.mapping.SqlIdentifier;
import olapreadlab.aggregation.adapter.jdbc.mapping.StorageBindingKey;
import olapreadlab.aggregation.application.QueryRequest;
import olapreadlab.aggregation.application.InstantRange;
import olapreadlab.aggregation.application.ResolvedQuery;
import olapreadlab.aggregation.model.ViewDefinition;
import olapreadlab.aggregation.model.ModelDefinition;
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
	private static final List<String> DEFAULT_DIMENSIONS = List.of("customerId", "region");

	@Test
	void postgresRawCompilerUsesOnlyTheProvidedRawBinding() {
		var fixture = fixture();
		var raw = new RawTableBinding(
				id("source.orders"), id("created_at"),
				Map.of("customerId", id("customer_id"), "region", id("region_code")),
				Map.of("amountSum", id("amount")));

		var compiled = new PostgresQueryCompiler().compile(
				fixture.query(), raw, new InstantRange(FROM, TO));

		assertThat(compiled.sql())
				.isEqualTo(
						"SELECT created_at AS __event_time, customer_id AS __d0, region_code AS __d1, amount AS __m0 "
								+ "FROM source.orders "
								+ "WHERE created_at >= :fromInclusive AND created_at < :toExclusive "
								+ "AND customer_id IN (:where0)");
		assertThat(compiled.parameters().getValue("where0")).isEqualTo(List.of(7L));
		assertThat(compiled.projection().dimensions())
				.extracting(DimensionProjection::name)
				.containsExactly("customerId", "region");
		assertThat(compiled.projection().measures())
				.extracting(MeasureProjection::name, MeasureProjection::alias)
				.containsExactly(tuple("amountSum", "__m0"), tuple("orderCount", null));
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

		var compiled = new PostgresQueryCompiler().compile(
				fixture.query(), raw, new InstantRange(FROM, TO));

		assertThat(compiled.sql())
				.isEqualTo(
						"SELECT created_at AS __event_time, customer_id AS __d0, region_code AS __d1, amount AS __m0 "
								+ "FROM source.orders "
								+ "WHERE created_at >= :fromInclusive AND created_at < :toExclusive "
								+ "AND ((customer_id >= :where0 AND customer_id BETWEEN :where1 AND :where2 "
								+ "AND region_code LIKE :where3 AND region_code LIKE :where4) OR region_code IS NULL)");
		assertThat(compiled.parameters().getValue("where0")).isEqualTo(10L);
		assertThat(compiled.parameters().getValue("where4")).isEqualTo("BUS%");
	}

	@Test
	void rawCompilerOmitsPredicateSqlWhenResolvedQueryHasMatchAllWhere() {
		var fixture = fixture(TimeBucket.DAY, DEFAULT_DIMENSIONS,
				FilterExpression.MATCH_ALL, FilterExpression.MATCH_ALL);
		var raw = rawTable();

		var compiled = new PostgresQueryCompiler().compile(
				fixture.query(), raw, new InstantRange(FROM, TO));

		assertThat(compiled.sql()).contains("WHERE created_at >= :fromInclusive");
		assertThat(compiled.sql()).doesNotContain(":where0");
		assertThat(compiled.parameters().hasValue("where0")).isFalse();
	}

	@Test
	void postgresRollupCompilerBuildsSqlFromThePostgresTableBinding() {
		var fixture = fixture();
		var table = rollupTable("reporting.pg_order_rollup", "pg_bucket");

		var compiled = new PostgresQueryCompiler().compile(
				fixture.query(), table, new InstantRange(FROM, TO));

		assertThat(compiled.sql())
				.isEqualTo(
						"SELECT pg_bucket AS __bucket, customer_id AS __d0, region_code AS __d1, "
								+ "sum(amount_total) AS __m0, sum(order_count) AS __m1 "
								+ "FROM reporting.pg_order_rollup "
								+ "WHERE pg_bucket >= :fromInclusive AND pg_bucket < :toExclusive "
								+ "AND customer_id IN (:where0) "
								+ "GROUP BY pg_bucket, customer_id, region_code");
		assertThat(compiled.parameters().getValue("where0")).isEqualTo(List.of(7L));
	}

	@Test
	void rollupCompilersIgnoreHavingBecauseItIsAppliedAfterMerge() {
		var fixture = fixture(
				TimeBucket.DAY,
				DEFAULT_DIMENSIONS,
				FilterExpression.MATCH_ALL,
				new FilterExpression.Comparison("amountSum", ComparisonOperator.GT, new BigDecimal("10")));
		var table = rollupTable("reporting.rollup", "bucket_date");

		for (var compiler : List.of(new PostgresQueryCompiler(), new ClickHouseRollupQueryCompiler())) {
			var compiled = compiler.compile(fixture.query(), table, new InstantRange(FROM, TO));

			assertThat(compiled.sql()).doesNotContain("amount_total >").doesNotContain(":where0");
			assertThat(compiled.parameters().hasValue("where0")).isFalse();
		}
	}

	@Test
	void clickHouseCompilerOwnsClickHouseSqlGeneration() {
		var fixture = fixture();
		var table = rollupTable("olap_clickhouse.order_rollup", "bucket_start");

		var compiled = new ClickHouseRollupQueryCompiler().compile(
				fixture.query(), table, new InstantRange(FROM, TO));

		assertThat(compiled.sql())
				.isEqualTo(
						"SELECT bucket_start AS __bucket, customer_id AS __d0, region_code AS __d1, "
								+ "sum(amount_total) AS __m0, sum(order_count) AS __m1 "
								+ "FROM olap_clickhouse.order_rollup "
								+ "WHERE bucket_start >= :fromInclusive AND bucket_start < :toExclusive "
								+ "AND customer_id IN (:where0) "
								+ "GROUP BY bucket_start, customer_id, region_code");
		assertThat(compiled.parameters().getValue("where0")).isEqualTo(List.of(7L));
	}

	@Test
	void rollupCompilerUsesTimestampParametersForHourlyViews() {
		var fixture = fixture(TimeBucket.HOUR, DEFAULT_DIMENSIONS,
				new FilterExpression.In("customerId", List.of(7L)),
				FilterExpression.MATCH_ALL);
		var table = rollupTable("olap_clickhouse.hourly_order_rollup", "bucket_start");

		var compiled = new ClickHouseRollupQueryCompiler().compile(
				fixture.query(), table, new InstantRange(FROM, TO));

		assertThat(compiled.parameters().getValue("fromInclusive")).isInstanceOf(Timestamp.class);
		assertThat(compiled.parameters().getValue("toExclusive")).isInstanceOf(Timestamp.class);
	}

	@Test
	void rollupCompilerUsesDateParametersForDailyViews() {
		var fixture = fixture(TimeBucket.DAY, DEFAULT_DIMENSIONS,
				new FilterExpression.In("customerId", List.of(7L)),
				FilterExpression.MATCH_ALL);
		var table = rollupTable("reporting.daily_order_rollup", "bucket_date");

		var compiled = new PostgresQueryCompiler().compile(
				fixture.query(), table, new InstantRange(FROM, TO));

		assertThat(compiled.parameters().getValue("fromInclusive")).isInstanceOf(Date.class);
		assertThat(compiled.parameters().getValue("toExclusive")).isInstanceOf(Date.class);
	}

	@Test
	void compilersRespectResolvedViewDimensionOrder() {
		var fixture = fixture(
				TimeBucket.DAY,
				List.of("region", "customerId"),
				new FilterExpression.Comparison("region", ComparisonOperator.EQ, "SEOUL"),
				FilterExpression.MATCH_ALL);
		var raw = rawTable();
		var rollupTable = rollupTable("reporting.pg_order_rollup", "pg_bucket");

		var rawCompiled = new PostgresQueryCompiler().compile(
				fixture.query(), raw, new InstantRange(FROM, TO));
		var rollupCompiled = new PostgresQueryCompiler().compile(
				fixture.query(), rollupTable, new InstantRange(FROM, TO));

		assertThat(rawCompiled.projection().dimensions())
				.extracting(DimensionProjection::name)
				.containsExactly("region", "customerId");
		assertThat(rollupCompiled.projection().dimensions())
				.extracting(DimensionProjection::name)
				.containsExactly("region", "customerId");
		assertThat(rawCompiled.sql())
				.isEqualTo(
						"SELECT created_at AS __event_time, region_code AS __d0, customer_id AS __d1, amount AS __m0 "
								+ "FROM source.orders "
								+ "WHERE created_at >= :fromInclusive AND created_at < :toExclusive "
								+ "AND region_code = :where0");
		assertThat(rollupCompiled.sql())
				.isEqualTo(
						"SELECT pg_bucket AS __bucket, region_code AS __d0, customer_id AS __d1, "
								+ "sum(amount_total) AS __m0, sum(order_count) AS __m1 "
								+ "FROM reporting.pg_order_rollup "
								+ "WHERE pg_bucket >= :fromInclusive AND pg_bucket < :toExclusive "
								+ "AND region_code = :where0 "
								+ "GROUP BY pg_bucket, region_code, customer_id");
	}

	@Test
	void higherPriorityBusinessCompilerOverridesTheDefaultForItsView() {
		var fixture = fixture();
		var table = rollupTable("reporting.pg_order_rollup", "pg_bucket");
		QueryCompiler specialized = new QueryCompiler() {
			@Override public StorageBindingKey storageKey() {
				return JdbcStorageKeys.POSTGRES;
			}

			@Override public boolean supports(ResolvedQuery query) {
				return query.view().name().equals(fixture.query().view().name());
			}

			@Override public boolean supports(RollupTableBinding table) {
				return true;
			}

			@Override public int priority() {
				return 100;
			}

			@Override public CompiledQuery<RollupResultProjection> compile(
					ResolvedQuery query,
					RollupTableBinding ignored,
					InstantRange range) {
				return new CompiledQuery<>("SPECIALIZED SQL", null, null);
			}
		};
		var registry = new QueryCompilerRegistry(
				List.of(new PostgresQueryCompiler(), specialized));

		var compiled = registry.compile(
				JdbcStorageKeys.POSTGRES, fixture.query(), table, new InstantRange(FROM, TO));

		assertThat(compiled.sql()).isEqualTo("SPECIALIZED SQL");
	}

	@Test
	void registryDoesNotPickRollupOnlyCompilersForRawQueries() {
		var fixture = fixture();
		var raw = rawTable();
		QueryCompiler rollupOnly = new QueryCompiler() {
			@Override public StorageBindingKey storageKey() {
				return JdbcStorageKeys.POSTGRES;
			}

			@Override public boolean supports(RollupTableBinding table) {
				return true;
			}

			@Override public int priority() {
				return 100;
			}

			@Override public CompiledQuery<RollupResultProjection> compile(
					ResolvedQuery query,
					RollupTableBinding ignored,
					InstantRange range) {
				return new CompiledQuery<>("WRONG PATH", null, null);
			}
		};
		var registry = new QueryCompilerRegistry(
				List.of(new PostgresQueryCompiler(), rollupOnly));

		var compiled = registry.compile(
				JdbcStorageKeys.POSTGRES, fixture.query(), raw, new InstantRange(FROM, TO));

		assertThat(compiled.sql()).startsWith("SELECT created_at AS __event_time");
	}

	private static Fixture fixture() {
		return fixture(TimeBucket.DAY, DEFAULT_DIMENSIONS,
				new FilterExpression.In("customerId", List.of(7L)),
				FilterExpression.MATCH_ALL);
	}

	private static Fixture fixture(FilterExpression where) {
		return fixture(TimeBucket.DAY, DEFAULT_DIMENSIONS, where, FilterExpression.MATCH_ALL);
	}

	private static Fixture fixture(
			TimeBucket bucket,
			List<String> dimensions,
			FilterExpression where,
			FilterExpression having) {
		var customer = new DimensionDefinition("customerId", ScalarType.LONG);
		var region = new DimensionDefinition("region", ScalarType.STRING);
		var view = new ViewDefinition(
				"customer-region-" + bucket.name().toLowerCase(),
				bucket,
				dimensions,
				Set.copyOf(DEFAULT_DIMENSIONS));
		var model = new ModelDefinition(
				"orders",
				Map.of(customer.name(), customer, region.name(), region),
				Map.of(
						"orderCount", new MeasureDefinition("orderCount", RawAggregation.COUNT_ROWS),
						"amountSum", new MeasureDefinition("amountSum", RawAggregation.SUM)),
				Map.of(view.name(), view));
		var resolvedWhere = resolveValues(where);
		var resolvedHaving = resolveValues(having);
		var request = new QueryRequest(
				"orders", view.name(), QueryMode.POSTGRES_RAW, FROM, TO,
				resolvedWhere, resolvedHaving);
		return new Fixture(new ResolvedQuery(
				request, model, view,
				resolvedWhere,
				resolvedHaving));
	}

	private static FilterExpression resolveValues(FilterExpression expression) {
		if (expression instanceof FilterExpression.MatchAll) {
			return expression;
		}
		if (expression instanceof FilterExpression.Junction junction) {
			return new FilterExpression.Junction(junction.operator(),
					junction.children().stream().map(QueryCompilerTests::resolveValues).toList());
		}
		if (expression instanceof FilterExpression.In in
				&& in.field().equals("customerId")) {
			return new FilterExpression.In(
					in.field(),
					in.values().stream().map(value -> (Object) Long.valueOf(value.toString())).toList());
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

	private static RawTableBinding rawTable() {
		return new RawTableBinding(
				id("source.orders"), id("created_at"),
				Map.of("customerId", id("customer_id"), "region", id("region_code")),
				Map.of("amountSum", id("amount")));
	}

	private static RollupTableBinding rollupTable(String table, String bucket) {
		return new RollupTableBinding(
				id(table), id(bucket),
				Map.of("customerId", id("customer_id"), "region", id("region_code")),
				Map.of("orderCount", id("order_count"), "amountSum", id("amount_total")));
	}

	private static SqlIdentifier id(String value) {
		return SqlIdentifier.of(value);
	}

	private record Fixture(ResolvedQuery query) {
	}
}
