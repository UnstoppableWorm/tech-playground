package olapreadlab.benchmark;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.List;
import java.util.concurrent.Executors;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import olapreadlab.aggregation.model.QueryMode;
import olapreadlab.experiment.BenchmarkSuitePlan.Window;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

class GraphQlBenchmarkEngineTests {

	private static final byte[] RESPONSE = """
			{"data":{"olap":{"rows":[{"bucket":"2025-01-01T00:00:00Z","dimensions":[],"measures":[{"name":"eventCount","value":"1"}]}]}}}
			""".strip().getBytes(StandardCharsets.UTF_8);

	@TempDir
	Path temporaryDirectory;

	@Test
	void measuresEveryModeAndWritesEquivalentJsonAndCsvReports() throws Exception {
		var counts = new EnumMap<QueryMode, Integer>(QueryMode.class);
		var server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
		server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
		server.createContext("/graphql", exchange -> respond(exchange, counts));
		server.start();
		try {
			var output = temporaryDirectory.resolve("benchmark.json");
			var settings = new BenchmarkSettings(
					true,
					URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/graphql"),
					output,
					"medical-history",
					"person-organ-disease-daily",
					Instant.parse("2025-01-01T00:00:00Z"),
					Instant.parse("2025-01-03T00:00:00Z"),
					Instant.parse("2025-01-02T00:00:00Z"),
					1_000_000,
					"leaf",
					100_000,
					List.of(QueryMode.values()),
					List.of(Window.ONE_DAY),
					1,
					2,
					42,
					Duration.ofSeconds(5));
			var mapper = JsonMapper.builder().findAndAddModules().build();
			var engine = new GraphQlBenchmarkEngine(
					settings, mapper, HttpClient.newHttpClient(),
					Clock.fixed(Instant.parse("2026-09-07T00:00:00Z"), ZoneOffset.UTC));

			var report = engine.run();

			assertThat(report.equivalent()).isTrue();
			assertThat(report.results()).hasSize(3)
					.allSatisfy(result -> {
						assertThat(result.sampleCount()).isEqualTo(2);
						assertThat(result.rowCount()).isEqualTo(1);
						assertThat(result.responseBytes()).isEqualTo(RESPONSE.length);
					});
			assertThat(counts).containsOnly(
					org.assertj.core.api.Assertions.entry(QueryMode.POSTGRES_RAW, 3),
					org.assertj.core.api.Assertions.entry(QueryMode.POSTGRES_BATCH_HYBRID, 3),
					org.assertj.core.api.Assertions.entry(QueryMode.CLICKHOUSE_HYBRID, 3));
			assertThat(output).exists();
			assertThat(temporaryDirectory.resolve("benchmark.csv")).exists();
			assertThat(mapper.readTree(output.toFile()).path("equivalent").asBoolean()).isTrue();
		}
		finally {
			server.stop(0);
		}
	}

	private static void respond(HttpExchange exchange, EnumMap<QueryMode, Integer> counts) throws IOException {
		var request = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
		for (var mode : QueryMode.values()) {
			if (request.contains("\"mode\":\"" + mode + "\"")) counts.merge(mode, 1, Integer::sum);
		}
		exchange.getResponseHeaders().set("Content-Type", "application/json");
		exchange.sendResponseHeaders(200, RESPONSE.length);
		try (var response = exchange.getResponseBody()) {
			response.write(RESPONSE);
		}
	}
}
