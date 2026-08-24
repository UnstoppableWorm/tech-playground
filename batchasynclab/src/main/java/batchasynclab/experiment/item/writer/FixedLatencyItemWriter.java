package batchasynclab.experiment.item.writer;

import java.time.Duration;

import batchasynclab.experiment.simulation.SimulatedIoService;

import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ItemWriter;

/**
 * Simulates a bulk request whose fixed I/O latency is independent of item count.
 */
final class FixedLatencyItemWriter implements ItemWriter<Integer> {

	private final SimulatedIoService ioService;
	private final Duration delay;

	FixedLatencyItemWriter(SimulatedIoService ioService, Duration delay) {
		this.ioService = ioService;
		this.delay = delay;
	}

	@Override
	public void write(Chunk<? extends Integer> items) throws Exception {
		ioService.writeAndWait(items.size(), delay);
	}
}
