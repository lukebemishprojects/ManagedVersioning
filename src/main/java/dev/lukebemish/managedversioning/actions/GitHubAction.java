package dev.lukebemish.managedversioning.actions;

import dev.lukebemish.managedversioning.Constants;
import groovy.lang.Closure;
import groovy.lang.DelegatesTo;
import groovy.transform.stc.ClosureParams;
import groovy.transform.stc.SimpleType;
import org.gradle.api.Action;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Nested;
import org.gradle.util.Configurable;

import javax.inject.Inject;
import java.util.*;

public abstract class GitHubAction implements Configurable<GitHubAction> {
    @Input
    public abstract Property<String> getConcurrency();
    @Input
    public abstract ListProperty<String> getOnBranches();
    @Input
    public abstract Property<Boolean> getPullRequest();
    @Input
    public abstract Property<Boolean> getWorkflowDispatch();
    @Input
    public abstract ListProperty<String> getCompletedWorkflows();
    @Nested
    public abstract ListProperty<Job> getJobs();
    @Input
    public abstract Property<String> getPrettyName();

    private final ObjectFactory objectFactory;
    private final String name;

    @Inject
    public GitHubAction(String name, ObjectFactory objectFactory) {
        this.name = name;
        this.objectFactory = objectFactory;
        this.getPullRequest().convention(false);
        this.getWorkflowDispatch().convention(false);
        this.getConcurrency().convention("ci-${{ github.ref }}");
        String processedName = Arrays.stream(name.split("_")).map(s -> s.substring(0, 1).toUpperCase(Locale.ROOT) + s.substring(1)).reduce("", String::concat);
        this.getPrettyName().convention(processedName);
    }

    @Input
    public String getName() {
        return name;
    }

    public Job job(Action<Job> action) {
        Job job = objectFactory.newInstance(Job.class, objectFactory);
        action.execute(job);
        this.getJobs().add(job);
        return job;
    }

    public GradleJob gradleJob(Action<GradleJob> action) {
        GradleJob gradleJob = objectFactory.newInstance(GradleJob.class, objectFactory);
        action.execute(gradleJob);
        this.getJobs().add(gradleJob);
        return gradleJob;
    }

    public GradleJob gradleJob(
        @DelegatesTo(value = GradleJob.class, strategy = Closure.DELEGATE_FIRST)
        @ClosureParams(value = SimpleType.class, options = "dev.lukebemish.managedversioning.actions.GradleJob")
        Closure<?> closure
    ) {
        GradleJob gradleJob = objectFactory.newInstance(GradleJob.class, objectFactory);
        closure.setDelegate(gradleJob);
        closure.setResolveStrategy(Closure.DELEGATE_FIRST);
        closure.call(gradleJob);
        this.getJobs().add(gradleJob);
        return gradleJob;
    }

    public Job testReportJob(Action<TestReportJob> action) {
        TestReportJob testReportJob = objectFactory.newInstance(TestReportJob.class, objectFactory);
        action.execute(testReportJob);
        Job job = testReportJob.create();
        this.getJobs().add(job);
        return job;
    }

    public Job testReportJob(
        @DelegatesTo(value = TestReportJob.class, strategy = Closure.DELEGATE_FIRST)
        @ClosureParams(value = SimpleType.class, options = "dev.lukebemish.managedversioning.actions.TestReportJob")
        Closure<?> closure
    ) {
        TestReportJob testReportJob = objectFactory.newInstance(TestReportJob.class, objectFactory);
        closure.setDelegate(testReportJob);
        closure.setResolveStrategy(Closure.DELEGATE_FIRST);
        closure.call(testReportJob);
        Job job = testReportJob.create();
        this.getJobs().add(job);
        return job;
    }

