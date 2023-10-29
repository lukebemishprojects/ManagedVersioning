package dev.lukebemish.managedversioning.git;

import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.ValueSource;
import org.gradle.api.provider.ValueSourceParameters;
import org.gradle.process.ExecOperations;
import org.jetbrains.annotations.Nullable;

import javax.inject.Inject;
import java.io.ByteArrayOutputStream;

public abstract class GitValueSource implements ValueSource<String, GitValueSource.Parameters> {

    private final ExecOperations execOperations;

    @Inject
    public GitValueSource(ExecOperations execOperations) {
        this.execOperations = execOperations;
    }

    @Nullable
    @Override
    public final String obtain() {
        var args = getParameters().getArgs().getOrNull();
        if (args == null) {
            return null;
        }
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();
        try {
            execOperations.exec(spec -> {
                spec.setExecutable("git");
                spec.setArgs(args);
                spec.setStandardOutput(out);
                spec.setErrorOutput(err);
                if (getParameters().getWorkingDir().isPresent()) {
                    spec.setWorkingDir(getParameters().getWorkingDir().get().getAsFile());
                }
            });
            return out.toString().trim();
        } catch (Exception e) {
            throw new RuntimeException("Failed to execute git command `git "+String.join(" ", args)+"`: " + err.toString().trim(), e);
        }
    }

    public interface Parameters extends ValueSourceParameters {
        ListProperty<String> getArgs();
        DirectoryProperty getWorkingDir();
    }
}
