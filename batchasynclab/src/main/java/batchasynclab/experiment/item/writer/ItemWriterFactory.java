package batchasynclab.experiment.item.writer;

import java.time.Duration;

import batchasynclab.experiment.simulation.SimulatedIoService;

import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.stereotype.Component;

@Component
public class ItemWriterFactory {
	private final SimulatedIoService ioService;

	ItemWriterFactory(SimulatedIoService ioService) {
		this.ioService = ioService;
	}

	public ItemWriter<Integer> create(WriterType type, Duration ioDelay) {
		return switch (type) {
			case NO_OP -> new NoOpItermWriter();
			case FIXED_LATENCY -> new FixedLatencyItemWriter(ioService, ioDelay);
			case SERIALIZED_BULK -> new SerializedBulkItemWriter(ioService, ioDelay);
		};
	}

}
