package dev.lukebemish.managedversioning.actions;

import dev.lukebemish.managedversioning.Constants;
import org.gradle.api.Action;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;

import javax.inject.Inject;
import java.util.List;
import java.util.Locale;

public abstract class GradleJob {
    @Input
    public abstract Property<String> getName();
    @Input
    public abstract Property<String> getJavaVersion();
    @Input
    public abstract Property<Boolean> getReadOnly();
    @Input
    public abstract ListProperty<String> getCachePaths();
    @Input
    public abstract ListProperty<Step> getSteps();
    @Input
    public abstract MapProperty<String, String> getGradleEnv();

    private final ObjectFactory objectFactory;
    @Inject
    public GradleJob(ObjectFactory objectFactory) {
        this.objectFactory = objectFactory;
        getJavaVersion().convention("17");
        getReadOnly().convention(true);
        getCachePaths().add("**/.gradle/loom-cache");
    }

    public Step step(Action<Step> action) {
        Step step = objectFactory.newInstance(Step.class, objectFactory);
        action.execute(step);
        this.getSteps().add(step);
        return step;
    }

    Job create() {
        Job job = objectFactory.newInstance(Job.class, objectFactory);
        boolean readOnly = getReadOnly().get();
        job.getName().set(getName().get());
        if (!readOnly) {
            job.getPermissions().put("contents", "write");
        }
        job.step(step -> {
            step.getName().set("Setup Java");
            step.getRun().set("echo \"JAVA_HOME=$JAVA_HOME_" + getJavaVersion().get() + "_X64\" >> \"$GITHUB_ENV\"");
        });
        job.step(step -> {
            step.getName().set("Checkout");
            step.getUses().set("actions/checkout@v4");
            step.getWith().put("fetch-depth", "0");
            if (readOnly) {
                step.getWith().put("persist-credentials", "false");
            }
        });
        if (!getCachePaths().get().isEmpty()) {
            job.step(step -> {
                step.getName().set("Cache");
                if (readOnly) {
                    step.getUses().set("actions/cache/restore@v3");
                } else {
                    step.getUses().set("actions/cache@v3");
                }
                step.getWith().put("path", String.join("\n", getCachePaths().get()));
                step.getWith().put("key", "${{ runner.os }}-gradle-${{ hashFiles('**/libs.versions.*', '**/*.gradle*', '**/gradle-wrapper.properties') }}");
                step.getWith().put("restore-keys", "${{ runner.os }}-gradle-");
            });
        }
        job.step(step -> {
            step.getName().set("Setup Gradle");
            step.getUses().set("gradle/gradle-build-action@v2");
            if (readOnly) {
                step.getWith().put("cache-read-only", true);
            }
            step.getWith().put("gradle-home-cache-cleanup", true);
        });
        getSteps().get().forEach(step -> job.getSteps().add(step));
        return job;
    }

    public Step gradlew(String name, List<String> args) {
        return step(step -> {
            step.getName().set(name);
            step.getId().set(name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "_"));
            step.getEnv().putAll(getGradleEnv());
            step.getRun().set("./gradlew " + String.join(" ", args));
        });
    }

    public Step gradlew(String name, String... args) {
        return gradlew(name, List.of(args));
    }

    public void secret(String alias, String name) {
        getGradleEnv().put(alias, "${{ secrets."+name+" }}");
    }

    public void secret(String name) {
        secret(name, name);
    }

    public void pullRequestArtifact() {
        getGradleEnv().put(Constants.PR_NUMBER, "${{ github.event.pull_request.number }}");
        step(step -> {
            step.getName().set("Archive Publishable Artifacts");
            step.getUses().set("actions/upload-artifact@v3");
            step.getWith().put("name", "artifacts");
            step.getWith().put("path", "build/repo");
        });
    }

    public void mavenSnapshot(String user) {
        secret(Constants.SNAPSHOT_MAVEN_PASSWORD);
        getGradleEnv().put(Constants.SNAPSHOT_MAVEN_USER, user);
        getGradleEnv().put(Constants.SNAPSHOT_MAVEN_URL, "https://maven.lukebemish.dev/snapshots/");
    }

    public void mavenRelease(String user) {
        secret(Constants.RELEASE_MAVEN_PASSWORD);
        getGradleEnv().put(Constants.RELEASE_MAVEN_USER, user);
        getGradleEnv().put(Constants.RELEASE_MAVEN_URL, "https://maven.lukebemish.dev/releases/");
    }

    public void mavenCentral() {
        secret(Constants.CENTRAL_PASSWORD);
        secret(Constants.CENTRAL_USER);
    }

    public void pluginPortal() {
        secret("GRADLE_PUBLISH_KEY", "GRADLE_PLUGIN_KEY");
        secret("GRADLE_PUBLISH_SECRET", "GRADLE_PLUGIN_SECRET");
    }

    public void sign() {
        secret(Constants.GPG_KEY);
        secret(Constants.GPG_PASSWORD);
    }

    public void buildCache() {
        secret(Constants.BUILD_CACHE_PASSWORD);
        secret(Constants.BUILD_CACHE_USER);
        secret(Constants.BUILD_CACHE_URL);
    }

    public void modPublishing() {
        secret(Constants.CURSEFORGE_KEY);
        secret(Constants.MODRINTH_KEY);
    }
}
