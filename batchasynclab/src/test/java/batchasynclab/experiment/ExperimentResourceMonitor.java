package batchasynclab.experiment;

import java.lang.management.ManagementFactory;

final class ExperimentResourceMonitor {

	private final com.sun.management.OperatingSystemMXBean operatingSystemBean =
			(com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
	private final long startedProcessCpuNanos;

	private ExperimentResourceMonitor() {
		this.startedProcessCpuNanos = operatingSystemBean.getProcessCpuTime();
	}

	static ExperimentResourceMonitor start() {
		return new ExperimentResourceMonitor();
	}

	ResourceMetrics stop() {
		long processCpuNanos = Math.max(0, operatingSystemBean.getProcessCpuTime() - startedProcessCpuNanos);
		return new ResourceMetrics(processCpuNanos / 1_000_000);
	}

	record ResourceMetrics(long processCpuMs) {
	}
}
