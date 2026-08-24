package batchasynclab.experiment.item.processor;

public enum ProcessorType {
	CPU_HEAVY(2_000_000),
	SIMULATED_IO(0),
	PROBABLY_FAIL_IO(0),
	PASS_THROUGH(0);

	private final int workIterations;

	ProcessorType(int workIterations) {
		this.workIterations = workIterations;
	}

	public int workIterations() {
		return workIterations;
	}
}
