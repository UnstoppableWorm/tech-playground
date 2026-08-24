package batchasynclab.experiment.simulation;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

import org.springframework.stereotype.Component;

@Component
public class SimulatedIoService {

	private final AtomicInteger activeRequests = new AtomicInteger();
	private final AtomicInteger peakConcurrentRequests = new AtomicInteger();
	private final AtomicInteger completedRequests = new AtomicInteger();
	private final AtomicInteger activeReads = new AtomicInteger();
	private final AtomicInteger peakConcurrentReads = new AtomicInteger();
	private final AtomicInteger completedReads = new AtomicInteger();
	private final AtomicInteger activeWrites = new AtomicInteger();
	private final AtomicInteger peakConcurrentWrites = new AtomicInteger();
	private final AtomicInteger completedWrites = new AtomicInteger();
	private final AtomicInteger writtenItems = new AtomicInteger();
	private final ReentrantLock serializedWriteLock = new ReentrantLock();

	public Integer requestAndWait(Integer item, Duration delay) throws InterruptedException {
		int active = activeRequests.incrementAndGet();
		peakConcurrentRequests.accumulateAndGet(active, Math::max);
		try {
			Thread.sleep(delay);
			completedRequests.incrementAndGet();
			return item;
		} finally {
			activeRequests.decrementAndGet();
		}
	}

	public Integer readAndWait(Integer item, Duration delay) throws InterruptedException {
		int active = activeReads.incrementAndGet();
		peakConcurrentReads.accumulateAndGet(active, Math::max);
		try {
			Thread.sleep(delay);
			completedReads.incrementAndGet();
			return item;
		} finally {
			activeReads.decrementAndGet();
		}
	}

	public void writeAndWait(int itemCount, Duration delay) throws InterruptedException {
		int active = activeWrites.incrementAndGet();
		peakConcurrentWrites.accumulateAndGet(active, Math::max);
		try {
			Thread.sleep(delay);
			completedWrites.incrementAndGet();
			writtenItems.addAndGet(itemCount);
		} finally {
			activeWrites.decrementAndGet();
		}
	}

	public void writeSeriallyAndWait(int itemCount, Duration delay) throws InterruptedException {
		serializedWriteLock.lockInterruptibly();
		try {
			writeAndWait(itemCount, delay);
		} finally {
			serializedWriteLock.unlock();
		}
	}

	public int peakConcurrentRequests() {
		return peakConcurrentRequests.get();
	}

	public int completedRequests() {
		return completedRequests.get();
	}

	public int peakConcurrentReads() {
		return peakConcurrentReads.get();
	}

	public int completedReads() {
		return completedReads.get();
	}

	public int peakConcurrentWrites() {
		return peakConcurrentWrites.get();
	}

	public int completedWrites() {
		return completedWrites.get();
	}

	public int writtenItems() {
		return writtenItems.get();
	}

	public void resetMetrics() {
		activeRequests.set(0);
		peakConcurrentRequests.set(0);
		completedRequests.set(0);
		activeReads.set(0);
		peakConcurrentReads.set(0);
		completedReads.set(0);
		activeWrites.set(0);
		peakConcurrentWrites.set(0);
		completedWrites.set(0);
		writtenItems.set(0);
	}
}
