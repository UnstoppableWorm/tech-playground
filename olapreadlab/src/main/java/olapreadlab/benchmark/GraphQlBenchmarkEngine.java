package olapreadlab.benchmark;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

import olapreadlab.aggregation.model.QueryMode;
import olapreadlab.benchmark.BenchmarkReport.Configuration;
import olapreadlab.benchmark.BenchmarkReport.ModeResult;
import olapreadlab.experiment.BenchmarkSuitePlan.Window;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.ObjectMapper;

final class GraphQlBenchmarkEngine {

	private static final String QUERY = """
			query Benchmark($input: OlapQueryInput!) {
			  olap(input: $input) {
			    rows {
			      bucket
			      dimensions { name value }
			      measures { name value }
			    }
			  }
			}
			""";

	private final BenchmarkSettings settings;
	private final ObjectMapper objectMapper;
	private final HttpClient httpClient;
	private final Clock clock;

	GraphQlBenchmarkEngine(BenchmarkSettings settings, ObjectMapper objectMapper) {
		this(settings, objectMapper, HttpClient.newBuilder()
				.connectTimeout(settings.requestTimeout())
				.build(), Clock.systemUTC());
	}

	GraphQlBenchmarkEngine(
			BenchmarkSettings settings,
			ObjectMapper objectMapper,
			HttpClient httpClient,
			Clock clock) {
		this.settings = settings;
		this.objectMapper = objectMapper;
		this.httpClient = httpClient;
		this.clock = clock;
	}

	BenchmarkReport run() {
		validateSettings();
		var cases = benchmarkCases();
		var requestBodies = requestBodies(cases);
		var startedAt = System.nanoTime();

		var warmups = repeatedCases(cases, settings.warmupCount());
		Collections.shuffle(warmups, new Random(settings.randomSeed()));
		for (var benchmarkCase : warmups) execute(benchmarkCase, requestBodies.get(benchmarkCase));

		var measurements = repeatedCases(cases, settings.measurementCount());
		Collections.shuffle(measurements, new Random(settings.randomSeed() + 1));
		var samples = new LinkedHashMap<BenchmarkCase, List<Sample>>();
		for (var benchmarkCase : measurements) {
			samples.computeIfAbsent(benchmarkCase, ignored -> new ArrayList<>())
					.add(execute(benchmarkCase, requestBodies.get(benchmarkCase)));
		}

		var consistencyErrors = consistencyErrors(cases, samples);
		var report = new BenchmarkReport(
				clock.instant(),
				configuration(),
				(System.nanoTime() - startedAt) / 1_000_000,
				consistencyErrors.isEmpty(),
				consistencyErrors,
				results(cases, samples));
		writeReport(report);
		if (!report.equivalent()) {
			throw new IllegalStateException("Benchmark responses differ: " + String.join("; ", consistencyErrors));
		}
		return report;
	}

	private Sample execute(BenchmarkCase benchmarkCase, String requestBody) {
		var request = HttpRequest.newBuilder(settings.endpoint())
				.timeout(settings.requestTimeout())
				.header("Content-Type", "application/json")
				.header("Accept", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
				.build();
		try {
			var startedAt = System.nanoTime();
			var response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
			if (response.statusCode() < 200 || response.statusCode() >= 300) {
				try (var body = response.body()) {
					throw new IllegalStateException("GraphQL HTTP " + response.statusCode() + ": "
							+ new String(body.readNBytes(4096), StandardCharsets.UTF_8));
				}
			}
			var evidence = consumeResponse(response.body());
			return new Sample(
					System.nanoTime() - startedAt,
					evidence.rowCount(), evidence.bytes(), evidence.sha256());
		}
		catch (IOException exception) {
			throw new IllegalStateException("Cannot read benchmark response for " + benchmarkCase, exception);
		}
		catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Benchmark was interrupted", exception);
		}
	}

	private ResponseEvidence consumeResponse(InputStream responseBody) throws IOException {
		var digest = sha256();
		var counting = new CountingInputStream(responseBody);
		long rowCount = -1;
		try (var input = new DigestInputStream(counting, digest);
				var parser = objectMapper.createParser(input)) {
			while (parser.nextToken() != null) {
				if (parser.currentToken() != JsonToken.PROPERTY_NAME) continue;
				if ("errors".equals(parser.currentName())) {
					parser.nextToken();
					var errors = objectMapper.readTree(parser);
					if (errors != null && !errors.isEmpty()) {
						throw new IllegalStateException("GraphQL returned errors: " + errors);
					}
				}
				else if ("rows".equals(parser.currentName())) {
					if (parser.nextToken() != JsonToken.START_ARRAY) {
						throw new IllegalStateException("GraphQL rows must be an array");
					}
					rowCount = 0;
					while (parser.nextToken() != JsonToken.END_ARRAY) {
						if (parser.currentToken() != JsonToken.START_OBJECT) {
							throw new IllegalStateException("GraphQL row must be an object");
						}
						rowCount++;
						parser.skipChildren();
					}
				}
			}
		}
		if (rowCount < 0) throw new IllegalStateException("GraphQL response does not contain rows");
		return new ResponseEvidence(rowCount, counting.count(), java.util.HexFormat.of().formatHex(digest.digest()));
	}

