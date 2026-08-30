package olapreadlab.api.graphql;

import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;

import org.springframework.graphql.data.method.annotation.GraphQlExceptionHandler;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.web.bind.annotation.ControllerAdvice;

@ControllerAdvice
class AggregationGraphQlExceptionHandler {

	@GraphQlExceptionHandler
	GraphQLError handle(GraphqlErrorBuilder<?> errorBuilder, IllegalArgumentException exception) {
		return errorBuilder
				.errorType(ErrorType.BAD_REQUEST)
				.message(exception.getMessage())
				.build();
	}
}
