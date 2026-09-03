package olapreadlab.aggregation.application;

import java.time.Instant;
import java.util.Optional;

import olapreadlab.aggregation.application.port.CoverageCheckpointPort;
import olapreadlab.aggregation.model.RollupStore;
import olapreadlab.aggregation.model.RollupPipeline;

import org.springframework.stereotype.Component;

@Component
public class ReadPlanner {

	private final CoverageCheckpointPort checkpointPort;

	public ReadPlanner(CoverageCheckpointPort checkpointPort) {
		this.checkpointPort = checkpointPort;
	}

	public ReadPlan plan(ResolvedQuery resolved) {
		var query = resolved.query();
		return switch (query.mode()) {
			case POSTGRES_RAW -> rawOnly(query);
			case POSTGRES_BATCH_HYBRID -> hybrid(
					resolved, RollupPipeline.SPRING_BATCH, RollupStore.POSTGRES);
			case CLICKHOUSE_HYBRID -> hybrid(
					resolved, RollupPipeline.CLICKHOUSE, RollupStore.CLICKHOUSE);
		};
	}

	private ReadPlan rawOnly(QueryRequest query) {
		return new ReadPlan(
				Optional.empty(),
				Optional.empty(),
				Optional.of(new InstantRange(query.fromInclusive(), query.toExclusive())),
				null);
	}

	private ReadPlan hybrid(
			ResolvedQuery resolved,
			RollupPipeline pipeline,
			RollupStore store) {
		var query = resolved.query();
		var checkpoint = checkpointPort.findCoveredUntil(query.model(), query.view(), pipeline);
		if (checkpoint.isEmpty()) return rawOnly(query);

		var coveredUntil = checkpoint.get();
		if (!resolved.view().bucket().isBoundary(coveredUntil)) {
			throw new IllegalStateException("Checkpoint is not aligned with view bucket: " + query.view());
		}
		var splitAt = clamp(coveredUntil, query.fromInclusive(), query.toExclusive());
		var rollupRange = query.fromInclusive().isBefore(splitAt)
				? Optional.of(new InstantRange(query.fromInclusive(), splitAt)) : Optional.<InstantRange>empty();
		var rawRange = splitAt.isBefore(query.toExclusive())
				? Optional.of(new InstantRange(splitAt, query.toExclusive())) : Optional.<InstantRange>empty();
		return new ReadPlan(
				rollupRange.map(ignored -> store), rollupRange, rawRange, coveredUntil);
	}

	private static Instant clamp(Instant value, Instant minimum, Instant maximum) {
		if (value.isBefore(minimum)) return minimum;
		if (value.isAfter(maximum)) return maximum;
		return value;
	}
}
