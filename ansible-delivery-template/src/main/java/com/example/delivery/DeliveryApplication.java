package com.example.delivery;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

public final class DeliveryApplication {
    private static final Set<String> SUPPORTED_COUNTRIES = Set.of("kr", "eu", "na");
    private static final Set<String> SUPPORTED_ENVIRONMENTS = Set.of("stg", "prd");

    private DeliveryApplication() {
    }

    public static void main(String[] args) throws Exception {
        DeploymentContext context = deploymentContext(System.getenv());
        boolean smokeTest = hasArgument(args, "--smoke-test");
        int port = smokeTest ? 0 : configuredPort(System.getenv());
        HttpServer server = createServer(port, context);
        server.start();

        if (smokeTest) {
            try {
                verifyHealthEndpoint(server.getAddress().getPort(), context);
            } finally {
                server.stop(0);
            }
            return;
        }

        CountDownLatch shutdown = new CountDownLatch(1);
        Runtime.getRuntime().addShutdownHook(new Thread(shutdown::countDown));
        shutdown.await();
    }

    static DeploymentContext deploymentContext(Map<String, String> environment) {
        String country = requiredEnvironment(environment, "APP_COUNTRY");
        String deploymentEnvironment = requiredEnvironment(environment, "APP_ENV");
        validateDeployment(country, deploymentEnvironment);
        return new DeploymentContext(country, deploymentEnvironment);
    }

    static void validateDeployment(String country, String deploymentEnvironment) {
        if (!SUPPORTED_COUNTRIES.contains(country)) {
            throw new IllegalArgumentException("APP_COUNTRY must be one of " + SUPPORTED_COUNTRIES + ".");
        }
        if (!SUPPORTED_ENVIRONMENTS.contains(deploymentEnvironment)) {
            throw new IllegalArgumentException("APP_ENV must be one of " + SUPPORTED_ENVIRONMENTS + ".");
        }
    }

    static String healthPayload(DeploymentContext context) {
        return "{\"status\":\"UP\",\"country\":\"%s\",\"environment\":\"%s\"}"
                .formatted(context.country(), context.environment());
    }

    private static HttpServer createServer(int port, DeploymentContext context) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 0);
        server.createContext("/actuator/health", exchange -> respondHealth(exchange, context));
        return server;
    }

    private static void respondHealth(HttpExchange exchange, DeploymentContext context) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, -1);
            return;
        }

        byte[] body = healthPayload(context).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(body);
        }
    }

    private static void verifyHealthEndpoint(int port, DeploymentContext context) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + "/actuator/health"))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();
        HttpResponse<String> response = HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200 || !response.body().equals(healthPayload(context))) {
            throw new IllegalStateException("Packaged service health check did not return the expected response.");
        }
    }

    private static int configuredPort(Map<String, String> environment) {
        String rawPort = environment.getOrDefault("SERVER_PORT", "8080");
        try {
            int port = Integer.parseInt(rawPort);
            if (port < 1 || port > 65535) {
                throw new NumberFormatException("outside the TCP port range");
            }
            return port;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("SERVER_PORT must be a valid TCP port.", exception);
        }
    }

    private static String requiredEnvironment(Map<String, String> environment, String name) {
        String value = environment.get(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required.");
        }
        return value.toLowerCase(Locale.ROOT);
    }

    private static boolean hasArgument(String[] args, String expected) {
        return Arrays.stream(args).anyMatch(expected::equals);
    }

    record DeploymentContext(String country, String environment) {
    }
}
