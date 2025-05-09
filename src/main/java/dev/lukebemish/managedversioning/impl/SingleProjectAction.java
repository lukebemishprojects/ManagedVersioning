package dev.lukebemish.managedversioning.impl;

import dev.lukebemish.managedversioning.ManagedVersioningProjectExtension;
import dev.lukebemish.managedversioning.RecordVersionTask;
import dev.lukebemish.managedversioning.TagReleaseTask;
import dev.lukebemish.managedversioning.UpdateVersioningTask;
import org.gradle.api.IsolatedAction;
import org.gradle.api.Project;
import org.gradle.api.file.Directory;
import org.gradle.api.file.RegularFile;
import org.gradle.api.provider.Provider;

import java.util.List;

public record SingleProjectAction(
    Provider<Directory> gitWorkingDir,
    Provider<RegularFile> versionFile,
    Provider<String> metadataVersion,
    Provider<String> stagedChangesVersionSuffix,
    Provider<String> unstagedChangesVersionSuffix,
    Provider<List<String>> suffixParts
) implements IsolatedAction<Project> {

    @Override
    public void execute(Project project) {
        var generated = GeneratedVersionDetails.make(
            project.getProviders(),
            gitWorkingDir,
            versionFile,
            metadataVersion,
            stagedChangesVersionSuffix,
            unstagedChangesVersionSuffix,
            suffixParts
        );

        project.setVersion(generated.version().get());

        project.getExtensions().create("managedVersioning", ManagedVersioningProjectExtension.class, project, generated.gitTimestamp(), generated.gitHash());

        if (project.equals(project.getRootProject())) {
            var updateVersioning = project.getTasks().register("updateVersioning", UpdateVersioningTask.class, task -> {
                task.getVersionFile().set(versionFile.get().getAsFile().getAbsolutePath());
                task.getUpToDate().set(generated.getVersionUpToDate());
                task.getGitWorkingDir().set(gitWorkingDir.map(d -> d.getAsFile().getPath()));
                task.getVersion().set(generated.version());
                task.getUpdatable().set(generated.unstagedChanges().zip(generated.stagedChanges(), (u, s) -> !u && !s));
            });
            Provider<String> toTagVersion = project.provider(() -> {
                var version = generated.version().get();
                if (metadataVersion.isPresent()) {
                    version += "-" + metadataVersion.get();
                }
                return version;
            });
            project.getTasks().register("tagRelease", TagReleaseTask.class, task -> {
                task.getVersion().set(toTagVersion);
                task.getUpToDate().set(generated.getTagUpToDate());
                task.getGitWorkingDir().set(gitWorkingDir.map(d -> d.getAsFile().getPath()));
                task.getUpdatable().set(generated.unstagedChanges().zip(generated.stagedChanges(), (u, s) -> !u && !s));
                task.dependsOn(updateVersioning);
            });
            project.getTasks().register("recordVersion", RecordVersionTask.class, task -> {
                task.getVersion().set(toTagVersion);
                task.getOutputFile().set(project.getLayout().getBuildDirectory().file("recordVersion.txt"));
            });
        }
    }
}
