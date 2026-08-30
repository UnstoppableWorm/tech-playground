package olapreadlab.aggregation.adapter.jdbc.mapping;

import java.util.Map;

public record AggregationStorageBinding(
		String model,
		RawTableBinding raw,
		Map<String, AggregateViewStorageBinding> views) {

	public AggregationStorageBinding {
		views = Map.copyOf(views);
	}

	public AggregateViewStorageBinding requiredView(String view) {
		var binding = views.get(view);
		if (binding == null) throw new IllegalArgumentException("No storage binding for view: " + view);
		return binding;
	}
}
