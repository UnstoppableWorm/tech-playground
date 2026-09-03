package olapreadlab.aggregation.adapter.jdbc.mapping;

import java.util.Map;

public record ModelStorageBinding(
		String model,
		RawTableBinding raw,
		Map<String, RollupViewBinding> views) {

	public ModelStorageBinding {
		views = Map.copyOf(views);
	}

	public RollupViewBinding requiredView(String view) {
		var binding = views.get(view);
		if (binding == null) throw new IllegalArgumentException("No storage binding for view: " + view);
		return binding;
	}
}
