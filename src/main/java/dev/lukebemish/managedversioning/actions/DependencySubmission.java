package dev.lukebemish.managedversioning.actions;

import org.gradle.api.Project;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;

import java.util.Collection;

public abstract class DependencySubmission {
    public abstract ListProperty<String> getBuildModules();
    public abstract Property<String> getBuildConfiguration();
    public abstract Property<String> getSubModuleMode();
    public abstract Property<Boolean> getIncludeBuildEnvironment();

    public DependencySubmission() {
        getBuildConfiguration().convention("compileClasspath");
        getSubModuleMode().convention("INDIVIDUAL_DEEP");
        getIncludeBuildEnvironment().convention(true);
    }

    public void project(Project project) {
        getBuildModules().add(project.getPath());
    }

    public void projects(Collection<Project> projects) {
        projects.forEach(project -> {
            getBuildModules().add(project.getPath());
        });
    }
}
