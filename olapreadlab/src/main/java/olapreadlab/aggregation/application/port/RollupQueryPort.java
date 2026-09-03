package olapreadlab.aggregation.application.port;

import java.time.Instant;
import java.util.List;

import olapreadlab.aggregation.application.ResolvedQuery;
import olapreadlab.aggregation.model.RollupStore;
import olapreadlab.aggregation.model.ResultRow;

public interface RollupQueryPort {

	RollupStore store();

	List<ResultRow> queryRollup(
			ResolvedQuery query,
			Instant fromInclusive,
			Instant toExclusive);
}
