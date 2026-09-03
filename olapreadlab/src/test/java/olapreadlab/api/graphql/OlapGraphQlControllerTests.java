package olapreadlab.api.graphql;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import olapreadlab.aggregation.application.QueryService;
import olapreadlab.aggregation.application.QueryResult;
import olapreadlab.aggregation.model.ResultRow;
import olapreadlab.aggregation.model.QueryMode;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.graphql.test.autoconfigure.GraphQlTest;
import org.springframework.graphql.test.tester.GraphQlTester;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@GraphQlTest(OlapGraphQlController.class)
class OlapGraphQlControllerTests {

	@Autowired
	private GraphQlTester graphQlTester;

	@MockitoBean
	private QueryService queryService;

	@Test
	void exposesOneTypedOlapQueryAndLetsTheClientSelectResponseFields() {
		when(queryService.query(any())).thenReturn(new QueryResult(
				"medical-history",
				"person-organ-disease-daily",
				QueryMode.CLICKHOUSE_HYBRID,
				Instant.parse("2026-08-29T00:00:00Z"),
				List.of(new ResultRow(
						Instant.parse("2026-08-01T00:00:00Z"),
						Map.of("personId", 1L),
						Map.of("eventCount", new BigDecimal("25"))))));

		graphQlTester.document("""
				query {
				  olap(input: {
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
				    coveredUntil
				    rows {
				      bucket
				      measures { name value }
				    }
				  }
				}
				""")
				.execute()
				.path("olap.mode").entity(String.class).isEqualTo("CLICKHOUSE_HYBRID")
				.path("olap.coveredUntil").entity(String.class)
						.isEqualTo("2026-08-29T00:00:00Z")
				.path("olap.rows[0].measures[0].value").entity(String.class).isEqualTo("25");
	}
}
