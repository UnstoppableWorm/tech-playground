package olapreadlab.config;

import org.springframework.batch.core.configuration.support.JdbcDefaultBatchConfiguration;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class JdbcBatchConfiguration extends JdbcDefaultBatchConfiguration {
}
