package batchasynclab.experiment.item.reader;

import java.time.Duration;

import batchasynclab.experiment.simulation.SimulatedIoService;

import org.springframework.batch.infrastructure.item.ItemReader;

/**
 * Adds a blocking I/O wait to any delegate reader without changing its item-selection strategy.
 */
final class SimulatedIoItemReader implements ItemReader<Integer> {

	private final ItemReader<Integer> delegate;
	private final SimulatedIoService ioService;
	private final Duration delay;

	SimulatedIoItemReader(ItemReader<Integer> delegate, SimulatedIoService ioService, Duration delay) {
		this.delegate = delegate;
		this.ioService = ioService;
		this.delay = delay;
	}

	@Override
	public Integer read() throws Exception {
		Integer item = delegate.read();
		return item == null ? null : ioService.readAndWait(item, delay);
	}
}
