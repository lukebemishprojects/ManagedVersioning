package dev.lukebemish.managedversioning;

import dev.lukebemish.managedversioning.actions.GitHubAction;
import dev.lukebemish.managedversioning.git.GitResultSource;
import dev.lukebemish.managedversioning.git.GitValueSource;
import org.gradle.api.Action;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.Project;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Optional;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

public abstract class ManagedVersioningExtension {
    private final Provider<String> gitHash;
    private final Provider<String> tagHash;
    private final Provider<String> gitTimestamp;
    private final Provider<String> version;
    private final Provider<String> fromFile;
    private final Provider<Boolean> stagedChanges;
    private final Provider<Boolean> unstagedChanges;
    private final Project project;
    private final ManagedPublishingExtension publishing;

    public abstract RegularFileProperty getVersionFile();
    public abstract Property<String> getTimestampFormat();
    @Optional
    public abstract Property<String> getMetadataVersion();
    public abstract DirectoryProperty getGitWorkingDir();
    public abstract Property<String> getStagedChangesVersionSuffix();
    public abstract Property<String> getUnstagedChangesVersionSuffix();
    public abstract ListProperty<String> getSuffixParts();

    public ManagedVersioningExtension(Project project) {
        this.project = project;
        this.publishing = project.getObjects().newInstance(ManagedPublishingExtension.class, project);
        this.getGitWorkingDir().convention(project.getLayout().getProjectDirectory());
        this.getStagedChangesVersionSuffix().convention("dirty");
        this.getUnstagedChangesVersionSuffix().convention("dirty");
        this.getSuffixParts().convention(Collections.emptyList());
        this.gitHash = project.getProviders().of(GitValueSource.class, spec -> {
            spec.getParameters().getArgs().set(List.of("rev-parse", "HEAD"));
            spec.getParameters().getWorkingDir().set(this.getGitWorkingDir());
        });
        var tagName = project.getProviders().of(GitValueSource.class, spec -> {
            spec.getParameters().getArgs().set(List.of("describe", "--tags", "--abbrev=0", "--always"));
            spec.getParameters().getWorkingDir().set(this.getGitWorkingDir());
        });
        this.tagHash = project.getProviders().of(GitValueSource.class, spec -> {
            spec.getParameters().getArgs().set(List.of("rev-list", "-n", "1"));
            spec.getParameters().getArgs().add(tagName);
            spec.getParameters().getWorkingDir().set(this.getGitWorkingDir());
        });
        this.getTimestampFormat().convention("yyyy.MM.dd-HH.mm.ss");
        this.gitTimestamp = project.getProviders().of(GitValueSource.class, spec -> {
            spec.getParameters().getArgs().set(List.of("log", "-1", "--format=%at"));
            spec.getParameters().getWorkingDir().set(this.getGitWorkingDir());
        });
        this.fromFile = project.getProviders().of(VersionFileSource.class, spec -> {
            spec.getParameters().getVersionFile().set(this.getVersionFile());
        });
        this.stagedChanges = project.getProviders().of(GitResultSource.class, spec -> {
            spec.getParameters().getArgs().set(List.of("diff-index", "--quiet", "--cached", "HEAD", "--"));
            spec.getParameters().getWorkingDir().set(this.getGitWorkingDir());
        }).map(i -> i == 1);
        var tracked = project.getProviders().of(GitResultSource.class, spec -> {
            spec.getParameters().getArgs().set(List.of("diff-files", "--quiet"));
            spec.getParameters().getWorkingDir().set(this.getGitWorkingDir());
        });
        var untracked = project.getProviders().of(GitValueSource.class, spec -> {
            spec.getParameters().getArgs().set(List.of("ls-files", "--exclude-standard", "--others"));
            spec.getParameters().getWorkingDir().set(this.getGitWorkingDir());
        });
        this.unstagedChanges = project.provider(() -> tracked.get() == 1 || !untracked.get().isBlank());
        this.version = project.getProviders().of(VersionValueSource.class, spec -> {
            var ps = spec.getParameters();
            ps.getCommitHash().set(this.gitHash);
            ps.getHasMetadata().set(this.getMetadataVersion().map(s->true).orElse(false));
            ps.getStagedChangesVersionSuffix().set(this.getStagedChangesVersionSuffix());
            ps.getUnstagedChangesVersionSuffix().set(this.getUnstagedChangesVersionSuffix());
            ps.getSuffixParts().set(this.getSuffixParts());
            ps.getFromFile().set(this.fromFile);
            ps.getWorkingDir().set(this.getGitWorkingDir());
            ps.getTagHash().set(this.tagHash);
            ps.getStagedChanges().set(this.stagedChanges);
            ps.getUnstagedChanges().set(this.unstagedChanges);
        });
    }

    public ManagedPublishingExtension getPublishing() {
        return publishing;
    }

    public void gitHubActions(Action<NamedDomainObjectContainer<GitHubAction>> action) {
        action.execute(getGitHubActions());
    }
    public abstract NamedDomainObjectContainer<GitHubAction> getGitHubActions();

    public void apply() {
        project.setVersion(version.get());
    }

    public Provider<String> getVersion() {
        return version;
    }

    public Provider<String> getHash() {
        return gitHash;
    }

    public Provider<String> getTimestamp() {
        return project.provider(() -> {
            var output = gitTimestamp.get();
            long timestamp = Long.parseLong(output) * 1000;
            DateFormat dateFormat = new SimpleDateFormat(getTimestampFormat().get());
            dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
            return dateFormat.format(new Date(timestamp));
        });
    }

    public Provider<Boolean> getTagUpToDate() {
        return project.provider(() -> getVersionUpToDate().get() && tagHash.get().equals(gitHash.get()));
    }

    public Provider<Boolean> getVersionUpToDate() {
        return project.provider(() -> version.get().equals(fromFile.get()));
    }

    public Provider<Boolean> getUnstagedChanges() {
        return unstagedChanges;
    }

    public Provider<Boolean> getStagedChanges() {
        return stagedChanges;
    }

    public void versionPRs() {
        if (System.getenv(Constants.PR_NUMBER) != null) {
            getSuffixParts().add("pr" + System.getenv(Constants.PR_NUMBER));
        }
    }

    public void versionSnapshots() {
        if (System.getenv(Constants.SNAPSHOT_MAVEN_URL) != null) {
            getSuffixParts().add("SNAPSHOT");
        }
    }
}
