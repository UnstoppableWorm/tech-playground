package batchasynclab;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
		"experiment.item-count=1",
		"experiment.io-delay=1ms"
})
class SpringBatchThreadEfficiencyApplicationTests {

	@Test
	void contextLoads() {
	}

}
