package olapreadlab.api.graphql;

import olapreadlab.aggregation.application.QueryService;

import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

@Controller
public class OlapGraphQlController {

	private final QueryService queryService;

	public OlapGraphQlController(QueryService queryService) {
		this.queryService = queryService;
	}

	@QueryMapping
	public OlapGraphQlResult olap(@Argument OlapQueryInput input) {
		return OlapGraphQlResult.from(queryService.query(input.toQuery()));
	}
}
