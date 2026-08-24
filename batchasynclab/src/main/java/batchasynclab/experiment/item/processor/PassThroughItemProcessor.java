package batchasynclab.experiment.item.processor;

import org.springframework.batch.infrastructure.item.ItemProcessor;

/**
 * /NO-OP PROCESSOR
 */
final class PassThroughItemProcessor implements ItemProcessor<Integer, Integer> {

	@Override
	public Integer process(Integer item) {
		return item;
	}
}
