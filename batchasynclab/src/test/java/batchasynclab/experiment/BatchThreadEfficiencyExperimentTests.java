package batchasynclab.experiment;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.ToLongFunction;
import java.util.stream.Stream;

import batchasynclab.experiment.definition.ExperimentSpec;
import batchasynclab.experiment.item.processor.ProcessorType;
import batchasynclab.experiment.simulation.ProbabilisticFailureService;
import batchasynclab.experiment.item.reader.ReaderType;
import batchasynclab.experiment.item.writer.WriterType;
import batchasynclab.experiment.job.ExperimentJobFactory;
import batchasynclab.experiment.simulation.SimulatedIoService;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@Tag("experiment")
@Execution(ExecutionMode.SAME_THREAD)
@SpringBootTest(properties = "logging.level.org.springframework.batch=OFF")
class BatchThreadEfficiencyExperimentTests {

	private static final int MEASUREMENT_RUNS = 6;
	private static final Path REPORT_PATH =
			Path.of("build", "reports", "experiments", "experiment-results.txt");
	private static final Path JSON_REPORT_PATH =
			Path.of("build", "reports", "experiments", "experiment-results.json");
	private static final List<ExperimentResult> RESULTS = new CopyOnWriteArrayList<>();
	private static final List<ExperimentScenario> SCENARIOS = List.of(
			// Experiment 1-A: CPU-bound work does not gain unlimited throughput from virtual threads.
			scenario(1, "CPU-HEAVY PROCESSOR: STANDARD VS VIRTUAL", "standard",
					ExperimentSpec.CPU_HEAVY_PROCESSOR_STANDARD, settings(400, 400, 1, 1, 0)),
			scenario(2, "CPU-HEAVY PROCESSOR: STANDARD VS VIRTUAL", "virtual-concurrency-4",
					ExperimentSpec.CPU_HEAVY_PROCESSOR_CONCURRENT_VIRTUAL, settings(400, 400, 1, 4, 0)),
			scenario(3, "CPU-HEAVY PROCESSOR: STANDARD VS VIRTUAL", "virtual-concurrency-40",
					ExperimentSpec.CPU_HEAVY_PROCESSOR_CONCURRENT_VIRTUAL, settings(400, 400, 1, 40, 0)),
			scenario(4, "CPU-HEAVY PROCESSOR: STANDARD VS VIRTUAL", "virtual-concurrency-400",
					ExperimentSpec.CPU_HEAVY_PROCESSOR_CONCURRENT_VIRTUAL, settings(400, 400, 1, 400, 0)),

			// Experiment 1-B: concurrency benefit when Processor waits for blocking I/O.
			scenario(5, "I/O-HEAVY PROCESSOR: STANDARD VS VIRTUAL", "standard",
					ExperimentSpec.IO_HEAVY_PROCESSOR_STANDARD, settings(400, 400, 1, 1, 10)),
			scenario(6, "I/O-HEAVY PROCESSOR: STANDARD VS VIRTUAL", "virtual-concurrency-4",
					ExperimentSpec.IO_HEAVY_PROCESSOR_CONCURRENT_VIRTUAL, settings(400, 400, 1, 4, 10)),
			scenario(7, "I/O-HEAVY PROCESSOR: STANDARD VS VIRTUAL", "virtual-concurrency-40",
					ExperimentSpec.IO_HEAVY_PROCESSOR_CONCURRENT_VIRTUAL, settings(400, 400, 1, 40, 10)),
			scenario(8, "I/O-HEAVY PROCESSOR: STANDARD VS VIRTUAL", "virtual-concurrency-400",
					ExperimentSpec.IO_HEAVY_PROCESSOR_CONCURRENT_VIRTUAL, settings(400, 400, 1, 400, 10)),

			// Experiment 2-A: platform threads versus virtual threads for CPU-bound work.
			scenario(9, "CPU-HEAVY PROCESSOR: PLATFORM VS VIRTUAL", "platform-concurrency-4",
					ExperimentSpec.CPU_HEAVY_PROCESSOR_CONCURRENT_PLATFORM, settings(400, 400, 1, 4, 0)),
			scenario(10, "CPU-HEAVY PROCESSOR: PLATFORM VS VIRTUAL", "virtual-concurrency-4",
					ExperimentSpec.CPU_HEAVY_PROCESSOR_CONCURRENT_VIRTUAL, settings(400, 400, 1, 4, 0)),
			scenario(11, "CPU-HEAVY PROCESSOR: PLATFORM VS VIRTUAL", "platform-concurrency-40",
					ExperimentSpec.CPU_HEAVY_PROCESSOR_CONCURRENT_PLATFORM, settings(400, 400, 1, 40, 0)),
			scenario(12, "CPU-HEAVY PROCESSOR: PLATFORM VS VIRTUAL", "virtual-concurrency-40",
					ExperimentSpec.CPU_HEAVY_PROCESSOR_CONCURRENT_VIRTUAL, settings(400, 400, 1, 40, 0)),
			scenario(13, "CPU-HEAVY PROCESSOR: PLATFORM VS VIRTUAL", "platform-concurrency-400",
					ExperimentSpec.CPU_HEAVY_PROCESSOR_CONCURRENT_PLATFORM, settings(400, 400, 1, 400, 0)),
			scenario(14, "CPU-HEAVY PROCESSOR: PLATFORM VS VIRTUAL", "virtual-concurrency-400",
					ExperimentSpec.CPU_HEAVY_PROCESSOR_CONCURRENT_VIRTUAL, settings(400, 400, 1, 400, 0)),

			// Experiment 2-B: platform threads versus virtual threads for blocking I/O.
			scenario(15, "I/O-HEAVY PROCESSOR: PLATFORM VS VIRTUAL", "platform-concurrency-4",
					ExperimentSpec.IO_HEAVY_PROCESSOR_CONCURRENT_PLATFORM, settings(400, 400, 1, 4, 10)),
			scenario(16, "I/O-HEAVY PROCESSOR: PLATFORM VS VIRTUAL", "virtual-concurrency-4",
					ExperimentSpec.IO_HEAVY_PROCESSOR_CONCURRENT_VIRTUAL, settings(400, 400, 1, 4, 10)),
			scenario(17, "I/O-HEAVY PROCESSOR: PLATFORM VS VIRTUAL", "platform-concurrency-40",
					ExperimentSpec.IO_HEAVY_PROCESSOR_CONCURRENT_PLATFORM, settings(400, 400, 1, 40, 10)),
			scenario(18, "I/O-HEAVY PROCESSOR: PLATFORM VS VIRTUAL", "virtual-concurrency-40",
					ExperimentSpec.IO_HEAVY_PROCESSOR_CONCURRENT_VIRTUAL, settings(400, 400, 1, 40, 10)),
			scenario(19, "I/O-HEAVY PROCESSOR: PLATFORM VS VIRTUAL", "platform-concurrency-400",
					ExperimentSpec.IO_HEAVY_PROCESSOR_CONCURRENT_PLATFORM, settings(400, 400, 1, 400, 10)),
			scenario(20, "I/O-HEAVY PROCESSOR: PLATFORM VS VIRTUAL", "virtual-concurrency-400",
					ExperimentSpec.IO_HEAVY_PROCESSOR_CONCURRENT_VIRTUAL, settings(400, 400, 1, 400, 10)),

			// Experiment 3-A: partitioning fragments one efficient bulk write into four serialized requests.
			scenario(21, "BULK WRITER: STANDARD VS PARTITIONED", "standard",
					ExperimentSpec.FIXED_LATENCY_WRITER_STANDARD, settings(400, 400, 1, 1, 1_000)),
			scenario(22, "BULK WRITER: STANDARD VS PARTITIONED", "partitioned-4",
					ExperimentSpec.FIXED_LATENCY_WRITER_PARTITIONED_PLATFORM, settings(400, 400, 4, 1, 1_000)),
			scenario(23, "BULK WRITER: STANDARD VS PARTITIONED", "partitioned-40",
					ExperimentSpec.FIXED_LATENCY_WRITER_PARTITIONED_PLATFORM, settings(400, 400, 40, 1, 1_000)),

			// Experiment 3-B: total elapsed time across repeated executions of the same failed JobInstance.
			failureScenario(24, "standard-failure-0.01pct", ExperimentSpec.PROBABLY_FAIL_IO_PROCESSOR_STANDARD, 0.0001),
			failureScenario(25, "partitioned-failure-0.01pct",
					ExperimentSpec.PROBABLY_FAIL_IO_PROCESSOR_PARTITIONED_PLATFORM, 0.0001),
			failureScenario(26, "standard-failure-0.1pct", ExperimentSpec.PROBABLY_FAIL_IO_PROCESSOR_STANDARD, 0.001),
			failureScenario(27, "partitioned-failure-0.1pct",
					ExperimentSpec.PROBABLY_FAIL_IO_PROCESSOR_PARTITIONED_PLATFORM, 0.001),
			failureScenario(28, "standard-failure-1pct", ExperimentSpec.PROBABLY_FAIL_IO_PROCESSOR_STANDARD, 0.01),
			failureScenario(29, "partitioned-failure-1pct",
					ExperimentSpec.PROBABLY_FAIL_IO_PROCESSOR_PARTITIONED_PLATFORM, 0.01));


