package olapreadlab.aggregation.application;

import java.util.List;
import java.util.Map;

import olapreadlab.aggregation.model.ModelDefinition;

import org.springframework.stereotype.Component;

@Component
public class ModelRegistry {

	private final Map<String, ModelDefinition> models;

	public ModelRegistry(List<ModelDefinition> definitions) {
		var registered = new java.util.LinkedHashMap<String, ModelDefinition>();
		for (var definition : definitions) {
			if (registered.putIfAbsent(definition.name(), definition) != null) {
				throw new IllegalStateException("Duplicate aggregation model: " + definition.name());
			}
		}
		this.models = Map.copyOf(registered);
	}

	public ModelDefinition get(String modelName) {
		return required(models.get(modelName), "Unknown aggregation model: " + modelName);
	}

	private static <T> T required(T value, String message) {
		if (value == null) throw new IllegalArgumentException(message);
		return value;
	}

}
