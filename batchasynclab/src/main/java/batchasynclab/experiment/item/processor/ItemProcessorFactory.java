package batchasynclab.experiment.item.processor;

import java.time.Duration;

import batchasynclab.experiment.simulation.ProbabilisticFailureService;
import batchasynclab.experiment.simulation.SimulatedIoService;

import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.stereotype.Component;

@Component
public class ItemProcessorFactory {

	private final SimulatedIoService ioService;
	private final ProbabilisticFailureService failureService;

	ItemProcessorFactory(SimulatedIoService ioService, ProbabilisticFailureService failureService) {
		this.ioService = ioService;
		this.failureService = failureService;
	}

	public ItemProcessor<Integer, Integer> create(ProcessorType type, Duration ioDelay) {
		return switch (type) {
			case CPU_HEAVY -> new CpuHeavyItemProcessor();
			case SIMULATED_IO -> new SimulatedIoItemProcessor(ioService, ioDelay);
			case PROBABLY_FAIL_IO -> new ProbablyFailIoItemProcessor(ioService, failureService, ioDelay);
			case PASS_THROUGH -> new PassThroughItemProcessor();
		};
	}
}