	private final JobOperator jobOperator;
	private final SimulatedIoService ioService;
	private final ProbabilisticFailureService failureService;
	private final ExperimentJobFactory jobFactory;

	@Autowired
	BatchThreadEfficiencyExperimentTests(JobOperator jobOperator, SimulatedIoService ioService,
			ProbabilisticFailureService failureService, ExperimentJobFactory jobFactory) {
		this.jobOperator = jobOperator;
		this.ioService = ioService;
		this.failureService = failureService;
		this.jobFactory = jobFactory;
	}

	@TestFactory
	Stream<DynamicTest> experimentSuite() {
		RESULTS.clear();
		String categoryFilter = System.getenv("EXPERIMENT_CATEGORY");
		return SCENARIOS.stream()
				.filter(scenario -> categoryFilter == null || scenario.category().equals(categoryFilter))
				.map(ExperimentScenario::category)
				.distinct()
				.map(category -> DynamicTest.dynamicTest(category, () -> executeGroup(category)));
	}

	private void executeGroup(String category) throws Exception {
		List<ExperimentScenario> group = SCENARIOS.stream()
				.filter(scenario -> scenario.category().equals(category))
				.toList();
		Map<ExperimentScenario, List<ExperimentResult>> samples = new LinkedHashMap<>();
		group.forEach(scenario -> samples.put(scenario, new ArrayList<>()));

		for (ExperimentScenario scenario : group) {
			ExperimentResult warmup = runOnce(scenario, "warmup");
			assertCompleted(warmup);
		}

		for (int round = 0; round < MEASUREMENT_RUNS; round++) {
			List<ExperimentScenario> executionOrder = new ArrayList<>(group);
			if (round % 2 == 1) {
				Collections.reverse(executionOrder);
			}
			for (ExperimentScenario scenario : executionOrder) {
				ExperimentResult sample = runOnce(scenario, "round-" + (round + 1));
				assertCompleted(sample);
				samples.get(scenario).add(sample);
			}
		}

		for (ExperimentScenario scenario : group) {
			ExperimentResult result = ExperimentResult.median(scenario, samples.get(scenario));
			verifyRelevantMetrics(result);
			RESULTS.add(result);
			System.out.print(result.formattedSummary());
		}
	}

