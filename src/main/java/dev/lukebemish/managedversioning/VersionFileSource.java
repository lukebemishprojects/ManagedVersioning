package dev.lukebemish.managedversioning;

import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ValueSource;
import org.gradle.api.provider.ValueSourceParameters;
import org.jetbrains.annotations.Nullable;

import java.io.FileReader;
import java.io.IOException;
import java.util.Properties;

public abstract class VersionFileSource implements ValueSource<String, VersionFileSource.Parameters> {
    @Nullable
    @Override
    public String obtain() {
        if (!getParameters().getVersionFile().isPresent()) {
            throw new RuntimeException("Version file not specified");
        }
        var file = getParameters().getVersionFile().get().getAsFile();
        try (var reader = new FileReader(file)) {
            var properties = new Properties();
            properties.load(reader);
            return properties.getProperty("version");
        } catch (IOException e) {
            throw new RuntimeException("Failed to read version file: " + file, e);
        }
    }

    public interface Parameters extends ValueSourceParameters {
        RegularFileProperty getVersionFile();
    }
}
