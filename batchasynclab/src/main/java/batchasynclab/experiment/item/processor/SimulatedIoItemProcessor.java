package batchasynclab.experiment.item.processor;

import java.time.Duration;

import batchasynclab.experiment.simulation.SimulatedIoService;

import org.springframework.batch.infrastructure.item.ItemProcessor;

/**
 * IO-DELAYED processor
 */
final class SimulatedIoItemProcessor implements ItemProcessor<Integer, Integer> {

	private final SimulatedIoService ioService;
	private final Duration delay;

	SimulatedIoItemProcessor(SimulatedIoService ioService, Duration delay) {
		this.ioService = ioService;
		this.delay = delay;
	}

	@Override
	public Integer process(Integer item) throws Exception {
		return ioService.requestAndWait(item, delay);
	}
}
