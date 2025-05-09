package dev.lukebemish.managedversioning.impl;

import dev.lukebemish.managedversioning.VersionFileSource;
import dev.lukebemish.managedversioning.VersionValueSource;
import dev.lukebemish.managedversioning.git.GitResultSource;
import dev.lukebemish.managedversioning.git.GitValueSource;
import org.gradle.api.file.Directory;
import org.gradle.api.file.RegularFile;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;

import java.util.List;

public record GeneratedVersionDetails(
    Provider<String> gitHash,
    Provider<String> tagHash,
    Provider<String> gitTimestamp,
    Provider<String> fromFile,
    Provider<Boolean> stagedChanges,
    Provider<Boolean> unstagedChanges,
    Provider<String> version
) {
    public Provider<Boolean> getTagUpToDate() {
        return getVersionUpToDate().zip(tagHash().zip(gitHash(), String::equals), Boolean::logicalAnd);
    }

    public Provider<Boolean> getVersionUpToDate() {
        return version().zip(fromFile(), String::equals);
    }

    public static GeneratedVersionDetails make(
        ProviderFactory providers,
        Provider<Directory> gitWorkingDir,
        Provider<RegularFile> versionFile,
        Provider<String> metadataVersion,
        Provider<String> stagedChangesVersionSuffix,
        Provider<String> unstagedChangesVersionSuffix,
        Provider<List<String>> suffixParts
    ) {
        var gitHash = providers.of(GitValueSource.class, spec -> {
            spec.getParameters().getArgs().set(List.of("rev-parse", "HEAD"));
            spec.getParameters().getWorkingDir().set(gitWorkingDir);
        });
        var tagName = providers.of(GitValueSource.class, spec -> {
            spec.getParameters().getArgs().set(List.of("describe", "--tags", "--abbrev=0", "--always"));
            spec.getParameters().getWorkingDir().set(gitWorkingDir);
        });
        var tagHash = providers.of(GitValueSource.class, spec -> {
            spec.getParameters().getArgs().set(List.of("rev-list", "-n", "1"));
            spec.getParameters().getArgs().add(tagName);
            spec.getParameters().getWorkingDir().set(gitWorkingDir);
        }).zip(gitHash, (s, g) -> s.equals(g) ? "" : s);
        var gitTimestamp = providers.of(GitValueSource.class, spec -> {
            spec.getParameters().getArgs().set(List.of("log", "-1", "--format=%at"));
            spec.getParameters().getWorkingDir().set(gitWorkingDir);
        });
        var fromFile = providers.of(VersionFileSource.class, spec -> {
            spec.getParameters().getVersionFile().set(versionFile);
        });

        var stagedChanges = providers.of(GitResultSource.class, spec -> {
            spec.getParameters().getArgs().set(List.of("diff-index", "--quiet", "--cached", "HEAD", "--"));
            spec.getParameters().getWorkingDir().set(gitWorkingDir);
        }).map(i -> i == 1);

        var untracked = providers.of(GitValueSource.class, spec -> {
            spec.getParameters().getArgs().set(List.of("ls-files", "--exclude-standard", "--others"));
            spec.getParameters().getWorkingDir().set(gitWorkingDir);
        });

        var tracked = providers.of(GitResultSource.class, spec -> {
            spec.getParameters().getArgs().set(List.of("diff-files", "--quiet"));
            spec.getParameters().getWorkingDir().set(gitWorkingDir);
        });

        var unstagedChanges = tracked.zip(untracked, (t, u) -> t == 1 || !u.isBlank());

        var version = providers.of(VersionValueSource.class, spec -> {
            var ps = spec.getParameters();
            ps.getCommitHash().set(gitHash);
            ps.getHasMetadata().set(metadataVersion.map(s -> true).orElse(false));
            ps.getStagedChangesVersionSuffix().set(stagedChangesVersionSuffix);
            ps.getUnstagedChangesVersionSuffix().set(unstagedChangesVersionSuffix);
            ps.getSuffixParts().set(suffixParts);
            ps.getFromFile().set(fromFile);
            ps.getWorkingDir().set(gitWorkingDir);
            ps.getTagHash().set(tagHash);
            ps.getStagedChanges().set(stagedChanges);
            ps.getUnstagedChanges().set(unstagedChanges);
        });

        return new GeneratedVersionDetails(gitHash, tagHash, gitTimestamp, fromFile, stagedChanges, unstagedChanges, version);
    }
}
