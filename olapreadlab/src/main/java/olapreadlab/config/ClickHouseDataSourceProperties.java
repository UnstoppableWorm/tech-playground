package olapreadlab.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("clickhouse.datasource")
public record ClickHouseDataSourceProperties(
		String url,
		String username,
		String password
) {
}
