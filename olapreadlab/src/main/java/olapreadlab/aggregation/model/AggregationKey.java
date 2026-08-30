package olapreadlab.aggregation.model;

import java.time.Instant;
import java.util.Map;

public record AggregationKey(Instant bucket, Map<String, Object> dimensions) {
}
