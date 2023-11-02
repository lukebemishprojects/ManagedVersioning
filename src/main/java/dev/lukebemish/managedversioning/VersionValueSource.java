package dev.lukebemish.managedversioning;

import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.ValueSource;
import org.gradle.api.provider.ValueSourceParameters;
import org.gradle.api.tasks.Optional;
import org.gradle.process.ExecOperations;

import javax.inject.Inject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

public abstract class VersionValueSource implements ValueSource<String, VersionValueSource.Parameters> {
    private final ExecOperations execOperations;

    @Inject
    public VersionValueSource(ExecOperations execOperations) {
        this.execOperations = execOperations;
    }

    @Override
    public final String obtain() {
        var fileVersion = getParameters().getFromFile().get();
        var lastTagHash = getParameters().getTagHash().get();
        String lastTagVersion = "";
        if (!lastTagHash.isBlank()) {
            lastTagVersion = value(List.of("describe", "--tags", lastTagHash));
        }
        String version;

        if (getParameters().getHasMetadata().getOrElse(false)) {
            var parts = lastTagVersion.split("-");
            if (parts.length > 1) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < parts.length - 1; i++) {
                    if (i > 0) {
                        sb.append("-");
                    }
                    sb.append(parts[i]);
                }
                lastTagVersion = sb.toString();
            }
        }
        version = fileVersion;

        if (fileVersion.equals(lastTagVersion)) {
            if (!lastTagHash.equals(getParameters().getCommitHash().get())) {
                var noMetaParts = version.split("-");
                var noBuildParts = noMetaParts[0].split("\\+");
                var mainParts = noBuildParts[0].split("\\.");
                mainParts[mainParts.length-1] = Integer.toString(Integer.parseInt(mainParts[mainParts.length-1])+1);
                noBuildParts[0] = String.join(".", mainParts);
                noMetaParts[0] = String.join("+", noBuildParts);
                version = String.join("-", noMetaParts);
            }
        }

        if (getParameters().getSuffix().isPresent() && !fileVersion.endsWith("-"+getParameters().getSuffix().get())) {
            version += "-" + getParameters().getSuffix().get();
        }

        boolean unstagedChanges = getParameters().getUnstagedChanges().get();
        boolean stagedChanges = getParameters().getStagedChanges().get();

        if (unstagedChanges) {
            version += "-" + getParameters().getUnstagedChangesVersionSuffix().get();
        } else if (stagedChanges) {
            version += "-" + getParameters().getStagedChangesVersionSuffix().get();
        }

        return version;
    }

    private String value(List<String> args) {
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

    private Integer result(List<String> args) {
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
        DirectoryProperty getWorkingDir();
        Property<Boolean> getHasMetadata();
        Property<String> getStagedChangesVersionSuffix();
        Property<String> getUnstagedChangesVersionSuffix();
        Property<Boolean> getStagedChanges();
        Property<Boolean> getUnstagedChanges();
        Property<String> getCommitHash();
        @Optional
        Property<String> getSuffix();
        Property<String> getFromFile();
        Property<String> getTagHash();
    }
}