	private Map<BenchmarkCase, String> requestBodies(List<BenchmarkCase> cases) {
		var bodies = new HashMap<BenchmarkCase, String>();
		for (var benchmarkCase : cases) {
			var root = objectMapper.createObjectNode();
			root.put("query", QUERY);
			var input = root.putObject("variables").putObject("input");
			input.put("model", settings.model());
			input.put("view", settings.view());
			input.put("mode", benchmarkCase.mode().name());
			input.put("fromInclusive", benchmarkCase.fromInclusive().toString());
			input.put("toExclusive", benchmarkCase.toExclusive().toString());
			try {
				bodies.put(benchmarkCase, objectMapper.writeValueAsString(root));
			}
			catch (RuntimeException exception) {
				throw new IllegalStateException("Cannot serialize GraphQL benchmark request", exception);
			}
		}
		return bodies;
	}

	private List<BenchmarkCase> benchmarkCases() {
		var result = new ArrayList<BenchmarkCase>();
		for (var window : settings.windows()) {
			var toExclusive = endOf(window);
			for (var mode : settings.modes()) {
				result.add(new BenchmarkCase(window, mode, settings.fromInclusive(), toExclusive));
			}
		}
		return result;
	}

	private Instant endOf(Window window) {
		var from = settings.fromInclusive().atZone(ZoneOffset.UTC);
		var candidate = switch (window) {
			case ONE_DAY -> from.plusDays(1).toInstant();
			case ONE_MONTH -> from.plusMonths(1).toInstant();
			case ONE_YEAR -> from.plusYears(1).toInstant();
			case ALL -> settings.toExclusive();
		};
		return candidate.isAfter(settings.toExclusive()) ? settings.toExclusive() : candidate;
	}

	private List<BenchmarkCase> repeatedCases(List<BenchmarkCase> cases, int count) {
		var result = new ArrayList<BenchmarkCase>(cases.size() * count);
		for (int iteration = 0; iteration < count; iteration++) result.addAll(cases);
		return result;
	}

	private List<String> consistencyErrors(
			List<BenchmarkCase> cases,
			Map<BenchmarkCase, List<Sample>> samples) {
		var errors = new ArrayList<String>();
		var hashesByWindow = new EnumMap<Window, Map<QueryMode, String>>(Window.class);
		for (var benchmarkCase : cases) {
			var caseSamples = samples.getOrDefault(benchmarkCase, List.of());
			var signatures = caseSamples.stream()
					.map(sample -> sample.rowCount() + "|" + sample.bytes() + "|" + sample.sha256())
					.distinct().toList();
			if (signatures.size() != 1) {
				errors.add(benchmarkCase.window() + "/" + benchmarkCase.mode()
						+ " produced " + signatures.size() + " response signatures");
				continue;
			}
			hashesByWindow.computeIfAbsent(benchmarkCase.window(), ignored -> new EnumMap<>(QueryMode.class))
					.put(benchmarkCase.mode(), signatures.getFirst());
		}
		for (var entry : hashesByWindow.entrySet()) {
			if (entry.getValue().values().stream().distinct().count() != 1) {
				errors.add(entry.getKey() + " responses differ by query mode");
			}
		}
		return errors;
	}

	private List<ModeResult> results(
			List<BenchmarkCase> cases,
			Map<BenchmarkCase, List<Sample>> samples) {
		return cases.stream()
				.sorted(Comparator.comparing(BenchmarkCase::window).thenComparing(BenchmarkCase::mode))
				.map(benchmarkCase -> result(benchmarkCase, samples.get(benchmarkCase)))
				.toList();
	}

	private static ModeResult result(BenchmarkCase benchmarkCase, List<Sample> samples) {
		var sorted = samples.stream().map(Sample::durationNanos).sorted().toList();
		var first = samples.getFirst();
		return new ModeResult(
				benchmarkCase.window(), benchmarkCase.mode(),
				benchmarkCase.fromInclusive(), benchmarkCase.toExclusive(),
				samples.size(), first.rowCount(), first.bytes(), first.sha256(),
				BenchmarkStatistics.millis(sorted.getFirst()),
				BenchmarkStatistics.percentileMillis(sorted, 0.50),
				BenchmarkStatistics.percentileMillis(sorted, 0.95),
				BenchmarkStatistics.percentileMillis(sorted, 0.99),
				BenchmarkStatistics.millis(sorted.getLast()),
				BenchmarkStatistics.meanMillis(sorted),
				BenchmarkStatistics.requestsPerSecond(sorted),
				sorted.stream().map(BenchmarkStatistics::millis).toList());
	}

	private Configuration configuration() {
		return new Configuration(
				settings.endpoint().toString(), settings.model(), settings.view(),
				settings.fromInclusive(), settings.toExclusive(), settings.coveredUntil(),
				settings.sourceRowCount(), settings.clickHouseStrategy(), settings.clickHouseBlockSize(),
				settings.modes(), settings.windows(),
				settings.warmupCount(), settings.measurementCount(), settings.randomSeed(), "WARM");
	}