	private void assertCompleted(ExperimentResult result) {
		assertThat(result.status()).isEqualTo(BatchStatus.COMPLETED);
		assertThat(result.completedItems()).isEqualTo(result.scenario().settings().itemCount());
	}

	private void verifyRelevantMetrics(ExperimentResult result) {
		ExperimentSpec spec = result.scenario().spec();
		ExperimentSettings settings = result.scenario().settings();

		if (spec.readerType() == ReaderType.SEQUENTIAL_IO
				|| spec.readerType() == ReaderType.PARTITION_RANGE_IO) {
			assertThat(result.completedReads()).isEqualTo(settings.itemCount());
			assertThat(result.peakConcurrentReadIo()).isBetween(1, settings.partitionConcurrency());
		}
		if (spec.processorType() == ProcessorType.SIMULATED_IO
				|| spec.processorType() == ProcessorType.PROBABLY_FAIL_IO) {
			if (spec.processorType() == ProcessorType.PROBABLY_FAIL_IO) {
				assertThat(result.completedProcessorIo()).isGreaterThanOrEqualTo(settings.itemCount());
			} else {
				assertThat(result.completedProcessorIo()).isEqualTo(settings.itemCount());
			}
			int configuredLimit = spec.processingMode() == ExperimentSpec.ProcessingMode.CONCURRENT
					? settings.processorConcurrency()
					: settings.partitionConcurrency();
			assertThat(result.peakConcurrentProcessorIo()).isBetween(1, configuredLimit);
		} else {
			assertThat(result.completedProcessorIo()).isZero();
			assertThat(result.peakConcurrentProcessorIo()).isZero();
		}
		if (spec.writerType() == WriterType.FIXED_LATENCY
				|| spec.writerType() == WriterType.SERIALIZED_BULK) {
			int logicalItemCount = spec.partitionMode() == ExperimentSpec.PartitionMode.PARTITIONED
					? (settings.itemCount() + settings.partitionConcurrency() - 1)
							/ settings.partitionConcurrency()
					: settings.itemCount();
			int writesPerWorker = (logicalItemCount + settings.chunkSize() - 1) / settings.chunkSize();
			int expectedWrites = writesPerWorker * (spec.partitionMode() == ExperimentSpec.PartitionMode.PARTITIONED
					? settings.partitionConcurrency() : 1);
			assertThat(result.completedWrites()).isEqualTo(expectedWrites);
			assertThat(result.writtenItems()).isEqualTo(settings.itemCount());
		}
	}

