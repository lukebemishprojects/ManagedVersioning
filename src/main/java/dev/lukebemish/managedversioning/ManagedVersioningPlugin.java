package dev.lukebemish.managedversioning;

import dev.lukebemish.managedversioning.actions.MakeActions;
import org.gradle.api.Plugin;
import org.gradle.api.initialization.Settings;
import org.gradle.process.ExecOperations;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayOutputStream;
import java.util.List;

public class ManagedVersioningPlugin implements Plugin<Settings> {
    @Override
    public void apply(@NotNull Settings settings) {
        ManagedVersioningExtension extension = settings.getExtensions().create("managedVersioning", ManagedVersioningExtension.class, settings);
        var githubActionsActions = extension.githubActionsActions;
        settings.getGradle().getLifecycle().beforeProject(project -> {
            if (project.equals(project.getRootProject())) {
                project.getTasks().register("makeActions", MakeActions.class, task -> {
                    task.getActionsDirectory().set(project.getLayout().getProjectDirectory().dir(".github").dir("workflows"));
                    for (var action : githubActionsActions) {
                        action.execute(task.getGitHubActions());
                    }
                    task.onlyIf(t -> !((MakeActions) t).getGitHubActions().isEmpty());
                });
            }
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
