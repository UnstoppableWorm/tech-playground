package olapreadlab.aggregation.application;

import org.springframework.stereotype.Component;

@Component
public class QueryResolver {

	private final ModelRegistry modelRegistry;
	private final PredicateResolver predicateResolver;

	public QueryResolver(
			ModelRegistry modelRegistry,
			PredicateResolver predicateResolver) {
		this.modelRegistry = modelRegistry;
		this.predicateResolver = predicateResolver;
	}

	public ResolvedQuery resolve(QueryRequest query) {
		var model = modelRegistry.get(query.model());
		var view = required(model.views().get(query.view()), "Unknown aggregation view: " + query.view());
		var where = predicateResolver.resolveWhere(query.where(), model, view);
		var having = predicateResolver.resolveHaving(query.having(), model, view);
		return new ResolvedQuery(query, model, view, where, having);
	}

	private static <T> T required(T value, String message) {
		if (value == null) throw new IllegalArgumentException(message);
		return value;
	}

}
