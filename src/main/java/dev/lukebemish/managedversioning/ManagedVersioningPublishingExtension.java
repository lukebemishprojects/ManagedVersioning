package dev.lukebemish.managedversioning;

import dev.lukebemish.centralportalpublishing.CentralPortalProjectExtension;
import org.gradle.api.Action;
import org.gradle.api.initialization.Settings;

import javax.inject.Inject;

public abstract class ManagedVersioningPublishingExtension {
    private final Settings settings;

    @Inject
    public ManagedVersioningPublishingExtension(Settings settings) {
        this.settings = settings;
    }

    public void mavenCentralMakeBundle() {
        settings.getGradle().getLifecycle().beforeProject(project -> {
            if (project.equals(project.getRootProject())) {
                project.getPlugins().apply("dev.lukebemish.central-portal-publishing");
                if (System.getenv(Constants.CENTRAL_USER) != null) {
                    project.getExtensions().getByType(CentralPortalProjectExtension.class).bundle("central", spec -> {
                        spec.getPublishingType().set("AUTOMATIC");
                        spec.getUsername().set(System.getenv(Constants.CENTRAL_USER));
                        spec.getPassword().set(System.getenv(Constants.CENTRAL_PASSWORD));
                    });
                }
            }
        });
    }

    public void mavenCentral() {
        mavenCentralMakeBundle();
        configureProjectWise(ManagedVersioningProjectExtension::mavenCentralUseBundle);
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
