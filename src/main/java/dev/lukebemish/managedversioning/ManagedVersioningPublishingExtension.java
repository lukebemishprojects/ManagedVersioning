package dev.lukebemish.managedversioning;

import io.github.gradlenexus.publishplugin.NexusPublishExtension;
import io.github.gradlenexus.publishplugin.NexusPublishPlugin;
import org.gradle.api.Action;
import org.gradle.api.initialization.Settings;

import javax.inject.Inject;
import java.net.URI;

public abstract class ManagedVersioningPublishingExtension {
    private final Settings settings;

    @Inject
    public ManagedVersioningPublishingExtension(Settings settings) {
        this.settings = settings;
    }

    public void mavenCentral() {
        settings.getGradle().getLifecycle().beforeProject(project -> {
            if (project.equals(project.getRootProject())) {
                // TODO: find or make better alternative to nexus publish plugin
                project.getPlugins().apply(NexusPublishPlugin.class);
                if (System.getenv(Constants.CENTRAL_USER) != null) {
                    project.getExtensions().configure(NexusPublishExtension.class, ext -> {
                        ext.repositories(container -> {
                            container.sonatype(it -> {
                                it.getUsername().set(System.getenv(Constants.CENTRAL_USER));
                                it.getPassword().set(System.getenv(Constants.CENTRAL_PASSWORD));
                                it.getNexusUrl().set(URI.create("https://ossrh-staging-api.central.sonatype.com/service/local/"));
                                it.getSnapshotRepositoryUrl().set(URI.create("https://central.sonatype.com/repository/maven-snapshots/"));
                            });
                        });
                    });
                }
            }
        });
    }

    private void configureProjectWise(Action<? super ManagedVersioningProjectExtension> action) {
        settings.getGradle().getLifecycle().beforeProject(project -> {
            var extension = project.getExtensions().getByType(ManagedVersioningProjectExtension.class);
            action.execute(extension);
        });
    }

    public void mavenSnapshot() {
        configureProjectWise(ManagedVersioningProjectExtension::mavenSnapshot);
    }

    public void mavenRelease() {
        configureProjectWise(ManagedVersioningProjectExtension::mavenRelease);
    }

    public void mavenStaging() {
        configureProjectWise(ManagedVersioningProjectExtension::mavenStaging);
    }

    public void mavenPullRequest() {
        configureProjectWise(ManagedVersioningProjectExtension::mavenPullRequest);
    }
}
