package olapreadlab.aggregation.application.port;

import java.time.Instant;
import java.util.Optional;

import olapreadlab.aggregation.model.RollupPipeline;

public interface CoverageCheckpointPort {

	Optional<Instant> findCoveredUntil(String model, String view, RollupPipeline pipeline);
}
