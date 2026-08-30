package olapreadlab.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

@Configuration(proxyBeanMethods = false)
class ClickHouseJdbcConfiguration {

	@Bean("clickHouseJdbcTemplate")
	NamedParameterJdbcTemplate clickHouseJdbcTemplate(ClickHouseDataSourceProperties properties) {
		var dataSource = new DriverManagerDataSource(properties.url(), properties.username(), properties.password());
		return new NamedParameterJdbcTemplate(dataSource);
	}
}
