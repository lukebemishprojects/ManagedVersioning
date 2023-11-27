package dev.lukebemish.managedversioning;

import org.gradle.api.DefaultTask;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.UntrackedTask;
import org.gradle.process.ExecOperations;

import javax.inject.Inject;
import java.util.List;

@UntrackedTask(because = "Tagging release should not be tracked")
public abstract class TagReleaseTask extends DefaultTask {
    @Input
    public abstract Property<String> getVersion();
    @Input
    public abstract Property<Boolean> getUpToDate();
    @Input
    public abstract Property<Boolean> getUpdatable();
    @Input
    public abstract Property<String> getGitWorkingDir();

    private final ExecOperations execOperations;

    @Inject
    public TagReleaseTask(ExecOperations operations) {
        this.execOperations = operations;
        getOutputs().upToDateWhen(task -> getUpToDate().get() || !getUpdatable().get());
    }

    @TaskAction
    public void tagRelease() {
        if (getUpToDate().get() || !getUpdatable().get()) {
            return;
        }
        ManagedVersioningPlugin.smartExec(
            execOperations,
            "git",
            List.of("tag", getVersion().get()),
            getGitWorkingDir().get()
        );
    }
}
