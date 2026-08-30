package olapreadlab.aggregation.application;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;

import olapreadlab.aggregation.application.port.RawAggregationQueryPort;
import olapreadlab.aggregation.model.AggregationRow;

import org.springframework.stereotype.Service;

@Service
public class AggregationQueryService {

	private final AggregationModelCatalog catalog;
	private final AggregationReadPlanner readPlanner;
	private final RawAggregationQueryPort rawPort;
	private final AggregateStoreQueryPortRegistry aggregatePorts;

	public AggregationQueryService(
			AggregationModelCatalog catalog,
			AggregationReadPlanner readPlanner,
			RawAggregationQueryPort rawPort,
			AggregateStoreQueryPortRegistry aggregatePorts) {
		this.catalog = catalog;
		this.readPlanner = readPlanner;
		this.rawPort = rawPort;
		this.aggregatePorts = aggregatePorts;
	}

	public AggregationResult query(AggregationQuery query) {
		validateRange(query);
		var resolved = catalog.resolve(query);
		validateBucketBoundaries(resolved);
		var readPlan = readPlanner.plan(resolved);
		var rows = new ArrayList<AggregationRow>();

		readPlan.rawRange().ifPresent(range -> rows.addAll(
				rawPort.queryRaw(resolved, range.fromInclusive(), range.toExclusive())));
		readPlan.aggregateRange().ifPresent(range -> {
			var adapter = aggregatePorts.get(readPlan.aggregateStore().orElseThrow());
			rows.addAll(adapter.queryAggregate(
					resolved,
					range.fromInclusive(),
					range.toExclusive()));
		});

		return result(resolved, readPlan.aggregateCoveredUntil(), mergeFilterAndSort(rows, resolved));
	}

	private static AggregationResult result(
			ResolvedAggregationQuery resolved, Instant coveredUntil, List<AggregationRow> rows) {
		var query = resolved.query();
		return new AggregationResult(query.model(), query.view(), query.mode(), coveredUntil, rows);
	}

	private static List<AggregationRow> mergeFilterAndSort(
			List<AggregationRow> rows, ResolvedAggregationQuery resolved) {
		var merged = new LinkedHashMap<Object, AggregationRow>();
		rows.forEach(row -> merged.merge(row.key(), row, AggregationRow::add));
		return merged.values().stream()
				.filter(row -> AggregationHavingEvaluator.test(resolved.having(), row))
				.sorted(Comparator.comparing(AggregationRow::bucket)
						.thenComparing(row -> row.dimensions().toString()))
				.toList();
	}

	private static void validateRange(AggregationQuery query) {
		if (query.model() == null || query.view() == null || query.mode() == null
				|| query.fromInclusive() == null || query.toExclusive() == null) {
			throw new IllegalArgumentException("model, view, mode and time range are required");
		}
		if (!query.fromInclusive().isBefore(query.toExclusive())) {
			throw new IllegalArgumentException("fromInclusive must be before toExclusive");
		}
	}

	private static void validateBucketBoundaries(ResolvedAggregationQuery query) {
		var bucket = query.view().bucket();
		if (!bucket.isBoundary(query.query().fromInclusive())
				|| !bucket.isBoundary(query.query().toExclusive())) {
			throw new IllegalArgumentException("Time range must align with " + bucket + " boundaries");
		}
	}

}
