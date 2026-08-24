package batchasynclab.experiment.item.processor;

import java.time.Duration;

import batchasynclab.experiment.simulation.ProbabilisticFailureService;
import batchasynclab.experiment.simulation.SimulatedIoService;

import org.springframework.batch.infrastructure.item.ItemProcessor;

final class ProbablyFailIoItemProcessor implements ItemProcessor<Integer, Integer> {

	private final SimulatedIoService ioService;
	private final ProbabilisticFailureService failureService;
	private final Duration delay;

	ProbablyFailIoItemProcessor(SimulatedIoService ioService,
			ProbabilisticFailureService failureService, Duration delay) {
		this.ioService = ioService;
		this.failureService = failureService;
		this.delay = delay;
	}

	@Override
	public Integer process(Integer item) throws Exception {
		Integer result = ioService.requestAndWait(item, delay);
		failureService.failProbably();
		return result;
	}
}
