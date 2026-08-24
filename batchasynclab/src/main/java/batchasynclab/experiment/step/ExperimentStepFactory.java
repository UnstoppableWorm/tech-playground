package batchasynclab.experiment.step;

import batchasynclab.experiment.ExperimentSettings;
import batchasynclab.experiment.definition.ExperimentSpec;
import batchasynclab.experiment.item.processor.ItemProcessorFactory;
import batchasynclab.experiment.item.reader.ItemReaderFactory;
import batchasynclab.experiment.item.writer.ItemWriterFactory;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.ChunkOrientedStepBuilder;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.item.ItemReader;
import org.springframework.batch.infrastructure.item.ItemStream;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.stereotype.Component;

@Component
public class ExperimentStepFactory {

	private final JobRepository repository;
	private final ItemReaderFactory readerFactory;
	private final ItemProcessorFactory processorFactory;
	private final ItemWriterFactory writerFactory;
	private final ExperimentTaskExecutorFactory taskExecutorFactory;

	ExperimentStepFactory(JobRepository repository,
			ItemReaderFactory readerFactory, ItemProcessorFactory processorFactory,
			ItemWriterFactory writerFactory, ExperimentTaskExecutorFactory taskExecutorFactory) {
		this.repository = repository;
		this.readerFactory = readerFactory;
		this.processorFactory = processorFactory;
		this.writerFactory = writerFactory;
		this.taskExecutorFactory = taskExecutorFactory;
	}

    public Step create(ExperimentSpec spec, ExperimentSettings settings) {
        var builder = commonStepBuilder(spec, settings);

		if (spec.processingMode() == ExperimentSpec.ProcessingMode.CONCURRENT) {
            builder.taskExecutor(
					taskExecutor(spec, "processor", settings.processorConcurrency()));
        }

        Step worker = builder.build();

		if (spec.partitionMode() == ExperimentSpec.PartitionMode.PARTITIONED) {
            return partitionedWorkerStep(spec, settings, worker);
        }

        return worker;
    }

	private ChunkOrientedStepBuilder<Integer, Integer> commonStepBuilder(ExperimentSpec spec, ExperimentSettings settings) {
		String suffix = spec.partitionMode() == ExperimentSpec.PartitionMode.PARTITIONED ? "-worker" : "";
		ItemReader<Integer> reader = readerFactory.create(spec.readerType(), settings);
		ChunkOrientedStepBuilder<Integer, Integer> builder = new StepBuilder(stepName(spec) + suffix, repository)
				.<Integer, Integer>chunk(settings.chunkSize())
				.reader(reader)
				.processor(processorFactory.create(spec.processorType(), settings.ioDelay()))
				.writer(writerFactory.create(spec.writerType(), settings.ioDelay()));
		if (reader instanceof ItemStream itemStream) {
			builder.stream(itemStream);
		}
		return builder;
	}

	private Step partitionedWorkerStep(ExperimentSpec spec, ExperimentSettings settings, Step worker) {
		return new StepBuilder(stepName(spec) + "-manager", repository)
				.partitioner(worker)
				.partitioner(worker.getName(), new RangePartitioner(settings.itemCount()))
				.gridSize(settings.partitionConcurrency())
				.taskExecutor(taskExecutor(spec, "partition", settings.partitionConcurrency()))
				.build();
	}

	private AsyncTaskExecutor taskExecutor(ExperimentSpec spec, String role, int concurrency) {
		if (spec.threadMode() == ExperimentSpec.ThreadMode.CALLER) {
			throw new IllegalArgumentException("Executor is not available for caller-thread experiment: " + spec);
		}
		String executorRole = spec.jobName() + "-" + role;
		return spec.threadMode() == ExperimentSpec.ThreadMode.PLATFORM
				? taskExecutorFactory.createPlatform(executorRole, concurrency)
				: taskExecutorFactory.createVirtual(executorRole, concurrency);
	}

	private String stepName(ExperimentSpec spec) {
		return spec.jobName() + "-step";
	}
}
