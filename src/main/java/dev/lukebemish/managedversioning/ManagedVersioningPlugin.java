package dev.lukebemish.managedversioning;

import dev.lukebemish.managedversioning.actions.MakeActions;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.process.ExecOperations;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayOutputStream;
import java.util.List;

public class ManagedVersioningPlugin implements Plugin<Project> {
    @Override
    public void apply(@NotNull Project project) {
        ManagedVersioningExtension extension = project.getExtensions().create("managedVersioning", ManagedVersioningExtension.class);
        project.getTasks().register("makeActions", MakeActions.class, task -> {
            task.getActionsDirectory().set(project.getLayout().getProjectDirectory().dir(".github").dir("workflows"));
            task.getGitHubActions().addAllLater(project.provider(extension::getGitHubActions));
            task.onlyIf(t -> !extension.getGitHubActions().isEmpty());
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
