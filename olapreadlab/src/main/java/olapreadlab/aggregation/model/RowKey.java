package olapreadlab.aggregation.model;

import java.time.Instant;
import java.util.Map;

public record RowKey(Instant bucket, Map<String, Object> dimensions) {
}
