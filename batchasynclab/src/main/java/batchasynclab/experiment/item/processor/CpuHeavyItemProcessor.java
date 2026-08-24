package batchasynclab.experiment.item.processor;

import org.springframework.batch.infrastructure.item.ItemProcessor;

/**
 * Performs deterministic, input-dependent integer mixing without blocking or allocating per iteration.
 */
final class CpuHeavyItemProcessor implements ItemProcessor<Integer, Integer> {

	@Override
	public Integer process(Integer item) {
		long value = Integer.toUnsignedLong(item) + 0x9E3779B97F4A7C15L;
		for (int iteration = 0; iteration < ProcessorType.CPU_HEAVY.workIterations(); iteration++) {
			value ^= value >>> 12;
			value ^= value << 25;
			value ^= value >>> 27;
			value *= 0x2545F4914F6CDD1DL;
		}
		return (int) (value ^ value >>> 32);
	}
}
