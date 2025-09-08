package com.example.jenkins;

import hudson.Extension;
import hudson.model.Item;
import hudson.model.TaskListener;
import hudson.plugins.git.GitSCM;
import hudson.scm.SCM;
import hudson.util.FormValidation;
import jenkins.branch.BranchBuildStrategy;
import jenkins.branch.BranchBuildStrategyDescriptor;
import jenkins.model.Jenkins;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMRevision;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.SCMSourceOwner;
import org.jenkinsci.plugins.github_branch_source.GitHubSCMSource;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;
import org.kohsuke.github.GHCommit;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.domains.URIRequirementBuilder;

import java.io.IOException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * Build-Strategie für Multibranch Pipelines, die Builds basierend auf Änderungen in bestimmten Verzeichnissen filtert.
 * Verwendet reguläre Ausdrücke für die Verzeichnisspezifikation und prüft Änderungen über die GitHub API.
 */
public class GitDirectoryChangeBuildStrategy extends BranchBuildStrategy {

    private static final Logger LOGGER = Logger.getLogger(GitDirectoryChangeBuildStrategy.class.getName());

    private final List<String> directoryRegexes;
    private final String credentialsId;
    private final String githubApiUrl;
    private final boolean buildOnBranchChange;
    private final boolean buildOnPullRequestChange;

    @DataBoundConstructor
    public GitDirectoryChangeBuildStrategy(List<String> directoryRegexes, String credentialsId, String githubApiUrl,
                                         boolean buildOnBranchChange, boolean buildOnPullRequestChange) {
        this.directoryRegexes = directoryRegexes;
        this.credentialsId = credentialsId;
        this.githubApiUrl = githubApiUrl != null ? githubApiUrl : "https://api.github.com";
        this.buildOnBranchChange = buildOnBranchChange;
        this.buildOnPullRequestChange = buildOnPullRequestChange;
    }

    public List<String> getDirectoryRegexes() {
        return directoryRegexes;
    }

    public String getCredentialsId() {
        return credentialsId;
    }

    public String getGithubApiUrl() {
        return githubApiUrl;
    }

    public boolean isBuildOnBranchChange() {
        return buildOnBranchChange;
    }

    public boolean isBuildOnPullRequestChange() {
        return buildOnPullRequestChange;
    }

            @Override
        public boolean isAutomaticBuild(SCMSource source, SCMHead head, SCMRevision currRevision, SCMRevision prevRevision, SCMRevision lastSeenRevision, TaskListener listener) {
        if (directoryRegexes == null || directoryRegexes.isEmpty()) {
            LOGGER.fine("Keine Verzeichnis-Regexe konfiguriert, Build wird durchgeführt");
            return true;
        }

        if (!(source instanceof GitHubSCMSource)) {
            LOGGER.warning("Quelle ist kein GitHubSCMSource, Build wird durchgeführt");
            return true;
        }

        GitHubSCMSource gitHubSource = (GitHubSCMSource) source;
        
        // Prüfe ob es sich um einen Branch oder PR handelt
        if (head instanceof org.jenkinsci.plugins.github_branch_source.PullRequestSCMHead && !buildOnPullRequestChange) {
            LOGGER.fine("PR-Build ist deaktiviert");
            return false;
        }
        
        if (!(head instanceof org.jenkinsci.plugins.github_branch_source.PullRequestSCMHead) && !buildOnBranchChange) {
            LOGGER.fine("Branch-Build ist deaktiviert");
            return false;
        }

        try {
            return hasChangesInDirectories(gitHubSource, head, currRevision, prevRevision);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Fehler beim Prüfen der Verzeichnisänderungen, Build wird durchgeführt", e);
            return true; // Fail-open: Bei Fehlern wird der Build durchgeführt
        }
    }

    private boolean hasChangesInDirectories(GitHubSCMSource source, SCMHead head, SCMRevision currRevision, SCMRevision prevRevision) 
            throws IOException {
        
        if (prevRevision == null) {
            LOGGER.fine("Kein vorheriger Revision vorhanden, Build wird durchgeführt");
            return true;
        }

        String token = getGitHubToken(source);
        if (token == null) {
            LOGGER.warning("Kein GitHub Token verfügbar, Build wird durchgeführt");
            return true;
        }

        GitHub github = new GitHubBuilder()
                .withEndpoint(githubApiUrl)
                .withOAuthToken(token)
                .build();

        String repoOwner = source.getRepoOwner();
        String repository = source.getRepository();
        GHRepository ghRepo = github.getRepository(repoOwner + "/" + repository);

        String currSha = currRevision.getHead().toString();
        String prevSha = prevRevision.getHead().toString();

        LOGGER.fine("Prüfe Änderungen zwischen " + prevSha + " und " + currSha);

        GHCommit commit = ghRepo.getCommit(currSha);
        List<GHCommit.File> files = commit.getFiles();

        for (GHCommit.File file : files) {
            String fileName = file.getFileName();
            LOGGER.finest("Prüfe Datei: " + fileName);

            for (String regex : directoryRegexes) {
                try {
                    Pattern pattern = Pattern.compile(regex);
                    if (pattern.matcher(fileName).matches()) {
                        LOGGER.fine("Datei " + fileName + " matcht Regex " + regex + ", Build wird durchgeführt");
                        return true;
                    }
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "Fehler beim Auswerten der Regex " + regex, e);
                }
            }
        }

        LOGGER.fine("Keine Änderungen in den konfigurierten Verzeichnissen gefunden");
        return false;
    }

    private String getGitHubToken(GitHubSCMSource source) {
        // Verwende zuerst die konfigurierten Credentials
        if (credentialsId != null && !credentialsId.isEmpty()) {
            StringCredentials credentials = CredentialsProvider.findCredentialById(
                credentialsId, 
                StringCredentials.class, 
                null
            );
            if (credentials != null) {
                return credentials.getSecret().getPlainText();
            }
        }

        // Fallback: Verwende die Credentials der GitHubSCMSource
        String sourceCredentialsId = source.getCredentialsId();
        if (sourceCredentialsId != null && !sourceCredentialsId.isEmpty()) {
            StringCredentials credentials = CredentialsProvider.findCredentialById(
                sourceCredentialsId, 
                StringCredentials.class, 
                null
            );
            if (credentials != null) {
                return credentials.getSecret().getPlainText();
            }
        }

        return null;
    }

    @Extension
    public static class DescriptorImpl extends BranchBuildStrategyDescriptor {

        public String getDisplayName() {
            return "Nur bauen bei Änderungen in Verzeichnissen (Regex)";
        }

        public boolean isApplicable(SCMSource source) {
            return source instanceof GitHubSCMSource;
        }

        public StandardListBoxModel doFillCredentialsIdItems(@QueryParameter String credentialsId) {
            if (!Jenkins.get().hasPermission(Item.CONFIGURE)) {
                return new StandardListBoxModel();
            }
            StandardListBoxModel model = new StandardListBoxModel();
            model.withEmptySelection();
            model.withMatching(
                    CredentialsMatchers.anyOf(
                            CredentialsMatchers.instanceOf(StringCredentials.class)
                    ),
                    CredentialsProvider.lookupCredentials(
                            StandardCredentials.class,
                            Jenkins.get(),
                            null,
                            URIRequirementBuilder.fromUri("https://api.github.com").build()
                    )
            );
            return model;
        }

        public FormValidation doCheckCredentialsId(@QueryParameter String value) {
            if (value == null || value.isEmpty()) {
                return FormValidation.ok("Verwendet die Credentials der GitHub-Quelle");
            }
            return FormValidation.ok();
        }
    }
}