	private ExperimentResult runOnce(ExperimentScenario scenario, String runId) throws Exception {
		ioService.resetMetrics();
		failureService.reset(runId.hashCode(), scenario.failureProbability());
		ExperimentSettings settings = scenario.settings();
		Job job = jobFactory.create(scenario.spec(), settings);
		ExperimentResourceMonitor resourceMonitor = ExperimentResourceMonitor.start();
		Instant startedAt = Instant.now();

		JobExecution execution;
		int executionCount = 0;
		long completedItems = 0;
		ExperimentResourceMonitor.ResourceMetrics resourceMetrics;
		try {
			var parameters = new JobParametersBuilder()
					.addString("experimentId", "scenario-" + scenario.order() + "-" + runId, true)
					.addLong("startedAt", System.nanoTime(), false)
					.toJobParameters();
			do {
				execution = jobOperator.start(job, parameters);
				executionCount++;
				completedItems = writeCount(execution);
				if (executionCount >= 1_000 && execution.getStatus() != BatchStatus.COMPLETED) {
					throw new IllegalStateException("Experiment did not complete after 1,000 executions: " + scenario);
				}
			} while (execution.getStatus() != BatchStatus.COMPLETED
					&& scenario.spec().processorType() == ProcessorType.PROBABLY_FAIL_IO);
		} finally {
			resourceMetrics = resourceMonitor.stop();
		}
		long elapsedMs = Duration.between(startedAt, Instant.now()).toMillis();

		ExperimentResult result = new ExperimentResult(
				scenario, execution.getStatus(), elapsedMs, completedItems, executionCount,
				failureService.failures(),
				resourceMetrics.processCpuMs(),
				ioService.completedReads(), ioService.peakConcurrentReads(),
				ioService.completedRequests(), ioService.peakConcurrentRequests(),
				ioService.completedWrites(), ioService.writtenItems(), ioService.peakConcurrentWrites());

		return result;
	}

	private long writeCount(JobExecution execution) {
		return execution.getStepExecutions().stream()
				.filter(stepExecution -> !stepExecution.getStepName().endsWith("-manager"))
				.mapToLong(StepExecution::getWriteCount)
				.sum();
	}

