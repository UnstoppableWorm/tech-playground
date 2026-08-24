package batchasynclab.experiment.item.writer;

import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ItemWriter;

/**
 * Intentionally discards output so the experiment measures processor-side I/O only.
 */
final class NoOpItermWriter implements ItemWriter<Integer> {

	@Override
	public void write(Chunk<? extends Integer> items) {
		// No output persistence for this experiment.
	}
}
