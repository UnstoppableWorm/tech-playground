package batchasynclab.experiment.step;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.annotation.PreDestroy;

import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

@Component
class ExperimentTaskExecutorFactory {

	private final Map<PoolKey, ThreadPoolTaskExecutor> platformExecutors = new ConcurrentHashMap<>();

	AsyncTaskExecutor createPlatform(String role, int concurrency) {
		return platformExecutors.computeIfAbsent(new PoolKey(role, concurrency), this::fixedThreadPool);
	}

	AsyncTaskExecutor createVirtual(String role, int concurrency) {
		SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor(role + "-virtual-");
		executor.setConcurrencyLimit(concurrency);
		executor.setVirtualThreads(true);
		return executor;
	}

	private ThreadPoolTaskExecutor fixedThreadPool(PoolKey key) {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setThreadNamePrefix(key.role() + "-platform-");
		executor.setCorePoolSize(key.concurrency());
		executor.setMaxPoolSize(key.concurrency());
		executor.setWaitForTasksToCompleteOnShutdown(true);
		executor.initialize();
		return executor;
	}

	@PreDestroy
	void shutdown() {
		platformExecutors.values().forEach(ThreadPoolTaskExecutor::shutdown);
	}

	private record PoolKey(String role, int concurrency) {
	}
}
