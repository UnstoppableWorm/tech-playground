package olapreadlab.aggregation.application.port;

import java.time.Instant;
import java.util.Optional;

import olapreadlab.aggregation.model.AggregationPipeline;

public interface AggregationCheckpointPort {

	Optional<Instant> findCoveredUntil(String model, String view, AggregationPipeline pipeline);
}
