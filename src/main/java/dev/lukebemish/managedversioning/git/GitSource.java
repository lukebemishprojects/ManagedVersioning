package dev.lukebemish.managedversioning.git;

import org.gradle.api.provider.ValueSource;
import org.gradle.api.provider.ValueSourceParameters;
import org.gradle.process.ExecOperations;
import org.jetbrains.annotations.Nullable;

import javax.inject.Inject;
import java.io.ByteArrayOutputStream;
import java.util.List;

public abstract class GitSource<T, V extends ValueSourceParameters> implements ValueSource<T, V> {
    private final ExecOperations execOperations;

    @Inject
    public GitSource(ExecOperations execOperations) {
        this.execOperations = execOperations;
    }

    abstract @Nullable List<String> getArgs();
    abstract T transform(String output);

    @Nullable
    @Override
    public final T obtain() {
        if (getArgs() == null) {
            return null;
        }
        var out = new ByteArrayOutputStream();
        execOperations.exec(spec -> {
            spec.setExecutable("git");
            spec.setArgs(getArgs());
            spec.setStandardOutput(out);
        });
        return transform(out.toString().trim());
    }
}
