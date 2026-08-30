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

import olapreadlab.aggregation.application.port.AggregateStoreQueryPort;
import olapreadlab.aggregation.application.port.AggregationCheckpointPort;
import olapreadlab.aggregation.application.port.RawAggregationQueryPort;
import olapreadlab.aggregation.model.AggregateStore;
import olapreadlab.aggregation.model.AggregateViewDefinition;
import olapreadlab.aggregation.model.AggregationModelDefinition;
import olapreadlab.aggregation.model.AggregationPipeline;
import olapreadlab.aggregation.model.AggregationRow;
import olapreadlab.aggregation.model.DimensionDefinition;
import olapreadlab.aggregation.model.MeasureDefinition;
import olapreadlab.aggregation.model.MeasureDefinition.RawAggregation;
import olapreadlab.aggregation.model.QueryMode;
import olapreadlab.aggregation.model.ScalarType;
import olapreadlab.aggregation.model.TimeBucket;
import olapreadlab.aggregation.model.filter.ComparisonOperator;
import olapreadlab.aggregation.model.filter.FilterExpression;

class AggregationQueryServiceTests {

	private final RawAggregationQueryPort rawPort = mock(RawAggregationQueryPort.class);
	private final AggregateStoreQueryPort postgresAggregatePort = mock(AggregateStoreQueryPort.class);
	private final AggregateStoreQueryPort clickHouseAggregatePort = mock(AggregateStoreQueryPort.class);
	private final AggregationCheckpointPort checkpointPort = mock(AggregationCheckpointPort.class);
	private AggregationQueryService service;

	@BeforeEach
	void setUp() {
		var catalog = new AggregationModelCatalog(List.of(AggregationQueryServiceTests::orderModel));
		when(postgresAggregatePort.store()).thenReturn(AggregateStore.POSTGRES);
		when(clickHouseAggregatePort.store()).thenReturn(AggregateStore.CLICKHOUSE);
		var planner = new AggregationReadPlanner(checkpointPort);
		var aggregatePorts = new AggregateStoreQueryPortRegistry(
				List.of(postgresAggregatePort, clickHouseAggregatePort));
		service = new AggregationQueryService(catalog, planner, rawPort, aggregatePorts);
	}

	@Test
	void commonCoreQueriesRawWithoutKnowingTheBusinessDimensions() {
		var query = query(QueryMode.POSTGRES_RAW, Map.of("region", List.of("SEOUL")));
		var row = row("2026-08-01", "SEOUL", "15.50");
		when(rawPort.queryRaw(any(), eq(query.fromInclusive()), eq(query.toExclusive())))
				.thenReturn(List.of(row));

		var result = service.query(query);

		assertThat(result.model()).isEqualTo("orders");
		assertThat(result.view()).isEqualTo("customer-region-daily");
		assertThat(result.rows()).containsExactly(row);
		verify(postgresAggregatePort, never()).queryAggregate(any(), any(), any());
		verify(clickHouseAggregatePort, never()).queryAggregate(any(), any(), any());
		verify(checkpointPort, never()).findCoveredUntil(any(), any(), any());
	}

	@Test
	void hybridSplitsAtTheModelAndViewCheckpoint() {
		var query = query(QueryMode.CLICKHOUSE_HYBRID, Map.of());
		var splitAt = Instant.parse("2026-08-03T00:00:00Z");
		when(checkpointPort.findCoveredUntil(
				"orders", "customer-region-daily", AggregationPipeline.CLICKHOUSE))
				.thenReturn(Optional.of(splitAt));
		when(clickHouseAggregatePort.queryAggregate(
				any(),
				eq(Instant.parse("2026-08-01T00:00:00Z")), eq(Instant.parse("2026-08-03T00:00:00Z"))))
				.thenReturn(List.of(row("2026-08-01", "SEOUL", "10")));
		when(rawPort.queryRaw(any(), eq(splitAt), eq(query.toExclusive())))
				.thenReturn(List.of(row("2026-08-03", "BUSAN", "20")));

		var result = service.query(query);

		assertThat(result.aggregateCoveredUntil()).isEqualTo(splitAt);
		assertThat(result.rows()).extracting(AggregationRow::bucket)
				.containsExactly(
						Instant.parse("2026-08-01T00:00:00Z"),
						Instant.parse("2026-08-03T00:00:00Z"));
		verify(clickHouseAggregatePort).queryAggregate(
				any(),
				eq(Instant.parse("2026-08-01T00:00:00Z")), eq(Instant.parse("2026-08-03T00:00:00Z")));
		verify(postgresAggregatePort, never()).queryAggregate(any(), any(), any());
	}

