package dev.lukebemish.managedversioning.actions;

import groovy.json.JsonOutput;
import org.gradle.api.DefaultTask;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.UntrackedTask;

import java.io.IOException;
import java.nio.file.Files;

@UntrackedTask(because = "Regenerates GitHub Actions")
public abstract class MakeActions extends DefaultTask {
    @OutputDirectory
    public abstract DirectoryProperty getActionsDirectory();

    @Nested
    public abstract NamedDomainObjectContainer<GitHubAction> getGitHubActions();

    @TaskAction
    public void execute() {
        var actionsDirectory = this.getActionsDirectory().get().getAsFile();
        getGitHubActions().forEach(action -> {
            String name = action.getName();
            var actionFile = actionsDirectory.getAbsoluteFile().toPath().resolve(name+".yml");
            try {
                Files.createDirectories(actionFile.getParent());
                String json = JsonOutput.prettyPrint(JsonOutput.toJson(action.resolve()));
                Files.writeString(actionFile, json);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }
}