	private void writeReport(BenchmarkReport report) {
		var output = settings.output().toAbsolutePath().normalize();
		try {
			Files.createDirectories(output.getParent());
			objectMapper.writerWithDefaultPrettyPrinter().writeValue(output.toFile(), report);
			writeCsv(csvPath(output), report.results());
		}
		catch (IOException | RuntimeException exception) {
			throw new IllegalStateException("Cannot write benchmark report to " + output, exception);
		}
	}

	private static void writeCsv(Path output, List<ModeResult> results) throws IOException {
		try (var writer = Files.newBufferedWriter(output, StandardCharsets.UTF_8)) {
			writer.write("window,mode,from_inclusive,to_exclusive,samples,rows,response_bytes,min_ms,p50_ms,p95_ms,p99_ms,max_ms,mean_ms,requests_per_second,sha256\n");
			for (var result : results) {
				writer.write(String.format(Locale.ROOT,
						"%s,%s,%s,%s,%d,%d,%d,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%s%n",
						result.window(), result.mode(), result.fromInclusive(), result.toExclusive(),
						result.sampleCount(), result.rowCount(), result.responseBytes(),
						result.minMillis(), result.p50Millis(), result.p95Millis(), result.p99Millis(),
						result.maxMillis(), result.meanMillis(), result.requestsPerSecond(),
						result.responseSha256()));
			}
		}
	}

	private void validateSettings() {
		if (!settings.enabled()) throw new IllegalStateException("Benchmark runner is disabled");
		if (settings.endpoint() == null || settings.output() == null) {
			throw new IllegalArgumentException("benchmark.endpoint and benchmark.output are required");
		}
		if (settings.model() == null || settings.model().isBlank()
				|| settings.view() == null || settings.view().isBlank()) {
			throw new IllegalArgumentException("benchmark.model and benchmark.view are required");
		}
		if (settings.fromInclusive() == null || settings.toExclusive() == null
				|| !settings.fromInclusive().isBefore(settings.toExclusive())) {
			throw new IllegalArgumentException("Benchmark time range is invalid");
		}
		if (settings.coveredUntil() == null || !settings.fromInclusive().isBefore(settings.coveredUntil())
				|| !settings.coveredUntil().isBefore(settings.toExclusive())) {
			throw new IllegalArgumentException("benchmark.covered-until must be inside the query range");
		}
		if (settings.sourceRowCount() <= 0 || settings.clickHouseBlockSize() <= 0
				|| settings.clickHouseStrategy() == null || settings.clickHouseStrategy().isBlank()) {
			throw new IllegalArgumentException("Benchmark data preparation metadata is invalid");
		}
		if (settings.modes().isEmpty() || settings.modes().stream().distinct().count() != settings.modes().size()) {
			throw new IllegalArgumentException("benchmark.modes must contain unique values");
		}
		if (settings.windows().isEmpty()
				|| settings.windows().stream().distinct().count() != settings.windows().size()) {
			throw new IllegalArgumentException("benchmark.windows must contain unique values");
		}
		if (settings.warmupCount() < 0 || settings.measurementCount() <= 0) {
			throw new IllegalArgumentException("Benchmark iteration counts are invalid");
		}
		if (settings.requestTimeout() == null || settings.requestTimeout().isZero()
				|| settings.requestTimeout().isNegative()) {
			throw new IllegalArgumentException("benchmark.request-timeout must be positive");
		}
		for (var window : settings.windows()) {
			if (!settings.fromInclusive().isBefore(endOf(window))) {
				throw new IllegalArgumentException("Benchmark window has an empty range: " + window);
			}
		}
	}

	private static Path csvPath(Path jsonPath) {
		var fileName = jsonPath.getFileName().toString();
		var csvName = fileName.endsWith(".json")
				? fileName.substring(0, fileName.length() - 5) + ".csv"
				: fileName + ".csv";
		return jsonPath.resolveSibling(csvName);
	}

	private static MessageDigest sha256() {
		try {
			return MessageDigest.getInstance("SHA-256");
		}
		catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 is unavailable", exception);
		}
	}

	private record BenchmarkCase(
			Window window,
			QueryMode mode,
			Instant fromInclusive,
			Instant toExclusive) {
	}

	private record Sample(long durationNanos, long rowCount, long bytes, String sha256) {
	}

	private record ResponseEvidence(long rowCount, long bytes, String sha256) {
	}

	private static final class CountingInputStream extends FilterInputStream {

		private long count;

		private CountingInputStream(InputStream input) {
			super(input);
		}

		@Override
		public int read() throws IOException {
			var value = super.read();
			if (value >= 0) count++;
			return value;
		}

		@Override
		public int read(byte[] bytes, int offset, int length) throws IOException {
			var read = super.read(bytes, offset, length);
			if (read > 0) count += read;
			return read;
		}

		private long count() {
			return count;
		}
	}
}
