package dev.lukebemish.managedversioning.actions;

import dev.lukebemish.managedversioning.Constants;
import org.gradle.api.Action;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;

import javax.inject.Inject;
import java.util.HashMap;
import java.util.Map;

public abstract class Step {
    @Optional
    @Input
    public abstract Property<String> getName();
    @Optional
    @Input
    public abstract Property<String> getId();
    @Optional
    @Input
    public abstract Property<String> getUses();
    @Optional
    @Input
    public abstract Property<String> getRun();
    @Input
    public abstract MapProperty<String, String> getEnv();
    @Input
    public abstract MapProperty<String, Object> getWith();
    @Input
    public abstract Property<Boolean> getRunsOnError();
    @Input
    public abstract ListProperty<String> getRequiredSteps();

    private final ObjectFactory objectFactory;

    @Inject
    public Step(ObjectFactory objectFactory) {
        this.objectFactory = objectFactory;
        this.getRunsOnError().convention(false);
    }

    public void runsWith(Step step) {
        this.runsWith(step.getId().get());
    }

    public void runsWith(String id) {
        this.getRunsOnError().set(true);
        this.getRequiredSteps().add(id);
    }

    Object resolve() {
        Map<String, Object> step = new HashMap<>();
        if (this.getName().isPresent()) {
            step.put("name", this.getName().get());
        }
        if (this.getId().isPresent()) {
            step.put("id", this.getId().get());
        }
        if (this.getUses().isPresent()) {
            step.put("uses", this.getUses().get());
        }
        if (this.getRun().isPresent()) {
            step.put("run", this.getRun().get());
        }
        if (this.getRunsOnError().get()) {
            StringBuilder outIf = new StringBuilder("(success() || failure())");
            for (String requiredStep : this.getRequiredSteps().get()) {
                outIf.append(" && steps.").append(requiredStep).append(".outcome == 'success'");
            }
            step.put("if", outIf.toString());
        }
        if (this.getEnv().isPresent()) {
            var env = this.getEnv().get();
            if (!env.isEmpty()) {
                step.put("env", env);
            }
        }
        if (this.getWith().isPresent()) {
            var with = this.getWith().get();
            if (!with.isEmpty()) {
                step.put("with", with);
            }
        }
        return step;
    }

    public Step configure(Action<Step> action) {
        action.execute(this);
        return this;
    }

    public void secret(String alias, String name) {
        getEnv().put(alias, "${{ secrets."+name+" }}");
    }

    public void secret(String name) {
        secret(name, name);
    }

    public void uploadArtifact(String name, String path) {
        if (getUses().isPresent() || getRun().isPresent()) {
            throw new RuntimeException("Cannot upload artifact when using 'uses' or 'run'");
        }
        this.getUses().set(Constants.Versions.UPLOAD_ARTIFACT);
        this.getWith().put("name", name);
        this.getWith().put("path", path);
    }

    public void setupGitUser() {
        if (getUses().isPresent() || getRun().isPresent()) {
            throw new RuntimeException("Cannot setup git user when using 'uses' or 'run'");
        }
        this.getUses().set("fregante/setup-git-user@v2");
    }
}
