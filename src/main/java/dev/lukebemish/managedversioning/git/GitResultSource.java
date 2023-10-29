package dev.lukebemish.managedversioning.git;

import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.ValueSource;
import org.gradle.api.provider.ValueSourceParameters;
import org.gradle.process.ExecOperations;
import org.jetbrains.annotations.Nullable;

import javax.inject.Inject;
import java.io.IOException;
import java.io.OutputStream;

public abstract class GitResultSource implements ValueSource<Integer, GitResultSource.Parameters> {

    private final ExecOperations execOperations;

    @Inject
    public GitResultSource(ExecOperations execOperations) {
        this.execOperations = execOperations;
    }

    @Nullable
    @Override
    public final Integer obtain() {
        var args = getParameters().getArgs().getOrNull();
        if (args == null) {
            return null;
        }
        try (var out = OutputStream.nullOutputStream()) {
            var result = execOperations.exec(spec -> {
                spec.setExecutable("git");
                spec.setArgs(args);
                spec.setErrorOutput(out);
                spec.setStandardOutput(out);
                if (getParameters().getWorkingDir().isPresent()) {
                    spec.setWorkingDir(getParameters().getWorkingDir().get().getAsFile());
                }
                spec.setIgnoreExitValue(true);
            });
            return result.getExitValue();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public interface Parameters extends ValueSourceParameters {
        ListProperty<String> getArgs();
        DirectoryProperty getWorkingDir();
    }
}
