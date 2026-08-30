package olapreadlab.aggregation.model;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

public enum TimeBucket {
	HOUR {
		@Override public Instant floor(Instant value) {
			return value.truncatedTo(ChronoUnit.HOURS);
		}
	},
	DAY {
		@Override public Instant floor(Instant value) {
			return value.truncatedTo(ChronoUnit.DAYS);
		}
	},
	MONTH {
		@Override public Instant floor(Instant value) {
			return value.atZone(ZoneOffset.UTC)
					.withDayOfMonth(1)
					.truncatedTo(ChronoUnit.DAYS)
					.toInstant();
		}
	},
	YEAR {
		@Override public Instant floor(Instant value) {
			return value.atZone(ZoneOffset.UTC)
					.withDayOfYear(1)
					.truncatedTo(ChronoUnit.DAYS)
					.toInstant();
		}
	};

	public boolean isBoundary(Instant value) {
		return value.equals(floor(value));
	}

	public abstract Instant floor(Instant value);
}
