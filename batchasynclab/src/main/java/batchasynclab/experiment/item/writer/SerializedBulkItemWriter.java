package batchasynclab.experiment.item.writer;

import java.time.Duration;

import batchasynclab.experiment.simulation.SimulatedIoService;

import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ItemWriter;

/**
 * Simulates a bulk sink with one write channel. Every request takes the same time
 * regardless of item count, while concurrent requests queue behind each other.
 */
final class SerializedBulkItemWriter implements ItemWriter<Integer> {

	private final SimulatedIoService ioService;
	private final Duration delay;

	SerializedBulkItemWriter(SimulatedIoService ioService, Duration delay) {
		this.ioService = ioService;
		this.delay = delay;
	}

	@Override
	public void write(Chunk<? extends Integer> items) throws Exception {
		ioService.writeSeriallyAndWait(items.size(), delay);
	}
}
