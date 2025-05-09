package dev.lukebemish.managedversioning;

import dev.lukebemish.managedversioning.actions.GitHubAction;
import dev.lukebemish.managedversioning.impl.GeneratedVersionDetails;
import dev.lukebemish.managedversioning.impl.SingleProjectAction;
import org.gradle.api.Action;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.file.BuildLayout;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.initialization.Settings;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.tasks.Optional;

import javax.inject.Inject;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

public abstract class ManagedVersioningExtension {
    private final ManagedVersioningPublishingExtension publishing;
    private final GeneratedVersionDetails generatedVersionDetails;

    public abstract RegularFileProperty getVersionFile();
    public abstract Property<String> getTimestampFormat();
    @Optional
    public abstract Property<String> getMetadataVersion();
    public abstract DirectoryProperty getGitWorkingDir();
    public abstract Property<String> getStagedChangesVersionSuffix();
    public abstract Property<String> getUnstagedChangesVersionSuffix();
    public abstract ListProperty<String> getSuffixParts();

    @Inject
    public ManagedVersioningExtension(Settings settings) {
        this.publishing = getObjects().newInstance(ManagedVersioningPublishingExtension.class, settings);
        this.getGitWorkingDir().convention(getLayout().getRootDirectory());
        this.getStagedChangesVersionSuffix().convention("dirty");
        this.getUnstagedChangesVersionSuffix().convention("dirty");
        this.getSuffixParts().convention(Collections.emptyList());

        this.generatedVersionDetails = GeneratedVersionDetails.make(
            getProviders(),
            getGitWorkingDir(),
            getVersionFile(),
            getMetadataVersion(),
            getStagedChangesVersionSuffix(),
            getUnstagedChangesVersionSuffix(),
            getSuffixParts()
        );

        settings.getGradle().getLifecycle().beforeProject(new SingleProjectAction(
            this.getGitWorkingDir(),
            this.getVersionFile(),
            this.getMetadataVersion(),
            this.getStagedChangesVersionSuffix(),
            this.getUnstagedChangesVersionSuffix(),
            this.getSuffixParts()
        ));
    }

    @Inject
    protected abstract ObjectFactory getObjects();

    @Inject
    protected abstract BuildLayout getLayout();

    @Inject
    protected abstract ProviderFactory getProviders();

    public ManagedVersioningPublishingExtension getPublishing() {
        return publishing;
    }

    public void publishing(Action<? super ManagedVersioningPublishingExtension> action) {
        action.execute(getPublishing());
    }

    public void gitHubActions(Action<NamedDomainObjectContainer<? super GitHubAction>> action) {
        githubActionsActions.add(action);
    }

    final List<Action<? super NamedDomainObjectContainer<GitHubAction>>> githubActionsActions = new ArrayList<>();

    public Provider<String> getVersion() {
        return generatedVersionDetails.version();
    }

    public Provider<String> getHash() {
        return generatedVersionDetails.gitHash();
    }

    public Provider<String> getTimestamp() {
        return getProviders().provider(() -> {
            var output = generatedVersionDetails.gitTimestamp().get();
            long timestamp = Long.parseLong(output) * 1000;
            DateFormat dateFormat = new SimpleDateFormat(getTimestampFormat().get());
            dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
            return dateFormat.format(new Date(timestamp));
        });
    }

    public Provider<Boolean> getTagUpToDate() {
        return generatedVersionDetails.getTagUpToDate();
    }

    public Provider<Boolean> getVersionUpToDate() {
        return generatedVersionDetails.getVersionUpToDate();
    }

    public Provider<Boolean> getUnstagedChanges() {
        return generatedVersionDetails.unstagedChanges();
    }

    public Provider<Boolean> getStagedChanges() {
        return generatedVersionDetails.stagedChanges();
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
