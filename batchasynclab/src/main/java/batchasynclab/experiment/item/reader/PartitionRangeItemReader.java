package batchasynclab.experiment.item.reader;

import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.batch.infrastructure.item.ItemStreamReader;

/**
 * Reads the inclusive range assigned to the current partition's execution context.
 */
final class PartitionRangeItemReader implements ItemStreamReader<Integer> {

	private static final String CURRENT_KEY = "partition.reader.current";

	private final ThreadLocal<RangeState> state = new ThreadLocal<>();

	@Override
	public Integer read() {
		RangeState current = state.get();
		if (current == null) {
			throw new IllegalStateException("Partition reader must be opened before reading");
		}

		if (current.next() > current.end()) {
			state.remove();
			return null;
		}
		return current.takeNext();
	}

	@Override
	public void open(ExecutionContext executionContext) {
		int next = executionContext.containsKey(CURRENT_KEY)
				? executionContext.getInt(CURRENT_KEY)
				: executionContext.getInt("start");
		state.set(new RangeState(next, executionContext.getInt("end")));
	}

	@Override
	public void update(ExecutionContext executionContext) {
		RangeState current = state.get();
		if (current != null) {
			executionContext.putInt(CURRENT_KEY, current.next());
		}
	}

	@Override
	public void close() {
		state.remove();
	}

	private static final class RangeState {

		private int next;
		private final int end;

		private RangeState(int start, int end) {
			this.next = start;
			this.end = end;
		}

		private int next() {
			return next;
		}

		private int end() {
			return end;
		}

		private int takeNext() {
			return next++;
		}
	}
}
