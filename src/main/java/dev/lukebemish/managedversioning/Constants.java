package dev.lukebemish.managedversioning;

public final class Constants {
    private Constants() {}

    public static final String PR_NUMBER = "PR_NUMBER";
    public static final String SNAPSHOT_MAVEN_PASSWORD = "SNAPSHOT_MAVEN_PASSWORD";
    public static final String SNAPSHOT_MAVEN_URL = "SNAPSHOT_MAVEN_URL";
    public static final String SNAPSHOT_MAVEN_USER = "SNAPSHOT_MAVEN_USER";
    public static final String RELEASE_MAVEN_PASSWORD = "RELEASE_MAVEN_PASSWORD";
    public static final String RELEASE_MAVEN_URL = "RELEASE_MAVEN_URL";
    public static final String RELEASE_MAVEN_USER = "RELEASE_MAVEN_USER";
    public static final String PR_MAVEN_PASSWORD = "PR_MAVEN_PASSWORD";
    public static final String CENTRAL_PASSWORD = "SONATYPE_PASSWORD";
    public static final String CENTRAL_USER = "SONATYPE_USER";
    public static final String GPG_KEY = "GPG_KEY";
    public static final String GPG_PASSWORD = "GPG_PASSWORD";
    public static final String BUILD_CACHE_PASSWORD = "BUILD_CACHE_PASSWORD";
    public static final String BUILD_CACHE_USER = "BUILD_CACHE_USER";
    public static final String BUILD_CACHE_URL = "BUILD_CACHE_URL";

    public static final String CURSEFORGE_KEY = "CURSEFORGE_KEY";
    public static final String MODRINTH_KEY = "MODRINTH_KEY";

    public static final class Versions {

        public static final String UPLOAD_ARTIFACT = "actions/upload-artifact@v4";
        public static final String DOWNLOAD_ARTIFACT = "actions/download-artifact@v4";
        public static final String CHECKOUT = "actions/checkout@v4";
        public static final String WRAPPER_VALIDATION = "gradle/actions/wrapper-validation@v3";
        public static final String DEPENDENCY_SUBMISSION = "mikepenz/gradle-dependency-submission@v0.9.0";
        public static final String GITHUB_SCRIPT = "actions/github-script@v7";
        public static final String CACHE_RESTORE = "actions/cache/restore@v4";
        public static final String CACHE_BOTH = "actions/cache@v4";
        public static final String GRADLE = "gradle/actions/setup-gradle@v3";
        public static final String TEST_REPORTER = "dorny/test-reporter@v1";
        public static final String SETUP_GIT_USER = "fregante/setup-git-user@v2";

        private Versions() {}
    }
}
