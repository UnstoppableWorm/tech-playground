package olapreadlab.aggregation.application;

import java.time.Instant;
import java.util.Optional;

import olapreadlab.aggregation.application.port.AggregationCheckpointPort;
import olapreadlab.aggregation.model.AggregateStore;
import olapreadlab.aggregation.model.AggregationPipeline;

import org.springframework.stereotype.Component;

@Component
public class AggregationReadPlanner {

	private final AggregationCheckpointPort checkpointPort;

	public AggregationReadPlanner(AggregationCheckpointPort checkpointPort) {
		this.checkpointPort = checkpointPort;
	}

	public AggregationReadPlan plan(ResolvedAggregationQuery resolved) {
		var query = resolved.query();
		return switch (query.mode()) {
			case POSTGRES_RAW -> rawOnly(query);
			case POSTGRES_BATCH_HYBRID -> hybrid(
					resolved, AggregationPipeline.SPRING_BATCH, AggregateStore.POSTGRES);
			case CLICKHOUSE_HYBRID -> hybrid(
					resolved, AggregationPipeline.CLICKHOUSE, AggregateStore.CLICKHOUSE);
		};
	}

	private AggregationReadPlan rawOnly(AggregationQuery query) {
		return new AggregationReadPlan(
				Optional.empty(),
				Optional.empty(),
				Optional.of(new InstantRange(query.fromInclusive(), query.toExclusive())),
				null);
	}

	private AggregationReadPlan hybrid(
			ResolvedAggregationQuery resolved,
			AggregationPipeline pipeline,
			AggregateStore store) {
		var query = resolved.query();
		var checkpoint = checkpointPort.findCoveredUntil(query.model(), query.view(), pipeline);
		if (checkpoint.isEmpty()) return rawOnly(query);

		var coveredUntil = checkpoint.get();
		if (!resolved.view().bucket().isBoundary(coveredUntil)) {
			throw new IllegalStateException("Checkpoint is not aligned with view bucket: " + query.view());
		}
		var splitAt = clamp(coveredUntil, query.fromInclusive(), query.toExclusive());
		var aggregateRange = query.fromInclusive().isBefore(splitAt)
				? Optional.of(new InstantRange(query.fromInclusive(), splitAt)) : Optional.<InstantRange>empty();
		var rawRange = splitAt.isBefore(query.toExclusive())
				? Optional.of(new InstantRange(splitAt, query.toExclusive())) : Optional.<InstantRange>empty();
		return new AggregationReadPlan(
				aggregateRange.map(ignored -> store), aggregateRange, rawRange, coveredUntil);
	}

	private static Instant clamp(Instant value, Instant minimum, Instant maximum) {
		if (value.isBefore(minimum)) return minimum;
		if (value.isAfter(maximum)) return maximum;
		return value;
	}
}
