package olapreadlab.aggregation.application.port;

import java.time.Instant;
import java.util.List;

import olapreadlab.aggregation.application.ResolvedAggregationQuery;
import olapreadlab.aggregation.model.AggregateStore;
import olapreadlab.aggregation.model.AggregationRow;

public interface AggregateStoreQueryPort {

	AggregateStore store();

	List<AggregationRow> queryAggregate(
			ResolvedAggregationQuery query,
			Instant fromInclusive,
			Instant toExclusive);
}
