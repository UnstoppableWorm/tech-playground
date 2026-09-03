package olapreadlab.aggregation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import olapreadlab.aggregation.application.port.RollupQueryPort;
import olapreadlab.aggregation.application.port.CoverageCheckpointPort;
import olapreadlab.aggregation.application.port.RawQueryPort;
import olapreadlab.aggregation.model.RollupStore;
import olapreadlab.aggregation.model.ViewDefinition;
import olapreadlab.aggregation.model.ModelDefinition;
import olapreadlab.aggregation.model.RollupPipeline;
import olapreadlab.aggregation.model.ResultRow;
import olapreadlab.aggregation.model.DimensionDefinition;
import olapreadlab.aggregation.model.MeasureDefinition;
import olapreadlab.aggregation.model.MeasureDefinition.RawAggregation;
import olapreadlab.aggregation.model.QueryMode;
import olapreadlab.aggregation.model.ScalarType;
import olapreadlab.aggregation.model.TimeBucket;
import olapreadlab.aggregation.model.filter.ComparisonOperator;
import olapreadlab.aggregation.model.filter.FilterExpression;

class QueryServiceTests {

	private final RawQueryPort rawPort = mock(RawQueryPort.class);
	private final RollupQueryPort postgresRollupPort = mock(RollupQueryPort.class);
	private final RollupQueryPort clickHouseRollupPort = mock(RollupQueryPort.class);
	private final CoverageCheckpointPort checkpointPort = mock(CoverageCheckpointPort.class);
	private QueryService service;

	@BeforeEach
	void setUp() {
		var modelRegistry = new ModelRegistry(List.of(orderModel()));
		var predicateResolver = new PredicateResolver(List.of());
		var queryResolver = new QueryResolver(modelRegistry, predicateResolver);
		when(postgresRollupPort.store()).thenReturn(RollupStore.POSTGRES);
		when(clickHouseRollupPort.store()).thenReturn(RollupStore.CLICKHOUSE);
		var planner = new ReadPlanner(checkpointPort);
		var rollupPorts = new RollupQueryPortRegistry(
				List.of(postgresRollupPort, clickHouseRollupPort));
		service = new QueryService(queryResolver, planner, rawPort, rollupPorts);
	}

	@Test
	void commonCoreQueriesRawWithoutKnowingTheBusinessDimensions() {
		var query = query(QueryMode.POSTGRES_RAW,
				new FilterExpression.In("region", List.of("SEOUL")));
		var row = row("2026-08-01", "SEOUL", "15.50");
		when(rawPort.queryRaw(any(), eq(query.fromInclusive()), eq(query.toExclusive())))
				.thenReturn(List.of(row));

		var result = service.query(query);

		assertThat(result.model()).isEqualTo("orders");
		assertThat(result.view()).isEqualTo("customer-region-daily");
		assertThat(result.rows()).containsExactly(row);
		verify(postgresRollupPort, never()).queryRollup(any(), any(), any());
		verify(clickHouseRollupPort, never()).queryRollup(any(), any(), any());
		verify(checkpointPort, never()).findCoveredUntil(any(), any(), any());
	}

	@Test
	void hybridSplitsAtTheModelAndViewCheckpoint() {
		var query = query(QueryMode.CLICKHOUSE_HYBRID, FilterExpression.MATCH_ALL);
		var splitAt = Instant.parse("2026-08-03T00:00:00Z");
		when(checkpointPort.findCoveredUntil(
				"orders", "customer-region-daily", RollupPipeline.CLICKHOUSE))
				.thenReturn(Optional.of(splitAt));
		when(clickHouseRollupPort.queryRollup(
				any(),
				eq(Instant.parse("2026-08-01T00:00:00Z")), eq(Instant.parse("2026-08-03T00:00:00Z"))))
				.thenReturn(List.of(row("2026-08-01", "SEOUL", "10")));
		when(rawPort.queryRaw(any(), eq(splitAt), eq(query.toExclusive())))
				.thenReturn(List.of(row("2026-08-03", "BUSAN", "20")));

		var result = service.query(query);

		assertThat(result.coveredUntil()).isEqualTo(splitAt);
		assertThat(result.rows()).extracting(ResultRow::bucket)
				.containsExactly(
						Instant.parse("2026-08-01T00:00:00Z"),
						Instant.parse("2026-08-03T00:00:00Z"));
		verify(clickHouseRollupPort).queryRollup(
				any(),
				eq(Instant.parse("2026-08-01T00:00:00Z")), eq(Instant.parse("2026-08-03T00:00:00Z")));
		verify(postgresRollupPort, never()).queryRollup(any(), any(), any());
	}

