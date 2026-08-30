package olapreadlab.api.graphql;

import olapreadlab.aggregation.application.AggregationQueryService;

import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

@Controller
public class AggregationGraphQlController {

	private final AggregationQueryService queryService;

	public AggregationGraphQlController(AggregationQueryService queryService) {
		this.queryService = queryService;
	}

	@QueryMapping
	public AggregationGraphQlResult aggregate(@Argument AggregationQueryInput input) {
		return AggregationGraphQlResult.from(queryService.query(input.toQuery()));
	}
}
