package olapreadlab.experiment;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("experiment")
public record ExperimentSettings(
		long sourceRowCount,
		int warmupCount,
		int measurementCount,
		Correction correction
) {

	public record Correction(
			long targetRowCount,
			Duration completionTimeout
	) {
	}
}
