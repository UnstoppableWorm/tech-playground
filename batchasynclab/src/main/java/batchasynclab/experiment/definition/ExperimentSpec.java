package batchasynclab.experiment.definition;

import batchasynclab.experiment.item.processor.ProcessorType;
import batchasynclab.experiment.item.reader.ReaderType;
import batchasynclab.experiment.item.writer.WriterType;

public enum ExperimentSpec {

	CPU_HEAVY_PROCESSOR_STANDARD(
			"cpu-heavy-processor-standard", ReaderType.SEQUENTIAL, ProcessorType.CPU_HEAVY, WriterType.NO_OP,
			ProcessingMode.SEQUENTIAL, PartitionMode.SINGLE, ThreadMode.CALLER),
	CPU_HEAVY_PROCESSOR_CONCURRENT_VIRTUAL(
			"cpu-heavy-processor-concurrent-virtual", ReaderType.SEQUENTIAL, ProcessorType.CPU_HEAVY, WriterType.NO_OP,
			ProcessingMode.CONCURRENT, PartitionMode.SINGLE, ThreadMode.VIRTUAL),
	CPU_HEAVY_PROCESSOR_CONCURRENT_PLATFORM(
			"cpu-heavy-processor-concurrent-platform", ReaderType.SEQUENTIAL, ProcessorType.CPU_HEAVY, WriterType.NO_OP,
			ProcessingMode.CONCURRENT, PartitionMode.SINGLE, ThreadMode.PLATFORM),
	IO_HEAVY_PROCESSOR_STANDARD(
			"io-heavy-processor-standard", ReaderType.SEQUENTIAL, ProcessorType.SIMULATED_IO, WriterType.NO_OP,
			ProcessingMode.SEQUENTIAL, PartitionMode.SINGLE, ThreadMode.CALLER),
	IO_HEAVY_READER_STANDARD(
			"io-heavy-reader-standard", ReaderType.SEQUENTIAL_IO, ProcessorType.PASS_THROUGH, WriterType.NO_OP,
			ProcessingMode.SEQUENTIAL, PartitionMode.SINGLE, ThreadMode.CALLER),
	FIXED_LATENCY_WRITER_STANDARD(
			"fixed-latency-writer-standard", ReaderType.SEQUENTIAL, ProcessorType.PASS_THROUGH, WriterType.SERIALIZED_BULK,
			ProcessingMode.SEQUENTIAL, PartitionMode.SINGLE, ThreadMode.CALLER),
	FIXED_LATENCY_WRITER_PARTITIONED_PLATFORM(
			"fixed-latency-writer-partitioned-platform", ReaderType.PARTITION_RANGE,
			ProcessorType.PASS_THROUGH, WriterType.SERIALIZED_BULK,
			ProcessingMode.SEQUENTIAL, PartitionMode.PARTITIONED, ThreadMode.PLATFORM),
	PROBABLY_FAIL_IO_PROCESSOR_STANDARD(
			"probably-fail-io-processor-standard", ReaderType.SEQUENTIAL, ProcessorType.PROBABLY_FAIL_IO,
			WriterType.NO_OP, ProcessingMode.SEQUENTIAL, PartitionMode.SINGLE, ThreadMode.CALLER),
	PROBABLY_FAIL_IO_PROCESSOR_PARTITIONED_PLATFORM(
			"probably-fail-io-processor-partitioned-platform", ReaderType.PARTITION_RANGE,
			ProcessorType.PROBABLY_FAIL_IO, WriterType.NO_OP,
			ProcessingMode.SEQUENTIAL, PartitionMode.PARTITIONED, ThreadMode.PLATFORM),
	IO_HEAVY_PROCESSOR_CONCURRENT_PLATFORM(
			"io-heavy-processor-concurrent-platform", ReaderType.SEQUENTIAL, ProcessorType.SIMULATED_IO, WriterType.NO_OP,
			ProcessingMode.CONCURRENT, PartitionMode.SINGLE, ThreadMode.PLATFORM),
	IO_HEAVY_PROCESSOR_CONCURRENT_VIRTUAL(
			"io-heavy-processor-concurrent-virtual", ReaderType.SEQUENTIAL, ProcessorType.SIMULATED_IO, WriterType.NO_OP,
			ProcessingMode.CONCURRENT, PartitionMode.SINGLE, ThreadMode.VIRTUAL),
	IO_HEAVY_PROCESSOR_PARTITIONED_PLATFORM(
			"io-heavy-processor-partitioned-platform", ReaderType.PARTITION_RANGE, ProcessorType.SIMULATED_IO, WriterType.NO_OP,
			ProcessingMode.SEQUENTIAL, PartitionMode.PARTITIONED, ThreadMode.PLATFORM),
	IO_HEAVY_PROCESSOR_PARTITIONED_VIRTUAL(
			"io-heavy-processor-partitioned-virtual", ReaderType.PARTITION_RANGE, ProcessorType.SIMULATED_IO, WriterType.NO_OP,
			ProcessingMode.SEQUENTIAL, PartitionMode.PARTITIONED, ThreadMode.VIRTUAL),
	IO_HEAVY_READER_PARTITIONED_PLATFORM(
			"io-heavy-reader-partitioned-platform", ReaderType.PARTITION_RANGE_IO, ProcessorType.PASS_THROUGH,
			WriterType.NO_OP, ProcessingMode.SEQUENTIAL, PartitionMode.PARTITIONED, ThreadMode.PLATFORM),
	IO_HEAVY_READER_PARTITIONED_VIRTUAL(
			"io-heavy-reader-partitioned-virtual", ReaderType.PARTITION_RANGE_IO, ProcessorType.PASS_THROUGH,
			WriterType.NO_OP, ProcessingMode.SEQUENTIAL, PartitionMode.PARTITIONED, ThreadMode.VIRTUAL);

	private final String jobName;
	private final ReaderType readerType;
	private final ProcessorType processorType;
	private final WriterType writerType;
	private final ProcessingMode processingMode;
	private final PartitionMode partitionMode;
	private final ThreadMode threadMode;

	ExperimentSpec(String jobName, ReaderType readerType, ProcessorType processorType, WriterType writerType,
			ProcessingMode processingMode, PartitionMode partitionMode, ThreadMode threadMode) {
		this.jobName = jobName;
		this.readerType = readerType;
		this.processorType = processorType;
		this.writerType = writerType;
		this.processingMode = processingMode;
		this.partitionMode = partitionMode;
		this.threadMode = threadMode;
	}

	public String jobName() {
		return jobName;
	}

	public ReaderType readerType() {
		return readerType;
	}

	public ProcessorType processorType() {
		return processorType;
	}

	public WriterType writerType() {
		return writerType;
	}

	public ProcessingMode processingMode() {
		return processingMode;
	}

	public PartitionMode partitionMode() {
		return partitionMode;
	}

	public ThreadMode threadMode() {
		return threadMode;
	}

	public enum ProcessingMode {
		SEQUENTIAL,
		CONCURRENT
	}

	public enum PartitionMode {
		SINGLE,
		PARTITIONED
	}

	public enum ThreadMode {
		CALLER,
		PLATFORM,
		VIRTUAL
	}
}
