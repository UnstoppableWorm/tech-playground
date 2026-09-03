package olapreadlab.aggregation.application;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;

import olapreadlab.aggregation.application.port.RawQueryPort;
import olapreadlab.aggregation.model.ResultRow;

import org.springframework.stereotype.Service;

@Service
public class QueryService {

	private final QueryResolver queryResolver;
	private final ReadPlanner readPlanner;
	private final RawQueryPort rawPort;
	private final RollupQueryPortRegistry rollupPorts;

	public QueryService(
			QueryResolver queryResolver,
			ReadPlanner readPlanner,
			RawQueryPort rawPort,
			RollupQueryPortRegistry rollupPorts) {
		this.queryResolver = queryResolver;
		this.readPlanner = readPlanner;
		this.rawPort = rawPort;
		this.rollupPorts = rollupPorts;
	}

	public QueryResult query(QueryRequest query) {
		validateRange(query);
		var resolved = queryResolver.resolve(query);
		validateBucketBoundaries(resolved);
		var readPlan = readPlanner.plan(resolved);
		var rows = new ArrayList<ResultRow>();

		readPlan.rawRange().ifPresent(range -> rows.addAll(
				rawPort.queryRaw(resolved, range.fromInclusive(), range.toExclusive())));
		readPlan.rollupRange().ifPresent(range -> {
			var adapter = rollupPorts.get(readPlan.rollupStore().orElseThrow());
			rows.addAll(adapter.queryRollup(
					resolved,
					range.fromInclusive(),
					range.toExclusive()));
		});

		return result(resolved, readPlan.coveredUntil(), mergeFilterAndSort(rows, resolved));
	}

	private static QueryResult result(
			ResolvedQuery resolved, Instant coveredUntil, List<ResultRow> rows) {
		var query = resolved.query();
		return new QueryResult(query.model(), query.view(), query.mode(), coveredUntil, rows);
	}

	private static List<ResultRow> mergeFilterAndSort(
			List<ResultRow> rows, ResolvedQuery resolved) {
		var merged = new LinkedHashMap<Object, ResultRow>();
		rows.forEach(row -> merged.merge(row.key(), row, ResultRow::add));
		return merged.values().stream()
				.filter(row -> HavingEvaluator.test(resolved.having(), row))
				.sorted(Comparator.comparing(ResultRow::bucket)
						.thenComparing(row -> row.dimensions().toString()))
				.toList();
	}

	private static void validateRange(QueryRequest query) {
		if (query.model() == null || query.view() == null || query.mode() == null
				|| query.fromInclusive() == null || query.toExclusive() == null) {
			throw new IllegalArgumentException("model, view, mode and time range are required");
		}
		if (!query.fromInclusive().isBefore(query.toExclusive())) {
			throw new IllegalArgumentException("fromInclusive must be before toExclusive");
		}
	}

	private static void validateBucketBoundaries(ResolvedQuery query) {
		var bucket = query.view().bucket();
		if (!bucket.isBoundary(query.query().fromInclusive())
				|| !bucket.isBoundary(query.query().toExclusive())) {
			throw new IllegalArgumentException("Time range must align with " + bucket + " boundaries");
		}
	}

}