	@AfterAll
	static void writeReport() throws Exception {
		Files.createDirectories(REPORT_PATH.getParent());
		StringBuilder report = new StringBuilder("SPRING BATCH THREAD EFFICIENCY REPORT\n");
		String currentCategory = null;

		for (ExperimentResult result : RESULTS.stream()
				.sorted((left, right) -> Integer.compare(left.scenario().order(), right.scenario().order()))
				.toList()) {
			if (!result.scenario().category().equals(currentCategory)) {
				currentCategory = result.scenario().category();
				report.append("\n=== ").append(currentCategory).append(" ===\n");
			}
			report.append(result.formattedSummary());
		}

		Files.writeString(REPORT_PATH, report);
		Files.writeString(JSON_REPORT_PATH, jsonReport());
		System.out.println("Experiment report: " + REPORT_PATH.toAbsolutePath());
		System.out.println("Experiment data: " + JSON_REPORT_PATH.toAbsolutePath());
	}

	private static String jsonReport() {
		List<ExperimentResult> orderedResults = RESULTS.stream()
				.sorted((left, right) -> Integer.compare(left.scenario().order(), right.scenario().order()))
				.toList();
		StringBuilder json = new StringBuilder()
				.append("{\n")
				.append("  \"schemaVersion\": 1,\n")
				.append("  \"lab\": \"batchasynclab\",\n")
				.append("  \"title\": \"Spring Batch Thread Efficiency\",\n")
				.append("  \"generatedAt\": \"").append(Instant.now()).append("\",\n")
				.append("  \"charts\": [\n")
				.append(comparisonLineChartJson("experiment-1", "cpu-heavy-standard-vs-virtual",
						"CPU-heavy Processor: Standard vs Virtual",
						"CPU-HEAVY PROCESSOR: STANDARD VS VIRTUAL", "elapsedMs", "Execution time", "ms"))
				.append(",\n")
				.append(comparisonLineChartJson("experiment-1", "io-heavy-standard-vs-virtual",
						"I/O-heavy Processor: Standard vs Virtual",
						"I/O-HEAVY PROCESSOR: STANDARD VS VIRTUAL", "elapsedMs", "Execution time", "ms"))
				.append(",\n")
				.append(lineChartJson("experiment-2", "cpu-heavy-performance", "CPU-heavy: Platform vs Virtual",
						"CPU-HEAVY PROCESSOR: PLATFORM VS VIRTUAL",
						"elapsedMs", "Execution time", "ms",
						"throughputPerSec", "Throughput", "items/s"))
				.append(",\n")
				.append(lineChartJson("experiment-2", "cpu-heavy-cpu-time", "CPU-heavy: Process CPU time",
						"CPU-HEAVY PROCESSOR: PLATFORM VS VIRTUAL",
						"processCpuMs", "Process CPU time", "ms"))
				.append(",\n")
				.append(lineChartJson("experiment-2", "io-heavy-performance", "I/O-heavy: Platform vs Virtual",
						"I/O-HEAVY PROCESSOR: PLATFORM VS VIRTUAL",
						"elapsedMs", "Execution time", "ms",
						"throughputPerSec", "Throughput", "items/s"))
				.append(",\n")
				.append(lineChartJson("experiment-2", "io-heavy-cpu-time", "I/O-heavy: Process CPU time",
						"I/O-HEAVY PROCESSOR: PLATFORM VS VIRTUAL",
						"processCpuMs", "Process CPU time", "ms"))
				.append(",\n")
				.append(partitionLineChartJson("experiment-3", "bulk-writer-standard-vs-partitioned",
						"Bulk writer: Standard vs Partitioned",
						"BULK WRITER: STANDARD VS PARTITIONED",
						"elapsedMs", "Total execution time", "ms"))
				.append(",\n")
				.append(failureLineChartJson("experiment-3", "failure-recovery-standard-vs-partitioned",
						"Failure recovery: Standard vs Partitioned",
						"PROBABLY-FAIL I/O PROCESSOR: STANDARD VS PARTITIONED",
						"elapsedMs", "Time until completion", "ms",
						"jobExecutions", "Job executions", "count"))
				.append("\n  ],\n")
				.append("  \"results\": [\n");

		for (int index = 0; index < orderedResults.size(); index++) {
			json.append(orderedResults.get(index).json());
			json.append(index + 1 < orderedResults.size() ? ",\n" : "\n");
		}
		return json.append("  ]\n}\n").toString();
	}

