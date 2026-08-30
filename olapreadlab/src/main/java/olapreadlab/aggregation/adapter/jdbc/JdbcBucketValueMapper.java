package olapreadlab.aggregation.adapter.jdbc;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import olapreadlab.aggregation.model.TimeBucket;

public final class JdbcBucketValueMapper {

	private JdbcBucketValueMapper() {
	}

	public static Object toJdbcValue(TimeBucket bucket, Instant value) {
		return switch (bucket) {
			case HOUR -> Timestamp.from(value);
			case DAY, MONTH, YEAR -> Date.valueOf(value.atZone(ZoneOffset.UTC).toLocalDate());
		};
	}

	public static Instant fromJdbcValue(Object value) {
		return switch (value) {
			case Timestamp timestamp -> timestamp.toInstant();
			case Date date -> date.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant();
			case Instant instant -> instant;
			case OffsetDateTime offsetDateTime -> offsetDateTime.toInstant();
			case LocalDateTime localDateTime -> localDateTime.toInstant(ZoneOffset.UTC);
			case LocalDate localDate -> localDate.atStartOfDay(ZoneOffset.UTC).toInstant();
			case null -> throw new IllegalArgumentException("Bucket value must not be null");
			default -> throw new IllegalArgumentException(
					"Unsupported JDBC bucket value: " + value.getClass().getName());
		};
	}
}
