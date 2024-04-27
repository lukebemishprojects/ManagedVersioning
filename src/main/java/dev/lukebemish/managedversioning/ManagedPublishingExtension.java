package dev.lukebemish.managedversioning;

import io.github.gradlenexus.publishplugin.NexusPublishExtension;
import io.github.gradlenexus.publishplugin.NexusPublishPlugin;
import org.gradle.api.Project;
import org.gradle.api.publish.Publication;
import org.gradle.api.publish.PublishingExtension;
import org.gradle.api.publish.maven.MavenPublication;
import org.gradle.plugins.signing.SigningExtension;

import javax.inject.Inject;
import java.net.URI;
import java.net.URISyntaxException;

public abstract class ManagedPublishingExtension {

    private final Project project;
    @Inject
    public ManagedPublishingExtension(Project project) {
        this.project = project;
        this.licenses = new Licenses();
    }

    public void mavenCentral() {
        if (project.getRootProject() != project) {
            throw new UnsupportedOperationException("The nexus publishing plugin must be applied to the root project!");
        }

        project.getPlugins().apply(NexusPublishPlugin.class);
        if (System.getenv(Constants.CENTRAL_USER) != null) {
            project.getExtensions().configure(NexusPublishExtension.class, ext -> {
                ext.repositories(container -> {
                    container.sonatype(it -> {
                        it.getUsername().set(System.getenv(Constants.CENTRAL_USER));
                        it.getPassword().set(System.getenv(Constants.CENTRAL_PASSWORD));
                        it.getNexusUrl().set(URI.create("https://s01.oss.sonatype.org/service/local/"));
                    });
                });
            });
        }
    }

    public void mavenSnapshot(PublishingExtension publishing) {
        if (System.getenv(Constants.SNAPSHOT_MAVEN_URL) != null) {
            publishing.repositories(repositories -> {
                repositories.maven(maven -> {
                    maven.setName("PersonalMaven");
                    try {
                        maven.setUrl(new URI(System.getenv(Constants.SNAPSHOT_MAVEN_URL)));
                    } catch (URISyntaxException e) {
                        throw new RuntimeException(e);
                    }
                    maven.credentials(cred -> {
                        cred.setUsername(System.getenv(Constants.SNAPSHOT_MAVEN_USER));
                        cred.setPassword(System.getenv(Constants.SNAPSHOT_MAVEN_PASSWORD));
                    });
                });
            });
        }
    }

    public void mavenRelease(PublishingExtension publishing) {
        if (System.getenv(Constants.RELEASE_MAVEN_URL) != null) {
            publishing.repositories(repositories -> {
                repositories.maven(maven -> {
                    maven.setName("PersonalMaven");
                    try {
                        maven.setUrl(new URI(System.getenv(Constants.RELEASE_MAVEN_URL)));
                    } catch (URISyntaxException e) {
                        throw new RuntimeException(e);
                    }
                    maven.credentials(cred -> {
                        cred.setUsername(System.getenv(Constants.RELEASE_MAVEN_USER));
                        cred.setPassword(System.getenv(Constants.RELEASE_MAVEN_PASSWORD));
                    });
                });
            });
        }
    }

    public void mavenStaging(PublishingExtension publishing) {
        if (System.getenv(Constants.STAGING_MAVEN_URL) != null) {
            publishing.repositories(repositories -> {
                repositories.maven(maven -> {
                    maven.setName("StagingMaven");
                    try {
                        maven.setUrl(new URI(System.getenv(Constants.STAGING_MAVEN_URL)));
                    } catch (URISyntaxException e) {
                        throw new RuntimeException(e);
                    }
                    maven.credentials(cred -> {
                        cred.setUsername(System.getenv(Constants.STAGING_MAVEN_USER));
                        cred.setPassword(System.getenv(Constants.STAGING_MAVEN_PASSWORD));
                    });
                });
            });
        }
    }

    public void mavenPullRequest(PublishingExtension publishing) {
        if (System.getenv(Constants.PR_NUMBER) != null) {
            publishing.repositories(repositories -> {
                repositories.maven(maven -> {
                    maven.setName("LocalMaven");
                    maven.setUrl(project.getRootProject().getLayout().getBuildDirectory().dir("repo"));
                });
            });
        }
    }

    public void sign(SigningExtension signing, Publication... publications) {
        if (System.getenv(Constants.GPG_KEY) != null) {
            signing.useInMemoryPgpKeys(System.getenv(Constants.GPG_KEY), System.getenv(Constants.GPG_PASSWORD));
            for (Publication publication : publications) {
                signing.sign(publication);
            }
        }
    }

    public void sign(SigningExtension signing, PublishingExtension publishing) {
        if (System.getenv(Constants.GPG_KEY) != null) {
            signing.useInMemoryPgpKeys(System.getenv(Constants.GPG_KEY), System.getenv(Constants.GPG_PASSWORD));
            publishing.getPublications().all(signing::sign);
        }
    }

    public static class Licenses {
        protected Licenses() {}

        public final String bsd3 = "BSD-3-Clause";
        public final String lgpl3 = "LGPL-3.0-or-later";
    }

    public final Licenses licenses;

    public void pom(Publication publication, String repo, String license) {
        if (!(publication instanceof MavenPublication maven)) {
            throw new IllegalArgumentException("Publication must be a MavenPublication");
        }
        maven.pom(pom -> {
            pom.scm(scm -> {
                scm.getConnection().set("scm:git:git://github.com/lukebemishprojects/"+repo+".git");
                scm.getUrl().set("https://github.com/lukebemishprojects/"+repo);
            });
            pom.developers(developers -> {
                developers.developer(developer -> {
                    developer.getId().set("lukebemish");
                    developer.getName().set("Luke Bemish");
                    developer.getEmail().set("lukebemish@lukebemish.dev");
                    developer.getUrl().set("https://github.com/lukebemish");
                });
            });
            pom.issueManagement(issues -> {
                issues.getUrl().set("https://github.com/lukebemishprojects/"+repo+"/issues");
            });
            pom.setPackaging("jar");
            pom.getUrl().set("https://github.com/lukebemishprojects/"+repo);
            pom.licenses(licenses -> {
                licenses.license(l -> {
                    l.getUrl().set("https://spdx.org/licenses/"+license);
                    l.getName().set(license);
                });
            });
        });
    }
}
