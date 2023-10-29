package dev.lukebemish.managedversioning.test

import groovy.transform.CompileStatic
import org.gradle.api.provider.Property
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import org.gradle.process.ExecOperations

import javax.inject.Inject

@CompileStatic
abstract class TestRepositorySource implements ValueSource<String, Parameters> {
    ExecOperations execOperations

    @Inject
    TestRepositorySource(ExecOperations execOperations) {
        this.execOperations = execOperations
    }

    @Override
    String obtain() {
        String path = parameters.projectPath.get()+'/build/repository'
        File directory = new File(path)
        if (!directory.exists()) {
            execOperations.exec {
                it.commandLine 'mkdir', '-p', path
            }
            execOperations.exec {
                it.commandLine 'git', 'init', path
            }
            new File(path+'/version.properties').write('version=0.1.0')
            execOperations.exec {
                it.commandLine 'git', '-C', path, 'add', 'version.properties'
            }
            execOperations.exec {
                it.commandLine 'git', '-C', path, '-c', 'commit.gpgsign=false', 'commit', '-m', '"Initial commit"'
            }
        }
        return path
    }

    interface Parameters extends ValueSourceParameters {
        Property<String> getProjectPath();
    }
}
