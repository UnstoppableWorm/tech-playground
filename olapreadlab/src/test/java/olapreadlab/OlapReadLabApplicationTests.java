package olapreadlab;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import olapreadlab.experiment.ExperimentSettings;

@SpringBootTest
class OlapReadLabApplicationTests {

	@Autowired
	private ExperimentSettings settings;

	@Test
	void contextLoads() {
		assertThat(settings.sourceRowCount()).isEqualTo(100_000_000L);
		assertThat(settings.warmupCount()).isEqualTo(3);
		assertThat(settings.measurementCount()).isEqualTo(10);
		assertThat(settings.correction().targetRowCount()).isEqualTo(100_000L);
	}
}
