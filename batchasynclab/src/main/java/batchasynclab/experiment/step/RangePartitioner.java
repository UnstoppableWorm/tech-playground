package batchasynclab.experiment.step;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.batch.core.partition.Partitioner;
import org.springframework.batch.infrastructure.item.ExecutionContext;

class RangePartitioner implements Partitioner {

	private final int itemCount;

	RangePartitioner(int itemCount) {
		this.itemCount = itemCount;
	}

	@Override
	public Map<String, ExecutionContext> partition(int gridSize) {
		int partitionCount = Math.min(gridSize, itemCount);
		int baseSize = itemCount / partitionCount;
		int remainder = itemCount % partitionCount;
		int start = 1;
		Map<String, ExecutionContext> partitions = new LinkedHashMap<>();

		for (int index = 0; index < partitionCount; index++) {
			int size = baseSize + (index < remainder ? 1 : 0);
			ExecutionContext context = new ExecutionContext();
			context.putInt("start", start);
			context.putInt("end", start + size - 1);
			partitions.put("partition" + index, context);
			start += size;
		}
		return partitions;
	}
}
