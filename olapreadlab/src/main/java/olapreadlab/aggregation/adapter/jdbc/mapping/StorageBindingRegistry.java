package olapreadlab.aggregation.adapter.jdbc.mapping;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import olapreadlab.aggregation.application.ModelRegistry;
import olapreadlab.aggregation.model.MeasureDefinition.RawAggregation;

import org.springframework.stereotype.Component;

@Component
public class StorageBindingRegistry {

	private final Map<String, ModelStorageBinding> bindings;

	public StorageBindingRegistry(
			List<ModelStorageBindingProvider> providers,
			ModelRegistry modelRegistry) {
		var registered = new LinkedHashMap<String, ModelStorageBinding>();
		for (var provider : providers) {
			var binding = provider.binding();
			validate(binding, modelRegistry);
			if (registered.putIfAbsent(binding.model(), binding) != null) {
				throw new IllegalStateException("Duplicate storage binding: " + binding.model());
			}
		}
		this.bindings = Map.copyOf(registered);
	}

	public ModelStorageBinding get(String model) {
		var binding = bindings.get(model);
		if (binding == null) throw new IllegalArgumentException("No storage binding for model: " + model);
		return binding;
	}

	private static void validate(
			ModelStorageBinding binding, ModelRegistry modelRegistry) {
		var model = modelRegistry.get(binding.model());
		if (!binding.raw().dimensionColumns().keySet().containsAll(model.dimensions().keySet())) {
			throw new IllegalArgumentException("Raw binding is missing dimensions for " + binding.model());
		}
		for (var measure : model.measures().values()) {
			if (measure.rawAggregation() == RawAggregation.SUM
					&& !binding.raw().measureColumns().containsKey(measure.name())) {
				throw new IllegalArgumentException("Raw binding is missing measure " + measure.name());
			}
		}
		for (var view : model.views().values()) {
			var viewBinding = binding.requiredView(view.name());
			if (viewBinding.tables().isEmpty()) {
				throw new IllegalArgumentException("No rollup table registered for view: " + view.name());
			}
			for (var table : viewBinding.tables().values()) {
				if (!table.dimensionColumns().keySet().containsAll(view.dimensions())) {
					throw new IllegalArgumentException("Rollup binding is missing view dimensions: " + view.name());
				}
				if (!table.measureColumns().keySet().containsAll(model.measures().keySet())) {
					throw new IllegalArgumentException("Rollup binding is missing measures: " + view.name());
				}
			}
		}
	}
}
