package batchasynclab.experiment.job;

import batchasynclab.experiment.ExperimentSettings;
import batchasynclab.experiment.definition.ExperimentSpec;
import batchasynclab.experiment.step.ExperimentStepFactory;

import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.stereotype.Component;

@Component
public class ExperimentJobFactory {

	private final JobRepository repository;
	private final ExperimentStepFactory stepFactory;

	ExperimentJobFactory(JobRepository repository, ExperimentStepFactory stepFactory) {
		this.repository = repository;
		this.stepFactory = stepFactory;
	}

	public Job create(ExperimentSpec spec, ExperimentSettings settings) {
		return new JobBuilder(spec.jobName(), repository)
				.start(stepFactory.create(spec, settings))
				.build();
	}
}
