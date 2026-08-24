package batchasynclab.experiment;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "experiment")
public record ExperimentSettings(
		int itemCount,
		int chunkSize,
		int partitionConcurrency,
		int processorConcurrency,
		Duration ioDelay) {

	public ExperimentSettings {
		if (itemCount < 1 || chunkSize < 1 || partitionConcurrency < 1 || processorConcurrency < 1) {
			throw new IllegalArgumentException(
					"item-count, chunk-size, partition-concurrency, processor-concurrency must be positive");
		}
		if (ioDelay.isNegative()) {
			throw new IllegalArgumentException("io-delay must not be negative");
		}
	}
}
