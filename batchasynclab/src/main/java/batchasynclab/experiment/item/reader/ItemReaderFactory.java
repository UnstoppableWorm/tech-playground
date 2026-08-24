package batchasynclab.experiment.item.reader;

import batchasynclab.experiment.ExperimentSettings;
import batchasynclab.experiment.simulation.SimulatedIoService;

import org.springframework.batch.infrastructure.item.ItemReader;
import org.springframework.stereotype.Component;

@Component
public class ItemReaderFactory {

	private final SimulatedIoService ioService;

    ItemReaderFactory(SimulatedIoService ioService) {
		this.ioService = ioService;
	}

	public ItemReader<Integer> create(ReaderType type, ExperimentSettings settings) {
		return switch (type) {
			case SEQUENTIAL -> sequentialReader(settings);
			case SEQUENTIAL_IO -> sequentialIoReader(settings);
			case PARTITION_RANGE -> partitionReader();
			case PARTITION_RANGE_IO -> partitionIoReader(settings);
		};
	}

	private ItemReader<Integer> sequentialReader(ExperimentSettings settings) {
		return new SequentialSequenceItemReader(settings.itemCount());
	}

	private ItemReader<Integer> sequentialIoReader(ExperimentSettings settings) {
		return new SimulatedIoItemReader(sequentialReader(settings), ioService, settings.ioDelay());
	}

	private ItemReader<Integer> partitionReader() {
		return new PartitionRangeItemReader();
	}

	private ItemReader<Integer> partitionIoReader(ExperimentSettings settings) {
		return new SimulatedIoItemReader(partitionReader(), ioService, settings.ioDelay());
	}
}
