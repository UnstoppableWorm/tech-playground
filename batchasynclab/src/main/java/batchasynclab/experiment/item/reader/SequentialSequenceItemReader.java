package batchasynclab.experiment.item.reader;

import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.batch.infrastructure.item.ItemStreamReader;

/**
 * Sequentially reads the configured range and checkpoints only committed progress.
 */
final class SequentialSequenceItemReader implements ItemStreamReader<Integer> {

	private static final String CURRENT_KEY = "sequential.reader.current";

	private final int end;
	private int current;

	SequentialSequenceItemReader(int itemCount) {
		this.end = itemCount;
	}

	@Override
	public void open(ExecutionContext executionContext) {
		current = executionContext.containsKey(CURRENT_KEY)
				? executionContext.getInt(CURRENT_KEY)
				: 1;
	}

	@Override
	public Integer read() {
		return current <= end ? current++ : null;
	}

	@Override
	public void update(ExecutionContext executionContext) {
		executionContext.putInt(CURRENT_KEY, current);
	}
}