	private static String chartJson(String directory, String id, String title, String group,
			String... metricDefinitions) {
		if (metricDefinitions.length == 0 || metricDefinitions.length % 3 != 0) {
			throw new IllegalArgumentException("Chart metrics must be key, label, unit triples");
		}
		StringBuilder json = new StringBuilder()
				.append("    {\"directory\": \"").append(directory)
				.append("\", \"id\": \"").append(id)
				.append("\", \"title\": \"").append(title)
				.append("\", \"group\": \"").append(group)
				.append("\", \"metrics\": [");
		for (int index = 0; index < metricDefinitions.length; index += 3) {
			if (index > 0) {
				json.append(", ");
			}
			json.append(metricJson(
					metricDefinitions[index], metricDefinitions[index + 1], metricDefinitions[index + 2]));
		}
		return json.append("]}").toString();
	}

	private static String lineChartJson(String directory, String id, String title, String group,
			String... metricDefinitions) {
		String barChart = chartJson(directory, id, title, group, metricDefinitions);
		return barChart.substring(0, barChart.length() - 1)
				+ ", \"type\": \"line\", \"x\": {\"key\": \"processorConcurrency\", "
				+ "\"label\": \"Concurrency\"}, \"series\": ["
				+ "{\"value\": \"platform\", \"label\": \"Platform\"}, "
				+ "{\"value\": \"virtual\", \"label\": \"Virtual\"}]}";
	}

	private static String comparisonLineChartJson(String directory, String id, String title, String group,
			String... metricDefinitions) {
		String barChart = chartJson(directory, id, title, group, metricDefinitions);
		return barChart.substring(0, barChart.length() - 1)
				+ ", \"type\": \"line\", \"x\": {\"key\": \"processorConcurrency\", "
				+ "\"label\": \"Concurrency\"}, \"series\": ["
				+ "{\"value\": \"standard-vs-virtual\", \"label\": \"Standard → Virtual\"}]}";
	}

	private static String failureLineChartJson(String directory, String id, String title, String group,
			String... metricDefinitions) {
		String barChart = chartJson(directory, id, title, group, metricDefinitions);
		return barChart.substring(0, barChart.length() - 1)
				+ ", \"type\": \"line\", \"x\": {\"key\": \"failureProbabilityPct\", "
				+ "\"label\": \"Failure probability (%)\"}, \"series\": ["
				+ "{\"value\": \"standard\", \"label\": \"Standard\"}, "
				+ "{\"value\": \"partitioned\", \"label\": \"Partitioned\"}]}";
	}

	private static String partitionLineChartJson(String directory, String id, String title, String group,
			String... metricDefinitions) {
		String barChart = chartJson(directory, id, title, group, metricDefinitions);
		return barChart.substring(0, barChart.length() - 1)
				+ ", \"type\": \"line\", \"x\": {\"key\": \"partitionConcurrency\", "
				+ "\"label\": \"Partition concurrency\"}, \"series\": ["
				+ "{\"value\": \"standard-vs-partitioned\", \"label\": \"Standard → Partitioned\"}]}";
	}

	private static String metricJson(String key, String label, String unit) {
		return "{\"key\": \"%s\", \"label\": \"%s\", \"unit\": \"%s\"}"
				.formatted(key, label, unit);
	}

	private static String seriesName(ExperimentScenario scenario) {
		if (scenario.category().endsWith("STANDARD VS VIRTUAL")) {
			return "standard-vs-virtual";
		}
		if (scenario.category().startsWith("PROBABLY-FAIL")) {
			return scenario.spec().partitionMode() == ExperimentSpec.PartitionMode.PARTITIONED
					? "partitioned" : "standard";
		}
		if (scenario.category().startsWith("BULK WRITER")) {
			return "standard-vs-partitioned";
		}
		return scenario.spec().threadMode().name().toLowerCase();
	}

