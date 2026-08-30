package olapreadlab.config;

import javax.sql.DataSource;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

@Configuration(proxyBeanMethods = false)
class PostgresJdbcConfiguration {

	@Bean("postgresJdbcTemplate")
	@Primary
	NamedParameterJdbcTemplate postgresJdbcTemplate(DataSource dataSource) {
		var jdbcTemplate = new NamedParameterJdbcTemplate(dataSource);
		jdbcTemplate.getJdbcTemplate().setFetchSize(10_000);
		return jdbcTemplate;
	}
}
