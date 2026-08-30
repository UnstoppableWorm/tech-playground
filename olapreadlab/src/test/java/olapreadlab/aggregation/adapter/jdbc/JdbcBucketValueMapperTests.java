package olapreadlab.aggregation.adapter.jdbc;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import olapreadlab.aggregation.model.TimeBucket;

class JdbcBucketValueMapperTests {

	@Test
	void mapsLogicalBucketsToStorageSpecificJdbcTypes() {
		var value = Instant.parse("2026-08-01T13:00:00Z");

		assertThat(JdbcBucketValueMapper.toJdbcValue(TimeBucket.HOUR, value))
				.isInstanceOf(Timestamp.class);
		assertThat(JdbcBucketValueMapper.toJdbcValue(TimeBucket.DAY, value))
				.isInstanceOf(Date.class);
		assertThat(JdbcBucketValueMapper.toJdbcValue(TimeBucket.MONTH, value))
				.isInstanceOf(Date.class);
		assertThat(JdbcBucketValueMapper.toJdbcValue(TimeBucket.YEAR, value))
				.isInstanceOf(Date.class);
	}
}