	private static ExperimentScenario scenario(int order, String category, String name,
			ExperimentSpec spec, ExperimentSettings settings) {
		return new ExperimentScenario(order, category, name, spec, settings, 0);
	}

	private static ExperimentScenario failureScenario(int order, String name,
			ExperimentSpec spec, double failureProbability) {
		return new ExperimentScenario(order, "PROBABLY-FAIL I/O PROCESSOR: STANDARD VS PARTITIONED",
				name, spec, settings(200, 1, 4, 1, 1), failureProbability);
	}

	private static ExperimentSettings settings(int itemCount, int chunkSize,
			int partitionConcurrency, int processorConcurrency, long ioDelayMs) {
		return new ExperimentSettings(itemCount, chunkSize, partitionConcurrency,
				processorConcurrency, Duration.ofMillis(ioDelayMs));
	}

	private record ExperimentScenario(
			int order,
			String category,
			String name,
			ExperimentSpec spec,
			ExperimentSettings settings,
			double failureProbability) {
	}

	private record ExperimentResult(
			ExperimentScenario scenario,
			BatchStatus status,
			long elapsedMs,
			long completedItems,
			int executionCount,
			int failureCount,
			long processCpuMs,
			int completedReads,
			int peakConcurrentReadIo,
			int completedProcessorIo,
			int peakConcurrentProcessorIo,
			int completedWrites,
			int writtenItems,
			int peakConcurrentWriteIo) {

		static ExperimentResult median(ExperimentScenario scenario, List<ExperimentResult> samples) {
			if (samples.size() < 5) {
				throw new IllegalArgumentException("At least five measurement samples are required");
			}
			return new ExperimentResult(
					scenario,
					BatchStatus.COMPLETED,
					medianOf(samples, ExperimentResult::elapsedMs),
					medianOf(samples, ExperimentResult::completedItems),
					Math.toIntExact(medianOf(samples, ExperimentResult::executionCount)),
					Math.toIntExact(medianOf(samples, ExperimentResult::failureCount)),
					medianOf(samples, ExperimentResult::processCpuMs),
					Math.toIntExact(medianOf(samples, ExperimentResult::completedReads)),
					Math.toIntExact(medianOf(samples, ExperimentResult::peakConcurrentReadIo)),
					Math.toIntExact(medianOf(samples, ExperimentResult::completedProcessorIo)),
					Math.toIntExact(medianOf(samples, ExperimentResult::peakConcurrentProcessorIo)),
					Math.toIntExact(medianOf(samples, ExperimentResult::completedWrites)),
					Math.toIntExact(medianOf(samples, ExperimentResult::writtenItems)),
					Math.toIntExact(medianOf(samples, ExperimentResult::peakConcurrentWriteIo)));
		}

		private static long medianOf(List<ExperimentResult> samples,
				ToLongFunction<ExperimentResult> metric) {
			long[] values = samples.stream().mapToLong(metric).sorted().toArray();
			int middle = values.length / 2;
			if (values.length % 2 == 1) {
				return values[middle];
			}
			return Math.round((values[middle - 1] + values[middle]) / 2.0);
		}

		String formattedSummary() {
			ExperimentSpec spec = scenario.spec();
			ExperimentSettings settings = scenario.settings();
			StringBuilder summary = new StringBuilder()
					.append("\n[").append("%02d".formatted(scenario.order())).append("] ")
					.append(scenario.name()).append('\n')
					.append("spec=").append(spec)
					.append(", status=").append(status).append('\n')
					.append("items=").append(settings.itemCount())
					.append(", chunkSize=").append(settings.chunkSize())
					.append(", partitionConcurrency=").append(settings.partitionConcurrency())
					.append(", processorConcurrency=").append(settings.processorConcurrency()).append('\n')
					.append("warmupRuns=1, measurementRuns=").append(MEASUREMENT_RUNS)
					.append(", aggregation=median").append('\n')
					.append("elapsedMs=").append(elapsedMs)
					.append(", throughputPerSec=").append(throughputPerSecond())
					.append(", completedItems=").append(completedItems)
					.append(", jobExecutions=").append(executionCount).append('\n');
			summary.append("processCpuMs=").append(processCpuMs).append('\n');

			if (spec.processorType() == ProcessorType.CPU_HEAVY) {
				summary.append("cpuWorkIterations=").append(spec.processorType().workIterations()).append('\n');
			}
			if (usesConfiguredDelay(spec)) {
				summary.append("configuredDelay=").append(settings.ioDelay()).append('\n');
			}

			if (spec.readerType() == ReaderType.SEQUENTIAL_IO
					|| spec.readerType() == ReaderType.PARTITION_RANGE_IO) {
				summary.append("readerIoCount=").append(completedReads)
						.append(", peakConcurrentReaderIo=").append(peakConcurrentReadIo).append('\n');
			}
			if (spec.processorType() == ProcessorType.SIMULATED_IO
					|| spec.processorType() == ProcessorType.PROBABLY_FAIL_IO) {
				summary.append("processorIoCount=").append(completedProcessorIo)
						.append(", peakConcurrentProcessorIo=").append(peakConcurrentProcessorIo).append('\n');
			}
			if (spec.processorType() == ProcessorType.PROBABLY_FAIL_IO) {
				summary.append("failureProbabilityPct=").append(scenario.failureProbability() * 100)
						.append(", simulatedFailures=").append(failureCount).append('\n');
			}
			if (spec.writerType() == WriterType.FIXED_LATENCY
					|| spec.writerType() == WriterType.SERIALIZED_BULK) {
				summary.append("writerIoCount=").append(completedWrites)
						.append(", writtenItems=").append(writtenItems)
						.append(", peakConcurrentWriterIo=").append(peakConcurrentWriteIo).append('\n');
			}
			return summary.toString();
		}

		private long throughputPerSecond() {
			return elapsedMs == 0 ? completedItems * 1_000 : completedItems * 1_000 / elapsedMs;
		}

		private boolean usesConfiguredDelay(ExperimentSpec spec) {
			return spec.readerType() == ReaderType.SEQUENTIAL_IO
					|| spec.readerType() == ReaderType.PARTITION_RANGE_IO
					|| spec.processorType() == ProcessorType.SIMULATED_IO
					|| spec.processorType() == ProcessorType.PROBABLY_FAIL_IO
					|| spec.writerType() == WriterType.FIXED_LATENCY
					|| spec.writerType() == WriterType.SERIALIZED_BULK;
		}

		String json() {
			ExperimentSettings settings = scenario.settings();
			return """
					    {
					      "order": %d,
					      "group": "%s",
					      "scenario": "%s",
					      "spec": "%s",
					      "series": "%s",
					      "status": "%s",
					      "warmupRuns": 1,
					      "measurementRuns": %d,
					      "aggregation": "median",
					      "parameters": {"itemCount": %d, "chunkSize": %d, "ioDelayMs": %d, "partitionConcurrency": %d, "processorConcurrency": %d, "failureProbabilityPct": %.4f},
					      "metrics": {"elapsedMs": %d, "throughputPerSec": %d, "completedItems": %d, "jobExecutions": %d, "simulatedFailures": %d, "processCpuMs": %d, "readerIoCount": %d, "peakConcurrentReaderIo": %d, "processorIoCount": %d, "peakConcurrentProcessorIo": %d, "writerIoCount": %d, "writtenItems": %d, "peakConcurrentWriterIo": %d}
					    }""".formatted(
					scenario.order(), scenario.category(), scenario.name(), scenario.spec(),
					seriesName(scenario), status, MEASUREMENT_RUNS,
					settings.itemCount(), settings.chunkSize(), settings.ioDelay().toMillis(),
					settings.partitionConcurrency(), settings.processorConcurrency(), scenario.failureProbability() * 100,
					elapsedMs, throughputPerSecond(), completedItems, executionCount, failureCount,
					processCpuMs,
					completedReads, peakConcurrentReadIo, completedProcessorIo, peakConcurrentProcessorIo,
					completedWrites, writtenItems, peakConcurrentWriteIo);
		}
	}
}
