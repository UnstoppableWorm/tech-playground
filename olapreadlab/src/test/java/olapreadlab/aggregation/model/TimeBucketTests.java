package olapreadlab.aggregation.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;

class TimeBucketTests {

	private static final Instant VALUE = Instant.parse("2026-08-29T13:47:59.123Z");

	@Test
	void floorsEverySupportedUtcBucket() {
		assertThat(TimeBucket.HOUR.floor(VALUE)).isEqualTo(Instant.parse("2026-08-29T13:00:00Z"));
		assertThat(TimeBucket.DAY.floor(VALUE)).isEqualTo(Instant.parse("2026-08-29T00:00:00Z"));
		assertThat(TimeBucket.MONTH.floor(VALUE)).isEqualTo(Instant.parse("2026-08-01T00:00:00Z"));
		assertThat(TimeBucket.YEAR.floor(VALUE)).isEqualTo(Instant.parse("2026-01-01T00:00:00Z"));
	}

	@Test
	void validatesBoundariesAtTheSelectedResolution() {
		assertThat(TimeBucket.HOUR.isBoundary(Instant.parse("2026-08-29T13:00:00Z"))).isTrue();
		assertThat(TimeBucket.DAY.isBoundary(Instant.parse("2026-08-29T13:00:00Z"))).isFalse();
		assertThat(TimeBucket.MONTH.isBoundary(Instant.parse("2026-08-01T00:00:00Z"))).isTrue();
		assertThat(TimeBucket.YEAR.isBoundary(Instant.parse("2026-01-01T00:00:00Z"))).isTrue();
	}

}
