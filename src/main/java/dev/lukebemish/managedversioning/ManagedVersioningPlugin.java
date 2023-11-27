package dev.lukebemish.managedversioning;

import dev.lukebemish.managedversioning.actions.MakeActions;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.process.ExecOperations;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.List;

public class ManagedVersioningPlugin implements Plugin<Project> {
    @Override
    public void apply(@NotNull Project project) {
        ManagedVersioningExtension extension = project.getExtensions().create("managedVersioning", ManagedVersioningExtension.class);
        var updateVersioning = project.getTasks().register("updateVersioning", UpdateVersioningTask.class, task -> {
            task.getVersionFile().set(extension.getVersionFile().get().getAsFile().getAbsolutePath());
            task.getUpToDate().set(extension.getVersionUpToDate());
            task.getGitWorkingDir().set(extension.getGitWorkingDir().getAsFile().map(File::getPath));
            task.getVersion().set(extension.getVersion());
            task.getUpdatable().set(project.provider(() -> !extension.getUnstagedChanges().get() && !extension.getStagedChanges().get()));
        });
        project.getTasks().register("tagRelease", TagReleaseTask.class, task -> {
            task.getVersion().set(project.provider(() -> {
                var version = extension.getVersion().get();
                if (extension.getMetadataVersion().isPresent()) {
                    version += "-" + extension.getMetadataVersion().get();
                }
                return version;
            }));
            task.getUpToDate().set(extension.getTagUpToDate());
            task.getGitWorkingDir().set(extension.getGitWorkingDir().getAsFile().map(File::getPath));
            task.getUpdatable().set(project.provider(() -> !extension.getUnstagedChanges().get() && !extension.getStagedChanges().get()));
            task.dependsOn(updateVersioning);
        });
        project.getTasks().register("makeActions", MakeActions.class, task -> {
            task.getActionsDirectory().set(project.getLayout().getProjectDirectory().dir(".github").dir("workflows"));
            task.getGitHubActions().addAllLater(project.provider(extension::getGitHubActions));
        });
    }

    @SuppressWarnings({"UnusedReturnValue", "SameParameterValue"})
    static String smartExec(ExecOperations operations, String command, List<String> args, Object workingDir) {
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();
        try {
            operations.exec(spec -> {
                spec.setStandardOutput(out);
                spec.setErrorOutput(err);
                spec.setExecutable(command);
                spec.setArgs(args);
                spec.setWorkingDir(workingDir);
            });
            return out.toString().trim();
        } catch (Exception e) {
            throw new RuntimeException("Failed to execute command: `"+command+" "+String.join(" ", args)+"`: " + err.toString().trim(), e);
        }
    }
}
