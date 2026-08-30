package olapreadlab.aggregation.application;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import olapreadlab.aggregation.application.port.AggregateStoreQueryPort;
import olapreadlab.aggregation.model.AggregateStore;

import org.springframework.stereotype.Component;

@Component
public class AggregateStoreQueryPortRegistry {

	private final Map<AggregateStore, AggregateStoreQueryPort> ports;

	public AggregateStoreQueryPortRegistry(List<AggregateStoreQueryPort> ports) {
		var registered = new EnumMap<AggregateStore, AggregateStoreQueryPort>(AggregateStore.class);
		for (var port : ports) {
			if (registered.putIfAbsent(port.store(), port) != null) {
				throw new IllegalStateException("Duplicate aggregate adapter: " + port.store());
			}
		}
		this.ports = Map.copyOf(registered);
	}

	public AggregateStoreQueryPort get(AggregateStore store) {
		var port = ports.get(store);
		if (port == null) throw new IllegalStateException("No aggregate adapter registered for " + store);
		return port;
	}
}
