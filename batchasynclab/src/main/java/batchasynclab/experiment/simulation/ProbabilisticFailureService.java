package batchasynclab.experiment.simulation;

import java.util.SplittableRandom;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.stereotype.Component;

@Component
public class ProbabilisticFailureService {

	private SplittableRandom random = new SplittableRandom(0);
	private double failureProbability;
	private final AtomicInteger failures = new AtomicInteger();

	public synchronized void reset(long seed, double failureProbability) {
		if (failureProbability < 0 || failureProbability > 1) {
			throw new IllegalArgumentException("Failure probability must be between 0 and 1");
		}
		random = new SplittableRandom(seed);
		this.failureProbability = failureProbability;
		failures.set(0);
	}

	public synchronized void failProbably() {
		if (random.nextDouble() < failureProbability) {
			failures.incrementAndGet();
			throw new ProbableIoFailureException();
		}
	}

	public int failures() {
		return failures.get();
	}

	private static final class ProbableIoFailureException extends RuntimeException {
		private ProbableIoFailureException() {
			super("Simulated probabilistic I/O failure");
		}
	}
}
