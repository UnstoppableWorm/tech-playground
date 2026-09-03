package olapreadlab.aggregation.application.port;

import java.time.Instant;
import java.util.List;

import olapreadlab.aggregation.application.ResolvedQuery;
import olapreadlab.aggregation.model.ResultRow;

public interface RawQueryPort {

	List<ResultRow> queryRaw(
			ResolvedQuery query, Instant fromInclusive, Instant toExclusive);
}
