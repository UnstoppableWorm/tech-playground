package olapreadlab;

import olapreadlab.benchmark.GraphQlBenchmarkRunner;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class OlapReadLabApplication {

	public static void main(String[] args) {
		var context = SpringApplication.run(OlapReadLabApplication.class, args);
		if (context.getEnvironment().getProperty("benchmark.enabled", Boolean.class, false)) {
			try (context) {
				context.getBean(GraphQlBenchmarkRunner.class).run();
			}
		}
	}
}
