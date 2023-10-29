package dev.lukebemish.managedversioning;

import org.gradle.api.DefaultTask;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskAction;
import org.gradle.process.ExecOperations;

import javax.inject.Inject;
import java.io.FileWriter;
import java.util.List;

public abstract class UpdateVersioningTask extends DefaultTask {
    @Input
    public abstract Property<String> getVersionFile();
    @Input
    public abstract Property<String> getVersion();
    @Input
    public abstract Property<Boolean> getUpdatable();
    @Input
    public abstract Property<Boolean> getUpToDate();
    @Input
    public abstract Property<String> getGitWorkingDir();

    private final ExecOperations execOperations;

    @Inject
    public UpdateVersioningTask(ExecOperations operations) {
        this.execOperations = operations;
        getOutputs().upToDateWhen(task -> getUpToDate().get() || !getUpdatable().get());
    }

    @TaskAction
    public void updateVersioning() {
        if (getUpToDate().get() || !getUpdatable().get()) {
            return;
        }
        try (var writer = new FileWriter(getVersionFile().get())) {
            writer.write("version=");
            writer.write(getVersion().get());
            writer.write("\n");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        ManagedVersioningPlugin.smartExec(
            execOperations,
            "git",
            List.of("add", getVersionFile().get()),
            getGitWorkingDir().get()
        );
        ManagedVersioningPlugin.smartExec(
            execOperations,
            "git",
            List.of("commit", "-m", "Bump version to "+getVersion().get()),
            getGitWorkingDir().get()
        );
    }
}