    Object resolve() {
        Map<String, Object> action = new HashMap<>();
        action.put("name", getPrettyName().get());
        action.put("concurrency", this.getConcurrency().get());
        Map<String, Object> on = new HashMap<>();
        var branches = this.getOnBranches().get();
        if (!branches.isEmpty()) {
            on.put("push", Map.of("branches", branches));
        }
        if (this.getPullRequest().get()) {
            on.put("pull_request", Map.of());
        }
        if (this.getWorkflowDispatch().get()) {
            on.put("workflow_dispatch", Map.of());
        }
        if (!this.getCompletedWorkflows().get().isEmpty()) {
            on.put("workflow_run", Map.of(
                "workflows", this.getCompletedWorkflows().get(),
                "types", List.of("completed")
            ));
        }
        if (!on.isEmpty()) {
            action.put("on", on);
        }
        Map<String, Object> jobs = new HashMap<>();
        for (var job : this.getJobs().get()) {
            jobs.put(job.getName().get(), job.resolve());
        }
        action.put("jobs", jobs);
        return action;
    }

    @Override
    public GitHubAction configure(Closure cl) {
        cl.setDelegate(this);
        cl.call(this);
        return this;
    }

    public void publishPullRequestAction(String user, String paths, String pullRequestRunAction) {
        this.getCompletedWorkflows().add(pullRequestRunAction);
        this.job(job -> {
            job.getName().set("publish");
            job.getIf().set("${{ github.event.workflow_run.event == 'pull_request' && github.event.workflow_run.conclusion == 'success' }}");
            job.step(step -> {
                step.getName().set("Checkout Artifact Sync");
                step.getUses().set(Constants.Versions.CHECKOUT);
                step.getWith().put("repository", "lukebemish/artifact-sync");
                step.getWith().put("ref", "refs/heads/main");
                step.getWith().put("persist-credentials", false);
            });
            job.step(step -> step.getRun().set("mkdir repo"));
            job.step(step -> {
                step.getName().set("Download Artifacts");
                step.getId().set("download_artifacts");
                step.getUses().set(Constants.Versions.GITHUB_SCRIPT);
                step.getWith().put("script", getUnpackScript());
            });
            job.step(step -> {
                step.getName().set("Unpack Artifacts");
                step.getRun().set("unzip repo.zip -d repo");
            });
            job.step(step -> {
                step.getName().set("Publish Artifacts");
                step.getRun().set("python3 run.py");
                step.getEnv().put("MAVEN_USER", user);
                step.secret("MAVEN_PASSWORD", Constants.PR_MAVEN_PASSWORD);
                step.getEnv().put("MAVEN_URL", "https://maven.lukebemish.dev/pullrequests/");
                step.getEnv().put("ALLOWED_VERSION", "*-pr${{ steps.download_artifacts.outputs.result }}");
                step.getEnv().put("ALLOWED_PATHS", paths);
            });
        });
    }

    private String getUnpackScript() {
        return """
            const response = await github.rest.search.issuesAndPullRequests({
                q: 'repo:${{ github.repository }} is:pr sha:${{ github.event.workflow_run.head_sha }}',
                per_page: 1,
            })
            const items = response.data.items
            if (items.length < 1) {
                console.error('No PRs found')
                return
            }
            const pullRequestNumber = items[0].number
            let allArtifacts = await github.rest.actions.listWorkflowRunArtifacts({
               owner: context.repo.owner,
               repo: context.repo.repo,
               run_id: context.payload.workflow_run.id,
            });
            let matchArtifact = allArtifacts.data.artifacts.filter((artifact) => {
              return artifact.name == "artifacts"
            })[0];
            let download = await github.rest.actions.downloadArtifact({
               owner: context.repo.owner,
               repo: context.repo.repo,
               artifact_id: matchArtifact.id,
               archive_format: 'zip',
            });
            let fs = require('fs');
            fs.writeFileSync(`${process.env.GITHUB_WORKSPACE}/repo.zip`, Buffer.from(download.data));""";
            return pullRequestNumber
    }
}
