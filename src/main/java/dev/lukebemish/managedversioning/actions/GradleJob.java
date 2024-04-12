package dev.lukebemish.managedversioning.actions;

import dev.lukebemish.managedversioning.Constants;
import org.gradle.api.Action;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.*;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;

import javax.inject.Inject;
import java.util.*;

public abstract class GradleJob extends Job {
    @Input
    public abstract Property<String> getName();
    @Input
    public abstract Property<String> getJavaVersion();
    @Input
    public abstract Property<Boolean> getReadOnly();
    @Input
    public abstract Property<Boolean> getCacheReadOnly();
    @Input
    public abstract ListProperty<String> getCachePaths();
    @Input
    public abstract ListProperty<Step> getSteps();
    @Input
    public abstract MapProperty<String, String> getGradleEnv();
    @Input
    public abstract MapProperty<String, String> getOutputs();
    @Input
    public abstract ListProperty<String> getNeeds();

    @Input
    @Optional
    public abstract Property<String> getTag();

    private final ObjectFactory objectFactory;

    @Inject
    protected abstract ProviderFactory getProviders();

    @Inject
    public GradleJob(ObjectFactory objectFactory) {
        super(objectFactory);
        this.objectFactory = objectFactory;
        getJavaVersion().convention("17");
        getReadOnly().convention(true);
        getCachePaths().add("**/.gradle/loom-cache");
        getCachePaths().add("**/.gradle/quilt-loom-cache");

        this.getCacheReadOnly().convention(getReadOnly());

        this.setup();
    }

    public Step step(Action<Step> action) {
        Step step = objectFactory.newInstance(Step.class, objectFactory);
        action.execute(step);
        this.getSteps().add(step);
        return step;
    }

    public Step dependencySubmission(Action<DependencySubmission> action) {
        DependencySubmission dependencySubmission = objectFactory.newInstance(DependencySubmission.class);
        action.execute(dependencySubmission);
        return step(step -> {
            step.getName().set("Submit Dependencies");
            step.getEnv().putAll(getGradleEnv());
            step.getUses().set(Constants.Versions.DEPENDENCY_SUBMISSION);
            step.getWith().put("gradle-build-module", String.join("\n", dependencySubmission.getBuildModules().get()));
            step.getWith().put("gradle-build-configuration", dependencySubmission.getBuildConfiguration().get());
            step.getWith().put("sub-module-mode", dependencySubmission.getSubModuleMode().get());
            step.getWith().put("include-build-environment", dependencySubmission.getIncludeBuildEnvironment().get());
        });
    }

    void setup() {
        Provider<List<Step>> earlyStepProvider = getProviders().provider(() -> {
            List<Step> earlySteps = new ArrayList<>();
            earlySteps.add(configureStep(step -> {
                step.getName().set("Setup Java");
                step.getRun().set("echo \"JAVA_HOME=$JAVA_HOME_" + getJavaVersion().get() + "_X64\" >> \"$GITHUB_ENV\"");
            }));
            earlySteps.add(configureStep(step -> {
                step.getName().set("Checkout");
                step.getUses().set(Constants.Versions.CHECKOUT);
                step.getWith().put("fetch-depth", "0");
                if (getTag().isPresent()) {
                    step.getWith().put("ref", "refs/tags/"+getTag().get());
                }
                if (getReadOnly().get()) {
                    step.getWith().put("persist-credentials", "false");
                }
            }));
            earlySteps.add(configureStep(step -> {
                step.getName().set("Validate Gradle Wrapper");
                step.getUses().set(Constants.Versions.WRAPPER_VALIDATION);
            }));
            if (!getCachePaths().get().isEmpty()) {
                earlySteps.add(configureStep(step -> {
                    step.getName().set("Cache");
                    if (getCacheReadOnly().get()) {
                        step.getUses().set(Constants.Versions.CACHE_RESTORE);
                    } else {
                        step.getUses().set(Constants.Versions.CACHE_BOTH);
                    }
                    step.getWith().put("path", String.join("\n", getCachePaths().get()));
                    step.getWith().put("key", "${{ runner.os }}-gradle-${{ hashFiles('**/libs.versions.*', '**/*.gradle*', '**/gradle-wrapper.properties') }}");
                    step.getWith().put("restore-keys", "${{ runner.os }}-gradle-");
                }));
            }
            earlySteps.add(configureStep(step -> {
                step.getName().set("Setup Gradle");
                step.getUses().set(Constants.Versions.GRADLE);
                if (getCacheReadOnly().get()) {
                    step.getWith().put("cache-read-only", true);
                }
                step.getWith().put("gradle-home-cache-cleanup", true);
            }));
            return earlySteps;
        });
        getSteps().addAll(earlyStepProvider);
        this.getPermissions().putAll(getProviders().provider(() -> {
            Map<String, String> permissions = new HashMap<>();
            if (!getReadOnly().get()) {
                permissions.put("contents", "write");
            }
            return permissions;
        }));
    }

    public Step gradlew(String name, List<String> args, Action<Step> action) {
        Step step = gradlew(name, args);
        action.execute(step);
        return step;
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

    public void secrets(String... names) {
        for (String name : names) {
            secret(name);
        }
    }

    public void pullRequestArtifact() {
        getGradleEnv().put(Constants.PR_NUMBER, "${{ github.event.pull_request.number }}");
        step(step -> {
            step.getName().set("Archive Publishable Artifacts");
            step.getUses().set(Constants.Versions.UPLOAD_ARTIFACT);
            step.getWith().put("name", "artifacts");
            step.getWith().put("path", "build/repo");
        });
    }

    public void upload(String name, List<String> paths, Action<Step> action) {
        step(step -> {
            step.getName().set("Upload "+name);
            step.getUses().set(Constants.Versions.UPLOAD_ARTIFACT);
            step.getWith().put("name", name);
            step.getWith().put("path", String.join("\n", paths));
            action.execute(step);
        });
    }

    public void recordVersion(String name, String outputName) {
        gradlew(name, "recordVersion");
        String captureId = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "_")+"_capture_version";
        step(step -> {
            step.getName().set("Capture Recorded Version");
            step.getId().set(captureId);
            step.getRun().set("echo version=$(cat build/recordVersion.txt) >> \"$GITHUB_OUTPUT\"");
        });
        getOutputs().put(outputName, "${{ steps."+captureId+".outputs.version }}");
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
