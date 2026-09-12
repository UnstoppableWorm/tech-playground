package olapreadlab.api.graphql;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.Test;

import olapreadlab.aggregation.model.ResultRow;

class OlapGraphQlRowTests {

	@Test
	void measureValuesHaveStorageIndependentDecimalFormatting() {
		var row = new ResultRow(
				Instant.parse("2025-01-01T00:00:00Z"),
				Map.of("personId", 1L),
				Map.of("eventCount", new BigDecimal("1.0"), "metricSum", new BigDecimal("1000.00")));

		assertThat(OlapGraphQlRow.from(row).measures())
				.containsExactlyInAnyOrder(
						new OlapGraphQlValue("eventCount", "1"),
						new OlapGraphQlValue("metricSum", "1000"));
	}
}
