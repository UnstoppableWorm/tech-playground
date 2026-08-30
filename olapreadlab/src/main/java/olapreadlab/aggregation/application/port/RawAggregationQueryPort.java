package olapreadlab.aggregation.application.port;

import java.time.Instant;
import java.util.List;

import olapreadlab.aggregation.application.ResolvedAggregationQuery;
import olapreadlab.aggregation.model.AggregationRow;

public interface RawAggregationQueryPort {

	List<AggregationRow> queryRaw(
			ResolvedAggregationQuery query, Instant fromInclusive, Instant toExclusive);
}