	@Test
	void postgresBatchModeSelectsOnlyThePostgresRollupAdapter() {
		var query = query(QueryMode.POSTGRES_BATCH_HYBRID, FilterExpression.MATCH_ALL);
		var splitAt = Instant.parse("2026-08-03T00:00:00Z");
		when(checkpointPort.findCoveredUntil(
				"orders", "customer-region-daily", RollupPipeline.SPRING_BATCH))
				.thenReturn(Optional.of(splitAt));
		when(postgresRollupPort.queryRollup(any(), any(), any())).thenReturn(List.of());
		when(rawPort.queryRaw(any(), eq(splitAt), eq(query.toExclusive()))).thenReturn(List.of());

		service.query(query);

		verify(postgresRollupPort).queryRollup(
				any(),
				eq(Instant.parse("2026-08-01T00:00:00Z")),
				eq(Instant.parse("2026-08-03T00:00:00Z")));
		verify(clickHouseRollupPort, never()).queryRollup(any(), any(), any());
	}

	@Test
	void havingIsAppliedAfterHybridRowsAreMerged() {
		var base = query(QueryMode.CLICKHOUSE_HYBRID, FilterExpression.MATCH_ALL);
		var query = new QueryRequest(
				base.model(), base.view(), base.mode(), base.fromInclusive(), base.toExclusive(),
				FilterExpression.MATCH_ALL,
				new FilterExpression.Comparison("amountSum", ComparisonOperator.GT, "10"));
		var splitAt = Instant.parse("2026-08-03T00:00:00Z");
		when(checkpointPort.findCoveredUntil(
				"orders", "customer-region-daily", RollupPipeline.CLICKHOUSE))
				.thenReturn(Optional.of(splitAt));
		// Neither fragment passes amountSum > 10 by itself, but the final merged row does.
		var rollupPart = row("2026-08-01", "SEOUL", "6");
		var rawPart = row("2026-08-01", "SEOUL", "5");
		when(clickHouseRollupPort.queryRollup(any(), any(), any())).thenReturn(List.of(rollupPart));
		when(rawPort.queryRaw(any(), any(), any())).thenReturn(List.of(rawPart));

		var result = service.query(query);

		assertThat(result.rows()).singleElement()
				.extracting(row -> row.measures().get("amountSum"))
				.isEqualTo(new BigDecimal("11"));
	}

	@Test
	void businessModelRejectsAFilterThatItsViewDoesNotExpose() {
		var query = query(QueryMode.POSTGRES_RAW,
				new FilterExpression.In("internalStatus", List.of("SECRET")));

		assertThatThrownBy(() -> service.query(query))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("not allowed");
	}

	private static QueryRequest query(QueryMode mode, FilterExpression where) {
		return new QueryRequest(
				"orders",
				"customer-region-daily",
				mode,
				Instant.parse("2026-08-01T00:00:00Z"),
				Instant.parse("2026-08-05T00:00:00Z"),
				where,
				FilterExpression.MATCH_ALL);
	}

	private static ResultRow row(String date, String region, String amount) {
		return new ResultRow(
				Instant.parse(date + "T00:00:00Z"),
				Map.of("customerId", 1L, "region", region),
				Map.of("orderCount", BigDecimal.ONE, "amountSum", new BigDecimal(amount)));
	}

	private static ModelDefinition orderModel() {
		var customer = new DimensionDefinition("customerId", ScalarType.LONG);
		var region = new DimensionDefinition("region", ScalarType.STRING);
		var view = new ViewDefinition(
				"customer-region-daily",
				TimeBucket.DAY,
				List.of("customerId", "region"),
				Set.of("customerId", "region"));
		return new ModelDefinition(
				"orders",
				Map.of(customer.name(), customer, region.name(), region),
				Map.of(
						"orderCount", new MeasureDefinition("orderCount", RawAggregation.COUNT_ROWS),
						"amountSum", new MeasureDefinition("amountSum", RawAggregation.SUM)),
				Map.of(view.name(), view));
	}
}
