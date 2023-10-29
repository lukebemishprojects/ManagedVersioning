package dev.lukebemish.managedversioning;

import org.gradle.api.DefaultTask;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskAction;

import java.util.List;

public abstract class TagReleaseTask extends DefaultTask {
    @Input
    public abstract Property<String> getVersion();
    @Input
    public abstract Property<Boolean> getUpToDate();
    @Input
    public abstract Property<Boolean> getUpdatable();
    @Input
    public abstract Property<String> getGitWorkingDir();

    public TagReleaseTask() {
        getOutputs().upToDateWhen(task -> getUpToDate().get() || !getUpdatable().get());
    }

    @TaskAction
    public void tagRelease() {
        if (getUpToDate().get() || !getUpdatable().get()) {
            return;
        }
        ManagedVersioningPlugin.smartExec(
            getProject(),
            "git",
            List.of("tag", getVersion().get()),
            getGitWorkingDir().get()
        );
    }
}
