package dev.lukebemish.managedversioning.actions;

import dev.lukebemish.managedversioning.Constants;
import org.gradle.api.Action;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;

import javax.inject.Inject;

public abstract class TestReportJob {
    @Input
    public abstract Property<String> getName();
    @Input
    public abstract ListProperty<Step> getSteps();

    @Inject
    protected abstract ObjectFactory getObjects();

    @Inject
    public TestReportJob() {}

    Job create() {
        Job job = getObjects().newInstance(Job.class);
        job.getName().set(getName().get());
        job.getPermissions().put("contents", "read");
        job.getPermissions().put("actions", "read");
        job.getPermissions().put("checks", "write");
        getSteps().get().forEach(step -> job.getSteps().add(step));
        return job;
    }

    public Step step(Action<Step> action) {
        Step step = getObjects().newInstance(Step.class);
        action.execute(step);
        this.getSteps().add(step);
        return step;
    }

    public Step junit(String name, String path, String artifact) {
        return step(step -> {
            step.getName().set(name.isBlank() ? "JUnit Test Report" : "JUnit Test Report - " + name);
            step.getUses().set(Constants.Versions.TEST_REPORTER);
            step.getWith().put("name", name.isBlank() ? "Test Results" : "Test Results - " + name);
            step.getWith().put("artifact", artifact);
            step.getWith().put("path", path);
            step.getWith().put("reporter", "java-junit");
            step.getWith().put("fail-on-error", "true");
            step.getWith().put("list-tests", "true");
        });
    }
}
