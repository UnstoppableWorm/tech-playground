package olapreadlab.benchmark;

import tools.jackson.databind.ObjectMapper;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "benchmark.enabled", havingValue = "true")
public class GraphQlBenchmarkRunner {

	private final GraphQlBenchmarkEngine engine;

	GraphQlBenchmarkRunner(BenchmarkSettings settings, ObjectMapper objectMapper) {
		this.engine = new GraphQlBenchmarkEngine(settings, objectMapper);
	}

	public BenchmarkReport run() {
		return engine.run();
	}
}
