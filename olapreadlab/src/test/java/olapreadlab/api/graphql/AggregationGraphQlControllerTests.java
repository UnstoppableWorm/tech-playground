package olapreadlab.api.graphql;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import olapreadlab.aggregation.application.AggregationQueryService;
import olapreadlab.aggregation.application.AggregationResult;
import olapreadlab.aggregation.model.AggregationRow;
import olapreadlab.aggregation.model.QueryMode;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.graphql.test.autoconfigure.GraphQlTest;
import org.springframework.graphql.test.tester.GraphQlTester;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@GraphQlTest(AggregationGraphQlController.class)
class AggregationGraphQlControllerTests {

	@Autowired
	private GraphQlTester graphQlTester;

	@MockitoBean
	private AggregationQueryService queryService;

	@Test
	void exposesOneTypedAggregateQueryAndLetsTheClientSelectResponseFields() {
		when(queryService.query(any())).thenReturn(new AggregationResult(
				"medical-history",
				"person-organ-disease-daily",
				QueryMode.CLICKHOUSE_HYBRID,
				Instant.parse("2026-08-29T00:00:00Z"),
				List.of(new AggregationRow(
						Instant.parse("2026-08-01T00:00:00Z"),
						Map.of("personId", 1L),
						Map.of("eventCount", new BigDecimal("25"))))));

		graphQlTester.document("""
				query {
				  aggregate(input: {
				    model: "medical-history"
				    view: "person-organ-disease-daily"
				    mode: CLICKHOUSE_HYBRID
				    fromInclusive: "2026-08-01T00:00:00Z"
				    toExclusive: "2026-09-01T00:00:00Z"
				    where: {
				      operator: OR
				      children: [
				        { operator: EQ, field: "personId", value: "1" }
				        { operator: CUSTOM, name: "organDiseasePair", arguments: [
				          { name: "organCode", values: ["10"] }
				          { name: "diseaseCode", values: ["20"] }
				        ] }
				      ]
				    }
				    having: { operator: GTE, field: "eventCount", value: "10" }
				  }) {
				    mode
				    aggregateCoveredUntil
				    rows {
				      bucket
				      measures { name value }
				    }
				  }
				}
				""")
				.execute()
				.path("aggregate.mode").entity(String.class).isEqualTo("CLICKHOUSE_HYBRID")
				.path("aggregate.aggregateCoveredUntil").entity(String.class)
						.isEqualTo("2026-08-29T00:00:00Z")
				.path("aggregate.rows[0].measures[0].value").entity(String.class).isEqualTo("25");
	}
}
