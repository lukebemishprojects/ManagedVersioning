package dev.lukebemish.managedversioning;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public abstract class RecordVersionTask extends DefaultTask {
    @OutputFile
    public abstract RegularFileProperty getOutputFile();

    @Input
    public abstract Property<String> getVersion();

    @TaskAction
    public void recordVersion() {
        Path path = getOutputFile().get().getAsFile().toPath();
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, getVersion().get(), StandardOpenOption.WRITE, StandardOpenOption.CREATE);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