	@Test
	void postgresBatchModeSelectsOnlyThePostgresAggregateAdapter() {
		var query = query(QueryMode.POSTGRES_BATCH_HYBRID, Map.of());
		var splitAt = Instant.parse("2026-08-03T00:00:00Z");
		when(checkpointPort.findCoveredUntil(
				"orders", "customer-region-daily", AggregationPipeline.SPRING_BATCH))
				.thenReturn(Optional.of(splitAt));
		when(postgresAggregatePort.queryAggregate(any(), any(), any())).thenReturn(List.of());
		when(rawPort.queryRaw(any(), eq(splitAt), eq(query.toExclusive()))).thenReturn(List.of());

		service.query(query);

		verify(postgresAggregatePort).queryAggregate(
				any(),
				eq(Instant.parse("2026-08-01T00:00:00Z")),
				eq(Instant.parse("2026-08-03T00:00:00Z")));
		verify(clickHouseAggregatePort, never()).queryAggregate(any(), any(), any());
	}

	@Test
	void havingIsAppliedAfterHybridRowsAreMerged() {
		var base = query(QueryMode.CLICKHOUSE_HYBRID, Map.of());
		var query = new AggregationQuery(
				base.model(), base.view(), base.mode(), base.fromInclusive(), base.toExclusive(), Map.of(),
				FilterExpression.MATCH_ALL,
				new FilterExpression.Comparison("amountSum", ComparisonOperator.GT, "10"));
		var splitAt = Instant.parse("2026-08-03T00:00:00Z");
		when(checkpointPort.findCoveredUntil(
				"orders", "customer-region-daily", AggregationPipeline.CLICKHOUSE))
				.thenReturn(Optional.of(splitAt));
		// Neither fragment passes amountSum > 10 by itself, but the final merged row does.
		var aggregatePart = row("2026-08-01", "SEOUL", "6");
		var rawPart = row("2026-08-01", "SEOUL", "5");
		when(clickHouseAggregatePort.queryAggregate(any(), any(), any())).thenReturn(List.of(aggregatePart));
		when(rawPort.queryRaw(any(), any(), any())).thenReturn(List.of(rawPart));

		var result = service.query(query);

		assertThat(result.rows()).singleElement()
				.extracting(row -> row.measures().get("amountSum"))
				.isEqualTo(new BigDecimal("11"));
	}

	@Test
	void businessModelRejectsAFilterThatItsViewDoesNotExpose() {
		var query = query(QueryMode.POSTGRES_RAW, Map.of("internalStatus", List.of("SECRET")));

		assertThatThrownBy(() -> service.query(query))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("not allowed");
	}

	private static AggregationQuery query(QueryMode mode, Map<String, List<Object>> filters) {
		return new AggregationQuery(
				"orders",
				"customer-region-daily",
				mode,
				Instant.parse("2026-08-01T00:00:00Z"),
				Instant.parse("2026-08-05T00:00:00Z"),
				filters);
	}

	private static AggregationRow row(String date, String region, String amount) {
		return new AggregationRow(
				Instant.parse(date + "T00:00:00Z"),
				Map.of("customerId", 1L, "region", region),
				Map.of("orderCount", BigDecimal.ONE, "amountSum", new BigDecimal(amount)));
	}

	private static AggregationModelDefinition orderModel() {
		var customer = new DimensionDefinition("customerId", ScalarType.LONG);
		var region = new DimensionDefinition("region", ScalarType.STRING);
		var view = new AggregateViewDefinition(
				"customer-region-daily",
				TimeBucket.DAY,
				List.of("customerId", "region"),
				Set.of("customerId", "region"));
		return new AggregationModelDefinition(
				"orders",
				Map.of(customer.name(), customer, region.name(), region),
				Map.of(
						"orderCount", new MeasureDefinition("orderCount", RawAggregation.COUNT_ROWS),
						"amountSum", new MeasureDefinition("amountSum", RawAggregation.SUM)),
				Map.of(view.name(), view));
	}
}
