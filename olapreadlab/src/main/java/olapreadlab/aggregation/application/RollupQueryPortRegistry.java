package olapreadlab.aggregation.application;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import olapreadlab.aggregation.application.port.RollupQueryPort;
import olapreadlab.aggregation.model.RollupStore;

import org.springframework.stereotype.Component;

@Component
public class RollupQueryPortRegistry {

	private final Map<RollupStore, RollupQueryPort> ports;

	public RollupQueryPortRegistry(List<RollupQueryPort> ports) {
		var registered = new EnumMap<RollupStore, RollupQueryPort>(RollupStore.class);
		for (var port : ports) {
			if (registered.putIfAbsent(port.store(), port) != null) {
				throw new IllegalStateException("Duplicate rollup adapter: " + port.store());
			}
		}
		this.ports = Map.copyOf(registered);
	}

	public RollupQueryPort get(RollupStore store) {
		var port = ports.get(store);
		if (port == null) throw new IllegalStateException("No rollup adapter registered for " + store);
		return port;
	}
}
